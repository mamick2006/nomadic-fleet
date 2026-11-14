package nomad.campaign.econ.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import nomad.campaign.econ.EconConstants;
import nomad.campaign.econ.Work;

public final class Foundry {

  private static final float EPS = 1e-4f;

  private Foundry() {
  }

  public static float pointsPerMonth(WorkSize def, float efficiency, List<String> items,
      boolean normalOrStory, Rules k, EconConstants shared) {
    if (def == null || efficiency <= 0f) {
      return 0f;
    }
    return def.makes(Works.POINTS) * efficiency
        * Production.itemYield(Works.POINTS, items, normalOrStory, shared)
        * k.survivalFoundryPointsMult * k.productionMult * k.foundryMult;
  }

  public static Map<String, Float> materials(WorkSize def, float points) {
    Map<String, Float> out = new LinkedHashMap<String, Float>();
    if (def == null || points <= 0f) {
      return out;
    }
    float perMonth = def.makes(Works.POINTS);
    if (perMonth <= 0f) {
      return out;
    }
    for (Map.Entry<String, Float> use : def.uses.entrySet()) {
      out.put(use.getKey(), Float.valueOf(use.getValue().floatValue() * points / perMonth));
    }
    return out;
  }

  public static Map<String, Float> materials(WorkSize def, float points, List<String> items,
      boolean normalOrStory, EconConstants shared) {
    Map<String, Float> out = materials(def, points);
    float mult = materialsMult(items, normalOrStory, shared);
    if (mult != 1f) {
      for (Map.Entry<String, Float> use : out.entrySet()) {
        use.setValue(Float.valueOf(use.getValue().floatValue() * mult));
      }
    }
    return out;
  }

  public static float materialsMult(List<String> items, boolean normalOrStory,
      EconConstants shared) {
    if (items == null || !items.contains(Work.DEALMAKER_HOLOSUITE)) {
      return 1f;
    }
    return 1f - Math.min(1f,
        shared.itemDealmakerAutoforgeCost * shared.itemEffectMult(normalOrStory));
  }

  public static float affordable(WorkSize def, float points, Map<String, Float> stock,
      List<String> items, boolean normalOrStory, EconConstants shared) {
    if (def == null || points <= 0f) {
      return 0f;
    }
    float part = 1f;
    for (Map.Entry<String, Float> need
        : materials(def, points, items, normalOrStory, shared).entrySet()) {
      float units = need.getValue().floatValue();
      if (units <= 0f) {
        continue;
      }
      Float have = stock == null ? null : stock.get(need.getKey());
      part = Math.min(part, (have == null ? 0f : Math.max(0f, have.floatValue())) / units);
    }
    return points * Math.max(0f, Math.min(1f, part));
  }

  public static boolean benchOn(TickSheet t) {
    TickSheet.WorkRun bench = t.runs.get(Works.DOMAIN_PARTS_WORKSHOP);
    return bench != null && bench.effectOn;
  }

  static float wanted(TickSheet t) {
    float wanted = t.s.foundryPointsWanted;
    return benchOn(t) ? wanted : wanted - Math.max(0f, t.s.foundryPointsNeedBench);
  }

  private static float wantedLeft(TickSheet t) {
    return wanted(t) - t.foundryPoints;
  }

  static WorkStatus.Reason cannotRun(TickSheet t, WorkSize def) {
    if (wantedLeft(t) <= EPS) {
      return t.s.foundryPointsNeedBench > EPS && !benchOn(t) ? WorkStatus.Reason.QUEUE_WAITING
          : WorkStatus.Reason.EMPTY_QUEUE;
    }
    for (Map.Entry<String, Float> need
        : materials(def, 1f, t.s.items, t.s.normalOrStory, t.shared).entrySet()) {
      if (need.getValue().floatValue() > 0f && t.stock(need.getKey()) <= EPS) {
        return WorkStatus.Reason.INPUT_EMPTY;
      }
    }
    return WorkStatus.Reason.NONE;
  }

  static void run(TickSheet t) {
    TickSheet.WorkRun run = t.run(Works.FOUNDRY);
    if (run == null || run.def == null) {
      return;
    }
    WorkStatus.Reason why = cannotRun(t, run.def);
    if (why != WorkStatus.Reason.NONE) {
      if (!t.preview && run.status == WorkStatus.RUNNING) {
        run.status = WorkStatus.IDLE;
        run.reason = why;
      }
      return;
    }
    float rate = pointsPerMonth(run.def, t.efficiency, t.s.items, t.s.normalOrStory, t.k, t.shared);
    if (rate <= 0f) {
      return;
    }
    run.makesPerMonth.put(Works.POINTS, Float.valueOf(rate));
    run.usesPerMonth.putAll(materials(run.def, rate, t.s.items, t.s.normalOrStory, t.shared));
    if (t.preview || t.stepFrac <= 0f) {
      return;
    }
    float wanted = Math.min(rate * t.stepFrac, wantedLeft(t));
    float points = affordable(run.def, wanted, t.stock, t.s.items, t.s.normalOrStory, t.shared);
    if (points <= EPS) {
      return;
    }
    for (Map.Entry<String, Float> need
        : materials(run.def, points, t.s.items, t.s.normalOrStory, t.shared).entrySet()) {
      float units = need.getValue().floatValue();
      Production.add(run.used, need.getKey(), units);
      t.stock.put(need.getKey(), Float.valueOf(Math.max(0f, t.stock(need.getKey()) - units)));
    }
    Production.add(run.made, Works.POINTS, points);
    t.foundryPoints += points;
  }
}
