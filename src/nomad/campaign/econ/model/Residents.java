package nomad.campaign.econ.model;

import java.util.List;

import nomad.campaign.econ.EconConstants;
import nomad.campaign.econ.Work;

public final class Residents {

  private Residents() {
  }

  public static final String PARAM_BERTHS = "berths";

  public static float effectiveBerths(float berths, List<String> items, WorkSize dormitory,
      boolean normalOrStory, EconConstants shared) {
    float total = Math.max(0f, berths);
    if (items != null && items.contains(Work.ORBITAL_FUSION_LAMP)) {
      total *= 1f + shared.itemOrbitalFusionLampBerths;
    }
    if (dormitory != null) {
      total += dormitory.param(PARAM_BERTHS, shared.dormitoryMult * dormitory.space);
    }
    return total;
  }

  public static float maxCrewIn(float residents, float berths, float fleetCrew,
      float skeletonCrew) {
    float freeBerths = Math.max(0f, berths - residents);
    float spareCrew = Math.max(0f, fleetCrew - skeletonCrew);
    return (float) Math.floor(Math.min(freeBerths, spareCrew));
  }

  public static int shown(float residents) {
    return Math.round(Math.max(0f, residents));
  }

  public static float maxCrewOut(float residents, EconConstants shared) {
    return (float) Math.floor(Math.max(0f, shown(residents) - shared.crewExtractReserve));
  }

  static boolean canSpare(TickSheet t, float count) {
    float left = t.residents - Math.max(0f, count);
    if (left + Staffing.EPS < t.shared.crewExtractReserve || left + Staffing.EPS < t.working) {
      return false;
    }
    NeedsLevel after = BasicNeeds.read(t.needs.haveSpace, left - t.working, left,
        t.s.normalOrStory, t.s.storyDifficulty, t.requirementFactor(), t.k).level;
    return after.ordinal() >= t.needs.level.ordinal();
  }

  static WorkStatus.Reason roomFor(TickSheet t, float count) {
    float after = t.residents + Math.max(0f, count);
    return after > t.berths + Staffing.EPS ? WorkStatus.Reason.NO_BERTHS_FOR_RESIDENTS
        : WorkStatus.Reason.NONE;
  }

  static void pausedDecay(TickSheet t) {
    if (!t.s.priorPaused) {
      t.event(FlotillaEvent.Type.PAUSED, null, t.residents);
    }
    if (t.preview || t.residents <= 0f) {
      return;
    }
    float before = t.residents;
    t.residents = before * (float) Math.pow(1.0 - share(t.shared.pausedDecay), t.monthFrac);
    t.residentsLeft = Math.max(0f, before - t.residents);
  }

  static void enforceBerths(TickSheet t) {
    if (t.s.priorPaused) {
      t.event(FlotillaEvent.Type.RESUMED, null, t.residents);
    }
    t.berths = effectiveBerths(t.s.berths, t.s.items, dormitory(t), t.s.normalOrStory, t.shared);
    if (t.preview || t.residents <= t.berths + Staffing.EPS) {
      return;
    }
    float lost = t.residents - t.berths;
    t.residents = t.berths;
    t.residentsLostBerths = lost;
    t.event(FlotillaEvent.Type.BERTHS_LOST, null, lost);
  }

  private static WorkSize dormitory(TickSheet t) {
    WorkState w = t.s.work(Works.DORMITORY);
    NeedsLevel prior = t.s.priorLevel;
    if (w == null || !w.isBuilt() || !w.staffed || w.mothballed || w.suspended || prior == null
        || !prior.atLeastSpartan()) {
      return null;
    }
    return t.catalog.size(w.id, w.size);
  }

  static void settle(TickSheet t) {
    NeedsLevel level = t.needs.level;
    if (!t.preview && t.residents > 0f) {
      float leave = share(t.k.leavePerMonth(level));
      float growth = growthPerYear(t);
      if (leave > 0f) {
        float lost = t.residents * (1f - (float) Math.pow(1.0 - leave, t.monthFrac));
        if (lost > 0f) {
          t.residents -= lost;
          t.residentsLeft = lost;
          NeedsLevel prior = t.s.priorLevel;
          if (prior == null || t.k.leavePerMonth(prior) <= 0f) {
            t.event(FlotillaEvent.Type.RESIDENTS_LEFT, level.key, lost);
          }
        }
      } else if (growth > 0f) {
        float grown = t.residents * ((float) Math.pow(1.0 + growth, t.yearFrac) - 1f);
        grown = Math.max(0f, Math.min(grown, room(t)));
        t.residents += grown;
        t.residentsGrown = grown;
      }
    }
    t.residentRoom = room(t);
  }

  public static float growthPerYear(TickSheet t) {
    return growthPerYear(t.k, t.needs.level, schoolGrowth(t));
  }

  public static float growthPerYear(Rules k, NeedsLevel level, float schoolGrowth) {
    float base = k.growthPerYear(level);
    if (base <= 0f) {
      return 0f;
    }
    return base + Math.max(0f, schoolGrowth);
  }

  public static float schoolGrowth(TickSheet t) {
    TickSheet.WorkRun run = t.run(Works.SCHOOL);
    if (run == null || !run.effectOn || run.def == null) {
      return 0f;
    }
    return Math.max(0f, run.def.param(Works.PARAM_GROWTH_PER_YEAR, 0f));
  }

  private static float room(TickSheet t) {
    float atLevel = BasicNeeds.room(t.needs.level, t.spaceLeftForNeeds(), t.residents,
        t.s.normalOrStory, t.s.storyDifficulty, t.requirementFactor(), t.k);
    return Staffing.roomBeforeTaken(t, Math.max(0f, Math.min(atLevel, t.berths - t.residents)));
  }

  public static float roomForArrivals(FlotillaState s, WorkCatalog catalog, Rules k,
      EconConstants shared) {
    if (s == null || s.paused || s.detached) {
      return 0f;
    }
    float room = FlotillaTick.preview(s, catalog, k, shared).residentRoom;
    return room > 0f ? room : 0f;
  }

  private static float share(float perMonth) {
    return Math.max(0f, Math.min(1f, perMonth));
  }
}
