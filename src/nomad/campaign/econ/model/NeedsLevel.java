package nomad.campaign.econ.model;

public enum NeedsLevel {

  NON_EXISTENT("nonexistent"),
  DECREPIT("decrepit"),
  SPARTAN("spartan"),
  NORMAL("normal"),
  NICE("nice"),
  LAVISH("lavish"),
  OPULENT("opulent");

  public final String key;

  NeedsLevel(String key) {
    this.key = key;
  }

  public boolean atLeastNormal() {
    return ordinal() >= NORMAL.ordinal();
  }

  public boolean atLeastSpartan() {
    return ordinal() >= SPARTAN.ordinal();
  }

  public NeedsLevel above() {
    return this == OPULENT ? null : values()[ordinal() + 1];
  }

  public NeedsLevel below() {
    return this == NON_EXISTENT ? null : values()[ordinal() - 1];
  }
}
