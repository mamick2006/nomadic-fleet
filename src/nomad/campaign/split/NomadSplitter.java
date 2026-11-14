package nomad.campaign.split;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.CargoPodsEntityPlugin;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.ids.Abilities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.CargoPods;
import com.fs.starfarer.api.impl.campaign.skills.MakeshiftEquipment;
import com.fs.starfarer.api.loading.VariantSource;
import com.fs.starfarer.api.util.Misc;

import nomad.NomadStrings;
import nomad.campaign.NomadCapacityUtil;
import nomad.campaign.NomadEcon;
import nomad.campaign.NomadEconomyScript;
import nomad.campaign.NomadHistory;
import nomad.campaign.NomadManager;
import nomad.campaign.NomadMiningResolver;
import nomad.campaign.NomadModel;
import nomad.campaign.NomadSettings;
import nomad.campaign.econ.Commod;
import nomad.campaign.econ.NomadCapacity;
import nomad.campaign.econ.RejoinReport;
import nomad.campaign.econ.ShellRules;
import nomad.campaign.econ.SplitShortfall;
import nomad.campaign.econ.Wreckage;
import nomad.campaign.econ.model.Detached;
import nomad.campaign.econ.model.FlotillaState;
import nomad.campaign.econ.model.Mining;
import nomad.campaign.econ.model.WorkState;
import nomad.crossmod.NomadCqFurnishing;
import nomad.data.NomadFleetData;
import nomad.ui.NomadAutoforge;
import nomad.ui.NomadItems;

public final class NomadSplitter {

  public static final String FLEET_TAG = "nomad_fleet";
  public static final String REJOINING_FLAG = "$nomad_rejoining";
  private static final String PARKED_AT_KEY = "$nomad_parkedAt";
  private static final String IMPORTANT_REASON = "nomad_contingent";
  private static final float SPAWN_GAP = 300f;
  private static final float HOLD_DAYS = 1_000_000f;

  private NomadSplitter() {
  }

  public static String canSplit() {
    if (!NomadFleetData.exists()) {
      return NomadStrings.get("nomad_split", "split_none");
    }
    NomadFleetData data = NomadFleetData.getInstance();
    if (data.isDetached()) {
      return NomadStrings.get("nomad_split", "split_already");
    }
    SectorAPI sector = Global.getSector();
    CampaignFleetAPI player = sector != null ? sector.getPlayerFleet() : null;
    if (player == null) {
      return NomadStrings.get("nomad_split", "split_no_fleet");
    }
    LocationAPI loc = player.getContainingLocation();
    if (loc == null || loc.isHyperspace()) {
      return NomadStrings.get("nomad_split", "split_hyperspace");
    }
    NomadManager mgr = NomadManager.getInstance();
    FleetMemberAPI flag = mgr != null ? mgr.getFlagshipMember() : null;
    if (flag == null || !player.getFleetData().getMembersListCopy().contains(flag)) {
      return NomadStrings.get("nomad_split", "split_flagship_away");
    }
    if (isPlayerPiloted(flag)) {
      return NomadStrings.get("nomad_split", "split_piloted");
    }
    if (splitMembers(player).isEmpty()) {
      return NomadStrings.get("nomad_split", "split_no_carriers");
    }
    return null;
  }

