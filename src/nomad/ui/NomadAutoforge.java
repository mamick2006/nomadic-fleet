package nomad.ui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI.ShipTypeHints;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.DModManager;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.util.Misc;

import nomad.NomadStrings;
import nomad.campaign.NomadEcon;
import nomad.campaign.NomadHistory;
import nomad.campaign.NomadModel;
import nomad.campaign.NomadSettings;
import nomad.campaign.NomadSurvival;
import nomad.campaign.econ.Commod;
import nomad.campaign.econ.EconConstants;
import nomad.campaign.econ.Work;
import nomad.campaign.econ.model.Foundry;
import nomad.campaign.econ.model.TickSheet;
import nomad.campaign.econ.model.WorkSize;
import nomad.campaign.econ.model.WorkState;
import nomad.campaign.econ.model.WorkStatus;
import nomad.campaign.econ.model.Works;
import nomad.campaign.works.WorksEffects;
import nomad.data.NomadFleetData;

public final class NomadAutoforge {

  private NomadAutoforge() {
  }

  public static final String SHIP = NomadAutoforgeQueue.SHIP;
  public static final String WEAPON = NomadAutoforgeQueue.WEAPON;
  public static final String FIGHTER = NomadAutoforgeQueue.FIGHTER;
  public static final String PART = NomadAutoforgeQueue.PART;
  public static final int QUEUE_MAX = 100;
  private static final String PAUSED_KEY = "nomad_autoforge_paused";
  private static final String FORGE_VARIANT_SUFFIX = "_nomadforge";
  private static final float EPS = 1e-4f;

  public static final class Recipe {
    public final String type;
    public final String id;
    public final String name;
    public final float baseValue;
    public final float pointCost;
    public final Color nameColor;
    public final String design;

    Recipe(String type, String id, String name, float baseValue, String design) {
      this.type = type;
      this.id = id;
      this.name = name;
      this.baseValue = baseValue;
      this.pointCost = Math.max(0f, baseValue);
      this.design = design;
      this.nameColor = designColor(design);
    }

    public boolean isShip() {
      return SHIP.equals(type);
    }

    public boolean isFighter() {
      return FIGHTER.equals(type);
    }

    public boolean isPart() {
      return PART.equals(type);
    }
  }

  public static Recipe recipeFor(NomadFleetData data, String type, String id) {
    if (SHIP.equals(type)) {
      ShipHullSpecAPI hull = hullSpec(id);
      if (hull == null) {
        return null;
      }
      float value = hullValue(hull.getBaseValue(),
          NomadSurvival.sizeIndex(hull.getHullSize()), data == null ? dModsBase() : dModsFor(data),
          NomadEcon.constants());
      return new Recipe(SHIP, id, hull.getHullName(), value, hull.getManufacturer());
    }
    if (FIGHTER.equals(type)) {
      FighterWingSpecAPI wing = wingSpec(id);
      if (wing == null) {
        return null;
      }
      float value = NomadEcon.constants().autoforgeSpecValue(wing.getBaseValue(), true,
          NomadSurvival.specValuesScaled());
      return new Recipe(FIGHTER, id, wing.getWingName(), value, wingManufacturer(wing));
    }
    if (PART.equals(type)) {
      CommoditySpecAPI part = commoditySpec(id);
      if (part == null || !Works.PARTS.equals(id)) {
        return null;
      }
      float value = NomadAutoforgeQueue.partValue(part.getUnmodifiedBasePrice(),
          NomadSettings.getFloat("ext_" + id + "PriceMult", 1f), NomadEcon.constants());
      return new Recipe(PART, id, part.getName(), value, null);
    }
    WeaponSpecAPI weap = weaponSpec(id);
    if (weap == null) {
      return null;
    }
    float value = NomadEcon.constants().autoforgeSpecValue(weap.getBaseValue(), false,
        NomadSurvival.specValuesScaled());
    return new Recipe(WEAPON, id, weap.getWeaponName(), value, weap.getManufacturer());
  }

