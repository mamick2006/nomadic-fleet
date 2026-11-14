package nomad.campaign.econ;

import java.util.Arrays;
import java.util.List;

public final class Work {

  private Work() {
  }

  public static final String MOD_ID = "nomadic_fleet";

  public static final String TAG_ITEM = "nomad_colony_item";

  public static final String HYDROPONIC_FARM = "hydroponic_farm";
  public static final String SUPPLIES_FACTORY = "supplies_factory";
  public static final String FUEL_PLANT = "fuel_plant";
  public static final String GUILD_HALL = "guild_hall";
  public static final String CRYOSANCTUM = "cryosanctum";

  public static final String BONDED_HOLD = "bonded_hold";
  public static final String DORMITORY = "dormitory";
  public static final String SURVEY_OFFICE = "survey_office";
  public static final String STEALTH_CONTROL_ROOM = "stealth_control_room";
  public static final String SENSOR_STATION = "sensor_station";
  public static final String SLIPSTREAM_CHART_ROOM = "slipstream_chart_room";
  public static final String TUG_BAY = "tug_bay";
  public static final String OFFICER_ACADEMY = "officer_academy";
  public static final String GUNNERY_RANGE = "gunnery_range";
  public static final String DAMAGE_CONTROL_ROOM = "damage_control_room";
  public static final String FAMILY_CABINS = "family_cabins";
  public static final String REPAIR_YARD = "repair_yard";
  public static final String STORM_SHIELDING_WORKSHOP = "storm_shielding_workshop";
  public static final String HOSPITAL = "hospital";
  public static final String RAID_TRAINING_GROUND = "raid_training_ground";
  public static final String PASSENGER_TERMINAL = "passenger_terminal";
  public static final String PURCHASING_OFFICE = "purchasing_office";
  public static final String FOUNDRY_ANNEX = "foundry_annex";
  public static final String WATCH_STATIONS = "watch_stations";
  public static final String TINKERING_FACILITIES = "tinkering_facilities";
  public static final String REFINERY = "refinery";

  public static final List<String> CORE = Arrays.asList(
      HYDROPONIC_FARM, SUPPLIES_FACTORY, FUEL_PLANT, GUILD_HALL);

  public static final List<String> ALL = Arrays.asList(
      HYDROPONIC_FARM, SUPPLIES_FACTORY, FUEL_PLANT, GUILD_HALL,
      BONDED_HOLD, DORMITORY,
      SURVEY_OFFICE, STEALTH_CONTROL_ROOM, SENSOR_STATION, SLIPSTREAM_CHART_ROOM, TUG_BAY, OFFICER_ACADEMY,
      GUNNERY_RANGE, DAMAGE_CONTROL_ROOM, FAMILY_CABINS, REPAIR_YARD,
      STORM_SHIELDING_WORKSHOP, HOSPITAL, RAID_TRAINING_GROUND,
      PASSENGER_TERMINAL, PURCHASING_OFFICE,
      FOUNDRY_ANNEX, WATCH_STATIONS, TINKERING_FACILITIES,
      REFINERY);

  public static final List<String> NEW_ROSTER = Arrays.asList(
      BONDED_HOLD, DORMITORY,
      SURVEY_OFFICE, STEALTH_CONTROL_ROOM, SENSOR_STATION, SLIPSTREAM_CHART_ROOM, TUG_BAY, OFFICER_ACADEMY,
      GUNNERY_RANGE, DAMAGE_CONTROL_ROOM, FAMILY_CABINS, REPAIR_YARD,
      STORM_SHIELDING_WORKSHOP, HOSPITAL, RAID_TRAINING_GROUND,
      PASSENGER_TERMINAL, PURCHASING_OFFICE,
      FOUNDRY_ANNEX, WATCH_STATIONS, TINKERING_FACILITIES,
      REFINERY);

  public static final String SOIL_NANITES = "soil_nanites";
  public static final String CATALYTIC_CORE = "catalytic_core";
  public static final String SYNCHROTRON = "synchrotron";
  public static final String CORRUPTED_NANOFORGE = "corrupted_nanoforge";
  public static final String PRISTINE_NANOFORGE = "pristine_nanoforge";
  public static final String BIOFACTORY_EMBRYO = "biofactory_embryo";
  public static final String FULLERENE_SPOOL = "fullerene_spool";
  public static final String DEALMAKER_HOLOSUITE = "dealmaker_holosuite";
  public static final String ORBITAL_FUSION_LAMP = "orbital_fusion_lamp";
  public static final String MANTLE_BORE = "mantle_bore";
  public static final String PLASMA_DYNAMO = "plasma_dynamo";
  public static final String CRYOARITHMETIC_ENGINE = "cryoarithmetic_engine";
  public static final String CORONAL_PORTAL = "coronal_portal";
  public static final String DRONE_REPLICATOR = "drone_replicator";
  public static final String ALPHA_CORE = "alpha_core";
  public static final String BETA_CORE = "beta_core";
  public static final String GAMMA_CORE = "gamma_core";

  public static final List<String> ITEMS = Arrays.asList(
      SOIL_NANITES, BIOFACTORY_EMBRYO, SYNCHROTRON, CATALYTIC_CORE,
      CORRUPTED_NANOFORGE, PRISTINE_NANOFORGE, FULLERENE_SPOOL, ORBITAL_FUSION_LAMP,
      MANTLE_BORE, PLASMA_DYNAMO, CRYOARITHMETIC_ENGINE, CORONAL_PORTAL,
      DEALMAKER_HOLOSUITE, DRONE_REPLICATOR,
      ALPHA_CORE, BETA_CORE, GAMMA_CORE);

  public static final String CQ_TALLY_BOARD = "nomad_tally_board";
}