  public static void split() {
    if (canSplit() != null) {
      return;
    }
    SectorAPI sector = Global.getSector();
    CampaignFleetAPI player = sector.getPlayerFleet();
    NomadFleetData data = NomadFleetData.getInstance();

    CampaignFleetAPI detached =
        FleetFactoryV3.createEmptyFleet(Factions.PLAYER, FleetTypes.TASK_FORCE, null);
    detached.setNoFactionInName(true);
    detached.setName("Nomad Flotilla");
    detached.setAIMode(true);
    detached.setNoAutoDespawn(true);
    Misc.makeImportant(detached, IMPORTANT_REASON);
    detached.addTag(FLEET_TAG);
    detached.getMemoryWithoutUpdate().set(NomadManager.NOMAD_FLEET_FLAG, true);
    detached.setFreeTransfer(true);

    List<FleetMemberAPI> moving = splitMembers(player);
    NomadCapacity.Capacity atSplit = NomadCapacityUtil.forMembers(moving);
    PersonAPI bestOfficer = null;
    int bestLevel = -1;
    for (FleetMemberAPI m : moving) {
      detached.getFleetData().addFleetMember(m);
      player.getFleetData().removeFleetMember(m);
      clearInflaterOrigin(m);
      PersonAPI cap = m.getCaptain();
      if (isOfficer(cap)) {
        int lvl = cap.getStats().getLevel();
        if (lvl > bestLevel) {
          bestLevel = lvl;
          bestOfficer = cap;
        }
      }
    }
    detached.getFleetData().ensureHasFlagship();
    player.getFleetData().ensureHasFlagship();

    detached.setCommander(bestOfficer != null ? bestOfficer : detached.getFaction().createRandomPerson());

    LocationAPI loc = player.getContainingLocation();
    loc.addEntity(detached);
    Park parked = park(player);
    Vector2f p = parkPoint(player, parked, detached.getRadius());
    detached.getMemoryWithoutUpdate().set(PARKED_AT_KEY,
        parked.body == null ? "" : parked.body.getId());
    detached.setLocation(p.x, p.y);
    detached.setFacing((float) (Math.random() * 360.0));
    detached.setTransponderOn(true);
    detached.removeAbility(Abilities.EMERGENCY_BURN);
    detached.removeAbility(Abilities.GO_DARK);
    detached.forceSync();
    detached.getFleetData().setSyncNeeded();
    detached.getFleetData().syncIfNeeded();

    holdStation(detached);

    detached.addEventListener(new NomadFleetEventListener(detached));

    data.setDetached(true);
    data.setDetachedFleetId(detached.getId());
    data.freezeCapacity(atSplit.space, atSplit.berths);
    NomadManager mgr = NomadManager.getInstance();
    if (mgr != null) {
      mgr.rescanForFlagship();
    }

    int ships = detached.getFleetData().getMembersListCopy().size();
    NomadHistory.add(data, NomadStrings.get("nomad_history",
        ships == 1 ? "hist_detached_one" : "hist_detached_many", systemName(detached), ships));
    data.markChanged();
  }

  private static final class Park {

    final PlanetAPI body;
    final float distance;

    Park(PlanetAPI body, float distance) {
      this.body = body;
      this.distance = distance;
    }
  }

  private static Park park(CampaignFleetAPI player) {
    PlanetAPI body = NomadMiningResolver.nearestEligibleBody(player,
        NomadSettings.getFloat("nomad_split_anchor_body_range", 2500f));
    if (body == null) {
      return new Park(null, 0f);
    }
    float from = Misc.getDistance(player.getLocation(), body.getLocation()) - body.getRadius();
    return new Park(body, SplitShortfall.parkDistance(from, SPAWN_GAP,
        NomadSettings.getFloat("nomad_mining_radius", 333f)));
  }

  private static Vector2f parkPoint(CampaignFleetAPI player, Park park, float detachedRadius) {
    if (park.body == null) {
      return Misc.getPointAtRadius(player.getLocation(), SPAWN_GAP + detachedRadius);
    }
    Vector2f centre = park.body.getLocation();
    Vector2f toward = Misc.getUnitVectorAtDegreeAngle(
        Misc.getAngleInDegrees(centre, player.getLocation()));
    float out = park.body.getRadius() + park.distance;
    return new Vector2f(centre.x + toward.x * out, centre.y + toward.y * out);
  }

  private static Mining.Site siteAfterSplit(Mining.Site now, Park park) {
    if (park.body == null) {
      return SplitShortfall.siteAfterSplit(now, null);
    }
    NomadMiningResolver.Result rock = NomadMiningResolver.resolveFor(park.body);
    return SplitShortfall.siteAfterSplit(now,
        new Mining.Body(rock.bodyName, park.distance, rock.deposits, true));
  }

  public static boolean isDetached() {
    return NomadFleetData.exists() && NomadFleetData.getInstance().isDetached();
  }

  public static boolean detachmentOut() {
    NomadFleetData data = NomadFleetData.getOrNull();
    return data != null && data.getDetachedFleetId() != null;
  }

  static boolean endsFlotilla(CampaignFleetAPI fleet, boolean rejoining) {
    NomadManager mgr = NomadManager.getInstance();
    CampaignFleetAPI carrier = mgr != null && !rejoining ? mgr.getFlagshipFleet() : null;
    return ShellRules.endsFlotilla(rejoining, carrier != null, carrier == fleet);
  }

  public static boolean isDetachedOutOfMiningRange() {
    if (!isDetached()) {
      return false;
    }
    CampaignFleetAPI f = findDetachedFleet();
    return f != null && !nomad.campaign.NomadMiningResolver.resolve(f).active;
  }

