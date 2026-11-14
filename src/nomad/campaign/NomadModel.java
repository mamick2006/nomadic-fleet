package nomad.campaign;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignClockAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.econ.MonthlyReport;
import com.fs.starfarer.api.impl.PlayerFleetPersonnelTracker;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.util.Misc;

import nomad.NomadStrings;
import nomad.campaign.econ.Commod;
import nomad.campaign.econ.EconConstants;
import nomad.campaign.econ.NomadCapacity;
import nomad.campaign.econ.ShellRules;
import nomad.campaign.econ.SupportEffects;
import nomad.campaign.econ.model.FlotillaState;
import nomad.campaign.econ.model.FlotillaTick;
import nomad.campaign.econ.model.GlueRules;
import nomad.campaign.econ.model.Marines;
import nomad.campaign.econ.model.NeedsLevel;
import nomad.campaign.econ.model.Rules;
import nomad.campaign.econ.model.StoreFlags;
import nomad.campaign.econ.model.Stores;
import nomad.campaign.econ.model.TickSheet;
import nomad.campaign.econ.model.WorkCatalog;
import nomad.campaign.econ.model.WorkDef;
import nomad.campaign.econ.model.WorkState;
import nomad.campaign.econ.model.Works;
import nomad.campaign.works.WorkCatalogTable;
import nomad.crossmod.NomadCqFurnishing;
import nomad.data.NomadFleetData;
import nomad.ui.NomadAutoforge;
import nomad.ui.NomadAutoforgeQueue;

public final class NomadModel {

  private NomadModel() {
  }

  private static final EconConstants.FloatSource SOURCE = new EconConstants.FloatSource() {
    @Override
    public float get(String key, float fallback) {
      return NomadSettings.getFloat(key, fallback);
    }
  };

  private static final String INCOME_NODE = "node_id_nomad_credits";
  private static final String WORK_NODE_PREFIX = "node_id_nomad_work_";
  private static final String SALE_NODE_PREFIX = "node_id_nomad_sale_";

  private static volatile Rules cached;

  private static boolean warnedUndefined;

  public static WorkCatalog catalog() {
    return WorkCatalogTable.read();
  }

  public static Rules rules() {
    Rules held = cached;
    if (held != null) {
      return held;
    }
    Rules built = Rules.build(SOURCE);
    built.applyPlayerSettings(
        NomadSettings.tunable("nomad_yield_mult", "nomad_yield_mult_override", 1f),
        NomadSettings.tunable("nomad_autoforge_yield_mult", "nomad_autoforge_yield_mult_override",
            1f),
        NomadSettings.tunable("nomad_growth_rate", "nomad_bn_normal_growth_per_year",
            built.growthPerYear(NeedsLevel.NORMAL)));
    if (NomadSurvival.isSurvivalCampaign()) {
      built.applySurvival(NomadSurvival.rules(Commod.STORED), NomadSurvival.adjustmentsOn());
    }
    cached = built;
    return built;
  }

  public static void onGameLoad() {
    cached = null;
    warnedUndefined = false;
    WorkCatalogTable.onGameLoad();
    nomad.campaign.works.WorkPrereqTable.onGameLoad();
  }

  public static void settingsChanged() {
    cached = null;
    NomadFleetData data = NomadFleetData.getOrNull();
    if (data != null) {
      data.markChanged();
    }
  }

  public static CampaignFleetAPI flotillaFleet() {
    NomadManager mgr = NomadManager.getInstance();
    return mgr == null ? null : mgr.getFlagshipFleet();
  }

  private static CampaignFleetAPI carrier(CampaignFleetAPI given) {
    NomadManager mgr = NomadManager.getInstance();
    return mgr != null ? mgr.getFlagshipFleet() : given;
  }

