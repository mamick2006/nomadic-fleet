package nomad.campaign.econ;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import nomad.campaign.econ.model.FlotillaState;
import nomad.campaign.econ.model.FlotillaTick;
import nomad.campaign.econ.model.Mining;
import nomad.campaign.econ.model.NeedsLevel;
import nomad.campaign.econ.model.Rules;
import nomad.campaign.econ.model.TickSheet;
import nomad.campaign.econ.model.WorkCatalog;
import nomad.campaign.econ.model.WorkState;

public final class SplitShortfall {

  private static final float SHOWN = 0.5f;

  public static final class Target {

    public final String good;
    public final float from;
    public final float to;

    Target(String good, float from, float to) {
      this.good = good;
      this.from = from;
      this.to = to;
    }
  }

  public final float spaceBefore;
  public final float spaceAfter;
  public final float spaceUsed;
  public final float berthsBefore;
  public final float berthsAfter;
  public final float residentsLost;
  public final float residentsAfter;
  public final NeedsLevel levelBefore;
  public final NeedsLevel levelAfter;
  public final float leavePerMonthAfter;
  public final List<Target> targets;
  public final List<String> suspended;
  public final Map<String, Float> lost;
  public final float suppliesPerMonth;
  public final float suppliesStock;
  public final float suppliesNetPerMonth;

  private SplitShortfall(FlotillaState now, TickSheet before, FlotillaState split,
      TickSheet first, Rules k) {
    this.spaceBefore = before.space;
    this.spaceAfter = first.space;
    this.spaceUsed = before.spaceWorks + before.spaceStores;
    this.berthsBefore = before.berths;
    this.berthsAfter = first.berths;
    this.residentsLost = first.residentsLostBerths;
    this.residentsAfter = first.residents;
    this.levelBefore = before.needs.level;
    this.levelAfter = first.needs.level;
    this.leavePerMonthAfter = Math.max(0f, k.leavePerMonth(first.needs.level));

    List<Target> lowered = new ArrayList<Target>();
    for (TickSheet.Store store : first.stores.values()) {
      TickSheet.Store was = before.stores.get(store.good);
      if (was != null && was.target - store.target >= SHOWN) {
        lowered.add(new Target(store.good, was.target, store.target));
      }
    }
    this.targets = Collections.unmodifiableList(lowered);

    List<String> out = new ArrayList<String>();
    for (TickSheet.WorkRun run : first.runs.values()) {
      WorkState after = first.work(run.id);
      WorkState was = now.work(run.id);
      if (after != null && after.suspended && (was == null || !was.suspended)) {
        out.add(run.id);
      }
    }
    this.suspended = Collections.unmodifiableList(out);

    Map<String, Float> gone = new LinkedHashMap<String, Float>();
    for (Map.Entry<String, Float> e : first.lost.entrySet()) {
      if (e.getValue() != null && e.getValue().floatValue() >= SHOWN) {
        gone.put(e.getKey(), e.getValue());
      }
    }
    this.lost = Collections.unmodifiableMap(gone);

    this.suppliesPerMonth = split.detachedSuppliesPerMonth;
    this.suppliesStock = first.stock(Commod.SUPPLIES);
    TickSheet.Store supplies = first.stores.get(Commod.SUPPLIES);
    this.suppliesNetPerMonth = supplies == null ? 0f : supplies.netPerMonth;
  }

  public static SplitShortfall predict(FlotillaState now, float frozenSpace, float frozenBerths,
      float suppliesPerMonth, Mining.Site siteAfter, WorkCatalog catalog, Rules k,
      EconConstants shared) {
    TickSheet before = FlotillaTick.preview(now, catalog, k, shared);
    FlotillaState split = afterSplit(now, frozenSpace, frozenBerths, suppliesPerMonth, siteAfter);
    TickSheet first = FlotillaTick.tick(split, catalog, k, shared);
    return new SplitShortfall(now, before, split, first, k);
  }

  public static FlotillaState afterSplit(FlotillaState now, float frozenSpace, float frozenBerths,
      float suppliesPerMonth, Mining.Site siteAfter) {
    FlotillaState s = now.copy();
    s.space = Math.max(0f, frozenSpace);
    s.berths = Math.max(0f, frozenBerths);
    s.detached = true;
    s.detachedSuppliesPerMonth = Math.max(0f, suppliesPerMonth);
    s.site = siteAfter == null ? Mining.Site.NONE : siteAfter;
    s.cargoSpare.clear();
    s.elapsedDays = 0f;
    return s;
  }

  public static float withoutCommanderCut(float shown, float commanderMult) {
    float value = Math.max(0f, shown);
    return commanderMult > 1e-4f ? value / commanderMult : value;
  }

  public static float parkDistance(float playerDistance, float standOff, float miningRadius) {
    float reach = Math.min(Math.max(0f, standOff), 0.9f * Math.max(0f, miningRadius));
    float from = Math.max(0f, playerDistance);
    return from < reach ? from / 2f : reach;
  }

  public static Mining.Site siteAfterSplit(Mining.Site now, Mining.Body parkedAt) {
    return (now == null ? Mining.Site.NONE : now).withInRange(parkedAt);
  }

  public boolean spaceDrops() {
    return spaceBefore - spaceAfter >= SHOWN;
  }

  public boolean berthsShort() {
    return residentsLost >= SHOWN;
  }

  public boolean levelFalls() {
    return levelAfter.ordinal() < levelBefore.ordinal();
  }

  public boolean residentsWouldLeave() {
    return levelFalls() && leavePerMonthAfter > 0f;
  }

  public boolean suppliesRunDown() {
    return suppliesPerMonth > 0f && suppliesNetPerMonth < -1e-4f;
  }

  public float suppliesMonths() {
    if (!suppliesRunDown() || suppliesStock < 1f) {
      return 0f;
    }
    return suppliesStock / -suppliesNetPerMonth;
  }
}
