package nomad.campaign.econ.model;

public final class WorkState {

  public String id;
  public int size;
  private Priority priority;
  public boolean mothballed;
  public boolean suspended;
  public boolean staffed;
  private boolean idle;
  public int order;
  public int buildTo;
  public float buildDays;
  public boolean remote;
  public boolean remoteSwitching;
  public float remoteDays;
  public boolean effectOn;
  public boolean standingDown;
  public boolean prereqUnmet;

  public WorkState() {
  }

  public WorkState(String id, int order) {
    this.id = id;
    this.order = order;
    this.priority = Priority.MEDIUM;
  }

  public Priority priority() {
    return priority == null ? Priority.MEDIUM : priority;
  }

  public void setPriority(Priority priority) {
    this.priority = priority;
  }

  public boolean isBuilt() {
    return size > 0;
  }

  public boolean isBuilding() {
    return buildTo > 0;
  }

  public WorkState copy() {
    WorkState c = new WorkState();
    c.id = id;
    c.size = size;
    c.priority = priority;
    c.mothballed = mothballed;
    c.suspended = suspended;
    c.staffed = staffed;
    c.idle = idle;
    c.order = order;
    c.buildTo = buildTo;
    c.buildDays = buildDays;
    c.remote = remote;
    c.remoteSwitching = remoteSwitching;
    c.remoteDays = remoteDays;
    c.effectOn = effectOn;
    c.standingDown = standingDown;
    c.prereqUnmet = prereqUnmet;
    return c;
  }
}
