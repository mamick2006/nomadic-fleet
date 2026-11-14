package nomad.campaign.econ.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import nomad.campaign.econ.Commod;
import nomad.campaign.econ.EconConstants;

public final class TickSheet {

  public static final class WorkRun {

    public final String id;
    public final int size;
    public final WorkSize def;
    public WorkStatus status = WorkStatus.RUNNING;
    public WorkStatus.Reason reason = WorkStatus.Reason.NONE;
    public float space;
    public float staffNeed;
    public float staffOwn;
    public boolean effectOn;
    public final Map<String, Float> makesPerMonth = new LinkedHashMap<String, Float>();
    public final Map<String, Float> usesPerMonth = new LinkedHashMap<String, Float>();
    public final Map<String, Float> made = new LinkedHashMap<String, Float>();
    public final Map<String, Float> used = new LinkedHashMap<String, Float>();
    public float buildDaysLeft;
    public float returnsAtResidents = Float.POSITIVE_INFINITY;

    WorkRun(String id, int size, WorkSize def) {
      this.id = id;
      this.size = size;
      this.def = def;
    }

    public boolean hasStaff() {
      return staffOwn + 1e-4f >= staffNeed;
    }
  }

  public static final class Store {

    public final String good;
    public float target;
    public float minTarget;
    public float reserved;
    public float netPerMonth;

    Store(String good) {
      this.good = good;
    }
  }

  public final FlotillaState s;
  public final WorkCatalog catalog;
  public final Rules k;
  public final EconConstants shared;
  public final boolean preview;
  public final float monthFrac;
  public final float yearFrac;
  public float stepFrac;
  public final float space;

  public float residents;
  public final List<WorkState> works = new ArrayList<WorkState>();
  public final Map<String, Float> stock = new LinkedHashMap<String, Float>();

  public float berths;

  public final Map<String, WorkRun> runs = new LinkedHashMap<String, WorkRun>();
  public float working;

  public int supportLimit;

  public Requirement.Reading requirement = new Requirement.Reading();

  public final Map<String, Store> stores = new LinkedHashMap<String, Store>();
  public float spaceWorks;
  public float spaceStores;
  public boolean stockMovedOut;

  final Map<String, Float> fitKept = new LinkedHashMap<String, Float>();
  final Map<String, Float> outKept = new LinkedHashMap<String, Float>();
  public boolean cargoFull;
  public boolean fuelFull;

  public BasicNeeds.Reading needs = new BasicNeeds.Reading();
  public float efficiency = 1f;
  public final Map<String, Float> efficiencySources = new LinkedHashMap<String, Float>();

  public final Map<String, Float> fromCargo = new LinkedHashMap<String, Float>();
  public final Map<String, Float> toCargo = new LinkedHashMap<String, Float>();
  public final Map<String, Float> sold = new LinkedHashMap<String, Float>();
  public final Map<String, Float> soldFromCargo = new LinkedHashMap<String, Float>();
  public final Map<String, Float> lost = new LinkedHashMap<String, Float>();
  public float saleCredits;
  public float credits;
  public float foundryPoints;

  public float spaceTaken;
  float detachedRoomAtFit = Float.NaN;

  public float upkeepDue;
  public float upkeepPaid;
  public float suppliesOwed;

  public float residentsGrown;
  public float residentsLeft;
  public float residentsLostBerths;
  public float residentsUsed;
  public float marinesMade;
  public float residentsMade;
  public float residentRoom;

  public final List<FlotillaEvent> events = new ArrayList<FlotillaEvent>();

  TickSheet(FlotillaState s, WorkCatalog catalog, Rules k, EconConstants shared, boolean preview) {
    this.s = s;
    this.catalog = catalog;
    this.k = k;
    this.shared = shared;
    this.preview = preview;
    float days = preview ? 0f : Math.max(0f, s.elapsedDays);
    this.monthFrac = days / 30f;
    this.yearFrac = days / 365f;
    this.stepFrac = this.monthFrac;
    this.space = Capacity.flotillaSpace(s, k, shared);
    this.residents = s.residents;
    this.berths = s.berths;
    this.suppliesOwed = s.suppliesOwed;
    this.cargoFull = s.priorCargoFull;
    this.fuelFull = s.priorFuelFull;
    for (WorkState w : s.works) {
      this.works.add(w.copy());
    }
    this.stock.putAll(s.stock);
    for (String good : Commod.STORED) {
      Store store = new Store(good);
      store.target = s.target(good);
      this.stores.put(good, store);
    }
  }

  void openRuns() {
    List<WorkState> ordered = new ArrayList<WorkState>(works);
    Collections.sort(ordered, Staffing.ORDER);
    for (WorkState w : ordered) {
      WorkRun run = new WorkRun(w.id, w.size, catalog.size(w.id, w.size));
      if (w.isBuilding()) {
        run.buildDaysLeft = Math.max(0f, shared.workBuildDays - w.buildDays);
      }
      runs.put(w.id, run);
    }
  }

  public float stock(String good) {
    Float v = stock.get(good);
    return v == null ? 0f : v.floatValue();
  }

  public WorkState work(String id) {
    for (WorkState w : works) {
      if (w.id != null && w.id.equals(id)) {
        return w;
      }
    }
    return null;
  }

  public WorkRun run(String id) {
    return runs.get(id);
  }

  public float spaceNeeds() {
    return Math.max(0f, space - spaceWorks - spaceStores);
  }

  public float spaceLeftForNeeds() {
    return Math.max(0f, needs.haveSpace - spaceTaken);
  }

  public float requirementFactor() {
    return requirement == null ? 1f : requirement.factor;
  }

  public float residentsInNeeds() {
    return Math.max(0f, residents - working);
  }

  public int unstaffedCount() {
    int n = 0;
    for (WorkRun run : runs.values()) {
      if (run.status == WorkStatus.UNSTAFFED) {
        n++;
      }
    }
    return n;
  }

  public int standingDownCount() {
    int n = 0;
    for (WorkRun run : runs.values()) {
      if (run.status == WorkStatus.STANDING_DOWN) {
        n++;
      }
    }
    return n;
  }

  public boolean anyReturnsAtResidents() {
    for (WorkRun run : runs.values()) {
      if (run.returnsAtResidents < Float.POSITIVE_INFINITY) {
        return true;
      }
    }
    return false;
  }

  void event(FlotillaEvent.Type type, String subject, float amount) {
    event(type, subject, amount, null);
  }

  void event(FlotillaEvent.Type type, String subject, float amount, String detail) {
    if (!preview) {
      events.add(new FlotillaEvent(type, subject, amount, detail));
    }
  }
}
