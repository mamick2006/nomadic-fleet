package nomad.campaign.econ.model;

public final class Roster {

  private Roster() {
  }

  public static boolean counts(WorkDef def) {
    if (def == null || def.isProduction()) {
      return false;
    }
    return WorkDef.CAT_FLEET.equals(def.category) || WorkDef.CAT_COMBAT.equals(def.category)
        || WorkDef.CAT_EVENT.equals(def.category);
  }

  public static int limit(float residents, Rules k) {
    int limit = Math.max(0, Math.round(k.supportLimitBase));
    float at = k.supportLimitFirstAt;
    while (reached(residents, at)) {
      limit++;
      at *= 2f;
    }
    return limit;
  }

  public static float nextAt(float residents, Rules k) {
    float at = k.supportLimitFirstAt;
    if (!(at > 0f)) {
      return Float.POSITIVE_INFINITY;
    }
    while (reached(residents, at)) {
      at *= 2f;
    }
    return at;
  }

  private static boolean reached(float residents, float at) {
    return at > 0f && !Float.isInfinite(at) && residents + Staffing.EPS >= at;
  }

  static boolean inOrder(WorkState w) {
    return w != null && w.isBuilt() && !w.mothballed && !w.suspended && !w.prereqUnmet;
  }

  static void settle(TickSheet t) {
    int limit = limit(t.residents, t.k);
    t.supportLimit = limit;
    int taken = 0;
    for (TickSheet.WorkRun run : t.runs.values()) {
      WorkState w = t.work(run.id);
      if (w == null) {
        continue;
      }
      boolean down = false;
      if (inOrder(w) && counts(t.catalog.work(run.id))) {
        down = taken >= limit;
        taken++;
      }
      WorkState before = t.s.work(w.id);
      boolean was = before != null && before.standingDown;
      if (down && !was) {
        for (java.util.Iterator<FlotillaEvent> it = t.events.iterator(); it.hasNext();) {
          FlotillaEvent e = it.next();
          if (e.type == FlotillaEvent.Type.WORK_PREREQ_MET && w.id.equals(e.subject)) {
            it.remove();
          }
        }
        t.event(FlotillaEvent.Type.WORK_STOOD_DOWN, w.id, limit);
      } else if (!down && was && inOrder(w)) {
        t.event(FlotillaEvent.Type.WORK_STOOD_UP, w.id, limit);
      }
      w.standingDown = down;
    }
  }
}
