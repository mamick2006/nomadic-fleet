package nomad.campaign.econ.model;

import java.util.List;

import nomad.campaign.econ.EconConstants;

public final class WorkOrders {

  private WorkOrders() {
  }

  private static final float DAYS_EPS = 1e-3f;

  public enum Refusal {
    NONE,
    UNKNOWN_WORK,
    ALREADY_BUILT,
    NOT_BUILT,
    AT_MAX_SIZE,
    AT_MIN_SIZE,
    BUILDING,
    NOT_BUILDING,
    NO_SPACE,
    NO_CREDITS,
    SWITCHING,
    NEEDS_PREREQ
  }

  public static final class Quote {

    public Refusal refusal = Refusal.NONE;
    public float credits;
    public float days;
    public float space;
    public float spaceFree;
    public BasicNeeds.Reading after = new BasicNeeds.Reading();
    public BasicNeeds.Reading before = new BasicNeeds.Reading();
    public float needsBefore = 1f;
    public float needsAfter = 1f;
    public boolean residentsWouldLeave;
    public List<Prereq> unmet = new java.util.ArrayList<Prereq>();
    public FlotillaEvent event;

    public boolean allowed() {
      return refusal == Refusal.NONE;
    }

    public float charged() {
      return allowed() ? credits : 0f;
    }
  }

  public static float stepCost(WorkCatalog catalog, String workId, int size) {
    WorkSize row = catalog.size(workId, size);
    return row == null ? 0f : Math.max(0, Math.round(row.cost));
  }

  public static float stepRefund(WorkCatalog catalog, String workId, int size,
      EconConstants shared) {
    float share = Math.max(0f, Math.min(1f, shared.workScrapRefund));
    return Math.round(stepCost(catalog, workId, size) * share);
  }

  public static float scrapRefund(WorkCatalog catalog, String workId, int size,
      EconConstants shared) {
    float total = 0f;
    for (int n = 1; n <= size; n++) {
      total += stepRefund(catalog, workId, n, shared);
    }
    return total;
  }

  public static Quote quoteBuild(FlotillaState s, String workId, float playerCredits,
      WorkCatalog catalog, Rules k, EconConstants shared) {
    if (catalog.work(workId) == null) {
      return refused(Refusal.UNKNOWN_WORK);
    }
    if (s.work(workId) != null) {
      return refused(Refusal.ALREADY_BUILT);
    }
    Quote q = new Quote();
    q.credits = stepCost(catalog, workId, 1);
    q.days = shared.workBuildDays;
    q.space = Capacity.spaceAt(catalog, workId, 1);
    FlotillaState then = s.copy();
    WorkState built = new WorkState(workId, nextOrder(then.works));
    built.size = 1;
    then.works.add(built);
    read(q, s, then, catalog, k, shared);
    prereqs(q, s, workId, 1);
    return payable(q, playerCredits);
  }

  public static Quote quoteUpgrade(FlotillaState s, String workId, float playerCredits,
      WorkCatalog catalog, Rules k, EconConstants shared) {
    WorkDef def = catalog.work(workId);
    WorkState w = s.work(workId);
    if (def == null) {
      return refused(Refusal.UNKNOWN_WORK);
    }
    if (w == null) {
      return refused(Refusal.NOT_BUILT);
    }
    if (w.isBuilding()) {
      return refused(Refusal.BUILDING);
    }
    if (w.size >= def.maxSize()) {
      return refused(Refusal.AT_MAX_SIZE);
    }
    Quote q = new Quote();
    q.credits = stepCost(catalog, workId, w.size + 1);
    q.days = shared.workBuildDays;
    q.space = Capacity.spaceAt(catalog, workId, w.size + 1) - held(w, catalog);
    FlotillaState then = s.copy();
    then.work(workId).size = w.size + 1;
    read(q, s, then, catalog, k, shared);
    prereqs(q, s, workId, w.size + 1);
    return payable(q, playerCredits);
  }

