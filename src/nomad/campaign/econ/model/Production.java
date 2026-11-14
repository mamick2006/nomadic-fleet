package nomad.campaign.econ.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import nomad.campaign.econ.Commod;
import nomad.campaign.econ.EconConstants;
import nomad.campaign.econ.SurvivalRules;
import nomad.campaign.econ.Work;

public final class Production {

  private static final float EPS = 1e-4f;

  private Production() {
  }

  public static float itemYield(String good, List<String> items, boolean normalOrStory,
      EconConstants shared) {
    if (good == null || items == null || items.isEmpty()) {
      return 1f;
    }
    float bonus = 0f;
    if (Commod.FOOD.equals(good)) {
      if (items.contains(Work.SOIL_NANITES)) {
        bonus += shared.itemSoilNanitesFood;
      }
      if (items.contains(Work.BIOFACTORY_EMBRYO)) {
        bonus += shared.itemBiofactoryFood;
      }
    } else if (Commod.LUXURY_GOODS.equals(good)) {
      if (items.contains(Work.BIOFACTORY_EMBRYO)) {
        bonus += shared.itemBiofactoryLuxury;
      }
    } else if (Commod.SUPPLIES.equals(good)) {
      if (items.contains(Work.PRISTINE_NANOFORGE)) {
        bonus += shared.itemPristineNanoforgeSupplies;
      } else if (items.contains(Work.CORRUPTED_NANOFORGE)) {
        bonus += shared.itemCorruptedNanoforgeSupplies;
      }
    } else if (Works.POINTS.equals(good)) {
      if (items.contains(Work.CRYOARITHMETIC_ENGINE)) {
        bonus += shared.itemCryoarithmeticAutoforgeSpeed;
      }
      if (items.contains(Work.PRISTINE_NANOFORGE)) {
        bonus += shared.itemPristineNanoforgeAutoforge;
      } else if (items.contains(Work.CORRUPTED_NANOFORGE)) {
        bonus += shared.itemCorruptedNanoforgeAutoforge;
      }
    } else if (Works.CREDITS.equals(good)) {
      if (items.contains(Work.DEALMAKER_HOLOSUITE)) {
        bonus += shared.itemDealmakerCredits;
      }
    } else if (Works.RESIDENTS.equals(good)) {
      if (items.contains(Work.BIOFACTORY_EMBRYO)) {
        bonus += shared.itemBiofactoryVats;
      }
    } else if (Commod.HAND_WEAPONS.equals(good)) {
      if (items.contains(Work.DRONE_REPLICATOR)) {
        bonus += shared.itemDroneReplicatorArmaments;
      }
    }
    return 1f + bonus * shared.itemEffectMult(normalOrStory);
  }

  public static float itemYield(String workId, String good, List<String> items,
      boolean normalOrStory, EconConstants shared) {
    float yield = itemYield(good, items, normalOrStory, shared);
    if (!Works.MINING_RIG.equals(workId) || items == null || items.isEmpty()) {
      return yield;
    }
    float scale = shared.itemEffectMult(normalOrStory);
    if (items.contains(Work.MANTLE_BORE)) {
      yield *= 1f + shared.itemMantleBoreMining * scale;
    }
    if (Commod.VOLATILES.equals(good) && items.contains(Work.PLASMA_DYNAMO)) {
      yield *= 1f + shared.itemPlasmaDynamoVolatiles * scale;
    }
    return yield;
  }

  public static float inputFactor(String workId, Collection<String> products, String input,
      List<String> items, boolean normalOrStory, Rules k, EconConstants shared) {
    if (input == null || products == null) {
      return 1f;
    }
    float factor = 1f;
    float scale = shared.itemEffectMult(normalOrStory);
    if (Commod.VOLATILES.equals(input) && products.contains(Commod.FUEL)) {
      if (Works.FUEL_PLANT.equals(workId)) {
        factor *= k.survivalFuelInputMult;
      }
      if (has(items, Work.SYNCHROTRON)) {
        factor *= 1f - Math.min(1f, shared.itemSynchrotronFuelInput * scale);
      }
    }
    boolean refines = (Commod.ORE.equals(input) && products.contains(Commod.METALS))
        || (Commod.RARE_ORE.equals(input) && products.contains(Commod.RARE_METALS));
    if (refines && has(items, Work.CATALYTIC_CORE)) {
      factor *= 1f - Math.min(1f, shared.itemCatalyticCoreOreInput * scale);
    }
    if (Works.RESIDENTS.equals(input) && products.contains(Commod.ORGANS)
        && has(items, Work.BIOFACTORY_EMBRYO)) {
      factor *= 1f - Math.min(1f, shared.itemBiofactoryHarvesterResidents * scale);
    }
    return Math.max(0f, factor);
  }