  public static FlotillaState snapshot(NomadFleetData data, CampaignFleetAPI fleet,
      float elapsedDays) {
    CampaignFleetAPI carrier = carrier(fleet);
    boolean attached = carrier != null && carrier.isPlayerFleet();
    WorkCatalog catalog = catalog();
    EconConstants shared = NomadEcon.constants();

    FlotillaState s = new FlotillaState();
    s.residents = data.getResidents();
    s.normalOrStory = NomadSettings.isNormalOrStory();
    s.storyDifficulty = NomadSettings.isStoryDifficulty();
    s.paused = carrier == null;
    s.priorPaused = data.isLastPaused();
    s.detached = carrier != null && !attached;
    s.priorLevel = level(data.getLastNeedsLevel());
    s.elapsedDays = elapsedDays;
    s.ticksPerMonth = ticksPerMonth();

    if (carrier == null) {
      s.space = data.isCapacityFrozen() ? data.getFrozenSpace() : data.getCachedSpace();
      s.berths = data.isCapacityFrozen() ? data.getFrozenBerths() : data.getCachedBerths();
    } else {
      NomadCapacity.Capacity cap = NomadCapacityUtil.forFlotilla(data, carrier);
      s.space = cap.space;
      s.berths = cap.berths;
    }
    s.foundingSpace = nomad.campaign.econ.model.Capacity.foundingSpace(NomadEcon.constants());
    if (s.detached) {
      s.detachedSuppliesPerMonth = nomad.campaign.split.NomadSplitter.shipsBill(
          carrier.getFleetData().getMembersListCopy());
    }

    List<WorkState> saved = data.getWorkStates();
    List<WorkState> defined = GlueRules.defined(saved, catalog);
    if (defined.size() < saved.size() && !warnedUndefined) {
      warnedUndefined = true;
      int kept = ShellRules.dormant(saved, catalog, WorkCatalogTable.named()).size();
      int gone = saved.size() - defined.size() - kept;
      if (kept > 0) {
        Global.getLogger(NomadModel.class).warn("Nomad Flotilla: " + kept + " saved Work(s) have"
            + " no usable row in an installed mod's Works table this session. They are kept, off,"
            + " holding no Space or staff, and come back when the table can be read.");
      }
      if (gone > 0) {
        Global.getLogger(NomadModel.class).warn("Nomad Flotilla: " + gone
            + " saved Work(s) no installed mod defines any more (most likely a mod that added Works"
            + " was removed). They leave the Flotilla at its next tick; the rest is intact.");
      }
    }
    for (WorkState w : defined) {
      s.works.add(w.copy());
    }
    s.items.addAll(data.getInstalledItems());
    List<String> furnishings = NomadCqFurnishing.owned();
    s.items.addAll(furnishings);
    s.furnishings.addAll(furnishings);

    CargoAPI stores = data.getFlotillaCargo();
    for (String good : Commod.STORED) {
      s.stock.put(good, Float.valueOf(
          stores.getCommodityQuantity(good) + data.getStorageRemainder(good)));
      s.flags.put(good, Integer.valueOf(data.getStoreFlags(good)));
      float target = data.getStoreTarget(good);
      if (target > 0f) {
        s.target.put(good, Float.valueOf(target));
      }
      if (attached) {
        s.cargoSpare.put(good, Float.valueOf(cargoSpare(carrier, good)));
      }
    }
    if (attached) {
      CargoAPI hold = carrier.getCargo();
      s.cargoRoom = hold.getMaxCapacity() - hold.getSpaceUsed();
      s.fuelRoom = hold.getMaxFuel() - hold.getFuel();
      s.fleetMarines = hold.getMarines();
      s.fleetCrewRoom = hold.getFreeCrewSpace();
    }
    s.marinesTarget = data.getMarinesTarget();
    s.marinesPending = data.getMarinesPending();
    s.priorCargoFull = data.isLastCargoFull();
    s.priorFuelFull = data.isLastFuelFull();

    if (carrier != null) {
      s.prereqs.addAll(nomad.campaign.works.WorkPrereqChecks.readings(s.items, carrier));
      s.site = NomadMiningResolver.site(data.getMiningCache(), carrier,
          s.work(Works.MINING_RIG), catalog, shared.basePrice, rules());
      foundryWants(data, s);
    }
    s.suppliesOwed = data.getSuppliesOwed();
    return s;
  }

  public static TickSheet preview(NomadFleetData data, CampaignFleetAPI fleet) {
    return FlotillaTick.preview(snapshot(data, fleet, 0f), catalog(), rules(),
        NomadEcon.constants());
  }