  public static float daysFor(NomadFleetData data, Recipe r, Line line) {
    return NomadAutoforgeQueue.daysForPoints(r.pointCost, pointsPerMonth(data, line));
  }

  public static int dModsFor(NomadFleetData data) {
    EconConstants k = NomadEcon.constants();
    List<String> items = data.getInstalledItems();
    if (items.contains(Work.PRISTINE_NANOFORGE)) {
      return Math.round(k.autoforgeDmodPristine);
    }
    if (items.contains(Work.CORRUPTED_NANOFORGE)) {
      return Math.round(k.autoforgeDmodCorrupted);
    }
    return Math.round(k.autoforgeDmodBase);
  }

  private static float hullValue(float hullBaseValue, int sizeIndex, int dmods, EconConstants k) {
    return NomadAutoforgeQueue.hullValue(hullBaseValue, sizeIndex, dmods, k);
  }

  public static float codexHullValue(ShipHullSpecAPI hull) {
    return Math.max(0f, hull.getBaseValue())
        * NomadEcon.constants().codexHullValueMult(NomadSurvival.sizeIndex(hull.getHullSize()));
  }

  public static int dModsBase() {
    return Math.round(NomadEcon.constants().autoforgeDmodBase);
  }

  public static int dModsCorrupted() {
    return Math.round(NomadEcon.constants().autoforgeDmodCorrupted);
  }

  public static int dModsPristine() {
    return Math.round(NomadEcon.constants().autoforgeDmodPristine);
  }

  public static List<String> knownShipIds() {
    List<String> out = new ArrayList<String>();
    for (String id : Global.getSector().getPlayerFaction().getKnownShips()) {
      ShipHullSpecAPI hull = hullSpec(id);
      if (hull != null && isRealHull(hull)) {
        out.add(id);
      }
    }
    Collections.sort(out);
    return out;
  }

  public static List<String> knownWeaponIds() {
    List<String> out = new ArrayList<String>();
    for (String id : Global.getSector().getPlayerFaction().getKnownWeapons()) {
      if (weaponSpec(id) != null) {
        out.add(id);
      }
    }
    Collections.sort(out);
    return out;
  }

  public static List<String> knownFighterIds() {
    List<String> out = new ArrayList<String>();
    for (String id : Global.getSector().getPlayerFaction().getKnownFighters()) {
      if (wingSpec(id) != null) {
        out.add(id);
      }
    }
    Collections.sort(out);
    return out;
  }

  public static boolean partsOffered(NomadFleetData data) {
    WorkState bench = data == null ? null : data.getWorkState(Works.DOMAIN_PARTS_WORKSHOP);
    return bench != null && bench.isBuilt();
  }

  public static boolean domainPartsWorkshopOn(NomadFleetData data) {
    return data != null && WorksEffects.levels().partsForged;
  }

  public static boolean waitsOnBench(Line line) {
    return line != null && line.status == WorkStatus.IDLE
        && line.reason == WorkStatus.Reason.QUEUE_WAITING;
  }

  public static final class Line {
    public final WorkState foundry;
    public final WorkSize row;
    public final float space;
    public final WorkStatus status;
    public final WorkStatus.Reason reason;
    public final float efficiency;
    public final boolean noFlagship;
    public final float pointsPerMonth;
    public final List<String> items;
    public final boolean normalOrStory;
    public final EconConstants k;
    public final boolean benchOn;

    Line(WorkState foundry, WorkSize row, WorkStatus status, WorkStatus.Reason reason,
        float efficiency, boolean noFlagship, float pointsPerMonth, List<String> items,
        boolean normalOrStory, EconConstants k, boolean benchOn) {
      this.benchOn = benchOn;
      this.foundry = foundry;
      this.row = row;
      this.space = row == null ? 0f : row.space;
      this.status = status;
      this.reason = reason;
      this.efficiency = efficiency;
      this.noFlagship = noFlagship;
      this.pointsPerMonth = pointsPerMonth;
      this.items = items;
      this.normalOrStory = normalOrStory;
      this.k = k;
    }
  }

