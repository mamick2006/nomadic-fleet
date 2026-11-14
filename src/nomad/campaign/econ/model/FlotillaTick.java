package nomad.campaign.econ.model;

import java.util.Map;

import nomad.campaign.econ.EconConstants;

public final class FlotillaTick {

  private FlotillaTick() {
  }

  public static TickSheet tick(FlotillaState s, WorkCatalog catalog, Rules k, EconConstants shared) {
    TickSheet t = new TickSheet(s, catalog, k, shared, false);
    run(t);
    return t;
  }

  public static TickSheet preview(FlotillaState s, WorkCatalog catalog, Rules k,
      EconConstants shared) {
    TickSheet t = new TickSheet(s, catalog, k, shared, true);
    run(t);
    return t;
  }

  public static FlotillaState next(TickSheet t) {
    FlotillaState s = t.s;
    FlotillaState n = s.copy();
    n.residents = t.residents;
    n.priorPaused = s.paused;
    n.priorLevel = s.paused ? s.priorLevel : t.needs.level;
    n.works.clear();
    for (WorkState w : t.works) {
      n.works.add(w.copy());
    }
    n.stock.clear();
    n.stock.putAll(t.stock);
    for (Map.Entry<String, Float> e : GlueRules.cargoNet(t).entrySet()) {
      n.cargoSpare.put(e.getKey(),
          Float.valueOf(Math.max(0f, s.cargoSpare(e.getKey()) + e.getValue().floatValue())));
      if (Stores.inTanks(e.getKey())) {
        n.fuelRoom -= e.getValue().floatValue();
      } else {
        n.cargoRoom -= e.getValue().floatValue();
      }
    }
    n.priorCargoFull = t.cargoFull;
    n.priorFuelFull = t.fuelFull;
    int finished = Marines.finished(s.marinesPending, t.marinesMade);
    n.fleetMarines = s.fleetMarines + finished;
    n.fleetCrewRoom = s.fleetCrewRoom - finished;
    n.marinesPending = Marines.stillTraining(s.marinesPending, t.marinesMade);
    n.foundryPointsWanted = Math.max(0f, s.foundryPointsWanted - t.foundryPoints);
    n.foundryPointsNeedBench = Math.min(Math.max(0f, s.foundryPointsNeedBench),
        n.foundryPointsWanted);
    n.suppliesOwed = t.suppliesOwed;
    return n;
  }

  public static void remember(TickSheet t) {
    if (t.preview) {
      return;
    }
    for (WorkState w : t.works) {
      TickSheet.WorkRun run = t.runs.get(w.id);
      w.effectOn = run != null && run.effectOn;
    }
  }

  public static float[] subTicks(float elapsedDays, int ticksPerMonth) {
    float days = Math.max(0f, elapsedDays);
    float standard = 30f / Math.max(1, ticksPerMonth);
    int count = 1;
    for (float left = days; left > standard && count < MAX_SUB_TICKS; left -= standard) {
      count++;
    }
    float[] out = new float[count];
    for (int i = 0; i < count - 1; i++) {
      out[i] = standard;
      days -= standard;
    }
    out[count - 1] = days;
    return out;
  }

  private static final int MAX_SUB_TICKS = 1000;

  private static void run(TickSheet t) {
    if (t.s.paused) {
      Residents.pausedDecay(t);
      remember(t);
      return;
    }
    WorkOrders.advance(t);
    t.openRuns();
    Prereqs.settle(t);
    Residents.enforceBerths(t);
    Stores.minTargets(t);
    Capacity.fit(t);
    Roster.settle(t);
    t.requirement = Requirement.read(t.works, t.s.installedItems(), t.catalog, t.k);
    float[] subTicks = subTicks(t.preview ? 0f : t.s.elapsedDays, t.s.ticksPerMonth);
    t.stepFrac = subTicks[0] / 30f;
    Staffing.assign(t);
    BasicNeeds.settle(t);
    for (float days : subTicks) {
      t.stepFrac = days / 30f;
      Production.run(t);
      Stores.settle(t);
    }
    Stores.closeHold(t);
    Detached.upkeep(t);
    Residents.settle(t);
    remember(t);
  }
}