  public static String detachedSummary() {
    DetachedWhere at = detachedWhere();
    if (at == null) {
      return "";
    }
    int ships = at.ships;
    String place = at.system + at.near;
    String where = ships == 1
        ? NomadStrings.get("nomad_split", "detach_summary_one", place, "" + ships)
        : NomadStrings.get("nomad_split", "detach_summary_many", place, "" + ships);
    float owed = NomadFleetData.getInstance().getSuppliesOwed();
    if (owed >= 0.5f) {
      where += " " + NomadStrings.get("nomad_split", "detach_summary_owed",
          Misc.getWithDGS(Math.round(owed)));
    }
    return where;
  }

  public static final class DetachedWhere {

    public final String system;
    public final String near;
    public final int ships;

    DetachedWhere(String system, String near, int ships) {
      this.system = system;
      this.near = near;
      this.ships = ships;
    }
  }

  public static DetachedWhere detachedWhere() {
    if (!isDetached()) {
      return null;
    }
    CampaignFleetAPI f = findDetachedFleet();
    if (f == null) {
      return null;
    }
    return new DetachedWhere(systemName(f), nearestBodyPhrase(f),
        f.getFleetData().getMembersListCopy().size());
  }

  public static String detachedLocation() {
    if (!isDetached()) {
      return "";
    }
    CampaignFleetAPI f = findDetachedFleet();
    if (f == null) {
      return "";
    }
    return systemName(f) + nearestBodyPhrase(f);
  }

  private static String nearestBodyPhrase(CampaignFleetAPI f) {
    LocationAPI loc = f.getContainingLocation();
    if (loc == null || loc.isHyperspace()) {
      return "";
    }
    com.fs.starfarer.api.campaign.PlanetAPI nearest = null;
    float bestDist = Float.MAX_VALUE;
    for (com.fs.starfarer.api.campaign.PlanetAPI p : loc.getPlanets()) {
      if (p == null || p.isStar()) {
        continue;
      }
      float d = Misc.getDistance(f.getLocation(), p.getLocation());
      if (d < bestDist) {
        bestDist = d;
        nearest = p;
      }
    }
    if (nearest == null || nearest.getName() == null || nearest.getName().isEmpty()) {
      return "";
    }
    return " " + NomadStrings.get("nomad_split", "detach_summary_near", nearest.getName(),
        Misc.getWithDGS(Math.round(bestDist)));
  }

  public static float suppliesPerMonth() {
    return shipsBill(flotillaMembers());
  }

  public static float shipsBill(List<FleetMemberAPI> members) {
    float bill = 0f;
    if (members == null) {
      return bill;
    }
    for (FleetMemberAPI m : members) {
      if (m == null || m.getStats() == null || m.getStats().getSuppliesPerMonth() == null) {
        continue;
      }
      MutableStat supplies = m.getStats().getSuppliesPerMonth();
      MutableStat.StatMod cut = supplies.getMultStatMod(MakeshiftEquipment.SUPPLIES_EFFECT_ID);
      bill += SplitShortfall.withoutCommanderCut(supplies.getModifiedValue(),
          cut == null ? 1f : cut.getValue());
    }
    return bill;
  }

  public static final class SplitPreview {
    public final int ships;
    public final float cargoBefore;
    public final float cargoAfter;
    public final float cargoHeld;
    public final float fuelBefore;
    public final float fuelAfter;
    public final float fuelHeld;
    public final float crewBefore;
    public final float crewAfter;
    public final float crewHeld;
    public final float burnGain;
    public float flotillaSpaceAfter;
    public float flotillaBerthsAfter;

    SplitPreview(int ships, float cargoBefore, float cargoAfter, float cargoHeld,
        float fuelBefore, float fuelAfter, float fuelHeld,
        float crewBefore, float crewAfter, float crewHeld, float burnGain) {
      this.ships = ships;
      this.cargoBefore = cargoBefore;
      this.cargoAfter = cargoAfter;
      this.cargoHeld = cargoHeld;
      this.fuelBefore = fuelBefore;
      this.fuelAfter = fuelAfter;
      this.fuelHeld = fuelHeld;
      this.crewBefore = crewBefore;
      this.crewAfter = crewAfter;
      this.crewHeld = crewHeld;
      this.burnGain = burnGain;
    }

    public float cargoOver() {
      return Math.max(0f, cargoHeld - cargoAfter);
    }

    public float fuelOver() {
      return Math.max(0f, fuelHeld - fuelAfter);
    }

    public float crewOver() {
      return Math.max(0f, crewHeld - crewAfter);
    }

    public boolean anyOver() {
      return cargoOver() > 0.5f || fuelOver() > 0.5f || crewOver() > 0.5f;
    }
  }

