package nomad.campaign.econ.model;

import nomad.campaign.econ.EconConstants;

public final class Marines {

  private static final float EPS = 1e-4f;

  private Marines() {
  }

  public static float target(FlotillaState s, EconConstants shared) {
    if (s == null || Float.isNaN(s.marinesTarget)) {
      return shared == null ? 0f : Math.max(0f, shared.marinesTargetDefault);
    }
    return Math.max(0f, s.marinesTarget);
  }

  static float room(TickSheet t) {
    FlotillaState s = t.s;
    return Math.max(0f, target(s, t.shared) - Math.max(0f, s.fleetMarines)
        - Math.max(0f, s.marinesPending) - Math.max(0f, t.marinesMade));
  }

  static float part(TickSheet t, float amount) {
    if (amount <= EPS) {
      return 1f;
    }
    return Math.max(0f, Math.min(1f, room(t) / amount));
  }

  static WorkStatus.Reason blocked(TickSheet t, float amount) {
    FlotillaState s = t.s;
    if (s.detached) {
      return WorkStatus.Reason.MARINES_DETACHED;
    }
    float room = room(t);
    if (room <= EPS) {
      return WorkStatus.Reason.MARINES_AT_TARGET;
    }
    float training = Math.max(0f, s.marinesPending) + Math.max(0f, t.marinesMade);
    float trains = Math.min(Math.max(0f, amount), room);
    float finished = (float) Math.floor(training + trains + EPS);
    if (finished > 0f && finished > s.fleetCrewRoom + EPS) {
      return WorkStatus.Reason.MARINES_NO_ROOM;
    }
    return WorkStatus.Reason.NONE;
  }

  public static int finished(float pending, float made) {
    return (int) Math.floor(Math.max(0f, pending) + Math.max(0f, made) + 1e-3f);
  }

  public static float stillTraining(float pending, float made) {
    return Math.max(0f, Math.max(0f, pending) + Math.max(0f, made) - finished(pending, made));
  }

  public static float joinXp(int whole, float perMarine, boolean on) {
    return on && whole > 0 ? whole * Math.max(0f, perMarine) : 0f;
  }
}
