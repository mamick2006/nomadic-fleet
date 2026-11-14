package nomad.campaign.econ.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import nomad.campaign.econ.Commod;
import nomad.campaign.econ.EconConstants;

public final class Brokerage {

  private Brokerage() {
  }

  public static float salePrice(String good, EconConstants shared) {
    return shared.price(good) * shared.creditsSalePriceFrac;
  }

  public static float unitsPerMonth(WorkSize def, float efficiency) {
    return def == null ? 0f : Math.max(0f, def.param(Works.PARAM_SALE_UNITS, 0f) * efficiency);
  }

  public static float feeEfficiency(float efficiency, Rules k) {
    if (efficiency <= 1f) {
      return Math.max(0f, efficiency);
    }
    return 1f + (efficiency - 1f) * Math.max(0f, k.brokerageBonusShare);
  }

  public static float creditsPerMonth(WorkSize def, float efficiency, List<String> items,
      boolean normalOrStory, Rules k, EconConstants shared) {
    if (def == null) {
      return 0f;
    }
    return Math.max(0f, def.makes(Works.CREDITS) * feeEfficiency(efficiency, k)
        * Production.itemYield(Works.CREDITS, items, normalOrStory, shared) * k.productionMult);
  }

  public static boolean runs(TickSheet t) {
    if (t.s.paused || t.efficiency <= 0f) {
      return false;
    }
    TickSheet.WorkRun run = t.run(Works.BROKERAGE);
    if (run == null || run.def == null) {
      return false;
    }
    return run.status == WorkStatus.RUNNING;
  }

  public static float capacity(TickSheet t) {
    return runs(t) ? unitsPerMonth(t.run(Works.BROKERAGE).def, t.efficiency) * t.stepFrac : 0f;
  }

  static void pay(TickSheet t) {
    if (!runs(t)) {
      return;
    }
    TickSheet.WorkRun run = t.run(Works.BROKERAGE);
    float perMonth = creditsPerMonth(run.def, t.efficiency, t.s.items, t.s.normalOrStory, t.k,
        t.shared);
    run.makesPerMonth.put(Works.CREDITS, Float.valueOf(perMonth));
    float paid = perMonth * t.stepFrac;
    if (t.preview || paid <= 0f) {
      return;
    }
    Production.add(run.made, Works.CREDITS, paid);
    t.credits += paid;
  }

  static float sellSurplus(TickSheet t, float capacity) {
    if (capacity <= 0f) {
      return 0f;
    }
    for (String good : byPrice(t.shared)) {
      if (capacity <= 0f) {
        break;
      }
      TickSheet.Store store = t.stores.get(good);
      float price = salePrice(good, t.shared);
      if (store == null || price <= 0f || !StoreFlags.has(t.s.flags(good), StoreFlags.SELL)) {
        continue;
      }
      float units = Math.min(Stores.surplus(t, store), capacity);
      if (units <= 0f) {
        continue;
      }
      Production.restock(t, good, t.stock(good) - units);
      Stores.add(t.sold, good, units);
      t.saleCredits += units * price;
      capacity -= units;
    }
    return Math.max(0f, capacity);
  }

  static void sellCargo(TickSheet t, float capacity) {
    if (capacity <= 0f) {
      return;
    }
    for (String good : byPrice(t.shared)) {
      if (capacity <= 0f) {
        break;
      }
      float price = salePrice(good, t.shared);
      if (price <= 0f || !StoreFlags.has(t.s.flags(good), StoreFlags.SELL_CARGO)) {
        continue;
      }
      float inCargo = Stores.whole(Stores.spareLeft(t, good));
      float units = Math.min(inCargo, capacity);
      if (units <= 0f) {
        continue;
      }
      float stock = Math.max(0f, t.stock(good));
      float open = stock - Stores.whole(stock);
      float taken = Math.max(0f, (float) Math.ceil(units - open));
      t.stock.put(good, Float.valueOf(stock + taken - units));
      if (taken > 0f) {
        Stores.add(t.soldFromCargo, good, taken);
      }
      t.saleCredits += units * price;
      capacity -= units;
    }
  }

  private static List<String> byPrice(final EconConstants shared) {
    List<String> goods = new ArrayList<String>(Commod.STORED);
    Collections.sort(goods, new Comparator<String>() {
      @Override
      public int compare(String a, String b) {
        return Float.compare(salePrice(b, shared), salePrice(a, shared));
      }
    });
    return goods;
  }
}
