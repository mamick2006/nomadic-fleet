package nomad.campaign.econ.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import nomad.campaign.econ.Commod;
import nomad.campaign.econ.EconConstants;
import nomad.campaign.econ.Work;

public final class Stores {

  private Stores() {
  }

  private static float slack(float room) {
    return 1e-3f + 1e-6f * Math.abs(room);
  }

  public static float spacePerUnit(boolean storyDifficulty, Rules k, EconConstants shared) {
    float perUnit = k.storeSpacePerUnit;
    if (storyDifficulty) {
      perUnit *= shared.storyDifficultyStorageSpaceMult;
    }
    return perUnit;
  }

  public static float whole(float stock) {
    return stock > 0f ? (float) Math.floor(stock) : 0f;
  }

  public static float reserved(float target, float stock, float perUnit) {
    return Math.max(target, whole(stock)) * perUnit;
  }

  public static Map<String, Float> minTargets(FlotillaState s, WorkCatalog catalog, Rules k,
      EconConstants shared) {
    return minTargets(s.works, s, catalog, k, shared);
  }

  public static float minTarget(String good, FlotillaState s, WorkCatalog catalog, Rules k,
      EconConstants shared) {
    return value(minTargets(s.works, s, catalog, k, shared), good);
  }

  private static Map<String, Float> minTargets(List<WorkState> works, FlotillaState s,
      WorkCatalog catalog, Rules k, EconConstants shared) {
    Map<String, Float> perMonth = new LinkedHashMap<String, Float>();
    float top = Float.NaN;
    for (WorkState w : works) {
      if (w.id == null || !w.isBuilt() || w.mothballed || w.suspended || w.prereqUnmet) {
        continue;
      }
      WorkSize row = catalog.size(w.id, w.size);
      if (row == null || row.uses.isEmpty()) {
        continue;
      }
      if (Float.isNaN(top)) {
        top = topEfficiency(works, s, catalog, k, shared);
      }
      for (Map.Entry<String, Float> use : Production
          .usesPerMonth(row, top, s.items, s.normalOrStory, k, shared).entrySet()) {
        add(perMonth, use.getKey(), use.getValue().floatValue());
      }
    }
    int ticks = Math.max(1, s.ticksPerMonth);
    Map<String, Float> out = new LinkedHashMap<String, Float>();
    for (String good : Commod.STORED) {
      float used = value(perMonth, good);
      out.put(good, Float.valueOf(used > 0f ? (float) Math.ceil(used / ticks) : 0f));
    }
    return out;
  }

  private static float topEfficiency(List<WorkState> works, FlotillaState s, WorkCatalog catalog,
      Rules k, EconConstants shared) {
    float guildHall = 0f;
    for (WorkState w : works) {
      if (Works.GUILD_HALL.equals(w.id) && w.isBuilt() && !w.mothballed) {
        WorkSize row = catalog.size(w.id, w.size);
        guildHall = row == null ? 0f : row.param(Works.PARAM_EFFICIENCY, 0f);
      }
    }
    NeedsLevel[] levels = NeedsLevel.values();
    return BasicNeeds.globalEfficiency(levels[levels.length - 1], s.items, guildHall,
        s.normalOrStory, s.storyDifficulty, k, shared);
  }

  static void minTargets(TickSheet t) {
    Map<String, Float> mins = minTargets(t.works, t.s, t.catalog, t.k, t.shared);
    for (TickSheet.Store store : t.stores.values()) {
      store.minTarget = value(mins, store.good);
    }
    fitTargets(t, t.space - Math.max(t.spaceWorks, worksSpace(t)),
        Capacity.wantedBySuspended(t.works, t.catalog));
  }

  private static float worksSpace(TickSheet t) {
    float space = 0f;
    for (TickSheet.WorkRun run : t.runs.values()) {
      WorkState w = t.work(run.id);
      if (run.def != null && w != null && !w.suspended) {
        space += run.def.space;
      }
    }
    return space;
  }

