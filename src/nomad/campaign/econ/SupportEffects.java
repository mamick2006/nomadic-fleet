package nomad.campaign.econ;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import nomad.campaign.econ.model.WorkCatalog;
import nomad.campaign.econ.model.WorkSize;
import nomad.campaign.econ.model.WorkState;
import nomad.campaign.econ.model.Works;

public final class SupportEffects {

  private SupportEffects() {
  }

  public static final String SURVEY_MULT = "survey_mult";
  public static final String DETECTED_MULT = "detected_mult";
  public static final String SENSOR_PCT = "sensor_pct";
  public static final String REVEAL_LY = "reveal_ly";
  public static final String CR_RECOVERY_PCT = "cr_recovery_pct";
  public static final String REPAIR_PCT = "repair_pct";
  public static final String GROUND_RAID_PCT = "ground_raid_pct";
  public static final String MARINE_XP = "marine_xp";
  public static final String CR = "cr";
  public static final String EFFICIENCY = Works.PARAM_EFFICIENCY;
  public static final String SPARTAN_EFFICIENCY = Works.PARAM_SPARTAN_EFFICIENCY;
  public static final String REPAIR_MULT = "repair_mult";
  public static final String PD_RANGE = "pd_range";
  public static final String PD_DAMAGE_PCT = "pd_damage_pct";
  public static final String BERTHS = Works.PARAM_BERTHS;
  public static final String OFFICERS = "officers";
  public static final String CREW_RETURN_FRAC = "crew_return_frac";
  public static final String PPT_PCT = "ppt_pct";
  public static final String STAT_PCT = "stat_pct";
  public static final String RESIST_MULT = "resist_mult";
  public static final String STABILITY = "stability";
  public static final String INTAKE_PER_MONTH = "intake_per_month";
  public static final String RADIUS = "radius";
  public static final String REBATE_FRAC = "rebate_frac";
  public static final String BURN = "burn";
  public static final String CREDITS_PER_MONTH = "credits_per_month";
  public static final String ACCESSIBILITY = "accessibility";
  public static final String INSURE_FRAC = "insure_frac";
  public static final String RECOVER_MULT = "recover_mult";
  public static final String GROWTH_PER_YEAR = Works.PARAM_GROWTH_PER_YEAR;
  public static final String MARINE_JOIN_XP = "marine_join_xp";

  public static final class Levels {
    public float surveyMult = 1f;
    public float detectedMult = 1f;
    public float sensorPct;
    public float revealLy;
    public float yardsCrPct;
    public float yardsRepairPct;
    public float groundRaidPct;
    public float marineXp;
    public float officers;
    public float burn;
    public float prideCr;
    public float repairTimeMult = 1f;
    public float pdRange;
    public float pdDamagePct;
    public float pptPct;
    public float statPct;
    public float resistMult = 1f;
    public float crewReturnFrac;
    public float rebateFrac;
    public boolean harbor;
    public float harborStability;
    public float harborIntake;
    public float harborRadius;
    public float efficiency;
    public float spartanEfficiency;
    public float berths;
    public boolean restoreAnywhere;
    public boolean partsForged;
    public float gateCredits;
    public float patrolCredits;
    public boolean shuttles;
    public float shuttlesAccess;
    public float shuttlesRadius;
    public float insureFrac;
    public float recoverMult = 1f;
    public float schoolGrowth;
    public float marineJoinXp;

    public int shipDigest() {
      int h = 17;
      h = 31 * h + Float.floatToIntBits(prideCr);
      h = 31 * h + Float.floatToIntBits(repairTimeMult);
      h = 31 * h + Float.floatToIntBits(pdRange);
      h = 31 * h + Float.floatToIntBits(pdDamagePct);
      h = 31 * h + Float.floatToIntBits(pptPct);
      h = 31 * h + Float.floatToIntBits(statPct);
      h = 31 * h + Float.floatToIntBits(resistMult);
      h = 31 * h + Float.floatToIntBits(recoverMult);
      return h;
    }
  }

  public static final int OFF_SHIP_DIGEST = new Levels().shipDigest();

  public static boolean isOn(WorkState w) {
    return w != null && w.effectOn && w.isBuilt() && !w.mothballed && !w.suspended
        && !w.standingDown && !w.prereqUnmet;
  }

  public static WorkSize onRow(List<WorkState> works, WorkCatalog catalog, String id) {
    if (works == null || catalog == null || id == null) {
      return null;
    }
    for (WorkState w : works) {
      if (w != null && id.equals(w.id)) {
        return isOn(w) ? catalog.size(w.id, w.size) : null;
      }
    }
    return null;
  }

  public static List<String> rowsToClear(Collection<String> writtenDown,
      Collection<String> inTables) {
    Set<String> ids = new LinkedHashSet<String>();
    if (writtenDown != null) {
      ids.addAll(writtenDown);
    }
    if (inTables != null) {
      ids.addAll(inTables);
    }
    ids.remove(null);
    return new ArrayList<String>(ids);
  }

  public static Levels resolve(List<WorkState> works, WorkCatalog catalog,
      EconConstants.FloatSource settings) {
    Levels l = new Levels();
    if (works == null || catalog == null) {
      return l;
    }
    for (WorkState w : works) {
      if (!isOn(w)) {
        continue;
      }
      WorkSize row = catalog.size(w.id, w.size);
      if (row != null) {
        add(l, w.id, row, settings);
      }
    }
    return l;
  }