  private static Line cachedLine;
  private static String cachedKey;

  public static Line line(NomadFleetData data) {
    String key = lineKey(data);
    Line held = cachedLine;
    if (held != null && key.equals(cachedKey)) {
      return held;
    }
    Line built = line(data, NomadModel.preview(data, null));
    cachedLine = built;
    cachedKey = key;
    return built;
  }

  public static Line line(NomadFleetData data, TickSheet sheet) {
    WorkState saved = sheet.work(Works.FOUNDRY);
    WorkState foundry = saved == null ? null : saved.copy();
    TickSheet.WorkRun run = sheet.run(Works.FOUNDRY);
    WorkSize row = foundry == null || !foundry.isBuilt() ? null
        : run != null && run.def != null ? run.def : sheet.catalog.size(Works.FOUNDRY, foundry.size);
    List<String> items = new ArrayList<String>(sheet.s.items);
    float rate = Foundry.pointsPerMonth(row, sheet.efficiency, items, sheet.s.normalOrStory, sheet.k,
        sheet.shared);
    return new Line(foundry, row, run == null ? null : run.status,
        run == null ? null : run.reason, sheet.efficiency, sheet.s.paused, rate, items,
        sheet.s.normalOrStory, sheet.shared, Foundry.benchOn(sheet));
  }

  private static String lineKey(NomadFleetData data) {
    StringBuilder sb = new StringBuilder();
    sb.append(System.identityHashCode(data)).append(';').append(data.getLastTickTimestamp())
        .append(';').append(data.getAllocSerial()).append(';').append(isPaused())
        .append(';').append(NomadSettings.isNormalOrStory())
        .append(';').append(NomadSettings.isStoryDifficulty());
    List<String> q = data.getAutoforgeQueue();
    sb.append(';').append(q.size()).append(';').append(q.isEmpty() ? "" : q.get(0));
    sb.append(';').append(data.getInstalledItems());
    WorkState f = data.getWorkState(Works.FOUNDRY);
    if (f != null) {
      sb.append(';').append(f.size).append(f.mothballed).append(f.suspended).append(f.staffed)
          .append(f.buildTo).append(f.prereqUnmet);
    }
    CargoAPI hold = data.getFlotillaCargo();
    for (String good : MATERIALS) {
      sb.append(';').append(hold.getCommodityQuantity(good) > EPS);
    }
    return sb.toString();
  }

  private static final String[] MATERIALS = { Commod.METALS, Commod.RARE_METALS,
      Commod.HEAVY_MACHINERY };

  public static boolean lineActive(NomadFleetData data) {
    return lineActive(line(data));
  }

  public static boolean lineActive(Line line) {
    return line.row != null;
  }

  public static float pointsPerMonth(NomadFleetData data) {
    return pointsPerMonth(data, line(data));
  }

  public static float pointsPerMonth(NomadFleetData data, Line line) {
    return line.pointsPerMonth;
  }

  private static Map<String, Float> pointMaterials(Line line, float points) {
    if (line.row == null) {
      return new LinkedHashMap<String, Float>();
    }
    return Foundry.materials(line.row, points, line.items, line.normalOrStory, line.k);
  }

  public static Map<String, Float> materialsPerMonth(NomadFleetData data, Line line) {
    if (data.getAutoforgeQueue().isEmpty() || isPaused() || waitsOnBench(line)) {
      return new LinkedHashMap<String, Float>();
    }
    float pts = pointsPerMonth(data, line);
    if (pts <= 0f) {
      return new LinkedHashMap<String, Float>();
    }
    return pointMaterials(line, pts);
  }

  public static boolean canQueue(NomadFleetData data) {
    return data.getAutoforgeQueue().size() < QUEUE_MAX;
  }

