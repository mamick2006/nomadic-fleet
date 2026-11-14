package nomad.campaign.econ.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import nomad.campaign.econ.Commod;

public final class GlueRules {

  private GlueRules() {
  }

  public static final float EPS = 1e-3f;

  public static float spare(float held, float reserve) {
    return Math.max(0f, held - Math.max(0f, reserve));
  }

  public static float elapsedDays(boolean firstTick, float daysSinceLast, int ticksPerMonth) {
    float interval = 30f / Math.max(1, ticksPerMonth);
    if (firstTick) {
      return interval;
    }
    if (!(daysSinceLast > 0f)) {
      return 0f;
    }
    return Math.min(daysSinceLast, 2f * interval);
  }

  public static boolean foundersDue(long foundedAtTs, float berths) {
    return foundedAtTs != 0L && berths > 0f;
  }

  public static float monthJoined(float before, float after, float in, float out) {
    float rest = (after - before) - (Math.max(0f, in) - Math.max(0f, out));
    return Math.max(0f, in) + Math.max(0f, rest);
  }

  public static float monthLeft(float before, float after, float in, float out) {
    float rest = (after - before) - (Math.max(0f, in) - Math.max(0f, out));
    return Math.max(0f, out) + Math.max(0f, -rest);
  }

  public static float[] monthCounts(float before, float after, float in, float out, float made,
      float consumed) {
    float m = Math.max(0f, made);
    float u = Math.max(0f, consumed);
    float people = after - m + u;
    return new float[] {
        monthJoined(before, people, in, out), monthLeft(before, people, in, out), m, u};
  }

  public static float founders(float residents, float berths, float foundingResidents,
      float berthFrac) {
    float target = Math.max(0f, Math.min(foundingResidents, berthFrac * berths));
    return Math.max(0f, target - Math.max(0f, residents));
  }

  public static Map<String, Float> foundingTargets(float foundingTarget) {
    Map<String, Float> out = new LinkedHashMap<String, Float>();
    float t = foundingTarget > 0f ? (float) Math.ceil(foundingTarget) : 0f;
    for (String good : Commod.STORED) {
      out.put(good, Float.valueOf(Commod.TRACKED.contains(good) ? t : 0f));
    }
    return out;
  }

  public static List<WorkState> defined(List<WorkState> saved, WorkCatalog catalog) {
    List<WorkState> out = new ArrayList<WorkState>();
    boolean keepAll = catalog == null || catalog.isEmpty();
    for (WorkState w : saved) {
      if (w == null || w.id == null) {
        continue;
      }
      if (keepAll || catalog.work(w.id) != null) {
        out.add(w);
      }
    }
    return out;
  }

  public static List<String> working(TickSheet t) {
    List<String> out = new ArrayList<String>();
    for (TickSheet.WorkRun run : t.runs.values()) {
      WorkDef def = t.catalog.work(run.id);
      if (def == null || run.def == null) {
        continue;
      }
      boolean atWork = def.isProduction() ? run.status == WorkStatus.RUNNING : run.effectOn;
      if (atWork) {
        out.add(run.id);
      }
    }
    return out;
  }

  public static float pointsWanted(float queueCost, float queueBanked, float unspent) {
    return Math.max(0f, queueCost - queueBanked - unspent);
  }

  public static Map<String, Float> cargoNet(TickSheet t) {
    Map<String, Float> net = new LinkedHashMap<String, Float>();
    add(net, t.toCargo, 1f);
    add(net, t.fromCargo, -1f);
    add(net, t.soldFromCargo, -1f);
    Map<String, Float> out = new LinkedHashMap<String, Float>();
    for (Map.Entry<String, Float> e : net.entrySet()) {
      if (Math.abs(e.getValue().floatValue()) > EPS) {
        out.put(e.getKey(), e.getValue());
      }
    }
    return out;
  }

  public static Map<String, Float> soldUnits(TickSheet t) {
    Map<String, Float> units = new LinkedHashMap<String, Float>();
    add(units, t.sold, 1f);
    add(units, t.soldFromCargo, 1f);
    return units;
  }

  public static Map<String, Float> saleShares(Map<String, Float> units, Map<String, Float> prices,
      float saleCredits) {
    Map<String, Float> out = new LinkedHashMap<String, Float>();
    if (!(saleCredits > 0f)) {
      return out;
    }
    float total = 0f;
    for (Map.Entry<String, Float> e : units.entrySet()) {
      total += value(e, prices);
    }
    if (!(total > 0f)) {
      return out;
    }
    for (Map.Entry<String, Float> e : units.entrySet()) {
      float v = value(e, prices);
      if (v > 0f) {
        out.put(e.getKey(), Float.valueOf(saleCredits * v / total));
      }
    }
    return out;
  }

  public static Map<String, Float> creditRows(TickSheet t, Map<String, Float> shares) {
    Map<String, Float> rows = new LinkedHashMap<String, Float>();
    float total = Math.max(0f, t.credits);
    float claimed = 0f;
    for (TickSheet.WorkRun run : t.runs.values()) {
      Float made = run.made.get(Works.CREDITS);
      if (made != null && made.floatValue() > 0f) {
        rows.put(run.id, made);
        claimed += made.floatValue();
      }
    }
    if (claimed > total) {
      for (Map.Entry<String, Float> e : rows.entrySet()) {
        e.setValue(Float.valueOf(e.getValue().floatValue() * total / claimed));
      }
      claimed = total;
    }
    float brokerage = total - claimed + (shares.isEmpty() ? Math.max(0f, t.saleCredits) : 0f);
    if (brokerage > 0f) {
      Float had = rows.get(Works.BROKERAGE);
      rows.put(Works.BROKERAGE, Float.valueOf((had == null ? 0f : had.floatValue()) + brokerage));
    }
    Map<String, Float> out = new LinkedHashMap<String, Float>();
    for (Map.Entry<String, Float> e : rows.entrySet()) {
      if (e.getValue().floatValue() > 0f) {
        out.put(e.getKey(), e.getValue());
      }
    }
    return out;
  }

  public static List<FlotillaEvent> historyEvents(List<FlotillaEvent> events) {
    List<FlotillaEvent> out = new ArrayList<FlotillaEvent>();
    boolean pausedDone = false;
    boolean resumedDone = false;
    for (FlotillaEvent e : events) {
      switch (e.type) {
        case MINED:
          break;
        case PAUSED:
          if (!pausedDone) {
            pausedDone = true;
            out.add(e);
          }
          break;
        case RESUMED:
          if (!resumedDone) {
            resumedDone = true;
            out.add(e);
          }
          break;
        default:
          out.add(e);
          break;
      }
    }
    return out;
  }

  public static boolean levelAlarm(NeedsLevel now, float oldOrdinal) {
    return now != null && !now.atLeastNormal() && now.ordinal() < Math.round(oldOrdinal);
  }

  private static float value(Map.Entry<String, Float> units, Map<String, Float> prices) {
    Float price = prices.get(units.getKey());
    if (price == null || units.getValue() == null) {
      return 0f;
    }
    return Math.max(0f, units.getValue().floatValue()) * Math.max(0f, price.floatValue());
  }

  private static void add(Map<String, Float> into, Map<String, Float> from, float sign) {
    for (Map.Entry<String, Float> e : from.entrySet()) {
      if (e.getValue() == null) {
        continue;
      }
      Float had = into.get(e.getKey());
      float v = (had == null ? 0f : had.floatValue()) + sign * e.getValue().floatValue();
      into.put(e.getKey(), Float.valueOf(v));
    }
  }
}