  public static Quote quoteDowngrade(FlotillaState s, String workId, WorkCatalog catalog, Rules k,
      EconConstants shared) {
    WorkState w = s.work(workId);
    if (catalog.work(workId) == null) {
      return refused(Refusal.UNKNOWN_WORK);
    }
    if (w == null) {
      return refused(Refusal.NOT_BUILT);
    }
    if (w.isBuilding()) {
      return refused(Refusal.BUILDING);
    }
    if (w.size <= 1) {
      return refused(Refusal.AT_MIN_SIZE);
    }
    Quote q = new Quote();
    q.credits = -stepRefund(catalog, workId, w.size, shared);
    q.space = w.suspended ? 0f : Capacity.spaceAt(catalog, workId, w.size - 1) - held(w, catalog);
    FlotillaState then = s.copy();
    downgrade(then.works, workId);
    read(q, s, then, catalog, k, shared);
    return q;
  }

  public static Quote quoteScrap(FlotillaState s, String workId, WorkCatalog catalog, Rules k,
      EconConstants shared) {
    WorkState w = s.work(workId);
    if (catalog.work(workId) == null) {
      return refused(Refusal.UNKNOWN_WORK);
    }
    if (w == null) {
      return refused(Refusal.NOT_BUILT);
    }
    if (w.isBuilding()) {
      return refused(Refusal.BUILDING);
    }
    Quote q = new Quote();
    q.credits = -scrapRefund(catalog, workId, w.size, shared);
    q.space = -held(w, catalog);
    FlotillaState then = s.copy();
    scrap(then.works, workId);
    read(q, s, then, catalog, k, shared);
    return q;
  }

  public static Quote quoteCancel(FlotillaState s, String workId, WorkCatalog catalog, Rules k,
      EconConstants shared) {
    WorkState w = s.work(workId);
    if (catalog.work(workId) == null) {
      return refused(Refusal.UNKNOWN_WORK);
    }
    if (w == null) {
      return refused(Refusal.NOT_BUILT);
    }
    if (!w.isBuilding()) {
      return refused(Refusal.NOT_BUILDING);
    }
    Quote q = new Quote();
    q.credits = -stepCost(catalog, workId, w.buildTo);
    q.space = w.suspended ? 0f : Capacity.spaceAt(catalog, workId, w.size) - held(w, catalog);
    FlotillaState then = s.copy();
    cancel(then.works, workId);
    read(q, s, then, catalog, k, shared);
    return q;
  }

  public static Quote quoteMothball(FlotillaState s, String workId, boolean mothball,
      WorkCatalog catalog, Rules k, EconConstants shared) {
    if (catalog.work(workId) == null) {
      return refused(Refusal.UNKNOWN_WORK);
    }
    if (s.work(workId) == null) {
      return refused(Refusal.NOT_BUILT);
    }
    Quote q = new Quote();
    FlotillaState then = s.copy();
    setMothballed(then.works, workId, mothball);
    read(q, s, then, catalog, k, shared);
    return q;
  }

  public static Quote quotePriority(FlotillaState s, String workId, Priority priority,
      WorkCatalog catalog, Rules k, EconConstants shared) {
    if (catalog.work(workId) == null) {
      return refused(Refusal.UNKNOWN_WORK);
    }
    if (s.work(workId) == null) {
      return refused(Refusal.NOT_BUILT);
    }
    Quote q = new Quote();
    FlotillaState then = s.copy();
    setPriority(then.works, workId, priority);
    read(q, s, then, catalog, k, shared);
    return q;
  }