  public static float inputScale(float efficiency, EconConstants shared) {
    if (efficiency >= 1f) {
      return efficiency;
    }
    float floor = shared.efficiencyWasteFloor;
    if (efficiency <= 0f) {
      return 0f;
    }
    return floor <= 0f ? 1f : Math.min(1f, efficiency / floor);
  }

  public static Map<String, Float> makesPerMonth(WorkSize def, float efficiency,
      List<String> items, boolean normalOrStory, Rules k, EconConstants shared) {
    Map<String, Float> out = new LinkedHashMap<String, Float>();
    if (def == null || efficiency <= 0f) {
      return out;
    }
    if (Works.FOUNDRY.equals(def.work)) {
      out.put(Works.POINTS,
          Float.valueOf(Foundry.pointsPerMonth(def, efficiency, items, normalOrStory, k, shared)));
      return out;
    }
    for (Map.Entry<String, Float> made : def.makes.entrySet()) {
      out.put(made.getKey(), Float.valueOf(output(def.work, made.getKey(),
          made.getValue().floatValue(), efficiency, items, normalOrStory, k, shared)));
    }
    return out;
  }

  private static float output(String workId, String good, float rowAmount, float efficiency,
      List<String> items, boolean normalOrStory, Rules k, EconConstants shared) {
    float scale = Works.BROKERAGE.equals(workId) ? Brokerage.feeEfficiency(efficiency, k)
        : efficiency;
    return rowAmount * scale * itemYield(workId, good, items, normalOrStory, shared)
        * k.productionMult;
  }

  public static Map<String, Float> usesPerMonth(WorkSize def, float efficiency,
      List<String> items, boolean normalOrStory, Rules k, EconConstants shared) {
    Map<String, Float> out = new LinkedHashMap<String, Float>();
    if (def == null || efficiency <= 0f) {
      return out;
    }
    if (Works.FOUNDRY.equals(def.work)) {
      return Foundry.materials(def,
          Foundry.pointsPerMonth(def, efficiency, items, normalOrStory, k, shared), items,
          normalOrStory, shared);
    }
    for (WorkSize.Line line : def.lines) {
      for (Map.Entry<String, Float> used : line.uses.entrySet()) {
        add(out, used.getKey(), need(def.work, line, used, efficiency, items, normalOrStory, k,
            shared));
      }
    }
    return out;
  }

  private static float need(String workId, WorkSize.Line line, Map.Entry<String, Float> used,
      float efficiency, List<String> items, boolean normalOrStory, Rules k, EconConstants shared) {
    String input = used.getKey();
    float row = used.getValue().floatValue();
    float factor = inputFactor(workId, line.makes.keySet(), input, items, normalOrStory, k, shared);
    if (Works.RESIDENTS.equals(input) && line.makes.containsKey(Works.MARINES)) {
      return row * output(workId, Works.MARINES, 1f, efficiency, items, normalOrStory, k, shared)
          * factor;
    }
    return row * lineDraw(workId, line, efficiency, items, normalOrStory, k, shared) * factor;
  }

  private static float marinesPart(TickSheet t, String workId, WorkSize.Line line, float share) {
    Float row = line.makes.get(Works.MARINES);
    if (row == null) {
      return 1f;
    }
    return Marines.part(t, output(workId, Works.MARINES, row.floatValue(), t.efficiency,
        t.s.items, t.s.normalOrStory, t.k, t.shared) * share);
  }

  public static Map<String, Float> minedPerMonth(WorkSize def, Map<String, Float> tiers,
      float efficiency, List<String> items, boolean normalOrStory, Rules k, EconConstants shared) {
    Map<String, Float> out = new LinkedHashMap<String, Float>();
    if (def == null || tiers == null || efficiency <= 0f) {
      return out;
    }
    for (Map.Entry<String, Float> raw : def.makes.entrySet()) {
      Float tier = tiers.get(raw.getKey());
      if (tier == null || tier.floatValue() <= 0f) {
        continue;
      }
      out.put(raw.getKey(), Float.valueOf(tier.floatValue() * output(def.work, raw.getKey(),
          raw.getValue().floatValue(), efficiency, items, normalOrStory, k, shared)));
    }
    return out;
  }

