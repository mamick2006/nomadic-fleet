package nomad.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;

import nomad.NomadStrings;
import nomad.campaign.NomadEcon;
import nomad.campaign.NomadModel;
import nomad.campaign.econ.EconConstants;
import nomad.campaign.econ.model.BasicNeeds;
import nomad.campaign.econ.model.Capacity;
import nomad.campaign.econ.model.FlotillaState;
import nomad.campaign.econ.model.FlotillaTick;
import nomad.campaign.econ.model.NeedsLevel;
import nomad.campaign.econ.model.Priority;
import nomad.campaign.econ.model.Residents;
import nomad.campaign.econ.model.Rules;
import nomad.campaign.econ.model.MiningSystem;
import nomad.campaign.econ.model.TickSheet;
import nomad.campaign.econ.model.WorkCatalog;
import nomad.campaign.econ.model.WorkDef;
import nomad.campaign.econ.model.WorkOrders;
import nomad.campaign.econ.model.WorkState;
import nomad.campaign.econ.model.WorkStatus;
import nomad.campaign.econ.model.Works;
import nomad.data.NomadFleetData;

public final class NomadFlotillaView {

  public final NomadFleetData data;
  public final TickSheet sheet;
  public final TickSheet standing;
  public final FlotillaState state;
  public final WorkCatalog catalog;
  public final Rules rules;
  public final EconConstants shared;

  public final float credits;
  public final float fleetCrew;
  public final float skeletonCrew;
  public final float fleetMarines;
  public final float crewInMax;
  public final float crewOutMax;

  public final boolean live;
  public final boolean paused;
  public final boolean foundryQueuePaused;

  public final String selected;
  public final boolean picking;

  private final List<WorkState> works;
  private final List<WorkDef> unbuilt;
  private final Map<String, WorkOrders.Quote> quotes = new HashMap<String, WorkOrders.Quote>();
  private final Map<String, NomadWorkCardModel.State> cardStates =
      new HashMap<String, NomadWorkCardModel.State>();
  private MiningSystem.Readout mining;

  private NomadFlotillaView(NomadFleetData data, TickSheet sheet, String selected,
      boolean picking) {
    this.data = data;
    this.sheet = sheet;
    this.state = sheet.s;
    this.catalog = sheet.catalog;
    this.rules = sheet.k;
    this.shared = sheet.shared;
    this.paused = state.paused;
    this.standing = paused ? standingSheet(sheet) : sheet;

    CampaignFleetAPI fleet = playerFleet();
    boolean hasCargo = fleet != null && fleet.getCargo() != null;
    this.credits = hasCargo ? fleet.getCargo().getCredits().get() : 0f;
    this.fleetCrew = hasCargo ? fleet.getCargo().getCrew() : 0f;
    this.fleetMarines = hasCargo ? fleet.getCargo().getMarines() : 0f;
    this.skeletonCrew = fleet == null || fleet.getFleetData() == null
        ? 0f : fleet.getFleetData().getMinCrew();
    this.crewInMax = Residents.maxCrewIn(sheet.residents, sheet.berths, fleetCrew, skeletonCrew);
    this.crewOutMax = Residents.maxCrewOut(sheet.residents, shared);

    this.live = !state.paused && !state.detached;
    this.foundryQueuePaused = !data.getAutoforgeQueue().isEmpty() && NomadAutoforge.isPaused();

    this.works = inCardOrder(sheet);
    this.unbuilt = unbuiltWorks(sheet);
    this.selected = selected != null && sheet.work(selected) != null ? selected : null;
    this.picking = picking && !unbuilt.isEmpty();
  }

  public static NomadFlotillaView of(NomadFleetData data, String selected, boolean picking) {
    TickSheet sheet = NomadModel.preview(data, playerFleet());
    return new NomadFlotillaView(data, sheet, selected, picking);
  }

  static NomadFlotillaView forOrders(NomadFleetData data) {
    return of(data, null, false);
  }

  private static TickSheet standingSheet(TickSheet paused) {
    FlotillaState lifted = paused.s.copy();
    lifted.paused = false;
    lifted.elapsedDays = 0f;
    return FlotillaTick.preview(lifted, paused.catalog, paused.k, paused.shared);
  }

