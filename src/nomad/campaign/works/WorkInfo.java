package nomad.campaign.works;

import nomad.NomadStrings;
import nomad.campaign.NomadSettings;
import nomad.campaign.econ.EconConstants;
import nomad.campaign.econ.SupportEffects;
import nomad.campaign.econ.model.Requirement;
import nomad.campaign.econ.model.WorkDef;
import nomad.campaign.econ.model.WorkSize;
import nomad.campaign.econ.model.Works;

public final class WorkInfo {

  private WorkInfo() {
  }

  public static final String CAT_ECONOMY = "Economy";
  public static final String CAT_CAPACITY = "Capacity";
  public static final String CAT_FLEET = "Fleet";
  public static final String CAT_COMBAT = "Combat";
  public static final String CAT_EVENT = "Fortune";

  public static String illustrationKey(String workId) {
    WorkDef row = row(workId);
    if (row != null && !row.illustration.isEmpty()) {
      return row.illustration;
    }
    return "nomad_work_illus_" + workId;
  }

  public static String name(String id) {
    switch (id) {
      case Works.HYDROPONIC_FARM: return "Hydroponic Farm";
      case Works.SUPPLIES_FACTORY: return "Supplies Factory";
      case Works.FUEL_PLANT: return "Fuel Plant";
      case Works.GUILD_HALL: return "Guild Hall";
      case Works.DORMITORY: return "Dormitory";
      case Works.SURVEY_OFFICE: return "Survey Office";
      case Works.STEALTH_CONTROL_ROOM: return "Stealth Control Room";
      case Works.SENSOR_STATION: return "Sensor Station";
      case Works.SLIPSTREAM_CHART_ROOM: return "Slipstream Chart Room";
      case Works.TUG_BAY: return "Tug Bay";
      case Works.OFFICER_ACADEMY: return "Officer Academy";
      case Works.GUNNERY_RANGE: return "Gunnery Range";
      case Works.DAMAGE_CONTROL_ROOM: return "Damage Control Room";
      case Works.FAMILY_CABINS: return "Family Cabins";
      case Works.REPAIR_YARD: return "Repair Yard";
      case Works.STORM_SHIELDING_WORKSHOP: return "Storm Shielding Workshop";
      case Works.HOSPITAL: return "Hospital";
      case Works.RAID_TRAINING_GROUND: return "Raid Training Ground";
      case Works.PASSENGER_TERMINAL: return "Passenger Terminal";
      case Works.PURCHASING_OFFICE: return "Purchasing Office";
      case Works.WATCH_STATIONS: return "Watch Stations";
      case Works.TINKERING_FACILITIES: return "Tinkering Facilities";
      case Works.PANOPTICON: return "Panopticon";
      case Works.RESTORATION_BAY: return "Restoration Bay";
      case Works.DOMAIN_PARTS_WORKSHOP: return "Domain Parts Workshop";
      case Works.REFINERY: return "Refinery";
      case Works.LOBSTER_FARM: return "Lobster Farm";
      case Works.GATE_FREIGHT_OFFICE: return "Gate Freight Office";
      case Works.PATROL_SUPPLY_DEPOT: return "Patrol Supply Depot";
      case Works.SCHOOL: return "School";
      case Works.CARGO_SHUTTLE_BAY: return "Cargo Shuttle Bay";
      case Works.INSURANCE_OFFICE: return "Insurance Office";
      case Works.ASSAULT_COURSE: return "Assault Course";
      case Works.WELDING_SHOP: return "Welding Shop";
      case Works.SHOPS: return "Shops";
      case Works.THEATER: return "Theater";
      case Works.LUXURY_QUARTERS: return "Luxury Quarters";
      default: break;
    }
    WorkDef row = row(id);
    return row != null && !row.name.isEmpty() ? row.name : humanize(id);
  }

  private static WorkDef row(String id) {
    return WorkCatalogTable.read().work(id);
  }