  public static float cargoSpare(CampaignFleetAPI fleet, String good) {
    if (fleet == null || fleet.getCargo() == null) {
      return 0f;
    }
    float reserve = 0f;
    if (Commod.SUPPLIES.equals(good)) {
      reserve = fleet.getLogistics().getTotalSuppliesPerDay()
          * NomadSettings.getFloat("nomad_autotransfer_reserve_days_supplies", 30f);
    } else if (Commod.FUEL.equals(good)) {
      reserve = fleet.getLogistics().getFuelCostPerLightYear()
          * NomadSettings.getFloat("nomad_autotransfer_reserve_ly_fuel", 10f);
    }
    return GlueRules.spare(fleet.getCargo().getCommodityQuantity(good), reserve);
  }

  private static int ticksPerMonth() {
    return Math.max(1, (int) Global.getSettings().getFloat("economyIterPerMonth"));
  }

  private static NeedsLevel level(String key) {
    for (NeedsLevel level : NeedsLevel.values()) {
      if (level.key.equals(key)) {
        return level;
      }
    }
    return null;
  }

  private static void foundryWants(NomadFleetData data, FlotillaState s) {
    WorkState foundry = s.work(Works.FOUNDRY);
    if (foundry == null || !foundry.isBuilt() || data.getAutoforgeQueue().isEmpty()
        || NomadAutoforge.isPaused()) {
      return;
    }
    final List<String> q = data.getAutoforgeQueue();
    final float[] costs = new float[q.size()];
    final boolean[] part = new boolean[q.size()];
    boolean anyPart = false;
    for (int i = 0; i < costs.length; i++) {
      String[] p = q.get(i).split("\\|");
      NomadAutoforge.Recipe r = p.length < 2 ? null : NomadAutoforge.recipeFor(data, p[0], p[1]);
      costs[i] = r == null ? -1f : r.pointCost;
      part[i] = NomadAutoforgeQueue.held(p[0], false);
      anyPart |= part[i];
    }
    s.foundryPointsWanted = queueWants(q, costs, null, data.getFoundryPoints());
    if (anyPart) {
      s.foundryPointsNeedBench = Math.max(0f, s.foundryPointsWanted
          - queueWants(q, costs, part, data.getFoundryPoints()));
    }
  }

  private static float queueWants(final List<String> q, final float[] costs,
      final boolean[] held, float banked) {
    return NomadAutoforgeQueue.wanted(new NomadAutoforgeQueue.Jobs() {
      @Override
      public int size() {
        return costs.length;
      }

      @Override
      public float cost(int i) {
        return costs[i];
      }

      @Override
      public float banked(int i) {
        return NomadAutoforgeQueue.banked(q.get(i));
      }

      @Override
      public boolean held(int i) {
        return held != null && held[i];
      }
    }, banked);
  }

  public static void apply(NomadFleetData data, TickSheet sheet, CampaignFleetAPI fleet) {
    if (data == null || sheet == null || sheet.preview) {
      return;
    }
    FlotillaState s = sheet.s;
    data.changeResidents(Math.max(0f, sheet.residents), sheet.residentsGrown,
        sheet.residentsLeft + sheet.residentsLostBerths, sheet.residentsMade, sheet.residentsUsed);
    data.setLastPaused(s.paused);
    data.setLastHoldFull(sheet.cargoFull, sheet.fuelFull);
    data.setWorkStates(ShellRules.withDormant(sheet.works,
        ShellRules.dormant(data.getWorkStates(), sheet.catalog, WorkCatalogTable.named())));

    if (!s.paused) {
      data.setLastNeedsLevel(sheet.needs.level.key);
      data.setCapacitySnapshot(s.space, sheet.berths);

      CampaignFleetAPI carrier = carrier(fleet);
      boolean attached = !s.detached && carrier != null && carrier.isPlayerFleet();
      if (attached) {
        moveCargo(carrier.getCargo(), GlueRules.cargoNet(sheet));
      }
      payCredits(sheet);
      sendMarines(data, sheet, attached ? carrier.getCargo() : null);
      writeStores(data, sheet.stock);
      data.addFoundryPoints(sheet.foundryPoints);
      data.setSuppliesOwed(sheet.suppliesOwed);
    }
    NomadTickLog.record(data, sheet);
  }

