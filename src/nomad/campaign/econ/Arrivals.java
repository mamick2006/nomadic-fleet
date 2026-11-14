package nomad.campaign.econ;

import nomad.campaign.econ.model.TickSheet;

public final class Arrivals {

  private Arrivals() {
  }

  public static float room(TickSheet reading) {
    if (reading == null || reading.s.paused || reading.s.detached
        || !reading.needs.level.atLeastNormal()) {
      return 0f;
    }
    return Math.max(0f, reading.residentRoom);
  }

  public static float take(float room, float offered) {
    return Math.max(0f, Math.min(room, offered));
  }
}
