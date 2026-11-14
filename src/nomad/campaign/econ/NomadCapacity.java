package nomad.campaign.econ;

import java.util.List;

public class NomadCapacity {

  public static final float DEFAULT_PCT = 30f;

  public static class ShipStats {
    public final float baseCargo;
    public final float baseMinCrew;
    public final float baseMaxCrew;
    public final float cargoPct;
    public final float quartersPct;

    public ShipStats(float baseCargo, float baseMinCrew, float baseMaxCrew,
        float cargoPct, float quartersPct) {
      this.baseCargo = baseCargo;
      this.baseMinCrew = baseMinCrew;
      this.baseMaxCrew = baseMaxCrew;
      this.cargoPct = cargoPct;
      this.quartersPct = quartersPct;
    }
  }

  public static class Capacity {
    public final float space;
    public final float berths;

    public Capacity(float space, float berths) {
      this.space = space;
      this.berths = berths;
    }
  }

  public static float clampPct(float pct) {
    if (pct != pct) {
      return DEFAULT_PCT;
    }
    return Math.max(0f, Math.min(100f, pct));
  }

  public static float clampPct(float pct, float minPct) {
    float floor = clampPct(minPct);
    if (pct != pct) {
      return floor;
    }
    return Math.max(floor, Math.min(100f, pct));
  }

  public static float cargoMult(float cargoPct) {
    return 1f - clampPct(cargoPct) / 100f;
  }

  public static float crewMult(float quartersPct, float baseMinCrew, float baseMaxCrew) {
    if (baseMaxCrew <= 0f) {
      return 1f;
    }
    float skeleton = Math.max(0f, Math.min(1f, baseMinCrew / baseMaxCrew));
    return Math.max(skeleton, 1f - clampPct(quartersPct) / 100f);
  }

  public static Capacity shipDelta(ShipStats s) {
    if (s == null) {
      return new Capacity(0f, 0f);
    }
    float space = s.baseCargo * (1f - cargoMult(s.cargoPct));
    float berths = s.baseMaxCrew * (1f - crewMult(s.quartersPct, s.baseMinCrew, s.baseMaxCrew));
    return new Capacity(Math.max(0f, space), Math.max(0f, berths));
  }

  public static Capacity totals(List<ShipStats> ships) {
    float space = 0f;
    float berths = 0f;
    if (ships != null) {
      for (ShipStats s : ships) {
        Capacity c = shipDelta(s);
        space += c.space;
        berths += c.berths;
      }
    }
    return new Capacity(space, berths);
  }
}