  private static void sendMarines(NomadFleetData data, TickSheet sheet, CargoAPI cargo) {
    float pending = data.getMarinesPending();
    if (cargo == null) {
      data.setMarinesPending(pending + Math.max(0f, sheet.marinesMade));
      return;
    }
    int whole = Marines.finished(pending, sheet.marinesMade);
    data.setMarinesPending(Marines.stillTraining(pending, sheet.marinesMade));
    if (whole > 0) {
      cargo.addMarines(whole);
      data.addMonthMarines(whole);
      TickSheet.WorkRun course = sheet.run(Works.ASSAULT_COURSE);
      boolean on = course != null && course.effectOn && course.def != null;
      float xp = Marines.joinXp(whole,
          on ? course.def.param(SupportEffects.MARINE_JOIN_XP, 0f) : 0f, on);
      if (xp > 0f) {
        joinMarineXp(xp);
      }
    }
  }

  private static void joinMarineXp(float xp) {
    try {
      PlayerFleetPersonnelTracker tracker = PlayerFleetPersonnelTracker.getInstance();
      tracker.update();
      tracker.getMarineData().addXP(xp);
      tracker.update();
    } catch (Throwable ignore) {
    }
  }

  private static void moveCargo(CargoAPI cargo, Map<String, Float> net) {
    if (cargo == null || net.isEmpty()) {
      return;
    }
    cargo.initPartialsIfNeeded();
    for (Map.Entry<String, Float> e : net.entrySet()) {
      float units = e.getValue().floatValue();
      if (units > 0f) {
        cargo.addCommodity(e.getKey(), units);
      } else {
        cargo.removeCommodity(e.getKey(), -units);
      }
    }
  }

  private static void writeStores(NomadFleetData data, Map<String, Float> stock) {
    CargoAPI stores = data.getFlotillaCargo();
    for (String good : Commod.STORED) {
      Float want = stock.get(good);
      if (want == null) {
        continue;
      }
      float target = Math.max(0f, want.floatValue());
      float delta = target - stores.getCommodityQuantity(good);
      if (delta > GlueRules.EPS) {
        stores.addCommodity(good, delta);
      } else if (delta < -GlueRules.EPS) {
        stores.removeCommodity(good, -delta);
      }
      data.setStorageRemainder(good, target - stores.getCommodityQuantity(good));
    }
  }

  private static void payCredits(TickSheet sheet) {
    if (!(sheet.credits + sheet.saleCredits > 0f)) {
      return;
    }
    Map<String, Float> units = GlueRules.soldUnits(sheet);
    Map<String, Float> shares =
        GlueRules.saleShares(units, sheet.shared.basePrice, sheet.saleCredits);
    for (Map.Entry<String, Float> e : GlueRules.creditRows(sheet, shares).entrySet()) {
      payIncome(WORK_NODE_PREFIX + e.getKey(), workName(e.getKey(), sheet.catalog),
          e.getValue().floatValue());
    }
    MonthlyReport report = SharedData.getData().getCurrentReport();
    for (Map.Entry<String, Float> e : shares.entrySet()) {
      String good = e.getKey();
      String id = SALE_NODE_PREFIX + good;
      MonthlyReport.FDNode row = report.getNode(incomeParent(report), id);
      row.custom = id;
      Float sold = units.get(good);
      float month = (row.custom2 instanceof Float ? ((Float) row.custom2).floatValue() : 0f)
          + (sold == null ? 0f : sold.floatValue());
      row.custom2 = Float.valueOf(month);
      row.name = goodName(good) + " (" + Misc.getRoundedValueMaxOneAfterDecimal(month) + ")";
      row.icon = goodIcon(good);
      row.income += e.getValue().floatValue();
    }
  }

  private static MonthlyReport.FDNode incomeParent(MonthlyReport report) {
    MonthlyReport.FDNode parent = report.getNode(INCOME_NODE);
    parent.name = "Flotilla income";
    parent.custom = INCOME_NODE;
    parent.icon = icon("nomad_credits");
    return parent;
  }

