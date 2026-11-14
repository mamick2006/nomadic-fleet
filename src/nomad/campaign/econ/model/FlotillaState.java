package nomad.campaign.econ.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FlotillaState {

  public float residents;
  public float space;
  public float berths;
  public float foundingSpace;

  public boolean normalOrStory;
  public boolean storyDifficulty;
  public boolean paused;
  public boolean priorPaused;
  public boolean detached;
  public float detachedSuppliesPerMonth;
  public NeedsLevel priorLevel;

  public List<WorkState> works = new ArrayList<WorkState>();
  public List<String> items = new ArrayList<String>();
  public List<String> furnishings = new ArrayList<String>();
  public List<Prereqs.Reading> prereqs = new ArrayList<Prereqs.Reading>();

  public Map<String, Float> stock = new LinkedHashMap<String, Float>();
  public Map<String, Float> target = new LinkedHashMap<String, Float>();
  public Map<String, Integer> flags = new LinkedHashMap<String, Integer>();
  public Map<String, Float> cargoSpare = new LinkedHashMap<String, Float>();
  public float cargoRoom = Float.POSITIVE_INFINITY;
  public float fuelRoom = Float.POSITIVE_INFINITY;
  public boolean priorCargoFull;
  public boolean priorFuelFull;

  public float fleetMarines;
  public float fleetCrewRoom = Float.POSITIVE_INFINITY;
  public float marinesTarget = Float.NaN;
  public float marinesPending;

  public Mining.Site site = Mining.Site.NONE;

  public float foundryPointsWanted;
  public float foundryPointsNeedBench;
  public float suppliesOwed;

  public float elapsedDays;
  public int ticksPerMonth = 10;

  public float stock(String good) {
    Float v = stock.get(good);
    return v == null ? 0f : v.floatValue();
  }

  public float target(String good) {
    Float v = target.get(good);
    return v == null ? 0f : v.floatValue();
  }

  public int flags(String good) {
    return StoreFlags.of(flags.get(good));
  }

  public float cargoSpare(String good) {
    Float v = cargoSpare.get(good);
    return v == null ? 0f : v.floatValue();
  }

  public List<String> installedItems() {
    if (furnishings.isEmpty()) {
      return items;
    }
    List<String> out = new ArrayList<String>(items);
    out.removeAll(furnishings);
    return out;
  }

  public WorkState work(String id) {
    for (WorkState w : works) {
      if (w.id != null && w.id.equals(id)) {
        return w;
      }
    }
    return null;
  }

  public boolean flagsAct() {
    return !detached && !paused;
  }

  public FlotillaState copy() {
    FlotillaState c = new FlotillaState();
    c.residents = residents;
    c.space = space;
    c.berths = berths;
    c.foundingSpace = foundingSpace;
    c.normalOrStory = normalOrStory;
    c.storyDifficulty = storyDifficulty;
    c.paused = paused;
    c.priorPaused = priorPaused;
    c.detached = detached;
    c.detachedSuppliesPerMonth = detachedSuppliesPerMonth;
    c.priorLevel = priorLevel;
    for (WorkState w : works) {
      c.works.add(w.copy());
    }
    c.items.addAll(items);
    c.furnishings.addAll(furnishings);
    c.prereqs.addAll(prereqs);
    c.stock.putAll(stock);
    c.target.putAll(target);
    c.flags.putAll(flags);
    c.cargoSpare.putAll(cargoSpare);
    c.cargoRoom = cargoRoom;
    c.fuelRoom = fuelRoom;
    c.priorCargoFull = priorCargoFull;
    c.priorFuelFull = priorFuelFull;
    c.fleetMarines = fleetMarines;
    c.fleetCrewRoom = fleetCrewRoom;
    c.marinesTarget = marinesTarget;
    c.marinesPending = marinesPending;
    c.site = site;
    c.foundryPointsWanted = foundryPointsWanted;
    c.foundryPointsNeedBench = foundryPointsNeedBench;
    c.suppliesOwed = suppliesOwed;
    c.elapsedDays = elapsedDays;
    c.ticksPerMonth = ticksPerMonth;
    return c;
  }
}