  public static Map<String, WorkStatus.Reason> stalled(TickSheet t, String workId) {
    Map<String, WorkStatus.Reason> out = new LinkedHashMap<String, WorkStatus.Reason>();
    TickSheet.WorkRun run = t == null ? null : t.run(workId);
    if (run == null || run.def == null || Works.FOUNDRY.equals(workId)
        || Works.BROKERAGE.equals(workId)) {
      return out;
    }
    if (Works.MINING_RIG.equals(workId)) {
      for (Map.Entry<String, Float> raw : mined(t, run).entrySet()) {
        if (outputFull(t, raw.getKey(), raw.getValue().floatValue())) {
          out.put(raw.getKey(), fullWhy(t, raw.getKey()));
        }
      }
      return out;
    }
    float share = share(t);
    for (WorkSize.Line line : run.def.lines) {
      if (line.uses.isEmpty()) {
        for (Map.Entry<String, Float> made : line.makes.entrySet()) {
          if (outputFull(t, made.getKey(), output(t, run.id, made))) {
            out.put(made.getKey(), fullWhy(t, made.getKey()));
          }
        }
        continue;
      }
      WorkStatus.Reason why = blocked(t, run.id, line, share);
      if (why != WorkStatus.Reason.NONE) {
        for (String good : line.makes.keySet()) {
          out.put(good, why);
        }
      }
    }
    return out;
  }

  public static float survivalFuelInputMult(WorkSize fuelPlant, SurvivalRules rules,
      EconConstants shared) {
    if (fuelPlant == null || rules == null) {
      return 1f;
    }
    float fuel = fuelPlant.makes(Commod.FUEL);
    float ratio = fuel <= 0f ? 0f : fuelPlant.uses(Commod.VOLATILES) / fuel;
    if (ratio <= 0f) {
      return 1f;
    }
    float fuelMult = rules.priceMult(Commod.FUEL);
    float volatilesMult = rules.priceMult(Commod.VOLATILES);
    float fuelPrice = shared.price(Commod.FUEL);
    float volatilesPrice = shared.price(Commod.VOLATILES);
    if (shared.survivalCampaign) {
      fuelPrice /= fuelMult;
      volatilesPrice /= volatilesMult;
    }
    return EconConstants.survivalFuelInputRatio(ratio, fuelPrice, volatilesPrice, fuelMult,
        volatilesMult) / ratio;
  }

  static boolean markIdle(TickSheet t) {
    boolean any = false;
    for (TickSheet.WorkRun run : t.runs.values()) {
      boolean inOrder = run.status == WorkStatus.RUNNING || run.status == WorkStatus.UNSTAFFED;
      if (!inOrder || !runsLines(run)) {
        continue;
      }
      WorkStatus.Reason why = cannotRun(t, run);
      if (why != WorkStatus.Reason.NONE) {
        run.status = WorkStatus.IDLE;
        run.reason = why;
        run.staffOwn = 0f;
        any = true;
      }
    }
    return any;
  }

  static void run(TickSheet t) {
    if (t.efficiency <= 0f) {
      return;
    }
    for (TickSheet.WorkRun run : t.runs.values()) {
      run.makesPerMonth.clear();
      run.usesPerMonth.clear();
    }
    for (TickSheet.WorkRun run : inRunOrder(t)) {
      if (!runsLines(run) || run.status != WorkStatus.RUNNING) {
        continue;
      }
      if (Works.MINING_RIG.equals(run.id)) {
        mine(t, run);
      } else if (Works.FOUNDRY.equals(run.id)) {
        Foundry.run(t);
      } else if (Works.BROKERAGE.equals(run.id)) {
        broker(t, run);
      } else {
        runLines(t, run);
      }
    }
  }

  private static boolean runsLines(TickSheet.WorkRun run) {
    return run.def != null && (!run.def.makes.isEmpty() || !run.def.uses.isEmpty());
  }

  private static List<TickSheet.WorkRun> inRunOrder(TickSheet t) {
    List<TickSheet.WorkRun> out = new ArrayList<TickSheet.WorkRun>(t.runs.size());
    TickSheet.WorkRun rig = t.runs.get(Works.MINING_RIG);
    TickSheet.WorkRun refinery = t.runs.get(Works.REFINERY);
    if (rig != null) {
      out.add(rig);
    }
    if (refinery != null) {
      out.add(refinery);
    }
    for (TickSheet.WorkRun run : t.runs.values()) {
      if (run != rig && run != refinery) {
        out.add(run);
      }
    }
    return out;
  }