  static CampaignFleetAPI playerFleet() {
    return Global.getSector() == null ? null : Global.getSector().getPlayerFleet();
  }

  public List<WorkState> works() {
    return works;
  }

  public List<WorkDef> unbuilt() {
    return unbuilt;
  }

  public WorkState work(String workId) {
    return sheet.work(workId);
  }

  private static List<WorkState> inCardOrder(TickSheet sheet) {
    List<WorkState> out = new ArrayList<WorkState>(sheet.works);
    Collections.sort(out, new Comparator<WorkState>() {
      @Override
      public int compare(WorkState a, WorkState b) {
        return Integer.compare(a.order, b.order);
      }
    });
    return Collections.unmodifiableList(out);
  }

  public NomadWorkCardModel.State cardState(String workId) {
    NomadWorkCardModel.State st = cardStates.get(workId);
    if (st == null) {
      WorkState w = work(workId);
      st = NomadWorkCardModel.state(sheet, w, catalog.work(workId), foundryQueuePaused,
          suspendedWhy(w));
      cardStates.put(workId, st);
    }
    return st;
  }

  private NomadWorkCardModel.Why suspendedWhy(WorkState w) {
    if (w == null || !w.suspended || paused) {
      return null;
    }
    boolean returns = Capacity.wouldReturn(state, w.id, catalog, rules, shared);
    boolean holdFull = !returns && Capacity.heldOutByHold(state, w.id, catalog, rules, shared);
    return NomadWorkCardModel.suspendedWhy(returns, holdFull, Capacity.spaceHeld(w, catalog), sheet.spaceNeeds());
  }

  public String stateText(String workId) {
    NomadWorkCardModel.State st = cardState(workId);
    WorkState w = work(workId);
    int days = w == null ? 0
        : NomadWorkCardModel.daysLeft(shared.workBuildDays - w.buildDays);
    return NomadWorkCardModel.stateText(st.shown, st.why, reasonName(st, Float.MAX_VALUE),
        levelName(sheet.needs.level), days, FLOTILLA);
  }

  static String reasonName(NomadWorkCardModel.State st, float box) {
    if (st.why == NomadWorkCardModel.Why.NEEDS_PREREQ) {
      return st.prereq == null ? null : NomadPrereqText.line(st.prereq,
          nomad.campaign.works.WorkPrereqChecks.NAMES, box, NomadUi.PARA, FLOTILLA);
    }
    return st.good == null ? null : NomadWorkHover.goodName(st.good);
  }

  private static List<WorkDef> unbuiltWorks(TickSheet sheet) {
    List<WorkDef> out = new ArrayList<WorkDef>();
    for (WorkDef def : sheet.catalog.works()) {
      if (sheet.work(def.id) == null) {
        out.add(def);
      }
    }
    return Collections.unmodifiableList(out);
  }

  public WorkOrders.Quote quoteBuild(String workId) {
    String key = "build*" + workId;
    WorkOrders.Quote q = quotes.get(key);
    if (q == null) {
      q = WorkOrders.quoteBuild(state, workId, credits, catalog, rules, shared);
      quotes.put(key, q);
    }
    return q;
  }

  public WorkOrders.Quote quoteUpgrade(String workId) {
    String key = "upgrade*" + workId;
    WorkOrders.Quote q = quotes.get(key);
    if (q == null) {
      q = WorkOrders.quoteUpgrade(state, workId, credits, catalog, rules, shared);
      quotes.put(key, q);
    }
    return q;
  }

  public WorkOrders.Quote quoteDowngrade(String workId) {
    String key = "downgrade*" + workId;
    WorkOrders.Quote q = quotes.get(key);
    if (q == null) {
      q = WorkOrders.quoteDowngrade(state, workId, catalog, rules, shared);
      quotes.put(key, q);
    }
    return q;
  }

  public WorkOrders.Quote quoteScrap(String workId) {
    String key = "scrap*" + workId;
    WorkOrders.Quote q = quotes.get(key);
    if (q == null) {
      q = WorkOrders.quoteScrap(state, workId, catalog, rules, shared);
      quotes.put(key, q);
    }
    return q;
  }

