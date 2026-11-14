package nomad.campaign.econ.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import nomad.campaign.econ.Commod;
import nomad.campaign.econ.EconConstants;
import nomad.campaign.econ.Work;

public final class Capacity {

  private Capacity() {
  }

  static final float EPS = 0.01f;

  public static float flotillaSpace(FlotillaState s, Rules k, EconConstants shared) {
    return flotillaSpace(s.space, s.items, k) + Math.max(0f, s.foundingSpace);
  }

  public static float foundingSpace(EconConstants shared) {
    return Math.max(0f, shared.foundingSpacePerGood) * Commod.TRACKED.size();
  }

  public static float flotillaSpace(float shipsSpace, List<String> items, Rules k) {
    if (items != null && items.contains(Work.FULLERENE_SPOOL)) {
      return shipsSpace * (1f + k.fullereneSpoolSpace);
    }
    return shipsSpace;
  }

  public static float spaceAt(WorkCatalog catalog, String workId, int size) {
    WorkSize row = catalog.size(workId, size);
    return row == null ? 0f : row.space;
  }

  public static float spaceHeld(WorkState w, WorkCatalog catalog) {
    if (w == null) {
      return 0f;
    }
    float held = spaceAt(catalog, w.id, w.size);
    if (w.isBuilding()) {
      held = Math.max(held, spaceAt(catalog, w.id, w.buildTo));
    }
    return held;
  }

  public static float wantedBySuspended(List<WorkState> works, WorkCatalog catalog) {
    float wanted = 0f;
    for (WorkState w : works) {
      if (w.suspended) {
        wanted += spaceHeld(w, catalog);
      }
    }
    return wanted;
  }

  static void claim(TickSheet t) {
    if (!t.preview) {
      resume(t);
    }
    t.spaceWorks = held(t.works, t.catalog);
  }

  static void fit(TickSheet t) {
    float perUnit = Stores.spacePerUnit(t.s.storyDifficulty, t.k, t.shared);
    float stores = reserve(t, perUnit);
    float works = held(t.works, t.catalog);
    if (!t.preview) {
      if (works + stores > t.space + EPS) {
        letGo(t.works, t.catalog, stores, t.space);
        works = held(t.works, t.catalog);
        if (works + stores > t.space + EPS) {
          stores = spill(t, perUnit, stores);
        }
      }
      for (WorkState w : t.works) {
        WorkState before = t.s.work(w.id);
        boolean was = before != null && before.suspended;
        if (w.suspended && !was) {
          t.event(FlotillaEvent.Type.WORK_SUSPENDED, w.id, spaceHeld(w, t.catalog));
        } else if (!w.suspended && was) {
          t.event(FlotillaEvent.Type.WORK_RESUMED, w.id, spaceHeld(w, t.catalog));
        }
      }
    }
    for (TickSheet.WorkRun run : t.runs.values()) {
      WorkState w = t.work(run.id);
      run.space = w == null || w.suspended ? 0f : spaceHeld(w, t.catalog);
    }
    t.spaceWorks = works;
    t.spaceStores = stores;
  }

  private static float reserve(TickSheet t, float perUnit) {
    float total = 0f;
    for (TickSheet.Store store : t.stores.values()) {
      store.reserved = Math.max(store.target, whole(t.stock(store.good))) * perUnit;
      total += store.reserved;
    }
    return total;
  }

  private static float whole(float stock) {
    return stock > 0f ? (float) Math.floor(stock) : 0f;
  }

  private static float held(List<WorkState> works, WorkCatalog catalog) {
    float total = 0f;
    for (WorkState w : works) {
      if (!w.suspended) {
        total += spaceHeld(w, catalog);
      }
    }
    return total;
  }

  private static List<WorkState> inOrder(List<WorkState> works) {
    List<WorkState> ordered = new ArrayList<WorkState>(works);
    Collections.sort(ordered, Staffing.ORDER);
    return ordered;
  }