  private static WorkStatus.Reason cannotRun(TickSheet t, TickSheet.WorkRun run) {
    if (Works.BROKERAGE.equals(run.id)) {
      return WorkStatus.Reason.NONE;
    }
    if (Works.FOUNDRY.equals(run.id)) {
      return Foundry.cannotRun(t, run.def);
    }
    if (Works.MINING_RIG.equals(run.id)) {
      Map<String, Float> mined = mined(t, run);
      WorkStatus.Reason full = WorkStatus.Reason.NO_DEPOSIT;
      for (Map.Entry<String, Float> tier : Mining.tiers(t.s.site, remoteOn(t, run), t.k)
          .entrySet()) {
        if (tier.getValue().floatValue() <= 0f || !run.def.makes.containsKey(tier.getKey())) {
          continue;
        }
        Float perMonth = mined.get(tier.getKey());
        if (!outputFull(t, tier.getKey(), perMonth == null ? 0f : perMonth.floatValue())) {
          return WorkStatus.Reason.NONE;
        }
        if (full == WorkStatus.Reason.NO_DEPOSIT) {
          full = fullWhy(t, tier.getKey());
        }
      }
      return full;
    }
    float share = share(t);
    WorkStatus.Reason first = WorkStatus.Reason.NONE;
    for (WorkSize.Line line : run.def.lines) {
      if (line.uses.isEmpty() && !line.makes.isEmpty()) {
        for (Map.Entry<String, Float> made : line.makes.entrySet()) {
          if (!outputFull(t, made.getKey(), output(t, run.id, made))) {
            return WorkStatus.Reason.NONE;
          }
          if (first == WorkStatus.Reason.NONE) {
            first = fullWhy(t, made.getKey());
          }
        }
        continue;
      }
      WorkStatus.Reason why = blocked(t, run.id, line, share);
      if (why == WorkStatus.Reason.NONE) {
        return WorkStatus.Reason.NONE;
      }
      if (first == WorkStatus.Reason.NONE) {
        first = why;
      }
    }
    return first;
  }

  private static WorkStatus.Reason blocked(TickSheet t, String workId, WorkSize.Line line,
      float share) {
    for (Map.Entry<String, Float> made : line.makes.entrySet()) {
      float perMonth = output(t, workId, made);
      if (Works.MARINES.equals(made.getKey())) {
        WorkStatus.Reason why = Marines.blocked(t, perMonth * share);
        if (why != WorkStatus.Reason.NONE) {
          return why;
        }
        continue;
      }
      if (Works.RESIDENTS.equals(made.getKey())) {
        WorkStatus.Reason noRoom = perMonth * share > EPS
            ? Residents.roomFor(t, perMonth * share) : WorkStatus.Reason.NONE;
        if (noRoom != WorkStatus.Reason.NONE) {
          return noRoom;
        }
        continue;
      }
      if (outputFull(t, made.getKey(), perMonth)) {
        return fullWhy(t, made.getKey());
      }
    }
    float part = marinesPart(t, workId, line, share);
    for (Map.Entry<String, Float> used : line.uses.entrySet()) {
      float need = need(workId, line, used, t.efficiency, t.s.items, t.s.normalOrStory, t.k,
          t.shared) * share * part;
      if (Works.RESIDENTS.equals(used.getKey())) {
        if (need > EPS && !Residents.canSpare(t, need)) {
          return WorkStatus.Reason.NO_RESIDENTS_TO_SPARE;
        }
        continue;
      }
      if (need > EPS && t.stock(used.getKey()) + EPS < need) {
        return WorkStatus.Reason.INPUT_EMPTY;
      }
    }
    return WorkStatus.Reason.NONE;
  }

  private static boolean outputFull(TickSheet t, String good, float perMonth) {
    TickSheet.Store store = t.stores.get(good);
    if (store == null) {
      return false;
    }
    float stock = t.stock(good);
    int flags = t.s.flags(good);
    boolean act = t.s.flagsAct();
    if (act && StoreFlags.has(flags, StoreFlags.OUT)) {
      return Stores.holdKeepsIn(t, store);
    }
    if (act && StoreFlags.has(flags, StoreFlags.SELL) && brokerageRuns(t)) {
      return wholeUnitOver(stock, store.target);
    }
    if (act && StoreFlags.has(flags, StoreFlags.IN)) {
      return wholeUnitOver(stock, store.target);
    }
    if (!t.s.detached) {
      return stock + EPS >= store.target;
    }
    return grown(t, store, stock, stock + perMonth * share(t)) > detachedRoom(t) + ROOM_EPS;
  }