  public static String category(String id) {
    if (isForeignFleetScoped(id)) {
      return CAT_FLEET;
    }
    WorkDef row = row(id);
    return row != null && !row.category.isEmpty() ? row.category : CAT_EVENT;
  }

  private static boolean isForeignFleetScoped(String id) {
    WorkDef row = row(id);
    return row != null && !row.ownRow && row.isFleetScoped();
  }

  public static final String TYPE_ECONOMIC = "Economic";
  public static final String TYPE_FLEET = "Fleet";
  public static final String TYPE_FLOTILLA = "Flotilla";
  public static final String TYPE_OTHER = "Other";

  public static String type(String id) {
    String cat = category(id);
    if (CAT_ECONOMY.equals(cat) || CAT_CAPACITY.equals(cat)) {
      return TYPE_ECONOMIC;
    }
    if (CAT_FLEET.equals(cat)) {
      return TYPE_FLEET;
    }
    if (CAT_COMBAT.equals(cat)) {
      return TYPE_FLOTILLA;
    }
    return TYPE_OTHER;
  }

  public static int typeRank(String id) {
    String t = type(id);
    if (TYPE_ECONOMIC.equals(t)) {
      return 0;
    }
    if (TYPE_FLEET.equals(t)) {
      return 1;
    }
    if (TYPE_FLOTILLA.equals(t)) {
      return 2;
    }
    return 3;
  }

  private static final EconConstants.FloatSource SETTINGS = new EconConstants.FloatSource() {
    @Override
    public float get(String key, float fallback) {
      return NomadSettings.getFloat(key, fallback);
    }
  };

  public static String effectLine(String id, int size) {
    WorkSize at = WorkCatalogTable.read().size(id, size);
    if (at != null) {
      String amenity = amenityLine(id, at);
      if (amenity != null) {
        return amenity;
      }
      String line = supportLine(id, SupportEffects.forRow(id, at, SETTINGS), false);
      if (line != null) {
        return line;
      }
    }
    WorkDef row = row(id);
    return row != null && !row.effect.isEmpty() ? row.effect : GENERIC;
  }

  private static final String GENERIC = "A Flotilla conversion.";

  public static String ownLine(String id, int size) {
    WorkDef row = row(id);
    if (row == null || !row.ownRow || row.isProduction()
        || WorkCatalogTable.read().size(id, size) == null) {
      return null;
    }
    String line = effectLine(id, size);
    return line.isEmpty() || line.equals(row.effect) || GENERIC.equals(line) ? null : line;
  }

  public static String cardLabel(String id, int size) {
    if (ownLine(id, size) == null) {
      return null;
    }
    WorkSize at = WorkCatalogTable.read().size(id, size);
    return supportLine(id, SupportEffects.forRow(id, at, SETTINGS), true);
  }

  public static float tierRelief() {
    return NomadSettings.getFloat("nomad_bn_tier_relief", 0.01f);
  }

  private static String amenityLine(String id, WorkSize at) {
    if (!Works.AMENITIES.contains(id)) {
      return null;
    }
    String less = pct(at.param(Requirement.PARAM_AMENITY, 0f) * tierRelief());
    switch (id) {
      case Works.SHOPS:
        return card("wk_card_shops", less);
      case Works.THEATER:
        return card("wk_card_theater", less);
      default:
        return card("wk_card_luxury_quarters", less);
    }
  }