  public static SplitPreview preview() {
    SectorAPI sector = Global.getSector();
    CampaignFleetAPI player = sector != null ? sector.getPlayerFleet() : null;
    if (player == null) {
      return null;
    }
    List<FleetMemberAPI> leaving = splitMembers(player);
    if (leaving.isEmpty()) {
      return null;
    }
    Set<FleetMemberAPI> going = new HashSet<FleetMemberAPI>(leaving);
    float cargoGone = 0f;
    float fuelGone = 0f;
    float crewGone = 0f;
    for (FleetMemberAPI m : leaving) {
      cargoGone += m.getCargoCapacity();
      fuelGone += m.getFuelCapacity();
      crewGone += m.getMaxCrew();
    }

    float slowestNow = Float.MAX_VALUE;
    float slowestAfter = Float.MAX_VALUE;
    for (FleetMemberAPI m : player.getFleetData().getMembersListCopy()) {
      if (m == null || m.getStats() == null || m.getStats().getMaxBurnLevel() == null) {
        continue;
      }
      float burn = m.getStats().getMaxBurnLevel().getModifiedValue();
      slowestNow = Math.min(slowestNow, burn);
      if (!going.contains(m)) {
        slowestAfter = Math.min(slowestAfter, burn);
      }
    }
    float burnGain = slowestNow == Float.MAX_VALUE || slowestAfter == Float.MAX_VALUE
        ? 0f : Math.max(0f, slowestAfter - slowestNow);

    CargoAPI hold = player.getCargo();
    SplitPreview p = new SplitPreview(leaving.size(),
        hold.getMaxCapacity(), Math.max(0f, hold.getMaxCapacity() - cargoGone), hold.getSpaceUsed(),
        hold.getMaxFuel(), Math.max(0f, hold.getMaxFuel() - fuelGone), hold.getFuel(),
        hold.getMaxPersonnel(), Math.max(0f, hold.getMaxPersonnel() - crewGone),
        hold.getTotalPersonnel(), burnGain);
    NomadCapacity.Capacity frozen = NomadCapacityUtil.forMembers(leaving);
    p.flotillaSpaceAfter = frozen.space;
    p.flotillaBerthsAfter = frozen.berths;
    return p;
  }

  public static SplitShortfall shortfall() {
    SectorAPI sector = Global.getSector();
    CampaignFleetAPI player = sector != null ? sector.getPlayerFleet() : null;
    if (player == null || !NomadFleetData.exists()) {
      return null;
    }
    List<FleetMemberAPI> leaving = splitMembers(player);
    if (leaving.isEmpty()) {
      return null;
    }
    NomadCapacity.Capacity frozen = NomadCapacityUtil.forMembers(leaving);
    FlotillaState now = NomadModel.snapshot(NomadFleetData.getInstance(), player, 0f);
    return SplitShortfall.predict(now, frozen.space, frozen.berths, shipsBill(leaving),
        siteAfterSplit(now.site, park(player)), NomadModel.catalog(), NomadModel.rules(),
        NomadEcon.constants());
  }

  public static int officersAtRisk() {
    int n = 0;
    for (FleetMemberAPI m : flotillaMembers()) {
      if (isOfficer(m.getCaptain())) {
        n++;
      }
    }
    return n;
  }

  public static void rejoin(CampaignFleetAPI detached) {
    if (detached == null) {
      return;
    }
    SectorAPI sector = Global.getSector();
    CampaignFleetAPI player = sector.getPlayerFleet();
    NomadFleetData data = NomadFleetData.getInstance();
    Detached.bringHome(new FleetSide(detached), new FleetSide(player));
    player.getFleetData().ensureHasFlagship();
    Detached.Settlement paid = settleOwed(data, player.getCargo());

    data.setDetached(false);
    data.setDetachedFleetId(null);
    data.clearFrozenCapacity();
    detached.getMemoryWithoutUpdate().set(REJOINING_FLAG, true);
    detached.despawn();

    NomadManager mgr = NomadManager.getInstance();
    if (mgr != null) {
      mgr.rescanForFlagship();
    }
    NomadHistory.add(data, NomadStrings.get("nomad_history", "hist_rejoined"));
    logSettled(data, paid);
    if (paid.forgiven >= 0.5f) {
      NomadHistory.add(data, NomadStrings.get("nomad_history", "fixglue_hist_owed_carried",
          Misc.getWithDGS(Math.round(paid.forgiven))));
    }
    data.markChanged();
  }