  public WorkOrders.Quote quoteCancel(String workId) {
    String key = "cancel*" + workId;
    WorkOrders.Quote q = quotes.get(key);
    if (q == null) {
      q = WorkOrders.quoteCancel(state, workId, catalog, rules, shared);
      quotes.put(key, q);
    }
    return q;
  }

  public WorkOrders.Quote quoteMothball(String workId, boolean mothball) {
    String key = (mothball ? "mothball*" : "revive*") + workId;
    WorkOrders.Quote q = quotes.get(key);
    if (q == null) {
      q = WorkOrders.quoteMothball(state, workId, mothball, catalog, rules, shared);
      quotes.put(key, q);
    }
    return q;
  }

  public WorkOrders.Quote quoteRemote(boolean on) {
    String key = on ? "remote*on" : "remote*off";
    WorkOrders.Quote q = quotes.get(key);
    if (q == null) {
      q = WorkOrders.quoteRemote(state, on, catalog, rules, shared);
      quotes.put(key, q);
    }
    return q;
  }

  public BasicNeeds.Reading needsAfter(FlotillaState changed) {
    return BasicNeeds.read(changed, catalog, rules, shared);
  }

  public boolean wouldStartLeaving(BasicNeeds.Reading after) {
    return rules.leavePerMonth(after.level) > 0f && rules.leavePerMonth(sheet.needs.level) <= 0f;
  }

  public MiningSystem.Readout mining() {
    if (mining == null) {
      mining = NomadMining.readout(NomadUi.flotillaFleet(), state, rules);
    }
    return mining;
  }

  public static String text(String key, Object... args) {
    return args == null || args.length == 0
        ? NomadStrings.get("nomad_flotilla", key) : NomadStrings.get("nomad_flotilla", key, args);
  }

  public static final NomadWorkCardModel.Strings FLOTILLA = new NomadWorkCardModel.Strings() {
    @Override
    public String get(String key, Object... args) {
      return text(key, args);
    }
  };

  public static String levelName(NeedsLevel level) {
    return NomadNeedsTag.levelName(level, FLOTILLA);
  }

  public static String statusName(WorkStatus status) {
    switch (status) {
      case RUNNING:
        return NomadStrings.get("nomad_flotilla", "screen_status_running");
      case IDLE:
        return NomadStrings.get("nomad_flotilla", "screen_status_idle");
      case UNSTAFFED:
        return NomadStrings.get("nomad_flotilla", "screen_status_unstaffed");
      case MOTHBALLED:
        return NomadStrings.get("nomad_flotilla", "screen_status_mothballed");
      case SUSPENDED:
        return NomadStrings.get("nomad_flotilla", "screen_status_suspended");
      case STANDING_DOWN:
      case LACKING:
        return NomadStrings.get("nomad_flotilla", "roster_status_standing_down");
      case BUILDING:
        return NomadStrings.get("nomad_flotilla", "screen_status_building");
      default:
        return status.name();
    }
  }

  public static String reasonText(WorkStatus.Reason reason) {
    return NomadWorkCardModel.whyText(NomadWorkCardModel.idleWhy(reason, false), null, null,
        FLOTILLA);
  }

  public static String refusalText(WorkOrders.Quote q) {
    if (q.refusal == WorkOrders.Refusal.NEEDS_PREREQ && !q.unmet.isEmpty()) {
      return NomadPrereqText.sentence(q.unmet.get(0), nomad.campaign.works.WorkPrereqChecks.NAMES,
          FLOTILLA);
    }
    return refusalText(q.refusal);
  }