  public static void queue(NomadFleetData data, String type, String id) {
    if (PART.equals(type) && !partsOffered(data)) {
      return;
    }
    Recipe r = recipeFor(data, type, id);
    if (canQueue(data) && r != null) {
      data.getAutoforgeQueue().add(type + "|" + id + "|0");
      NomadHistory.add(data, NomadStrings.get("nomad_history", "hist_af_queued", r.name));
      data.markChanged();
    }
  }

  public static void clear(NomadFleetData data) {
    data.getAutoforgeQueue().clear();
    data.clearFoundryPoints();
    data.markChanged();
  }

  public static boolean isPaused() {
    return Boolean.TRUE.equals(Global.getSector().getPersistentData().get(PAUSED_KEY));
  }

  public static void togglePaused() {
    Global.getSector().getPersistentData().put(PAUSED_KEY, !isPaused());
    NomadFleetData held = NomadFleetData.getOrNull();
    if (held != null) {
      held.markChanged();
    }
  }

  public static NomadAutoforgeQueue.LineState state(NomadFleetData data) {
    return state(data, line(data));
  }

  public static NomadAutoforgeQueue.LineState state(NomadFleetData data, Line line) {
    NomadAutoforgeQueue.LineState st = NomadAutoforgeQueue.state(line.foundry, line.status,
        line.reason, isPaused(), data.getAutoforgeQueue().isEmpty(), line.noFlagship,
        line.efficiency);
    if (st == NomadAutoforgeQueue.LineState.BUILDING && shortMaterial(data, line) != null) {
      return NomadAutoforgeQueue.LineState.STALLED;
    }
    return st;
  }

  public static String shortMaterial(NomadFleetData data) {
    return shortMaterial(data, line(data));
  }

  public static String shortMaterial(NomadFleetData data, Line line) {
    if (data.getAutoforgeQueue().isEmpty()) {
      return null;
    }
    CargoAPI hold = data.getFlotillaCargo();
    Map<String, Float> mix = pointMaterials(line, 1f);
    for (Map.Entry<String, Float> e : mix.entrySet()) {
      if (e.getValue() != null && e.getValue() > 0f
          && hold.getCommodityQuantity(e.getKey()) + data.getStorageRemainder(e.getKey()) <= EPS) {
        return e.getKey();
      }
    }
    return null;
  }

  public static float headPoints(NomadFleetData data) {
    List<String> q = data.getAutoforgeQueue();
    if (q.isEmpty()) {
      return 0f;
    }
    return NomadAutoforgeQueue.banked(q.get(0));
  }

  public static float headPointCost(NomadFleetData data) {
    List<String> q = data.getAutoforgeQueue();
    if (q.isEmpty()) {
      return 0f;
    }
    String[] p = q.get(0).split("\\|");
    Recipe r = recipeFor(data, p[0], p[1]);
    return r == null ? 0f : r.pointCost;
  }

  public static float headFraction(NomadFleetData data) {
    float cost = headPointCost(data);
    return cost <= 0f ? 0f : Math.max(0f, Math.min(1f, headPoints(data) / cost));
  }

  public static float headDaysRemaining(NomadFleetData data, Line line) {
    List<String> q = data.getAutoforgeQueue();
    if (q.isEmpty()) {
      return Float.POSITIVE_INFINITY;
    }
    float remaining = Math.max(0f, headPointCost(data) - headPoints(data) - data.getFoundryPoints());
    return NomadAutoforgeQueue.daysForPoints(remaining, pointsPerMonth(data, line));
  }

  public static List<Stack> stacks(NomadFleetData data) {
    List<Stack> out = new ArrayList<Stack>();
    for (String entry : data.getAutoforgeQueue()) {
      String[] p = entry.split("\\|");
      Recipe r = recipeFor(data, p[0], p[1]);
      String name = r != null ? r.name : p[1];
      if (!out.isEmpty() && out.get(out.size() - 1).id.equals(p[1]) && out.get(out.size() - 1).type.equals(p[0])) {
        out.get(out.size() - 1).count++;
      } else {
        out.add(new Stack(p[0], p[1], name));
      }
    }
    return out;
  }