  static float fitTargets(TickSheet t, float room, float kept) {
    float perUnit = spacePerUnit(t.s.storyDifficulty, t.k, t.shared);
    for (TickSheet.Store store : t.stores.values()) {
      float asked = t.s.target(store.good);
      asked = asked > 0f ? (float) Math.ceil(asked) : 0f;
      store.target = Math.max(asked, store.minTarget);
    }
    float need = measure(t, perUnit);
    float free = room - Math.max(0f, kept);
    if (need > free + slack(free)) {
      for (TickSheet.Store store : t.stores.values()) {
        float held = whole(t.stock(store.good));
        store.target = Math.max(store.minTarget, Math.min(store.target, held));
      }
      need = measure(t, perUnit);
      if (need > room + slack(room)) {
        for (TickSheet.Store store : t.stores.values()) {
          store.target = store.minTarget;
        }
        need = measure(t, perUnit);
      }
    }
    return Math.max(0f, need - room);
  }

  static float fitTargets(TickSheet t, float room) {
    return fitTargets(t, room, 0f);
  }

  private static float measure(TickSheet t, float perUnit) {
    double space = 0d;
    for (TickSheet.Store store : t.stores.values()) {
      store.reserved = reserved(store.target, t.stock(store.good), perUnit);
      space += store.reserved;
    }
    t.spaceStores = (float) space;
    return t.spaceStores;
  }

  static float surplus(TickSheet t, TickSheet.Store store) {
    float stock = t.stock(store.good);
    if (Commod.SUPPLIES.equals(store.good)) {
      stock -= Detached.claim(t);
    }
    return Math.max(0f, whole(stock) - (float) Math.ceil(store.target));
  }

  static void settle(TickSheet t) {
    writeNets(t);
    if (t.preview || t.stepFrac <= 0f) {
      return;
    }
    if (!t.s.flagsAct()) {
      Brokerage.sellSurplus(t, Brokerage.capacity(t));
      return;
    }
    for (TickSheet.Store store : t.stores.values()) {
      if (t.stockMovedOut || !StoreFlags.has(t.s.flags(store.good), StoreFlags.IN)) {
        continue;
      }
      float take = shortOf(store.target, t.stock(store.good), spareLeft(t, store.good));
      if (take > 0f) {
        t.stock.put(store.good, Float.valueOf(t.stock(store.good) + take));
        add(t.fromCargo, store.good, take);
      }
    }
    float capacity = Brokerage.sellSurplus(t, Brokerage.capacity(t));
    t.outKept.clear();
    for (TickSheet.Store store : t.stores.values()) {
      if (!StoreFlags.has(t.s.flags(store.good), StoreFlags.OUT)) {
        continue;
      }
      t.fitKept.remove(store.good);
      float over = surplus(t, store);
      float out = Math.min(over, holdLeft(t, inTanks(store.good)));
      if (out > 0f) {
        t.stock.put(store.good, Float.valueOf(t.stock(store.good) - out));
        add(t.toCargo, store.good, out);
      }
      if (over > out) {
        t.outKept.put(store.good, Float.valueOf(over - out));
      }
    }
    Brokerage.sellCargo(t, capacity);
  }

  static boolean inTanks(String good) {
    return Commod.FUEL.equals(good);
  }

  static float holdLeft(TickSheet t, boolean tanks) {
    float room = tanks ? t.s.fuelRoom : t.s.cargoRoom;
    if (Float.isInfinite(room)) {
      return room > 0f ? room : 0f;
    }
    for (String good : Commod.STORED) {
      if (inTanks(good) == tanks) {
        room += value(t.fromCargo, good) + value(t.soldFromCargo, good) - value(t.toCargo, good);
      }
    }
    return room > 0f ? (float) Math.floor(room) : 0f;
  }