  public static Levels forRow(String id, WorkSize row, EconConstants.FloatSource settings) {
    Levels l = new Levels();
    if (id != null && row != null) {
      add(l, id, row, settings);
    }
    return l;
  }

  private static void add(Levels l, String id, WorkSize row, EconConstants.FloatSource s) {
    switch (id) {
      case Works.SURVEY_OFFICE:
        l.surveyMult = row.param(SURVEY_MULT, 1f);
        break;
      case Works.STEALTH_CONTROL_ROOM:
        l.detectedMult = row.param(DETECTED_MULT, 1f);
        break;
      case Works.SENSOR_STATION:
        l.sensorPct = row.param(SENSOR_PCT, 0f);
        break;
      case Works.SLIPSTREAM_CHART_ROOM:
        l.revealLy = row.param(REVEAL_LY, 0f);
        break;
      case Works.REPAIR_YARD:
        l.yardsCrPct = row.param(CR_RECOVERY_PCT, 0f);
        l.yardsRepairPct = row.param(REPAIR_PCT, 0f);
        break;
      case Works.RAID_TRAINING_GROUND:
        l.groundRaidPct = row.param(GROUND_RAID_PCT, 0f);
        l.marineXp = row.param(MARINE_XP, f(s, "nomad_work_raid_training_ground_marine_xp_monthly", 200f));
        break;
      case Works.FAMILY_CABINS:
        l.prideCr = row.param(CR, 0f);
        break;
      case Works.GUILD_HALL:
        l.efficiency = row.param(EFFICIENCY, 0f);
        break;
      case Works.DAMAGE_CONTROL_ROOM:
        l.repairTimeMult = row.param(REPAIR_MULT, 1f);
        break;
      case Works.GUNNERY_RANGE:
        l.pdRange = row.param(PD_RANGE, 0f);
        l.pdDamagePct = row.param(PD_DAMAGE_PCT, 0f);
        break;
      case Works.DORMITORY:
        l.berths = row.param(BERTHS, f(s, "nomad_work_dormitory_mult", 3f) * row.space);
        break;
      case Works.OFFICER_ACADEMY:
        l.officers = row.param(OFFICERS, f(s, "nomad_work_officer_academy_bonus", 1f));
        break;
      case Works.HOSPITAL:
        l.crewReturnFrac = row.param(CREW_RETURN_FRAC,
            f(s, "nomad_work_hospital_crew_return_frac", 0.25f));
        break;
      case Works.WATCH_STATIONS:
        l.pptPct = row.param(PPT_PCT, f(s, "nomad_work_watch_stations_ppt_pct", 25f));
        break;
      case Works.TINKERING_FACILITIES:
        l.statPct = row.param(STAT_PCT, f(s, "nomad_work_tinkering_facilities_stat_pct", 5f));
        break;
      case Works.STORM_SHIELDING_WORKSHOP:
        l.resistMult = row.param(RESIST_MULT, f(s, "nomad_work_storm_shielding_workshop_resist_mult", 0.5f));
        break;
      case Works.PASSENGER_TERMINAL:
        l.harbor = true;
        l.harborStability = row.param(STABILITY, f(s, "nomad_work_passenger_terminal_stability", 2f));
        l.harborIntake = row.param(INTAKE_PER_MONTH,
            f(s, "nomad_work_passenger_terminal_intake_per_month", 3f));
        l.harborRadius = row.param(RADIUS, f(s, "nomad_work_passenger_terminal_radius", 500f));
        break;
      case Works.PURCHASING_OFFICE:
        l.rebateFrac = row.param(REBATE_FRAC, f(s, "nomad_work_purchasing_office_rebate_frac", 0.02f));
        break;
      case Works.TUG_BAY:
        l.burn = row.param(BURN, f(s, "nomad_work_tug_bay_burn", 1f));
        break;
      case Works.PANOPTICON:
        l.spartanEfficiency = row.param(SPARTAN_EFFICIENCY, 0f);
        break;
      case Works.RESTORATION_BAY:
        l.restoreAnywhere = true;
        break;
      case Works.DOMAIN_PARTS_WORKSHOP:
        l.partsForged = true;
        break;
      case Works.GATE_FREIGHT_OFFICE:
        l.gateCredits = row.param(CREDITS_PER_MONTH, 0f);
        break;
      case Works.PATROL_SUPPLY_DEPOT:
        l.patrolCredits = row.param(CREDITS_PER_MONTH, 0f);
        break;
      case Works.SCHOOL:
        l.schoolGrowth = row.param(GROWTH_PER_YEAR, 0f);
        break;
      case Works.CARGO_SHUTTLE_BAY:
        l.shuttles = true;
        l.shuttlesAccess = row.param(ACCESSIBILITY, 0f);
        l.shuttlesRadius = f(s, "nomad_work_passenger_terminal_radius", 500f);
        break;
      case Works.INSURANCE_OFFICE:
        l.insureFrac = row.param(INSURE_FRAC, 0f);
        break;
      case Works.ASSAULT_COURSE:
        l.marineJoinXp = row.param(MARINE_JOIN_XP, 0f);
        break;
      case Works.WELDING_SHOP:
        l.recoverMult = row.param(RECOVER_MULT, 1f);
        break;
      default:
        break;
    }
  }

  private static float f(EconConstants.FloatSource s, String key, float fallback) {
    return s == null ? fallback : s.get(key, fallback);
  }
}