  public static void payIncome(String rowId, String name, float credits) {
    if (!(credits > 0f)) {
      return;
    }
    MonthlyReport report = SharedData.getData().getCurrentReport();
    MonthlyReport.FDNode row = report.getNode(incomeParent(report), rowId);
    row.name = name;
    row.custom = rowId;
    row.icon = icon("nomad_credits");
    row.income += credits;
  }

  public static void addToStores(NomadFleetData data, Map<String, Float> goods, TickSheet sheet,
      CampaignFleetAPI fleet) {
    if (goods == null || goods.isEmpty() || sheet == null) {
      return;
    }
    CampaignFleetAPI carrier = carrier(fleet);
    CargoAPI hold = sheet.s.flagsAct() && carrier != null && carrier.isPlayerFleet()
        ? carrier.getCargo() : null;
    float cargoRoom = hold == null ? 0f : hold.getMaxCapacity() - hold.getSpaceUsed();
    float fuelRoom = hold == null ? 0f : hold.getMaxFuel() - hold.getFuel();
    CargoAPI stores = data.getFlotillaCargo();
    Map<String, Float> stock = new LinkedHashMap<String, Float>();
    Map<String, Float> out = new LinkedHashMap<String, Float>();
    for (Map.Entry<String, Float> e : goods.entrySet()) {
      String good = e.getKey();
      if (e.getValue() == null || !(e.getValue().floatValue() > 0f)
          || !Commod.STORED.contains(good)) {
        continue;
      }
      boolean fuel = Commod.FUEL.equals(good);
      TickSheet.Store store = sheet.stores.get(good);
      float held = stores.getCommodityQuantity(good) + data.getStorageRemainder(good);
      float[] placed = ShellRules.place(e.getValue().floatValue(), held,
          store == null ? 0f : store.target,
          hold != null && StoreFlags.has(sheet.s.flags(good), StoreFlags.OUT),
          fuel ? fuelRoom : cargoRoom);
      if (placed[0] > 0f) {
        stock.put(good, Float.valueOf(held + placed[0]));
      }
      if (placed[1] > 0f) {
        out.put(good, Float.valueOf(placed[1]));
        if (fuel) {
          fuelRoom -= placed[1];
        } else {
          cargoRoom -= placed[1];
        }
      }
    }
    if (!out.isEmpty()) {
      moveCargo(hold, out);
    }
    if (!stock.isEmpty()) {
      writeStores(data, stock);
      data.markChanged();
    }
  }

  static String workName(String workId, WorkCatalog catalog) {
    WorkDef def = workId == null ? null : catalog.work(workId);
    if (def == null) {
      return String.valueOf(workId);
    }
    if (def.ownRow) {
      String key = "wk_name_" + workId;
      return NomadStrings.get("nomad_works", key);
    }
    return def.name == null || def.name.isEmpty() ? workId : def.name;
  }

  static String icon(String key) {
    try {
      return Global.getSettings().getSpriteName("icons", key);
    } catch (RuntimeException e) {
      return null;
    }
  }

  private static String goodName(String good) {
    try {
      return Global.getSettings().getCommoditySpec(good).getName();
    } catch (RuntimeException e) {
      return good.replace('_', ' ');
    }
  }

  private static String goodIcon(String good) {
    try {
      return Global.getSettings().getCommoditySpec(good).getIconName();
    } catch (RuntimeException e) {
      return null;
    }
  }

  public static TickSheet runTick(NomadFleetData data, CampaignFleetAPI fleet, float elapsedDays) {
    TickSheet sheet = FlotillaTick.tick(snapshot(data, fleet, elapsedDays), catalog(), rules(),
        NomadEcon.constants());
    apply(data, sheet, fleet);
    return sheet;
  }