  private static boolean wholeUnitOver(float stock, float target) {
    float whole = stock > 0f ? (float) Math.floor(stock) : 0f;
    return whole - (float) Math.ceil(target) >= 1f - EPS;
  }

  private static WorkStatus.Reason fullWhy(TickSheet t, String good) {
    return t.s.flagsAct() && StoreFlags.has(t.s.flags(good), StoreFlags.OUT)
        ? WorkStatus.Reason.HOLD_FULL : WorkStatus.Reason.OUTPUT_FULL;
  }

  private static final float ROOM_EPS = 1e-3f;

  public static float detachedRoom(TickSheet t) {
    if (Float.isNaN(t.detachedRoomAtFit)) {
      float perUnit = Stores.spacePerUnit(t.s.storyDifficulty, t.k, t.shared);
      float targets = 0f;
      for (TickSheet.Store store : t.stores.values()) {
        targets += store.target * perUnit;
      }
      NeedsLevel level = BasicNeeds.read(t.space - t.spaceWorks - targets,
          t.residentsInNeeds(), t.residents, t.s.normalOrStory, t.s.storyDifficulty,
          t.requirementFactor(), t.k).level;
      t.detachedRoomAtFit = t.spaceNeeds() - BasicNeeds.spaceWanted(level, t.residents,
          t.s.normalOrStory, t.s.storyDifficulty, t.requirementFactor(), t.k);
    }
    return Math.max(0f, t.detachedRoomAtFit - t.spaceTaken);
  }

  private static float grown(TickSheet t, TickSheet.Store store, float from, float to) {
    float perUnit = Stores.spacePerUnit(t.s.storyDifficulty, t.k, t.shared);
    return Stores.reserved(store.target, to, perUnit)
        - Stores.reserved(store.target, from, perUnit);
  }

  static void restock(TickSheet t, String good, float to) {
    TickSheet.Store store = t.stores.get(good);
    if (store != null && t.s.detached) {
      t.spaceTaken += grown(t, store, t.stock(good), to);
    }
    t.stock.put(good, Float.valueOf(to));
  }

  private static float output(TickSheet t, String workId, Map.Entry<String, Float> made) {
    return output(workId, made.getKey(), made.getValue().floatValue(), t.efficiency, t.s.items,
        t.s.normalOrStory, t.k, t.shared);
  }

  private static boolean brokerageRuns(TickSheet t) {
    TickSheet.WorkRun brokerage = t.runs.get(Works.BROKERAGE);
    return brokerage != null && brokerage.def != null && brokerage.status == WorkStatus.RUNNING;
  }

  private static float lineDraw(String workId, WorkSize.Line line, float efficiency,
      List<String> items, boolean normalOrStory, Rules k, EconConstants shared) {
    float yield = 1f;
    boolean first = true;
    for (String good : line.makes.keySet()) {
      float one = itemYield(workId, good, items, normalOrStory, shared);
      yield = first ? one : Math.max(yield, one);
      first = false;
    }
    return inputScale(efficiency, shared) * yield * k.productionMult;
  }

  private static float share(TickSheet t) {
    return t.stepFrac > 0f ? t.stepFrac : 1f / Math.max(1, t.s.ticksPerMonth);
  }

  private static boolean moves(TickSheet t) {
    return !t.preview && t.stepFrac > 0f;
  }

  private static void runLines(TickSheet t, TickSheet.WorkRun run) {
    float share = share(t);
    boolean any = false;
    WorkStatus.Reason first = WorkStatus.Reason.NONE;
    for (WorkSize.Line line : run.def.lines) {
      if (line.uses.isEmpty() && !line.makes.isEmpty()) {
        for (Map.Entry<String, Float> made : line.makes.entrySet()) {
          float perMonth = output(t, run.id, made);
          if (outputFull(t, made.getKey(), perMonth)) {
            if (first == WorkStatus.Reason.NONE) {
              first = fullWhy(t, made.getKey());
            }
            continue;
          }
          any = true;
          make(t, run, made.getKey(), perMonth);
        }
        continue;
      }
      WorkStatus.Reason why = blocked(t, run.id, line, share);
      if (why != WorkStatus.Reason.NONE) {
        if (first == WorkStatus.Reason.NONE) {
          first = why;
        }
        continue;
      }
      any = true;
      float part = marinesPart(t, run.id, line, share);
      for (Map.Entry<String, Float> used : line.uses.entrySet()) {
        use(t, run, used.getKey(), need(run.id, line, used, t.efficiency, t.s.items,
            t.s.normalOrStory, t.k, t.shared), part);
      }
      for (Map.Entry<String, Float> made : line.makes.entrySet()) {
        make(t, run, made.getKey(), output(t, run.id, made), part);
      }
    }
    if (!any) {
      idleNow(t, run, first);
    }
  }