  private static String supportLine(String id, SupportEffects.Levels l, boolean label) {
    switch (id) {
      case Works.SURVEY_OFFICE:
        return card("wk_card_survey_office", pct(1f - l.surveyMult));
      case Works.STEALTH_CONTROL_ROOM:
        return card("wk_card_stealth_control_room", pct(1f - l.detectedMult));
      case Works.SENSOR_STATION:
        return card("wk_card_sensor_station", n(l.sensorPct));
      case Works.SLIPSTREAM_CHART_ROOM:
        return card("wk_card_slipstream_chart_room", n(l.revealLy));
      case Works.REPAIR_YARD:
        return card("wk_card_repair_yard", n(l.yardsCrPct), n(l.yardsRepairPct));
      case Works.RAID_TRAINING_GROUND:
        return card("wk_card_raid_training_ground", n(l.groundRaidPct));
      case Works.FAMILY_CABINS:
        return card("wk_card_family_cabins", pct(l.prideCr));
      case Works.GUILD_HALL:
        return card("wk_card_guild_hall", pct(l.efficiency));
      case Works.DAMAGE_CONTROL_ROOM:
        return card("wk_card_damage_control_room", pct(1f - l.repairTimeMult));
      case Works.GUNNERY_RANGE:
        return card("wk_card_gunnery_range", n(l.pdRange), n(l.pdDamagePct));
      case Works.DORMITORY:
        return card("wk_card_dormitory", n(l.berths));
      case Works.OFFICER_ACADEMY:
        return card(key(label, "officer_academy"), n(l.officers));
      case Works.HOSPITAL:
        return card(key(label, "hospital"), pct(l.crewReturnFrac));
      case Works.WATCH_STATIONS:
        return card(key(label, "watch_stations"), n(l.pptPct));
      case Works.TINKERING_FACILITIES:
        return card(key(label, "tinkering_facilities"), n(l.statPct));
      case Works.STORM_SHIELDING_WORKSHOP:
        return card(key(label, "storm_shielding_workshop"), pct(1f - l.resistMult));
      case Works.PASSENGER_TERMINAL:
        return card(key(label, "passenger_terminal"), n(l.harborStability));
      case Works.PURCHASING_OFFICE:
        return card(key(label, "purchasing_office"), pct(l.rebateFrac));
      case Works.TUG_BAY:
        return card(key(label, "tug_bay"), n(l.burn));
      case Works.PANOPTICON:
        return card("wk_card_panopticon", pct(l.spartanEfficiency));
      case Works.RESTORATION_BAY:
        return card(key(label, "restoration_bay"));
      case Works.DOMAIN_PARTS_WORKSHOP:
        return card(key(label, "domain_parts_workshop"));
      case Works.GATE_FREIGHT_OFFICE:
        return card("wk_card_gate_freight_office", n(l.gateCredits));
      case Works.PATROL_SUPPLY_DEPOT:
        return card("wk_card_patrol_supply_depot", n(l.patrolCredits));
      case Works.SCHOOL:
        return card("wk_card_school", pct(l.schoolGrowth));
      case Works.CARGO_SHUTTLE_BAY:
        return card("wk_card_cargo_shuttle_bay", pct(l.shuttlesAccess));
      case Works.INSURANCE_OFFICE:
        return nomad.campaign.NomadSurvival.isSurvivalCampaign()
            ? card("wk_card_insurance_office_survival", pct(l.insureFrac))
            : card("wk_card_insurance_office", pct(l.insureFrac));
      case Works.ASSAULT_COURSE:
        return card("wk_card_assault_course", pct(l.marineJoinXp));
      case Works.WELDING_SHOP:
        return card("wk_card_welding_shop", pct(1f - l.recoverMult));
      default:
        return null;
    }
  }

  private static String key(boolean label, String id) {
    return (label ? "wk_label_" : "wk_card_") + id;
  }

  private static String card(String key, Object... args) {
    return NomadStrings.get("nomad_works", key, args);
  }

  private static String n(float v) {
    return "" + Math.round(v);
  }

  private static String pct(float frac) {
    return Math.round(frac * 100f) + "%";
  }

  private static String humanize(String id) {
    StringBuilder sb = new StringBuilder();
    for (String part : id.split("_")) {
      if (part.isEmpty()) {
        continue;
      }
      sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(' ');
    }
    return sb.toString().trim();
  }
}