  private static void logSettled(NomadFleetData data, Detached.Settlement paid) {
    String supplies = Misc.getWithDGS(paid.supplies);
    switch (RejoinReport.owed(paid.supplies, paid.credits)) {
      case CARGO_AND_CREDITS:
        NomadHistory.add(data, NomadStrings.get("nomad_history", "detach_hist_owed_paid_credits",
            supplies, Misc.getDGSCredits(paid.credits)));
        break;
      case CREDITS:
        NomadHistory.add(data, NomadStrings.get("nomad_history",
            "detach_hist_owed_paid_credits_only", Misc.getDGSCredits(paid.credits)));
        break;
      case CARGO:
        NomadHistory.add(data, NomadStrings.get("nomad_history", "detach_hist_owed_paid",
            supplies));
        break;
      default:
        break;
    }
  }

  public static void collectOwed(NomadFleetData data, CampaignFleetAPI player) {
    if (data == null || player == null || !player.isPlayerFleet()
        || data.getSuppliesOwed() < 0.5f) {
      return;
    }
    logSettled(data, settleOwed(data, player.getCargo()));
  }

  static Detached.Settlement owedSettlement() {
    SectorAPI sector = Global.getSector();
    CampaignFleetAPI player = sector != null ? sector.getPlayerFleet() : null;
    if (player == null || !NomadFleetData.exists()) {
      return Detached.settle(0f, 0f, 0f, 0f);
    }
    return owedSettlement(NomadFleetData.getInstance(), player.getCargo());
  }

  private static Detached.Settlement owedSettlement(NomadFleetData data, CargoAPI hold) {
    return Detached.settle(data.getSuppliesOwed(), hold.getCommodityQuantity(Commod.SUPPLIES),
        hold.getCredits().get(), NomadEcon.constants().price(Commod.SUPPLIES));
  }

  private static Detached.Settlement settleOwed(NomadFleetData data, CargoAPI hold) {
    Detached.Settlement paid = owedSettlement(data, hold);
    if (paid.supplies > 0f) {
      hold.removeCommodity(Commod.SUPPLIES, paid.supplies);
    }
    if (paid.credits > 0f) {
      hold.getCredits().subtract(paid.credits);
    }
    data.setSuppliesOwed(paid.forgiven);
    return paid;
  }

  private static final class FleetSide implements Detached.Side<FleetMemberAPI, CargoStackAPI> {

    private final CampaignFleetAPI fleet;

    FleetSide(CampaignFleetAPI fleet) {
      this.fleet = fleet;
    }

    @Override
    public List<FleetMemberAPI> ships() {
      return fleet.getFleetData().getMembersListCopy();
    }

    @Override
    public void addShip(FleetMemberAPI ship) {
      fleet.getFleetData().addFleetMember(ship);
    }

    @Override
    public void removeShip(FleetMemberAPI ship) {
      fleet.getFleetData().removeFleetMember(ship);
    }

    @Override
    public List<CargoStackAPI> stacks() {
      CargoAPI hold = fleet.getCargo();
      return hold == null ? Collections.<CargoStackAPI>emptyList() : hold.getStacksCopy();
    }

    @Override
    public boolean isWeapons(CargoStackAPI stack) {
      return stack.isWeaponStack();
    }

    @Override
    public boolean isWings(CargoStackAPI stack) {
      return stack.isFighterWingStack();
    }

    @Override
    public String commodity(CargoStackAPI stack) {
      return stack.isCommodityStack() ? stack.getCommodityId() : null;
    }

    @Override
    public float size(CargoStackAPI stack) {
      return stack.getSize();
    }

    @Override
    public void addStack(CargoStackAPI stack) {
      fleet.getCargo().addFromStack(stack);
    }

    @Override
    public void removeStack(CargoStackAPI stack) {
      fleet.getCargo().removeStack(stack);
    }
  }

  public static void onGameLoad() {
    NomadFleetData data = NomadFleetData.exists() ? NomadFleetData.getInstance() : null;
    CampaignFleetAPI f = findDetachedFleet();
    if (f == null) {
      if (data != null && data.isCapacityFrozen()) {
        data.clearFrozenCapacity();
      }
      if (data != null && data.getDetachedFleetId() != null) {
        data.setDetachedFleetId(null);
      }
      return;
    }
    reattachListener(f);
    holdStation(f);
  }