  public static TickSheet economyTick(NomadFleetData data) {
    CampaignFleetAPI fleet = flotillaFleet();
    data.setPaused(fleet == null);
    data.setDetached(fleet != null && !fleet.isPlayerFleet());
    if (fleet != null && fleet.getContainingLocation() != null) {
      data.setLastFlagshipLoc(new Vector2f(fleet.getLocation()));
    }
    long now = Global.getSector().getClock().getTimestamp();

    boolean seated = false;
    if (data.isFoundersPending() && fleet != null) {
      seated = seatFounders(data, NomadCapacityUtil.forFlotilla(data, fleet).berths);
    }
    if (data.isFoundingStoresSeeded()) {
      NomadCqFurnishing.markFounded();
    }
    TickSheet sheet = runTick(data, fleet, seated ? 0f : elapsedDays(data));
    data.setLastTickTimestamp(now);
    return sheet;
  }

  public static void openStores(NomadFleetData data) {
    Map<String, Float> targets = GlueRules.foundingTargets(NomadEcon.constants().foundingStoreTarget);
    for (Map.Entry<String, Float> e : targets.entrySet()) {
      data.setStoreTarget(e.getKey(), e.getValue().floatValue());
    }
  }

  public static boolean seatFoundersNow(NomadFleetData data) {
    if (data == null || !data.isFounded() || !data.isFoundersPending()) {
      return false;
    }
    CampaignFleetAPI fleet = flotillaFleet();
    if (fleet == null || Global.getSector() == null) {
      return false;
    }
    if (!seatFounders(data, NomadCapacityUtil.forFlotilla(data, fleet).berths)) {
      return false;
    }
    data.setLastTickTimestamp(Global.getSector().getClock().getTimestamp());
    data.markChanged();
    return true;
  }

  public static boolean seatFounders(NomadFleetData data, float berths) {
    if (!GlueRules.foundersDue(data.getFoundersOwedTs(), berths)) {
      return false;
    }
    float arriving = GlueRules.founders(data.getResidents(), berths,
        NomadSettings.getFloat("nomad_founding_residents", 25f),
        NomadSettings.getFloat("nomad_founding_berth_frac", 1.0f));
    data.setResidents(data.getResidents() + arriving);
    data.foundersAboard();
    data.setFoundingStoresSeeded(true);
    int count = Math.round(arriving);
    if (count > 0) {
      NomadHistory.add(data, count == 1 ? NomadStrings.get("nomad_history", "hist_founders_one")
          : NomadStrings.get("nomad_history", "hist_founders_many", Integer.valueOf(count)));
    }
    return true;
  }

  public static float elapsedDays(NomadFleetData data) {
    CampaignClockAPI clock = Global.getSector().getClock();
    long last = data.getLastTickTimestamp();
    return GlueRules.elapsedDays(last == 0L, last == 0L ? 0f : clock.getElapsedDaysSince(last),
        ticksPerMonth());
  }

  public static Map<String, Float> fillFromCargo(NomadFleetData data, CampaignFleetAPI fleet) {
    FlotillaState s = snapshot(data, fleet, 0f);
    Map<String, Float> moved = new LinkedHashMap<String, Float>();
    CampaignFleetAPI carrier = carrier(fleet);
    if (!s.flagsAct() || carrier == null) {
      return moved;
    }
    Map<String, Float> stock = new LinkedHashMap<String, Float>(s.stock);
    Map<String, Float> take = new LinkedHashMap<String, Float>();
    for (Map.Entry<String, Float> e : Stores.fill(s, catalog(), rules(), NomadEcon.constants())
        .entrySet()) {
      float units = e.getValue() == null ? 0f
          : Math.min(e.getValue().floatValue(), s.cargoSpare(e.getKey()));
      if (units > GlueRules.EPS && Commod.STORED.contains(e.getKey())) {
        moved.put(e.getKey(), Float.valueOf(units));
        take.put(e.getKey(), Float.valueOf(-units));
        stock.put(e.getKey(), Float.valueOf(s.stock(e.getKey()) + units));
      }
    }
    if (!moved.isEmpty()) {
      moveCargo(carrier.getCargo(), take);
      writeStores(data, stock);
      data.markChanged();
    }
    return moved;
  }

  public static void charge(float credits) {
    CampaignFleetAPI player = Global.getSector().getPlayerFleet();
    if (player == null || credits == 0f) {
      return;
    }
    if (credits > 0f) {
      player.getCargo().getCredits().subtract(credits);
    } else {
      player.getCargo().getCredits().add(-credits);
    }
  }
}