  public static Quote quoteRemote(FlotillaState s, boolean on, WorkCatalog catalog, Rules k,
      EconConstants shared) {
    WorkState w = s.work(Works.MINING_RIG);
    if (catalog.work(Works.MINING_RIG) == null) {
      return refused(Refusal.UNKNOWN_WORK);
    }
    if (w == null) {
      return refused(Refusal.NOT_BUILT);
    }
    if (w.remoteSwitching && Mining.remoteTarget(w) == on) {
      return refused(Refusal.SWITCHING);
    }
    Quote q = new Quote();
    q.days = on && !w.remote ? k.remoteOnDays : 0f;
    read(q, s, s, catalog, k, shared);
    return q;
  }

  private static Quote refused(Refusal refusal) {
    Quote q = new Quote();
    q.refusal = refusal;
    return q;
  }

  private static void prereqs(Quote q, FlotillaState s, String workId, int size) {
    List<Prereq> unmet = Prereqs.unmetAt(s.prereqs, workId, size);
    if (!unmet.isEmpty()) {
      q.refusal = Refusal.NEEDS_PREREQ;
      q.unmet = unmet;
    }
  }

  private static Quote payable(Quote q, float playerCredits) {
    if (q.allowed() && playerCredits < q.credits) {
      q.refusal = Refusal.NO_CREDITS;
    }
    return q;
  }

  private static void read(Quote q, FlotillaState s, FlotillaState then, WorkCatalog catalog,
      Rules k, EconConstants shared) {
    TickSheet now = FlotillaTick.preview(s, catalog, k, shared);
    q.spaceFree = now.space - now.spaceWorks - now.spaceStores;
    if (q.space > 0f && q.space > q.spaceFree + Capacity.EPS) {
      q.refusal = Refusal.NO_SPACE;
    }
    TickSheet later = then == s ? now : FlotillaTick.preview(then, catalog, k, shared);
    q.before = now.needs;
    q.after = later.needs;
    q.needsBefore = now.requirementFactor();
    q.needsAfter = later.requirementFactor();
    q.residentsWouldLeave = k.leavePerMonth(q.after.level) > 0f
        && !(k.leavePerMonth(now.needs.level) > 0f);
  }

  private static float held(WorkState w, WorkCatalog catalog) {
    return w.suspended ? 0f : Capacity.spaceHeld(w, catalog);
  }

  private static int nextOrder(List<WorkState> works) {
    int next = 0;
    for (WorkState w : works) {
      next = Math.max(next, w.order + 1);
    }
    return next;
  }

  public static Quote orderBuild(FlotillaState s, String workId, float playerCredits, int order,
      WorkCatalog catalog, Rules k, EconConstants shared) {
    Quote q = quoteBuild(s, workId, playerCredits, catalog, k, shared);
    if (q.allowed()) {
      build(s.works, workId, order, catalog);
    }
    return q;
  }

  public static Quote orderUpgrade(FlotillaState s, String workId, float playerCredits,
      WorkCatalog catalog, Rules k, EconConstants shared) {
    Quote q = quoteUpgrade(s, workId, playerCredits, catalog, k, shared);
    if (q.allowed()) {
      upgrade(s.works, workId, catalog);
    }
    return q;
  }

  public static Quote orderDowngrade(FlotillaState s, String workId, WorkCatalog catalog, Rules k,
      EconConstants shared) {
    Quote q = quoteDowngrade(s, workId, catalog, k, shared);
    if (q.allowed()) {
      downgrade(s.works, workId);
    }
    return q;
  }

  public static Quote orderScrap(FlotillaState s, String workId, WorkCatalog catalog, Rules k,
      EconConstants shared) {
    Quote q = quoteScrap(s, workId, catalog, k, shared);
    if (q.allowed()) {
      scrap(s.works, workId);
    }
    return q;
  }

  public static Quote orderCancel(FlotillaState s, String workId, WorkCatalog catalog, Rules k,
      EconConstants shared) {
    Quote q = quoteCancel(s, workId, catalog, k, shared);
    if (q.allowed()) {
      cancel(s.works, workId);
    }
    return q;
  }