  public static final class Stack {
    public final String type;
    public final String id;
    public final String name;
    public int count = 1;

    Stack(String type, String id, String name) {
      this.type = type;
      this.id = id;
      this.name = name;
    }
  }

  public static void advance(NomadFleetData data) {
    final List<String> q = data.getAutoforgeQueue();
    if (data.getFoundryPoints() <= EPS && (q.isEmpty() || NomadAutoforgeQueue.banked(q.get(0))
        < headPointCost(data) - EPS)) {
      return;
    }
    final CampaignFleetAPI fleet = NomadUi.flotillaFleet();
    if (fleet == null) {
      return;
    }
    final Recipe[] recipes = new Recipe[q.size()];
    final boolean benchOn = domainPartsWorkshopOn(data);
    NomadAutoforgeQueue.Spend spent = NomadAutoforgeQueue.spend(new NomadAutoforgeQueue.Jobs() {
      @Override
      public int size() {
        return q.size();
      }

      @Override
      public float cost(int i) {
        String[] p = q.get(i).split("\\|");
        recipes[i] = p.length < 2 ? null : recipeFor(data, p[0], p[1]);
        return recipes[i] == null ? -1f : recipes[i].pointCost;
      }

      @Override
      public float banked(int i) {
        return NomadAutoforgeQueue.banked(q.get(i));
      }

      @Override
      public boolean held(int i) {
        String entry = q.get(i);
        int bar = entry.indexOf('|');
        return NomadAutoforgeQueue.held(bar < 0 ? entry : entry.substring(0, bar), benchOn);
      }
    }, data.takeFoundryPoints(data.getFoundryPoints()));

    for (int i = 0; i < spent.removed.size(); i++) {
      q.remove(0);
      if (spent.removed.get(i).booleanValue()) {
        produce(data, recipes[i], fleet);
      }
    }
    if (!q.isEmpty()) {
      String[] p = q.get(0).split("\\|");
      q.set(0, NomadAutoforgeQueue.entry(p[0], p.length > 1 ? p[1] : "", spent.headBanked));
    }
    data.addFoundryPoints(spent.bankLeft);
  }

