package nomad.campaign.econ;

import nomad.campaign.econ.model.NeedsLevel;
import nomad.campaign.econ.model.TickSheet;
import nomad.campaign.econ.model.WorkStatus;

public final class RejoinReport {

  public final NeedsLevel level;
  public final float efficiency;
  public final float leavePerMonth;
  public final int suspended;
  public final int unstaffed;

  private RejoinReport(NeedsLevel level, float efficiency, float leavePerMonth, int suspended,
      int unstaffed) {
    this.level = level;
    this.efficiency = efficiency;
    this.leavePerMonth = leavePerMonth;
    this.suspended = suspended;
    this.unstaffed = unstaffed;
  }

  public static RejoinReport of(TickSheet sheet) {
    if (sheet == null || sheet.s.paused) {
      return new RejoinReport(NeedsLevel.NORMAL, 1f, 0f, 0, 0);
    }
    int suspended = 0;
    for (TickSheet.WorkRun run : sheet.runs.values()) {
      if (run.status == WorkStatus.SUSPENDED) {
        suspended++;
      }
    }
    NeedsLevel level = sheet.needs.level;
    return new RejoinReport(level, sheet.efficiency, Math.max(0f, sheet.k.leavePerMonth(level)),
        suspended, sheet.unstaffedCount());
  }

  public boolean levelLow() {
    return !level.atLeastNormal();
  }

  public boolean residentsLeave() {
    return leavePerMonth > 0f;
  }

  public boolean nothing() {
    return !levelLow() && suspended == 0 && unstaffed == 0;
  }

  public enum Owed {
    NOTHING,
    CARGO,
    CARGO_AND_CREDITS,
    CREDITS
  }

  public static Owed owed(float fromCargo, float forCredits) {
    boolean cargo = fromCargo >= 0.5f;
    boolean credits = forCredits >= 0.5f;
    if (credits) {
      return cargo ? Owed.CARGO_AND_CREDITS : Owed.CREDITS;
    }
    return cargo ? Owed.CARGO : Owed.NOTHING;
  }
}