  private static void letGo(List<WorkState> works, WorkCatalog catalog, float stores,
      float space) {
    List<WorkState> ordered = inOrder(works);
    List<WorkState> out = new ArrayList<WorkState>();
    for (int i = ordered.size() - 1; i >= 0; i--) {
      if (held(works, catalog) + stores <= space + EPS) {
        break;
      }
      WorkState w = ordered.get(i);
      if (!w.suspended && spaceHeld(w, catalog) > 0f) {
        w.suspended = true;
        out.add(0, w);
      }
    }
    for (WorkState w : out) {
      if (held(works, catalog) + spaceHeld(w, catalog) + stores <= space + EPS) {
        w.suspended = false;
      }
    }
  }

  private static float spill(TickSheet t, float perUnit, float stores) {
    float goods = 0f;
    for (TickSheet.Store store : t.stores.values()) {
      goods += whole(t.stock(store.good)) * perUnit;
    }
    float room = Math.max(0f, t.space);
    if (goods <= room + EPS) {
      return stores;
    }
    float keep = room / goods;
    Map<String, Float> into = t.s.detached ? t.lost : t.toCargo;
    FlotillaEvent.Type type = t.s.detached
        ? FlotillaEvent.Type.STOCK_LOST : FlotillaEvent.Type.STOCK_MOVED;
    for (TickSheet.Store store : t.stores.values()) {
      float had = t.stock(store.good);
      float units = whole(had);
      float gone = Math.min(units, (float) Math.ceil(units * (1f - keep) - 1e-4f));
      if (gone <= 0f) {
        continue;
      }
      if (!t.s.detached) {
        float hold = Stores.holdLeft(t, Stores.inTanks(store.good));
        if (gone > hold) {
          t.fitKept.put(store.good, Float.valueOf(gone - hold));
          t.stockMovedOut = true;
          gone = hold;
        }
        if (gone <= 0f) {
          continue;
        }
      }
      t.stock.put(store.good, Float.valueOf(had - gone));
      Float already = into.get(store.good);
      into.put(store.good, Float.valueOf((already == null ? 0f : already.floatValue()) + gone));
      t.event(type, store.good, gone);
      t.stockMovedOut = true;
    }
    return reserve(t, perUnit);
  }

  public static boolean wouldReturn(FlotillaState s, String workId, WorkCatalog catalog, Rules k,
      EconConstants shared) {
    return wayBack(s, workId, catalog, k, shared).back;
  }

  public static boolean heldOutByHold(FlotillaState s, String workId, WorkCatalog catalog,
      Rules k, EconConstants shared) {
    return wayBack(s, workId, catalog, k, shared).holdFull;
  }

  private static final class Way {
    boolean back;
    boolean holdFull;
    final Map<String, Float> move = new LinkedHashMap<String, Float>();
  }

  private static Way wayBack(FlotillaState s, String workId, WorkCatalog catalog, Rules k,
      EconConstants shared) {
    Way way = new Way();
    if (fitsBack(s, workId, catalog, k, shared)) {
      way.back = true;
      return way;
    }
    WorkState asked = s.work(workId);
    if (asked == null || !asked.suspended || !s.flagsAct()) {
      return way;
    }
    FlotillaState c = s.copy();
    c.elapsedDays = 0f;
    TickSheet standing = FlotillaTick.preview(c, catalog, k, shared);
    List<String> goods = new ArrayList<String>();
    List<Float> over = new ArrayList<Float>();
    float total = 0f;
    for (TickSheet.Store store : standing.stores.values()) {
      float units = Stores.surplus(standing, store);
      if (units > 0f) {
        goods.add(store.good);
        over.add(Float.valueOf(units));
        total += units;
      }
    }
    if (total <= 0f || !fitsBack(without(s, goods, over, total), workId, catalog, k, shared)) {
      return way;
    }
    float cargo = wholeRoom(s.cargoRoom);
    float tanks = wholeRoom(s.fuelRoom);
    List<Float> take = new ArrayList<Float>();
    float room = 0f;
    for (int i = 0; i < goods.size(); i++) {
      boolean inTanks = Stores.inTanks(goods.get(i));
      float units = Math.min(over.get(i).floatValue(), inTanks ? tanks : cargo);
      take.add(Float.valueOf(units));
      room += units;
      if (inTanks) {
        tanks -= units;
      } else {
        cargo -= units;
      }
    }
    if (room < total && (room <= 0f
        || !fitsBack(without(s, goods, take, room), workId, catalog, k, shared))) {
      way.holdFull = true;
      return way;
    }
    int low = 1;
    int high = Math.round(room);
    while (low < high) {
      int mid = (low + high) >>> 1;
      if (fitsBack(without(s, goods, take, mid), workId, catalog, k, shared)) {
        high = mid;
      } else {
        low = mid + 1;
      }
    }
    float left = high;
    for (int i = 0; i < goods.size() && left > 0f; i++) {
      float units = Math.min(left, take.get(i).floatValue());
      if (units > 0f) {
        way.move.put(goods.get(i), Float.valueOf(units));
        left -= units;
      }
    }
    way.back = true;
    return way;
  }