  private static void produce(NomadFleetData data, Recipe r, CampaignFleetAPI fleet) {
    try {
      if (NomadAutoforgeQueue.deliversAsCargo(r.type)) {
        if (r.isPart()) {
          fleet.getCargo().addCommodity(r.id, 1f);
          NomadHistory.add(data, NomadStrings.get("nomad_history", "hist_af_finished_part", r.name));
          deliveredMessage(NomadStrings.get("nomad_messages", "msg_af_delivered_part", r.name));
          return;
        }
        if (FIGHTER.equals(r.type)) {
          fleet.getCargo().addFighters(r.id, 1);
        } else {
          fleet.getCargo().addWeapons(r.id, 1);
        }
        NomadHistory.add(data, NomadStrings.get("nomad_history", "hist_af_finished",
            Misc.getAOrAnFor(r.name), r.name));
        deliveredMessage(deliveredText(r.name));
        return;
      }
      ShipHullSpecAPI hull = hullSpec(r.id);
      if (hull == null) {
        return;
      }
      ShipVariantAPI variant = stockOrEmpty(r.id, hull, r.id + FORGE_VARIANT_SUFFIX);
      FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variant);
      int dmods = dModsFor(data);
      if (dmods > 0) {
        DModManager.setDHull(member.getVariant());
        DModManager.addDMods(member, false, dmods, new Random());
      }
      fleet.getFleetData().addFleetMember(member);
      if (NomadAutoforgeQueue.clearsInflaterOrigin(fleet.isPlayerFleet())) {
        nomad.campaign.split.NomadSplitter.clearInflaterOrigin(member);
      }
      member.getRepairTracker().setMothballed(true);
      NomadHistory.add(data, NomadStrings.get("nomad_history", "hist_af_finished",
          Misc.getAOrAnFor(hull.getHullName()), hull.getHullName()));
      deliveredMessage(deliveredText(hull.getHullName()));
    } catch (Exception e) {
    }
  }

  static ShipVariantAPI stockOrEmpty(String hullId, ShipHullSpecAPI spec, String fallbackId) {
    ShipVariantAPI stock = registeredHullVariant(hullId);
    if (stock != null) {
      return stock.clone();
    }
    ShipVariantAPI variant = Global.getSettings().createEmptyVariant(fallbackId, spec);
    variant.setVariantDisplayName(spec.getAllWeaponSlotsCopy().isEmpty() ? "Standard" : "Custom");
    return variant;
  }

  static ShipVariantAPI registeredHullVariant(String hullId) {
    return Global.getSettings().getVariant(hullId + "_Hull");
  }

  private static String deliveredText(String name) {
    return NomadStrings.get("nomad_messages", "msg_af_delivered", Misc.getAOrAnFor(name), name);
  }

  private static void deliveredMessage(String text) {
    try {
      MessageIntel intel = new MessageIntel(text, Misc.getPositiveHighlightColor());
      intel.setIcon(Global.getSettings().getSpriteName("icons", "nomad_intel"));
      Global.getSector().getCampaignUI().addMessage(intel);
    } catch (Exception e) {
    }
  }

  private static boolean isRealHull(ShipHullSpecAPI hull) {
    ShipAPI.HullSize size = hull.getHullSize();
    if (size == null || size == ShipAPI.HullSize.FIGHTER || size == ShipAPI.HullSize.DEFAULT) {
      return false;
    }
    return !hull.getHints().contains(ShipTypeHints.STATION) && !hull.getHints().contains(ShipTypeHints.UNBOARDABLE);
  }

  static Color designColor(String manufacturer) {
    if (manufacturer == null || manufacturer.trim().isEmpty()) {
      return NomadUi.text();
    }
    try {
      return Global.getSettings().hasDesignTypeColor(manufacturer)
          ? Misc.getDesignTypeColor(manufacturer) : NomadUi.text();
    } catch (Exception e) {
      return NomadUi.text();
    }
  }

  public static String manufacturer(String type, String id) {
    if (SHIP.equals(type)) {
      ShipHullSpecAPI hull = hullSpec(id);
      return hull == null ? null : hull.getManufacturer();
    }
    if (FIGHTER.equals(type)) {
      FighterWingSpecAPI wing = wingSpec(id);
      return wing == null ? null : wingManufacturer(wing);
    }
    if (PART.equals(type)) {
      return null;
    }
    WeaponSpecAPI weap = weaponSpec(id);
    return weap == null ? null : weap.getManufacturer();
  }

  private static String wingManufacturer(FighterWingSpecAPI wing) {
    try {
      ShipVariantAPI v = wing.getVariant();
      return v == null || v.getHullSpec() == null ? null : v.getHullSpec().getManufacturer();
    } catch (Exception e) {
      return null;
    }
  }

  static ShipHullSpecAPI hullSpec(String id) {
    try {
      return Global.getSettings().getHullSpec(id);
    } catch (Exception e) {
      return null;
    }
  }

  static WeaponSpecAPI weaponSpec(String id) {
    try {
      return Global.getSettings().getWeaponSpec(id);
    } catch (Exception e) {
      return null;
    }
  }

  static CommoditySpecAPI commoditySpec(String id) {
    try {
      return Global.getSettings().getCommoditySpec(id);
    } catch (Exception e) {
      return null;
    }
  }

  static FighterWingSpecAPI wingSpec(String id) {
    try {
      return Global.getSettings().getFighterWingSpec(id);
    } catch (Exception e) {
      return null;
    }
  }

}