  static boolean holdKeepsIn(TickSheet t, TickSheet.Store store) {
    float over = surplus(t, store);
    if (over <= 0f) {
      return false;
    }
    boolean tanks = inTanks(store.good);
    float room = holdLeft(t, tanks);
    if (Float.isInfinite(room)) {
      return false;
    }
    if (!tanks) {
      for (TickSheet.Store ahead : t.stores.values()) {
        if (ahead == store || room < over) {
          break;
        }
        if (!inTanks(ahead.good) && StoreFlags.has(t.s.flags(ahead.good), StoreFlags.OUT)) {
          room -= surplus(t, ahead);
        }
      }
    }
    return over > room;
  }

  static void closeHold(TickSheet t) {
    if (t.preview || !t.s.flagsAct() || (t.monthFrac <= 0f && t.fitKept.isEmpty())) {
      return;
    }
    t.cargoFull = closeHold(t, false, t.s.priorCargoFull);
    t.fuelFull = closeHold(t, true, t.s.priorFuelFull);
  }

  private static boolean closeHold(TickSheet t, boolean tanks, boolean was) {
    float kept = 0f;
    float sent = 0f;
    for (String good : Commod.STORED) {
      if (inTanks(good) == tanks) {
        kept += keptIn(t, good);
        sent += value(t.toCargo, good);
      }
    }
    if (kept <= 0f) {
      return was && holdLeft(t, tanks) < Math.max(1f, sent);
    }
    if (!was) {
      for (String good : Commod.STORED) {
        float here = keptIn(t, good);
        if (inTanks(good) == tanks && here > 0f) {
          t.event(FlotillaEvent.Type.HOLD_FULL, good, here);
        }
      }
    }
    return true;
  }

  private static float keptIn(TickSheet t, String good) {
    return value(t.fitKept, good) + value(t.outKept, good);
  }

  static float spareLeft(TickSheet t, String good) {
    return Math.max(0f,
        t.s.cargoSpare(good) - value(t.fromCargo, good) - value(t.soldFromCargo, good));
  }

  private static float shortOf(float target, float stock, float spare) {
    float want = (float) Math.floor(target) - whole(stock);
    return Math.max(0f, Math.min(want, whole(spare)));
  }

  private static void writeNets(TickSheet t) {
    for (TickSheet.Store store : t.stores.values()) {
      store.netPerMonth = 0f;
    }
    for (TickSheet.WorkRun run : t.runs.values()) {
      for (Map.Entry<String, Float> made : run.makesPerMonth.entrySet()) {
        TickSheet.Store store = t.stores.get(made.getKey());
        if (store != null) {
          store.netPerMonth += made.getValue().floatValue();
        }
      }
      for (Map.Entry<String, Float> used : run.usesPerMonth.entrySet()) {
        TickSheet.Store store = t.stores.get(used.getKey());
        if (store != null) {
          store.netPerMonth -= used.getValue().floatValue();
        }
      }
    }
  }

  public static Map<String, Float> fill(FlotillaState s, WorkCatalog catalog, Rules k,
      EconConstants shared) {
    Map<String, Float> out = new LinkedHashMap<String, Float>();
    if (!s.flagsAct()) {
      return out;
    }
    TickSheet now = FlotillaTick.preview(s, catalog, k, shared);
    for (TickSheet.Store store : now.stores.values()) {
      if (!StoreFlags.has(s.flags(store.good), StoreFlags.IN)) {
        continue;
      }
      float take = shortOf(store.target, s.stock(store.good), s.cargoSpare(store.good));
      if (take > 0f) {
        out.put(store.good, Float.valueOf(take));
      }
    }
    return out;
  }

  static float value(Map<String, Float> map, String key) {
    Float v = map.get(key);
    return v == null ? 0f : v.floatValue();
  }

  static void add(Map<String, Float> map, String key, float amount) {
    map.put(key, Float.valueOf(value(map, key) + amount));
  }
}