  public static String refusalText(WorkOrders.Refusal refusal) {
    if (refusal == null) {
      return "";
    }
    switch (refusal) {
      case UNKNOWN_WORK:
        return NomadStrings.get("nomad_flotilla", "screen_refusal_unknown_work");
      case ALREADY_BUILT:
        return NomadStrings.get("nomad_flotilla", "screen_refusal_already_built");
      case NOT_BUILT:
        return NomadStrings.get("nomad_flotilla", "screen_refusal_not_built");
      case AT_MAX_SIZE:
        return NomadStrings.get("nomad_flotilla", "screen_refusal_at_max_size");
      case AT_MIN_SIZE:
        return NomadStrings.get("nomad_flotilla", "screen_refusal_at_min_size");
      case BUILDING:
        return NomadStrings.get("nomad_flotilla", "screen_refusal_building");
      case NOT_BUILDING:
        return NomadStrings.get("nomad_flotilla", "screen_refusal_not_building");
      case NO_SPACE:
        return NomadStrings.get("nomad_flotilla", "screen_refusal_no_space");
      case NO_CREDITS:
        return NomadStrings.get("nomad_flotilla", "screen_refusal_no_credits");
      case SWITCHING:
        return NomadStrings.get("nomad_flotilla", "screen_refusal_switching");
      case NEEDS_PREREQ:
        return NomadStrings.get("nomad_flotilla", "prereq_card_unmet");
      default:
        return "";
    }
  }

  public static String priorityName(Priority priority) {
    switch (priority) {
      case HIGH:
        return NomadStrings.get("nomad_flotilla", "screen_priority_high");
      case MEDIUM:
        return NomadStrings.get("nomad_flotilla", "screen_priority_medium");
      case LOW:
        return NomadStrings.get("nomad_flotilla", "screen_priority_low");
      default:
        return priority.name();
    }
  }

  public String workName(String workId) {
    return workName(catalog, workId);
  }

  public static String workName(WorkCatalog catalog, String workId) {
    if (workId == null) {
      return "";
    }
    WorkDef def = catalog.work(workId);
    if (Works.BASIC_NEEDS.equals(workId) || def != null && def.ownRow) {
      String own = ownWorkName(workId);
      if (own != null) {
        return own;
      }
    }
    if (def == null) {
      return workId;
    }
    return def.name == null || def.name.isEmpty() ? workId : def.name;
  }