  static void handleTotalLoss(CampaignFleetAPI fleet, List<FleetMemberAPI> lastRoster) {
    NomadFleetData data = NomadFleetData.getInstance();
    retireOfficers(lastRoster);

    LocationAPI loc = fleet != null ? fleet.getContainingLocation() : null;
    Vector2f at = fleet != null ? fleet.getLocation() : data.getLastFlagshipLoc();
    Wreckage.Left left = Wreckage.Left.NOTHING;
    if (loc != null && at != null) {
      left = spillWreckageToPods(data.getFlotillaCargo(), data.getInstalledItems(), loc, at);
    }

    data.setResidents(0f);
    data.bumpAllocSerial();
    for (FleetMemberAPI m : lastRoster == null ? new ArrayList<FleetMemberAPI>() : lastRoster) {
      if (m != null && m.getId() != null) {
        data.getDedication().remove(m.getId());
      }
    }
    data.setWorkStates(new ArrayList<WorkState>());
    for (String good : Commod.STORED) {
      if (data.getStoreTarget(good) > 0f) {
        data.setStoreTarget(good, 0f);
      }
    }
    NomadAutoforge.clear(data);
    data.setSuppliesOwed(0f);
    data.getInstalledItems().clear();
    data.setDetached(false);
    data.setDetachedFleetId(null);
    data.setCapacitySnapshot(0f, 0f);
    data.clearFrozenCapacity();

    NomadManager mgr = NomadManager.getInstance();
    if (mgr != null) {
      mgr.rescanForFlagship();
    }
    NomadEconomyScript.standDown(data);
    NomadCqFurnishing.unmarkFounded();
    data.endFlotilla();
    String where = systemName(fleet);
    switch (left) {
      case STORES_AND_ITEMS:
        NomadHistory.add(data, NomadStrings.get("nomad_history", "hist_destroyed_with_items"));
        lossMessage("msg_destroyed_with_items", "nomad_intel_paused", where);
        break;
      case STORES:
        NomadHistory.add(data, NomadStrings.get("nomad_history", "hist_destroyed"));
        lossMessage("msg_destroyed", "nomad_intel_paused", where);
        break;
      case ITEMS:
        NomadHistory.add(data, NomadStrings.get("nomad_history", "hist_destroyed_items_only"));
        lossMessage("msg_destroyed_items_only", "nomad_intel_paused", where);
        break;
      default:
        NomadHistory.add(data, NomadStrings.get("nomad_history", "hist_destroyed_bare"));
        lossMessage("msg_destroyed_bare", "nomad_intel_paused", where);
        break;
    }
  }

  static void handleRemnantLoss(CampaignFleetAPI fleet, List<FleetMemberAPI> lastRoster) {
    NomadFleetData data = NomadFleetData.getInstance();
    List<FleetMemberAPI> lost = lastRoster == null ? new ArrayList<FleetMemberAPI>() : lastRoster;
    int officers = retireOfficers(lost);
    for (FleetMemberAPI m : lost) {
      if (m != null && m.getId() != null) {
        data.getDedication().remove(m.getId());
      }
    }
    data.setDetachedFleetId(null);
    data.clearFrozenCapacity();
    data.markChanged();
    int ships = lost.size();
    NomadHistory.add(data, ships == 1
        ? NomadStrings.get("nomad_messages", "msg_lost_ships_one", ships, systemName(fleet))
        : NomadStrings.get("nomad_messages", "msg_lost_ships_many", ships, systemName(fleet)));
    if (officers == 1) {
      NomadHistory.add(data, NomadStrings.get("nomad_history", "detach_hist_officers_one",
          officers));
    } else if (officers > 1) {
      NomadHistory.add(data, NomadStrings.get("nomad_history", "detach_hist_officers_many",
          officers));
    }
    lossMessage(ships == 1 ? "msg_lost_ships_one" : "msg_lost_ships_many", "nomad_intel_detached",
        ships, systemName(fleet));
  }

  static void handlePartialLoss(CampaignFleetAPI fleet, List<FleetMemberAPI> lost) {
    NomadFleetData data = NomadFleetData.getInstance();
    if (data.isCapacityFrozen()) {
      NomadCapacity.Capacity gone = NomadCapacityUtil.forMembers(lost);
      NomadCapacity.Capacity left = NomadCapacityUtil.forFleetData(fleet.getFleetData());
      data.freezeCapacity(Detached.afterLoss(data.getFrozenSpace(), left.space, gone.space),
          Detached.afterLoss(data.getFrozenBerths(), left.berths, gone.berths));
      data.bumpAllocSerial();
    }
    int officers = retireOfficers(lost);
    int ships = lost.size();
    NomadHistory.add(data, ships == 1
        ? NomadStrings.get("nomad_messages", "msg_lost_ships_one", ships, systemName(fleet))
        : NomadStrings.get("nomad_messages", "msg_lost_ships_many", ships, systemName(fleet)));
    if (officers == 1) {
      NomadHistory.add(data, NomadStrings.get("nomad_history", "detach_hist_officers_one",
          officers));
    } else if (officers > 1) {
      NomadHistory.add(data, NomadStrings.get("nomad_history", "detach_hist_officers_many",
          officers));
    }
    lossMessage(ships == 1 ? "msg_lost_ships_one" : "msg_lost_ships_many", "nomad_intel_detached",
        ships, systemName(fleet));
    NomadManager mgr = NomadManager.getInstance();
    if (mgr != null) {
      mgr.rescanForFlagship();
    }
  }

