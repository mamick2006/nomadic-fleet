package nomad.campaign.econ.model;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import nomad.campaign.econ.EconConstants;
import nomad.campaign.econ.Work;

public final class Staffing {

  private Staffing() {
  }

  static final float EPS = 1e-4f;

  public static final Comparator<WorkState> ORDER = new Comparator<WorkState>() {
    @Override
    public int compare(WorkState a, WorkState b) {
      int byPriority = a.priority().ordinal() - b.priority().ordinal();
      return byPriority != 0 ? byPriority : a.order - b.order;
    }
  };

  static final class Plan {

    private final Map<String, Float> own = new LinkedHashMap<String, Float>();
    float working;
    float nextTakenAt = Float.POSITIVE_INFINITY;
    final java.util.Set<String> returning = new java.util.HashSet<String>();

    boolean staffed(String workId) {
      return own.containsKey(workId);
    }
  }

  public static float coreStaffMult(List<String> items, EconConstants shared) {
    float cut = 0f;
    if (has(items, Work.ALPHA_CORE)) {
      cut += shared.itemCoreStaffAlpha;
    }
    if (has(items, Work.BETA_CORE)) {
      cut += shared.itemCoreStaffBeta;
    }
    if (has(items, Work.GAMMA_CORE)) {
      cut += shared.itemCoreStaffGamma;
    }
    return Math.max(0f, 1f - cut);
  }

  public static float staffNeed(String workId, WorkSize def, List<String> items,
      boolean normalOrStory, EconConstants shared) {
    if (def == null) {
      return 0f;
    }
    float need = def.staff;
    boolean militia = Works.GUNNERY_RANGE.equals(workId)
        || Works.DAMAGE_CONTROL_ROOM.equals(workId);
    if (militia && has(items, Work.DRONE_REPLICATOR)) {
      need *= Math.max(0f, 1f - shared.itemDroneReplicatorStaff);
    }
    return need * coreStaffMult(items, shared);
  }

  static Plan plan(TickSheet t) {
    return plan(t, t.residents);
  }

  static Plan plan(TickSheet t, float residents) {
    Plan plan = new Plan();
    float aboard = Math.max(0f, residents);
    float left = aboard;
    int places = Roster.limit(aboard, t.k);
    for (TickSheet.WorkRun run : t.runs.values()) {
      WorkState w = t.work(run.id);
      if (w == null || !w.isBuilt() || w.mothballed || w.suspended
          || run.status == WorkStatus.IDLE) {
        continue;
      }
      if (w.prereqUnmet) {
        if (aboard < run.returnsAtResidents) {
          plan.nextTakenAt = Math.min(plan.nextTakenAt, run.returnsAtResidents);
          continue;
        }
      }
      boolean counted = Roster.counts(t.catalog.work(run.id));
      if (counted && places <= 0) {
        plan.nextTakenAt = Math.min(plan.nextTakenAt, Roster.nextAt(aboard, t.k));
        continue;
      }
      if (counted) {
        places--;
      }
      if (w.prereqUnmet || w.standingDown) {
        plan.returning.add(run.id);
      }
      float need = staffNeed(run.id, run.def, t.s.items, t.s.normalOrStory, t.shared);
      if (need > left + EPS) {
        plan.nextTakenAt = Math.min(plan.nextTakenAt, plan.working + need - EPS);
        continue;
      }
      plan.own.put(run.id, Float.valueOf(need));
      plan.working += need;
      left -= need;
    }
    return plan;
  }

  private static float slack(float headCount) {
    return 1e-3f + 2e-6f * Math.abs(headCount);
  }

  static float roomBeforeTaken(TickSheet t, float cap) {
    if (!(cap > 0f)) {
      return 0f;
    }
    if (t.unstaffedCount() == 0 && t.standingDownCount() == 0 && !t.anyReturnsAtResidents()) {
      return cap;
    }
    float aboard = Math.max(0f, t.residents);
    NeedsLevel now = t.needs.level;
    float held = aboard;
    float at = Float.POSITIVE_INFINITY;
    for (int pass = 0; pass <= 3 * t.runs.size(); pass++) {
      at = plan(t, held).nextTakenAt;
      if (at - aboard >= cap) {
        return cap;
      }
      float past = at + slack(at);
      Plan taken = plan(t, past);
      float factor = taken.returning.isEmpty() ? t.requirementFactor()
          : Requirement.read(t.works, t.s.installedItems(), t.catalog, t.k,
              taken.returning).factor;
      NeedsLevel level = BasicNeeds.read(t.spaceLeftForNeeds(),
          past - taken.working, past, t.s.normalOrStory, t.s.storyDifficulty, factor,
          t.k).level;
      if (level.ordinal() < now.ordinal()) {
        break;
      }
      held = past;
    }
    return Math.max(0f, Math.min(cap, at - slack(at) - aboard));
  }

  static void assign(TickSheet t) {
    for (TickSheet.WorkRun run : t.runs.values()) {
      WorkState w = t.work(run.id);
      run.staffNeed = staffNeed(run.id, run.def, t.s.items, t.s.normalOrStory, t.shared);
      run.staffOwn = 0f;
      if (w != null && w.suspended) {
        run.status = WorkStatus.SUSPENDED;
      } else if (w == null || !w.isBuilt()) {
        run.status = WorkStatus.BUILDING;
      } else if (w.mothballed) {
        run.status = WorkStatus.MOTHBALLED;
      } else if (w.prereqUnmet) {
        run.status = WorkStatus.LACKING;
      } else if (w.standingDown) {
        run.status = WorkStatus.STANDING_DOWN;
      } else {
        run.status = WorkStatus.RUNNING;
      }
    }
    do {
      Plan plan = plan(t);
      for (TickSheet.WorkRun run : t.runs.values()) {
        if (run.status == WorkStatus.RUNNING || run.status == WorkStatus.UNSTAFFED) {
          boolean staffed = plan.staffed(run.id);
          run.status = staffed ? WorkStatus.RUNNING : WorkStatus.UNSTAFFED;
          run.staffOwn = staffed ? run.staffNeed : 0f;
        }
      }
      t.working = plan.working;
      BasicNeeds.level(t);
    } while (Production.markIdle(t));
    for (TickSheet.WorkRun run : t.runs.values()) {
      WorkState w = t.work(run.id);
      if (w == null) {
        continue;
      }
      boolean has = run.status == WorkStatus.RUNNING;
      boolean idle = run.status == WorkStatus.IDLE;
      if (w.staffed && run.status == WorkStatus.UNSTAFFED) {
        t.event(FlotillaEvent.Type.WORK_UNSTAFFED, run.id, run.staffNeed);
      } else if (!w.staffed && has && wasWaiting(t.s.work(run.id))) {
        t.event(FlotillaEvent.Type.WORK_STAFFED, run.id, run.staffNeed);
      }
      w.staffed = has || idle;
    }
  }

  private static boolean wasWaiting(WorkState before) {
    return before != null && before.isBuilt() && !before.suspended && !before.staffed
        && !before.standingDown && !before.prereqUnmet;
  }

  private static boolean has(List<String> list, String id) {
    return list != null && list.contains(id);
  }
}