  private static void mine(TickSheet t, TickSheet.WorkRun run) {
    Map<String, Float> mined = mined(t, run);
    boolean any = false;
    WorkStatus.Reason full = WorkStatus.Reason.NO_DEPOSIT;
    for (Map.Entry<String, Float> raw : mined.entrySet()) {
      if (outputFull(t, raw.getKey(), raw.getValue().floatValue())) {
        if (full == WorkStatus.Reason.NO_DEPOSIT) {
          full = fullWhy(t, raw.getKey());
        }
        continue;
      }
      any = true;
      make(t, run, raw.getKey(), raw.getValue().floatValue());
      if (moves(t)) {
        t.event(FlotillaEvent.Type.MINED, raw.getKey(),
            raw.getValue().floatValue() * t.stepFrac);
      }
    }
    if (!any) {
      idleNow(t, run, full);
    }
  }

  private static void broker(TickSheet t, TickSheet.WorkRun run) {
    for (Map.Entry<String, Float> made : run.def.makes.entrySet()) {
      run.makesPerMonth.put(made.getKey(), Float.valueOf(output(run.id, made.getKey(),
          made.getValue().floatValue(), t.efficiency, t.s.items, t.s.normalOrStory, t.k,
          t.shared)));
    }
    Brokerage.pay(t);
  }

  private static void idleNow(TickSheet t, TickSheet.WorkRun run, WorkStatus.Reason why) {
    if (!t.preview && run.status == WorkStatus.RUNNING && why != WorkStatus.Reason.NONE) {
      run.status = WorkStatus.IDLE;
      run.reason = why;
    }
  }

  private static void make(TickSheet t, TickSheet.WorkRun run, String good, float perMonth) {
    make(t, run, good, perMonth, 1f);
  }

  private static void make(TickSheet t, TickSheet.WorkRun run, String good, float perMonth,
      float part) {
    if (perMonth <= 0f) {
      return;
    }
    add(run.makesPerMonth, good, perMonth);
    if (!moves(t) || part <= 0f) {
      return;
    }
    float amount = perMonth * t.stepFrac * part;
    add(run.made, good, amount);
    if (Works.RESIDENTS.equals(good)) {
      t.residents += amount;
      t.residentsMade += amount;
    } else if (t.stores.containsKey(good)) {
      restock(t, good, t.stock(good) + amount);
    } else if (Works.CREDITS.equals(good)) {
      t.credits += amount;
    } else if (Works.MARINES.equals(good)) {
      t.marinesMade += amount;
    }
  }

  private static void use(TickSheet t, TickSheet.WorkRun run, String good, float perMonth,
      float part) {
    if (perMonth <= 0f) {
      return;
    }
    add(run.usesPerMonth, good, perMonth);
    if (!moves(t) || part <= 0f) {
      return;
    }
    float amount = perMonth * t.stepFrac * part;
    add(run.used, good, amount);
    if (Works.RESIDENTS.equals(good)) {
      t.residents = Math.max(0f, t.residents - amount);
      t.residentsUsed += amount;
      return;
    }
    restock(t, good, Math.max(0f, t.stock(good) - amount));
  }

  private static Map<String, Float> mined(TickSheet t, TickSheet.WorkRun run) {
    return minedPerMonth(run.def, Mining.tiers(t.s.site, remoteOn(t, run), t.k), t.efficiency,
        t.s.items, t.s.normalOrStory, t.k, t.shared);
  }

  private static boolean remoteOn(TickSheet t, TickSheet.WorkRun run) {
    WorkState w = t.work(run.id);
    return w != null && w.remote;
  }

  private static boolean has(List<String> items, String id) {
    return items != null && items.contains(id);
  }

  static void add(Map<String, Float> into, String good, float amount) {
    Float had = into.get(good);
    into.put(good, Float.valueOf((had == null ? 0f : had.floatValue()) + amount));
  }
}