  private static float wholeRoom(float room) {
    if (!(room > 0f)) {
      return 0f;
    }
    return Float.isInfinite(room) ? room : (float) Math.floor(room);
  }

  private static FlotillaState without(FlotillaState s, List<String> goods, List<Float> amounts,
      float units) {
    FlotillaState c = s.copy();
    float left = units;
    for (int i = 0; i < goods.size() && left > 0f; i++) {
      float gone = Math.min(left, amounts.get(i).floatValue());
      c.stock.put(goods.get(i), Float.valueOf(c.stock(goods.get(i)) - gone));
      left -= gone;
    }
    return c;
  }

  private static boolean fitsBack(FlotillaState s, String workId, WorkCatalog catalog, Rules k,
      EconConstants shared) {
    WorkState asked = s.work(workId);
    if (asked == null || !asked.suspended) {
      return false;
    }
    float above = 0f;
    for (WorkState w : inOrder(s.works)) {
      if (workId.equals(w.id)) {
        break;
      }
      above += w.suspended ? 0f : spaceHeld(w, catalog);
    }
    if (above + spaceHeld(asked, catalog) > flotillaSpace(s, k, shared) + EPS) {
      return false;
    }
    FlotillaState c = s.copy();
    c.elapsedDays = 0f;
    WorkState back = c.work(workId);
    back.suspended = false;
    TickSheet p = FlotillaTick.preview(c, catalog, k, shared);
    if (p.spaceWorks + p.spaceStores > p.space + EPS) {
      letGo(c.works, catalog, p.spaceStores, p.space);
      if (back.suspended) {
        return false;
      }
      p = FlotillaTick.preview(c, catalog, k, shared);
      if (p.spaceWorks + p.spaceStores > p.space + EPS) {
        return false;
      }
    }
    return true;
  }

  private static void resume(TickSheet t) {
    FlotillaState now = null;
    for (WorkState w : inOrderIfAnySuspended(t.works)) {
      if (!w.suspended) {
        continue;
      }
      if (now == null) {
        now = t.s.copy();
        now.residents = t.residents;
        now.stock.clear();
        now.stock.putAll(t.stock);
        now.works.clear();
        for (WorkState each : t.works) {
          now.works.add(each.copy());
        }
      }
      Way way = wayBack(now, w.id, t.catalog, t.k, t.shared);
      if (!way.back) {
        continue;
      }
      for (Map.Entry<String, Float> moved : way.move.entrySet()) {
        String good = moved.getKey();
        float units = moved.getValue().floatValue();
        float left = t.stock(good) - units;
        t.stock.put(good, Float.valueOf(left));
        now.stock.put(good, Float.valueOf(left));
        Float already = t.toCargo.get(good);
        t.toCargo.put(good, Float.valueOf((already == null ? 0f : already.floatValue()) + units));
        if (Stores.inTanks(good)) {
          now.fuelRoom -= units;
        } else {
          now.cargoRoom -= units;
        }
        t.event(FlotillaEvent.Type.STOCK_MOVED, good, units);
      }
      w.suspended = false;
      now.work(w.id).suspended = false;
    }
  }

  private static List<WorkState> inOrderIfAnySuspended(List<WorkState> works) {
    for (WorkState w : works) {
      if (w.suspended) {
        return inOrder(works);
      }
    }
    return Collections.emptyList();
  }
}
