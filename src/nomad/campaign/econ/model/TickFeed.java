package nomad.campaign.econ.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import nomad.campaign.econ.Commod;

public final class TickFeed {

  private TickFeed() {
  }

  public enum Kind {
    LEVEL_FELL,
    LEVEL_FELL_LOW,
    LEVEL_ROSE,
    LEVEL_ROSE_LOW,
    LEVEL_ROSE_BACK,
    WORKS_SUSPENDED,
    WORKS_UNSTAFFED,
    STOCK_LOST,
    UPKEEP_OWED,
    CARGO_FULL,
    FUEL_FULL,
    BUILDS_DONE,
    WORKS_DISABLED
  }

  public static final class Line {

    public final Kind kind;
    public final String subject;
    public final int count;
    public final float amount;

    Line(Kind kind, String subject, int count, float amount) {
      this.kind = kind;
      this.subject = subject;
      this.count = count;
      this.amount = amount;
    }
  }

  public static List<Line> lines(List<FlotillaEvent> events, boolean orderGiven) {
    List<Line> out = new ArrayList<Line>();
    FlotillaEvent level = null;
    FlotillaEvent owed = null;
    FlotillaEvent firstBuild = null;
    int builds = 0;
    String firstLost = null;
    int goodsLost = 0;
    float unitsLost = 0f;
    String firstKept = null;
    int goodsKept = 0;
    float unitsKept = 0f;
    float fuelKept = 0f;
    for (FlotillaEvent e : events) {
      switch (e.type) {
        case HOLD_FULL:
          if (Stores.inTanks(e.subject)) {
            fuelKept += e.amount;
          } else {
            if (firstKept == null) {
              firstKept = e.subject;
            }
            goodsKept++;
            unitsKept += e.amount;
          }
          break;
        case LEVEL_CHANGED:
          level = e;
          break;
        case UPKEEP_OWED:
          owed = e;
          break;
        case BUILD_DONE:
          if (firstBuild == null) {
            firstBuild = e;
          }
          builds++;
          break;
        case STOCK_LOST:
          if (firstLost == null) {
            firstLost = e.subject;
          }
          goodsLost++;
          unitsLost += e.amount;
          break;
        default:
          break;
      }
    }
    Set<String> suspended = standing(events, FlotillaEvent.Type.WORK_SUSPENDED,
        FlotillaEvent.Type.WORK_RESUMED);
    Set<String> disabled = standing(events, FlotillaEvent.Type.WORK_PREREQ_LOST,
        FlotillaEvent.Type.WORK_PREREQ_MET);
    disabled.removeAll(suspended);
    if (!orderGiven) {
      Line levelLine = level == null ? null : levelLine(level);
      if (levelLine != null) {
        out.add(levelLine);
      }
      Set<String> unstaffed = standing(events, FlotillaEvent.Type.WORK_UNSTAFFED,
          FlotillaEvent.Type.WORK_STAFFED);
      unstaffed.removeAll(suspended);
      if (!suspended.isEmpty()) {
        out.add(new Line(Kind.WORKS_SUSPENDED, suspended.iterator().next(), suspended.size(), 0f));
      }
      if (!unstaffed.isEmpty()) {
        out.add(new Line(Kind.WORKS_UNSTAFFED, unstaffed.iterator().next(), unstaffed.size(), 0f));
      }
    }
    if (!disabled.isEmpty()) {
      out.add(new Line(Kind.WORKS_DISABLED, disabled.iterator().next(), disabled.size(), 0f));
    }
    if (!orderGiven) {
      if (goodsLost > 0) {
        out.add(new Line(Kind.STOCK_LOST, firstLost, goodsLost, unitsLost));
      }
      if (owed != null) {
        out.add(new Line(Kind.UPKEEP_OWED, null, 1, owed.amount));
      }
    }
    if (goodsKept > 0) {
      out.add(new Line(Kind.CARGO_FULL, firstKept, goodsKept, unitsKept));
    }
    if (fuelKept > 0f) {
      out.add(new Line(Kind.FUEL_FULL, Commod.FUEL, 1, fuelKept));
    }
    if (firstBuild != null) {
      out.add(new Line(Kind.BUILDS_DONE, firstBuild.subject, builds, firstBuild.amount));
    }
    return out;
  }

  private static Line levelLine(FlotillaEvent e) {
    NeedsLevel now = level(e.subject);
    if (now == null) {
      return null;
    }
    int old = Math.round(e.amount);
    Kind kind;
    if (now.ordinal() < old) {
      kind = GlueRules.levelAlarm(now, e.amount) ? Kind.LEVEL_FELL_LOW : Kind.LEVEL_FELL;
    } else if (now.ordinal() > old) {
      boolean wasSpartan = old >= NeedsLevel.SPARTAN.ordinal();
      kind = !now.atLeastSpartan() ? Kind.LEVEL_ROSE_LOW
          : wasSpartan ? Kind.LEVEL_ROSE : Kind.LEVEL_ROSE_BACK;
    } else {
      return null;
    }
    return new Line(kind, now.key, 1, e.amount);
  }

  private static Set<String> standing(List<FlotillaEvent> events, FlotillaEvent.Type down,
      FlotillaEvent.Type up) {
    Set<String> out = new LinkedHashSet<String>();
    for (FlotillaEvent e : events) {
      if (e.type == down && e.subject != null) {
        out.add(e.subject);
      }
    }
    for (FlotillaEvent e : events) {
      if (e.type == up) {
        out.remove(e.subject);
      }
    }
    return out;
  }

  public static NeedsLevel level(String key) {
    for (NeedsLevel level : NeedsLevel.values()) {
      if (level.key.equals(key)) {
        return level;
      }
    }
    return null;
  }

  public static int orders(List<WorkState> works, Map<String, Float> targets, List<String> items,
      boolean detached) {
    int h = detached ? 1 : 2;
    for (WorkState w : works) {
      if (w == null || w.id == null) {
        continue;
      }
      int one = w.id.hashCode();
      one = 31 * one + w.size;
      one = 31 * one + w.buildTo;
      one = 31 * one + (w.mothballed ? 1 : 0);
      one = 31 * one + w.priority().ordinal();
      h += 31 * one + 7;
    }
    for (Map.Entry<String, Float> e : targets.entrySet()) {
      if (e.getKey() != null && e.getValue() != null && e.getValue().floatValue() > 0f) {
        h += 31 * (31 * e.getKey().hashCode() + Float.floatToIntBits(e.getValue().floatValue())) + 5;
      }
    }
    for (String item : items) {
      if (item != null) {
        h += 31 * item.hashCode() + 3;
      }
    }
    return h == 0 ? 1 : h;
  }

  public static boolean orderGiven(int ordersThen, int ordersNow, float residentsThen,
      float residentsNow) {
    if (ordersThen == 0) {
      return false;
    }
    return ordersThen != ordersNow || residentsNow <= residentsThen - 1f;
  }

  public static int owedAtMonthEnd(boolean detached, float suppliesOwed) {
    return detached && suppliesOwed >= 1f ? Math.round(suppliesOwed) : 0;
  }
}