  public static Quote orderMothball(FlotillaState s, String workId, boolean mothball,
      WorkCatalog catalog, Rules k, EconConstants shared) {
    Quote q = quoteMothball(s, workId, mothball, catalog, k, shared);
    if (q.allowed()) {
      setMothballed(s.works, workId, mothball);
    }
    return q;
  }

  public static Quote orderPriority(FlotillaState s, String workId, Priority priority,
      WorkCatalog catalog, Rules k, EconConstants shared) {
    Quote q = quotePriority(s, workId, priority, catalog, k, shared);
    if (q.allowed()) {
      setPriority(s.works, workId, priority);
    }
    return q;
  }

  public static Quote orderRemote(FlotillaState s, boolean on, WorkCatalog catalog, Rules k,
      EconConstants shared) {
    Quote q = quoteRemote(s, on, catalog, k, shared);
    if (q.allowed()) {
      q.event = switchRemote(s.works, on);
    }
    return q;
  }

  public static WorkState build(List<WorkState> works, String workId, int order,
      WorkCatalog catalog) {
    if (catalog.size(workId, 1) == null || find(works, workId) != null) {
      return null;
    }
    WorkState w = new WorkState(workId, order);
    w.buildTo = 1;
    works.add(w);
    return w;
  }

  public static void upgrade(List<WorkState> works, String workId, WorkCatalog catalog) {
    WorkState w = find(works, workId);
    if (w == null || !w.isBuilt() || w.isBuilding()
        || catalog.size(workId, w.size + 1) == null) {
      return;
    }
    w.buildTo = w.size + 1;
    w.buildDays = 0f;
  }

  public static void downgrade(List<WorkState> works, String workId) {
    WorkState w = find(works, workId);
    if (w == null || w.isBuilding() || w.size <= 1) {
      return;
    }
    w.size -= 1;
  }

  public static void scrap(List<WorkState> works, String workId) {
    WorkState w = find(works, workId);
    if (w == null || w.isBuilding()) {
      return;
    }
    works.remove(w);
  }

  public static void cancel(List<WorkState> works, String workId) {
    WorkState w = find(works, workId);
    if (w == null || !w.isBuilding()) {
      return;
    }
    if (!w.isBuilt()) {
      works.remove(w);
      return;
    }
    w.buildTo = 0;
    w.buildDays = 0f;
  }

  public static void setMothballed(List<WorkState> works, String workId, boolean mothballed) {
    WorkState w = find(works, workId);
    if (w != null) {
      w.mothballed = mothballed;
    }
  }

  public static void setPriority(List<WorkState> works, String workId, Priority priority) {
    WorkState w = find(works, workId);
    if (w != null && priority != null) {
      w.setPriority(priority);
    }
  }

  public static FlotillaEvent switchRemote(List<WorkState> works, boolean on) {
    WorkState w = find(works, Works.MINING_RIG);
    return Mining.pressRemote(w, on)
        ? new FlotillaEvent(FlotillaEvent.Type.REMOTE_SWITCHED, w.id, 0f) : null;
  }

  private static WorkState find(List<WorkState> works, String workId) {
    for (WorkState w : works) {
      if (w.id != null && w.id.equals(workId)) {
        return w;
      }
    }
    return null;
  }

  static void advance(TickSheet t) {
    if (!t.preview) {
      float days = Math.max(0f, t.s.elapsedDays);
      for (WorkState w : t.works) {
        if (w.isBuilding() && !w.suspended) {
          w.buildDays += days;
          if (w.buildDays + DAYS_EPS >= t.shared.workBuildDays) {
            w.size = w.buildTo;
            w.buildTo = 0;
            w.buildDays = 0f;
            t.event(FlotillaEvent.Type.BUILD_DONE, w.id, w.size);
          }
        }
        if (Mining.advanceRemote(w, days, t.k)) {
          t.event(FlotillaEvent.Type.REMOTE_SWITCHED, w.id, w.remote ? 1f : 0f);
        }
      }
    }
    Capacity.claim(t);
  }
}
