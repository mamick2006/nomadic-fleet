package nomad.campaign.econ.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class Requirement {

  private Requirement() {
  }

  public static final String PARAM_AMENITY = "needs_amenity";
  public static final String PARAM_RAISE = "needs_raise";

  private static final float EPS = 1e-5f;

  public static final class Part {

    public final String workId;
    public final int size;
    public final float share;

    Part(String workId, int size, float share) {
      this.workId = workId;
      this.size = size;
      this.share = share;
    }
  }

  public static final class Reading {

    public int workTiers;
    public int items;
    public float capped;
    public float overLimit;
    public float limit;
    public float amenity;
    public float raise;
    public float factor = 1f;
    public final List<Part> amenities = new ArrayList<Part>();
    public final List<Part> raises = new ArrayList<Part>();

    public float worksShare;
    public float itemsShare;

    public boolean atLimit() {
      return limit > 0f && capped + EPS >= limit;
    }

    public float change() {
      return factor - 1f;
    }
  }

  public static boolean counts(WorkState w) {
    return w != null && w.isBuilt() && !w.mothballed && !w.suspended && !w.standingDown
        && !w.prereqUnmet;
  }

  public static int tiers(WorkSize row) {
    if (row == null) {
      return 0;
    }
    if (row.params.containsKey(PARAM_AMENITY) || row.params.containsKey(PARAM_RAISE)) {
      return 0;
    }
    return row.size;
  }

  public static float share(WorkSize row, Rules k) {
    if (row == null) {
      return 0f;
    }
    return k.needsTierRelief * (row.param(PARAM_RAISE, 0f) - row.param(PARAM_AMENITY, 0f)
        - tiers(row));
  }

  public static boolean isOrdinary(WorkSize row) {
    return tiers(row) > 0;
  }

  public static Reading read(FlotillaState s, WorkCatalog catalog, Rules k) {
    return read(s.works, s.installedItems(), catalog, k, Collections.<String>emptySet());
  }

  public static Reading read(List<WorkState> works, List<String> items, WorkCatalog catalog,
      Rules k) {
    return read(works, items, catalog, k, Collections.<String>emptySet());
  }

  static Reading read(List<WorkState> works, List<String> items, WorkCatalog catalog, Rules k,
      Collection<String> returning) {
    Reading r = new Reading();
    float relief = k.needsTierRelief;
    r.limit = Math.max(0f, k.needsReliefCap);
    float amenityTiers = 0f;
    float raiseTiers = 0f;
    if (works != null) {
      for (WorkState w : works) {
        if (!counts(w) && !returns(w, returning)) {
          continue;
        }
        WorkSize row = catalog.size(w.id, w.size);
        if (row == null) {
          continue;
        }
        r.workTiers += tiers(row);
        float amenity = row.param(PARAM_AMENITY, 0f);
        if (amenity > 0f) {
          amenityTiers += amenity;
          r.amenities.add(new Part(w.id, w.size, -relief * amenity));
        }
        float raise = row.param(PARAM_RAISE, 0f);
        if (raise > 0f) {
          raiseTiers += raise;
          r.raises.add(new Part(w.id, w.size, relief * raise));
        }
      }
    }
    r.items = items == null ? 0 : items.size();
    r.worksShare = relief * r.workTiers;
    r.itemsShare = relief * r.items * k.needsItemTiers;
    float before = r.worksShare + r.itemsShare;
    r.capped = Math.min(r.limit, before);
    r.overLimit = Math.max(0f, before - r.capped);
    r.amenity = relief * amenityTiers;
    r.raise = relief * raiseTiers;
    r.factor = Math.max(k.needsRequirementFloor, 1f - r.capped - r.amenity + r.raise);
    return r;
  }

  private static boolean returns(WorkState w, Collection<String> returning) {
    return w != null && !returning.isEmpty() && returning.contains(w.id) && w.isBuilt()
        && !w.mothballed && !w.suspended;
  }
}