  private static void lossMessage(String key, String icon, Object... args) {
    try {
      MessageIntel intel = new MessageIntel(NomadStrings.get("nomad_messages", key, args),
          Misc.getNegativeHighlightColor());
      intel.setIcon(Global.getSettings().getSpriteName("icons", icon));
      Global.getSector().getCampaignUI().addMessage(intel);
    } catch (Exception e) {
    }
  }

  static void holdStation(CampaignFleetAPI fleet) {
    LocationAPI loc = fleet.getContainingLocation();
    if (loc == null) {
      return;
    }
    fleet.clearAssignments();
    Object written = fleet.getMemoryWithoutUpdate().get(PARKED_AT_KEY);
    String parked = written instanceof String ? (String) written : null;
    PlanetAPI nearest = parked != null ? null : nomad.campaign.NomadMiningResolver.nearestEligibleBody(
        fleet, nomad.campaign.NomadSettings.getFloat("nomad_split_anchor_body_range", 2500f));
    String at = ShellRules.holdsAt(parked, nearest == null ? null : nearest.getId());
    PlanetAPI body = nearest;
    if (body == null && at != null) {
      SectorEntityToken held = loc.getEntityById(at);
      body = held instanceof PlanetAPI ? (PlanetAPI) held : null;
    }
    if (body != null) {
      fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, body, HOLD_DAYS, "orbiting " + body.getName());
    } else {
      Vector2f pos = fleet.getLocation();
      SectorEntityToken anchor = loc.createToken(pos.x, pos.y);
      fleet.addAssignment(FleetAssignment.DEFEND_LOCATION, anchor, HOLD_DAYS, "holding position");
    }
    MemoryAPI mem = fleet.getMemoryWithoutUpdate();
    mem.set(MemFlags.MEMORY_KEY_MAKE_HOLD_VS_STRONGER, true);
    mem.set(MemFlags.MEMORY_KEY_NO_JUMP, true);
    mem.set(MemFlags.DO_NOT_TRY_TO_AVOID_NEARBY_FLEETS, true);
    mem.set(MemFlags.MEMORY_KEY_NEVER_AVOID_PLAYER_SLOWLY, true);
  }

  public static CampaignFleetAPI findDetachedFleet() {
    SectorAPI sector = Global.getSector();
    if (sector == null) {
      return null;
    }
    NomadManager mgr = NomadManager.getInstance();
    CampaignFleetAPI carrier = mgr != null ? mgr.getFlagshipFleet() : null;
    if (carrier != null && carrier != sector.getPlayerFleet()
        && carrier.getContainingLocation() != null
        && carrier.getMemoryWithoutUpdate().getBoolean(NomadManager.NOMAD_FLEET_FLAG)) {
      return carrier;
    }
    String id = NomadFleetData.exists() ? NomadFleetData.getInstance().getDetachedFleetId() : null;
    for (StarSystemAPI sys : sector.getStarSystems()) {
      CampaignFleetAPI f = matchNomadFleet(sys.getFleets(), id);
      if (f != null) {
        return f;
      }
    }
    return matchNomadFleet(sector.getHyperspace().getFleets(), id);
  }

  private static CampaignFleetAPI matchNomadFleet(List<CampaignFleetAPI> fleets, String id) {
    if (fleets == null) {
      return null;
    }
    for (CampaignFleetAPI f : fleets) {
      if (f == null) {
        continue;
      }
      if (f.getMemoryWithoutUpdate().getBoolean(NomadManager.NOMAD_FLEET_FLAG)
          || (id != null && id.equals(f.getId()))) {
        return f;
      }
    }
    return null;
  }

  public static void clearInflaterOrigin(FleetMemberAPI m) {
    if (m == null || m.getVariant() == null) {
      return;
    }
    ShipVariantAPI v = m.getVariant();
    if (v.isStockVariant() || v.getSource() == VariantSource.HULL) {
      v = v.clone();
      v.setSource(VariantSource.REFIT);
      m.setVariant(v, false, false);
    }
    if (v.getOriginalVariant() == null) {
      return;
    }
    v.setOriginalVariant(null);
    if (v.getOriginalVariant() != null) {
      v.setHullVariantId(v.getHullVariantId() + "_nomad_" + m.getId());
    }
  }

  private static void reattachListener(CampaignFleetAPI fleet) {
    for (FleetEventListener l : fleet.getEventListeners()) {
      if (l instanceof NomadFleetEventListener) {
        ((NomadFleetEventListener) l).refreshRoster(fleet);
        return;
      }
    }
    fleet.addEventListener(new NomadFleetEventListener(fleet));
  }

  private static List<FleetMemberAPI> splitMembers(CampaignFleetAPI player) {
    List<FleetMemberAPI> out = new ArrayList<FleetMemberAPI>();
    if (player == null) {
      return out;
    }
    for (FleetMemberAPI m : player.getFleetData().getMembersListCopy()) {
      if (isNomadMember(m) && !isPlayerPiloted(m)) {
        out.add(m);
      }
    }
    return out;
  }

  private static List<FleetMemberAPI> flotillaMembers() {
    if (isDetached()) {
      CampaignFleetAPI f = findDetachedFleet();
      return f != null ? f.getFleetData().getMembersListCopy() : Collections.<FleetMemberAPI>emptyList();
    }
    SectorAPI sector = Global.getSector();
    return splitMembers(sector != null ? sector.getPlayerFleet() : null);
  }

  private static int retireOfficers(List<FleetMemberAPI> members) {
    SectorAPI sector = Global.getSector();
    CampaignFleetAPI player = sector != null ? sector.getPlayerFleet() : null;
    int n = 0;
    for (FleetMemberAPI m : members) {
      PersonAPI cap = m.getCaptain();
      if (isOfficer(cap)) {
        if (player != null) {
          player.getFleetData().removeOfficer(cap);
        }
        n++;
      }
    }
    return n;
  }

  private static Wreckage.Left spillWreckageToPods(CargoAPI store, List<String> items, LocationAPI loc,
      Vector2f at) {
    boolean hasStore = store != null && !cargoIsEmpty(store);
    List<String> cores = new ArrayList<String>();
    List<String> specials = new ArrayList<String>();
    for (String id : items == null ? Collections.<String>emptyList() : items) {
      if (id == null || id.isEmpty()) {
        continue;
      }
      switch (Wreckage.spillAs(NomadItems.gameDefines(id), NomadItems.isCore(id))) {
        case COMMODITY:
          cores.add(id);
          break;
        case SPECIAL:
          specials.add(id);
          break;
        default:
          Global.getLogger(NomadSplitter.class).warn("Nomad Flotilla: installed item '" + id
              + "' is defined by no installed mod, so it was not left in the pods.");
          break;
      }
    }
    int itemCount = cores.size() + specials.size();
    Wreckage.Left left = Wreckage.left(hasStore, itemCount);
    if (left == Wreckage.Left.NOTHING) {
      return left;
    }
    CustomCampaignEntityAPI pods = Misc.addCargoPods(loc, at);
    if (hasStore) {
      pods.getCargo().addAll(store);
      store.clear();
    }
    for (String id : cores) {
      pods.getCargo().addCommodity(id, 1f);
    }
    for (String id : specials) {
      pods.getCargo().addSpecial(new SpecialItemData(id, null), 1f);
    }
    if (pods.getCustomPlugin() instanceof CargoPodsEntityPlugin) {
      ((CargoPodsEntityPlugin) pods.getCustomPlugin()).setExtraDays(100f);
    }
    CargoPods.stabilizeOrbit(pods, false);
    return left;
  }

  private static boolean cargoIsEmpty(CargoAPI cargo) {
    for (String c : Commod.STORED) {
      if (cargo.getCommodityQuantity(c) > 0.001f) {
        return false;
      }
    }
    return true;
  }

  static boolean isNomadMember(FleetMemberAPI m) {
    if (m == null || m.getVariant() == null) {
      return false;
    }
    for (String id : NomadManager.ALL_HULLMOD_IDS) {
      if (m.getVariant().hasHullMod(id)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isPlayerPiloted(FleetMemberAPI m) {
    PersonAPI cap = m.getCaptain();
    return cap != null && cap.isPlayer();
  }

  private static boolean isOfficer(PersonAPI cap) {
    return cap != null && !cap.isDefault() && !cap.isPlayer();
  }

  private static String systemName(CampaignFleetAPI f) {
    LocationAPI loc = f != null ? f.getContainingLocation() : null;
    if (loc == null) {
      return "deep space";
    }
    if (loc.isHyperspace()) {
      return "hyperspace";
    }
    String typed = loc instanceof StarSystemAPI
        ? ((StarSystemAPI) loc).getNameWithLowercaseType() : null;
    return nomad.ui.NomadHomeFigures.placeInSentence(typed, loc.getName());
  }
}
