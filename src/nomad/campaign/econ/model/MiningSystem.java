package nomad.campaign.econ.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import nomad.campaign.econ.Commod;

public final class MiningSystem {

  private MiningSystem() {
  }

  public interface Conditions {
    boolean has(String conditionId);
  }

  private static final float[] TIER_MULTS = { 0.5f, 1.0f, 1.5f, 2.0f, 3.0f };

  private static final String[][] DEPOSITS = {
      { Commod.ORE, "ore_sparse", "ore_moderate", "ore_abundant", "ore_rich", "ore_ultrarich" },
      { Commod.RARE_ORE, "rare_ore_sparse", "rare_ore_moderate", "rare_ore_abundant",
          "rare_ore_rich", "rare_ore_ultrarich" },
      { Commod.ORGANICS, "organics_trace", "organics_common", "organics_abundant",
          "organics_plentiful", null },
      { Commod.VOLATILES, "volatiles_trace", "volatiles_diffuse", "volatiles_abundant",
          "volatiles_plentiful", null } };

  public static Map<String, Float> tiers(Conditions c) {
    Map<String, Float> out = new LinkedHashMap<String, Float>();
    if (c == null) {
      return out;
    }
    for (String[] raw : DEPOSITS) {
      for (int i = TIER_MULTS.length; i >= 1; i--) {
        if (raw[i] != null && c.has(raw[i])) {
          out.put(raw[0], Float.valueOf(TIER_MULTS[i - 1]));
          break;
        }
      }
    }
    return out;
  }

  public static Conditions conditions(final Collection<String> ids) {
    return new Conditions() {
      @Override
      public boolean has(String conditionId) {
        return ids != null && ids.contains(conditionId);
      }
    };
  }

  public static final class Planet {

    public final String name;
    public final float distance;
    public final boolean surveyed;
    public final Conditions conditions;

    public Planet(String name, float distance, boolean surveyed, Conditions conditions) {
      this.name = name == null ? "" : name;
      this.distance = Math.max(0f, distance);
      this.surveyed = surveyed;
      this.conditions = conditions;
    }

    public Planet at(float newDistance) {
      return new Planet(name, newDistance, surveyed, conditions);
    }
  }

  public static Mining.Body body(Planet p) {
    return new Mining.Body(p.name, p.distance, tiers(p.conditions), p.surveyed);
  }

  public static Mining.Sources sources(boolean hyperspace, boolean asteroids,
      List<Planet> planets) {
    if (hyperspace) {
      return Mining.Sources.HYPERSPACE;
    }
    List<Mining.Body> bodies = new ArrayList<Mining.Body>();
    if (planets != null) {
      for (Planet p : planets) {
        if (p != null) {
          bodies.add(body(p));
        }
      }
    }
    return new Mining.Sources(false, asteroids, bodies);
  }

  public static Mining.Body inRange(List<Planet> planets, float radius) {
    if (planets == null) {
      return null;
    }
    Mining.Body best = null;
    for (Planet p : planets) {
      if (p == null || !p.surveyed || p.distance > radius
          || (best != null && p.distance >= best.distance)) {
        continue;
      }
      Mining.Body b = body(p);
      if (b.minable()) {
        best = b;
      }
    }
    return best;
  }

  public enum Kind {
    IN_RANGE,
    REMOTE,
    REMOTE_ASTEROIDS,
    IDLE_HYPERSPACE,
    IDLE_EMPTY,
    IDLE_OUT_OF_RANGE
  }

  public static final class Readout {

    public final Kind kind;
    public final String body;
    public final Map<String, Float> shares;

    Readout(Kind kind, String body, Map<String, Float> shares) {
      this.kind = kind;
      this.body = body == null ? "" : body;
      this.shares = shares == null ? Collections.<String, Float>emptyMap()
          : Collections.unmodifiableMap(new LinkedHashMap<String, Float>(shares));
    }

    public boolean idle() {
      return shares.isEmpty();
    }
  }

  public static Readout readout(Mining.Site site, boolean hyperspace, boolean remoteOn,
      Rules k) {
    if (hyperspace) {
      return new Readout(Kind.IDLE_HYPERSPACE, "", null);
    }
    Map<String, Float> shares = Mining.tiers(site, remoteOn, k);
    if (site != null && site.inRange != null && site.inRange.minable()) {
      return new Readout(Kind.IN_RANGE, site.inRange.name, shares);
    }
    if (!remoteOn) {
      return new Readout(Kind.IDLE_OUT_OF_RANGE, "", null);
    }
    if (shares.isEmpty() || site == null || site.system == null) {
      return new Readout(Kind.IDLE_EMPTY, "", null);
    }
    if (site.system.name.isEmpty()) {
      return new Readout(Kind.REMOTE_ASTEROIDS, "", shares);
    }
    return new Readout(Kind.REMOTE, site.system.name, shares);
  }
}
