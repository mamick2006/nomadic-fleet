package nomad.campaign.econ.model;

public final class FlotillaEvent {

  public enum Type {
    LEVEL_CHANGED,
    WORK_SUSPENDED,
    WORK_RESUMED,
    WORK_UNSTAFFED,
    WORK_STAFFED,
    WORK_STOOD_DOWN,
    WORK_STOOD_UP,
    BUILD_DONE,
    REMOTE_SWITCHED,
    RESIDENTS_LEFT,
    BERTHS_LOST,
    STOCK_MOVED,
    STOCK_LOST,
    MINED,
    UPKEEP_OWED,
    PAUSED,
    RESUMED,
    HOLD_FULL,
    WORK_PREREQ_LOST,
    WORK_PREREQ_MET
  }

  public final Type type;
  public final String subject;
  public final float amount;
  public final String detail;

  public FlotillaEvent(Type type, String subject, float amount) {
    this(type, subject, amount, null);
  }

  public FlotillaEvent(Type type, String subject, float amount, String detail) {
    this.type = type;
    this.subject = subject;
    this.amount = amount;
    this.detail = detail;
  }
}
