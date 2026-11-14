package nomad.campaign.econ.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import nomad.campaign.econ.Commod;

public final class Mining {

  private static final float DAY_EPS = 1e-3f;

  private Mining() {
  }

  public static final class Body {

    public final String name;
    public final float distance;
    public final Map<String, Float> tiers;
    public final boolean surveyed;

    public Body(String name, float distance, Map<String, Float> tiers) {
      this(name, distance, tiers, true);
    }

    public Body(String name, float distance, Map<String, Float> tiers, boolean surveyed) {
      this.name = name == null ? "" : name;
      this.distance = distance;
      this.tiers = Collections.unmodifiableMap(new LinkedHashMap<String, Float>(
          tiers == null ? Collections.<String, Float>emptyMap() : tiers));
      this.surveyed = surveyed;
    }

    public boolean minable() {
      if (!surveyed) {
        return false;
      }
      for (Float t : tiers.values()) {
        if (t != null && t.floatValue() > 0f) {
          return true;
        }
      }
      return false;
    }
  }

  public static final class Sources {

    public static final Sources HYPERSPACE = new Sources(true, false, null);

    public final boolean hyperspace;
    public final boolean asteroids;
    public final List<Body> bodies;

    public Sources(boolean hyperspace, boolean asteroids, List<Body> bodies) {
      this.hyperspace = hyperspace;
      this.asteroids = asteroids;
      this.bodies = Collections.unmodifiableList(new ArrayList<Body>(
          bodies == null ? Collections.<Body>emptyList() : bodies));
    }
  }

  public static final class Site {

    public static final Site NONE = new Site(null, null);

    public final Body inRange;
    public final Body system;

    public Site(Body inRange, Body system) {
      this.inRange = inRange;
      this.system = system;
    }

    public Site withInRange(Body body) {
      return new Site(body, system);
    }
  }

  public static float remoteTier(float tier, Rules k) {
    if (!(tier > 0f)) {
      return 0f;
    }
    return Math.min(k.remoteCap, k.remoteTierMult * tier);
  }

  public static Map<String, Float> remote(Body body, Rules k) {
    if (body == null || !body.minable()) {
      return Collections.emptyMap();
    }
    Map<String, Float> out = new LinkedHashMap<String, Float>();
    for (Map.Entry<String, Float> e : body.tiers.entrySet()) {
      float r = remoteTier(e.getValue() == null ? 0f : e.getValue().floatValue(), k);
      if (r > 0f) {
        out.put(e.getKey(), Float.valueOf(r));
      }
    }
    return out;
  }

  public static float remoteValue(Body body, Map<String, Float> ratePerTier,
      Map<String, Float> prices, Rules k) {
    float v = 0f;
    for (Map.Entry<String, Float> e : remote(body, k).entrySet()) {
      v += e.getValue().floatValue() * get(ratePerTier, e.getKey()) * get(prices, e.getKey());
    }
    return v;
  }

  public static Body best(List<Body> bodies, Map<String, Float> ratePerTier,
      Map<String, Float> prices, Rules k) {
    if (bodies == null) {
      return null;
    }
    Body best = null;
    float bestValue = 0f;
    for (Body b : bodies) {
      if (b == null || !b.minable()) {
        continue;
      }
      float v = remoteValue(b, ratePerTier, prices, k);
      if (best == null) {
        best = b;
        bestValue = v;
        continue;
      }
      float slack = 1e-5f * Math.max(1f, Math.max(Math.abs(v), Math.abs(bestValue)));
      if (v > bestValue + slack || (Math.abs(v - bestValue) <= slack
          && b.distance < best.distance)) {
        best = b;
        bestValue = v;
      }
    }
    return best;
  }

  public static Body asteroids(Rules k) {
    Map<String, Float> tiers = new LinkedHashMap<String, Float>();
    tiers.put(Commod.ORE, Float.valueOf(k.remoteAsteroidTier));
    return new Body("", 0f, tiers);
  }

  public static Body systemBody(Sources src, Map<String, Float> ratePerTier,
      Map<String, Float> prices, Rules k) {
    if (src == null || src.hyperspace) {
      return null;
    }
    Body b = best(src.bodies, ratePerTier, prices, k);
    if (b != null) {
      return b;
    }
    return src.asteroids ? asteroids(k) : null;
  }

  public static Map<String, Float> tiers(Site site, boolean remoteOn, Rules k) {
    if (site == null) {
      return Collections.emptyMap();
    }
    Map<String, Float> out = new LinkedHashMap<String, Float>();
    if (site.inRange != null && site.inRange.minable()) {
      for (Map.Entry<String, Float> e : site.inRange.tiers.entrySet()) {
        if (e.getValue() != null && e.getValue().floatValue() > 0f) {
          out.put(e.getKey(), e.getValue());
        }
      }
    }
    if (remoteOn) {
      for (Map.Entry<String, Float> e : remote(site.system, k).entrySet()) {
        Float have = out.get(e.getKey());
        if (have == null || have.floatValue() < e.getValue().floatValue()) {
          out.put(e.getKey(), e.getValue());
        }
      }
    }
    return out.isEmpty() ? Collections.<String, Float>emptyMap() : out;
  }

  public static boolean remoteTarget(WorkState w) {
    if (w == null) {
      return false;
    }
    return w.remoteSwitching ? !w.remote : w.remote;
  }

  public static boolean remotePress(WorkState w) {
    return w != null && !remoteTarget(w);
  }

  public static boolean remoteStarting(WorkState w) {
    return w != null && w.remoteSwitching && !w.remote;
  }

  public static boolean pressRemote(WorkState w, boolean on) {
    if (w == null || remoteTarget(w) == on) {
      return false;
    }
    if (w.remoteSwitching) {
      w.remoteSwitching = false;
      w.remoteDays = 0f;
      return false;
    }
    if (!on) {
      w.remote = false;
      w.remoteDays = 0f;
      return true;
    }
    w.remoteSwitching = true;
    w.remoteDays = 0f;
    return false;
  }

  public static boolean advanceRemote(WorkState w, float days, Rules k) {
    if (w == null || !w.remoteSwitching || !(days > 0f)) {
      return false;
    }
    w.remoteDays += days;
    float need = w.remote ? 0f : k.remoteOnDays;
    if (w.remoteDays + DAY_EPS < need) {
      return false;
    }
    w.remote = !w.remote;
    w.remoteSwitching = false;
    w.remoteDays = 0f;
    return true;
  }

  public static float remoteDaysLeft(WorkState w, Rules k) {
    if (!remoteStarting(w)) {
      return 0f;
    }
    return Math.max(0f, k.remoteOnDays - w.remoteDays);
  }

  private static float get(Map<String, Float> m, String key) {
    if (m == null) {
      return 0f;
    }
    Float v = m.get(key);
    return v == null ? 0f : v.floatValue();
  }
}