  private static String ownWorkName(String workId) {
    switch (workId) {
      case Works.HYDROPONIC_FARM:
        return NomadStrings.get("nomad_works", "wk_name_hydroponic_farm");
      case Works.DOMESTIC_GOODS_PLANT:
        return NomadStrings.get("nomad_works", "wk_name_domestic_goods_plant");
      case Works.SUPPLIES_FACTORY:
        return NomadStrings.get("nomad_works", "wk_name_supplies_factory");
      case Works.LUXURY_GOODS_FACTORY:
        return NomadStrings.get("nomad_works", "wk_name_luxury_goods_factory");
      case Works.FUEL_PLANT:
        return NomadStrings.get("nomad_works", "wk_name_fuel_plant");
      case Works.REFINERY:
        return NomadStrings.get("nomad_works", "wk_name_refinery");
      case Works.MACHINERY_FACTORY:
        return NomadStrings.get("nomad_works", "wk_name_machinery_factory");
      case Works.MINING_RIG:
        return NomadStrings.get("nomad_works", "wk_name_mining_rig");
      case Works.FOUNDRY:
        return NomadStrings.get("nomad_works", "wk_name_foundry");
      case Works.BROKERAGE:
        return NomadStrings.get("nomad_works", "wk_name_brokerage");
      case Works.MILITARY_FABRICATOR:
        return NomadStrings.get("nomad_works", "wk_name_military_fabricator");
      case Works.DRUG_LAB:
        return NomadStrings.get("nomad_works", "wk_name_drug_lab");
      case Works.ORGAN_HARVESTER:
        return NomadStrings.get("nomad_works", "wk_name_organ_harvester");
      case Works.MARINE_BARRACKS:
        return NomadStrings.get("nomad_works", "wk_name_marine_barracks");
      case Works.CLONING_VATS:
        return NomadStrings.get("nomad_works", "wk_name_cloning_vats");
      case Works.SURVEY_OFFICE:
        return NomadStrings.get("nomad_works", "wk_name_survey_office");
      case Works.STEALTH_CONTROL_ROOM:
        return NomadStrings.get("nomad_works", "wk_name_stealth_control_room");
      case Works.SENSOR_STATION:
        return NomadStrings.get("nomad_works", "wk_name_sensor_station");
      case Works.SLIPSTREAM_CHART_ROOM:
        return NomadStrings.get("nomad_works", "wk_name_slipstream_chart_room");
      case Works.REPAIR_YARD:
        return NomadStrings.get("nomad_works", "wk_name_repair_yard");
      case Works.RAID_TRAINING_GROUND:
        return NomadStrings.get("nomad_works", "wk_name_raid_training_ground");
      case Works.FAMILY_CABINS:
        return NomadStrings.get("nomad_works", "wk_name_family_cabins");
      case Works.GUILD_HALL:
        return NomadStrings.get("nomad_works", "wk_name_guild_hall");
      case Works.DAMAGE_CONTROL_ROOM:
        return NomadStrings.get("nomad_works", "wk_name_damage_control_room");
      case Works.GUNNERY_RANGE:
        return NomadStrings.get("nomad_works", "wk_name_gunnery_range");
      case Works.DORMITORY:
        return NomadStrings.get("nomad_works", "wk_name_dormitory");
      case Works.OFFICER_ACADEMY:
        return NomadStrings.get("nomad_works", "wk_name_officer_academy");
      case Works.HOSPITAL:
        return NomadStrings.get("nomad_works", "wk_name_hospital");
      case Works.WATCH_STATIONS:
        return NomadStrings.get("nomad_works", "wk_name_watch_stations");
      case Works.TINKERING_FACILITIES:
        return NomadStrings.get("nomad_works", "wk_name_tinkering_facilities");
      case Works.STORM_SHIELDING_WORKSHOP:
        return NomadStrings.get("nomad_works", "wk_name_storm_shielding_workshop");
      case Works.PASSENGER_TERMINAL:
        return NomadStrings.get("nomad_works", "wk_name_passenger_terminal");
      case Works.PURCHASING_OFFICE:
        return NomadStrings.get("nomad_works", "wk_name_purchasing_office");
      case Works.TUG_BAY:
        return NomadStrings.get("nomad_works", "wk_name_tug_bay");
      case Works.PANOPTICON:
        return NomadStrings.get("nomad_works", "wk_name_panopticon");
      case Works.RESTORATION_BAY:
        return NomadStrings.get("nomad_works", "wk_name_restoration_bay");
      case Works.DOMAIN_PARTS_WORKSHOP:
        return NomadStrings.get("nomad_works", "wk_name_domain_parts_workshop");
      case Works.LOBSTER_FARM:
        return NomadStrings.get("nomad_works", "wk_name_lobster_farm");
      case Works.GATE_FREIGHT_OFFICE:
        return NomadStrings.get("nomad_works", "wk_name_gate_freight_office");
      case Works.PATROL_SUPPLY_DEPOT:
        return NomadStrings.get("nomad_works", "wk_name_patrol_supply_depot");
      case Works.SCHOOL:
        return NomadStrings.get("nomad_works", "wk_name_school");
      case Works.CARGO_SHUTTLE_BAY:
        return NomadStrings.get("nomad_works", "wk_name_cargo_shuttle_bay");
      case Works.INSURANCE_OFFICE:
        return NomadStrings.get("nomad_works", "wk_name_insurance_office");
      case Works.ASSAULT_COURSE:
        return NomadStrings.get("nomad_works", "wk_name_assault_course");
      case Works.WELDING_SHOP:
        return NomadStrings.get("nomad_works", "wk_name_welding_shop");
      case Works.SHOPS:
        return NomadStrings.get("nomad_works", "wk_name_shops");
      case Works.THEATER:
        return NomadStrings.get("nomad_works", "wk_name_theater");
      case Works.LUXURY_QUARTERS:
        return NomadStrings.get("nomad_works", "wk_name_luxury_quarters");
      case Works.BASIC_NEEDS:
        return NomadStrings.get("nomad_works", "wk_name_basic_needs");
      default:
        return null;
    }
  }

  public String notLiveReason() {
    if (state.paused) {
      return NomadStrings.get("nomad_flotilla", "screen_not_live_paused");
    }
    if (state.detached) {
      return NomadStrings.get("nomad_flotilla", "screen_not_live_detached");
    }
    return "";
  }
}
