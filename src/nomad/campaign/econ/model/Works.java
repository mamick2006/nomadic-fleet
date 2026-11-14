package nomad.campaign.econ.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class Works {

  private Works() {
  }

  public static final String MOD_ID = "nomadic_fleet";

  public static final String HYDROPONIC_FARM = "hydroponic_farm";
  public static final String DOMESTIC_GOODS_PLANT = "domestic_goods_plant";
  public static final String SUPPLIES_FACTORY = "supplies_factory";
  public static final String LUXURY_GOODS_FACTORY = "luxury_goods_factory";
  public static final String FUEL_PLANT = "fuel_plant";
  public static final String REFINERY = "refinery";
  public static final String MACHINERY_FACTORY = "machinery_factory";
  public static final String MINING_RIG = "mining_rig";
  public static final String FOUNDRY = "foundry";
  public static final String BROKERAGE = "brokerage";
  public static final String MILITARY_FABRICATOR = "military_fabricator";
  public static final String DRUG_LAB = "drug_lab";
  public static final String ORGAN_HARVESTER = "organ_harvester";
  public static final String MARINE_BARRACKS = "marine_barracks";
  public static final String CLONING_VATS = "cloning_vats";
  public static final String LOBSTER_FARM = "lobster_farm";

  public static final String SURVEY_OFFICE = "survey_office";
  public static final String STEALTH_CONTROL_ROOM = "stealth_control_room";
  public static final String SENSOR_STATION = "sensor_station";
  public static final String SLIPSTREAM_CHART_ROOM = "slipstream_chart_room";
  public static final String REPAIR_YARD = "repair_yard";
  public static final String RAID_TRAINING_GROUND = "raid_training_ground";
  public static final String FAMILY_CABINS = "family_cabins";
  public static final String GUILD_HALL = "guild_hall";
  public static final String DAMAGE_CONTROL_ROOM = "damage_control_room";
  public static final String GUNNERY_RANGE = "gunnery_range";

  public static final String DORMITORY = "dormitory";
  public static final String OFFICER_ACADEMY = "officer_academy";
  public static final String HOSPITAL = "hospital";
  public static final String WATCH_STATIONS = "watch_stations";
  public static final String TINKERING_FACILITIES = "tinkering_facilities";
  public static final String STORM_SHIELDING_WORKSHOP = "storm_shielding_workshop";
  public static final String PASSENGER_TERMINAL = "passenger_terminal";
  public static final String PURCHASING_OFFICE = "purchasing_office";
  public static final String TUG_BAY = "tug_bay";
  public static final String PANOPTICON = "panopticon";
  public static final String RESTORATION_BAY = "restoration_bay";
  public static final String DOMAIN_PARTS_WORKSHOP = "domain_parts_workshop";
  public static final String GATE_FREIGHT_OFFICE = "gate_freight_office";
  public static final String PATROL_SUPPLY_DEPOT = "patrol_supply_depot";
  public static final String SCHOOL = "school";
  public static final String CARGO_SHUTTLE_BAY = "cargo_shuttle_bay";
  public static final String INSURANCE_OFFICE = "insurance_office";
  public static final String ASSAULT_COURSE = "assault_course";
  public static final String WELDING_SHOP = "welding_shop";
  public static final String SHOPS = "shops";
  public static final String THEATER = "theater";
  public static final String LUXURY_QUARTERS = "luxury_quarters";

  public static final List<String> PRODUCTION = Collections.unmodifiableList(Arrays.asList(
      HYDROPONIC_FARM, DOMESTIC_GOODS_PLANT, SUPPLIES_FACTORY, LUXURY_GOODS_FACTORY, FUEL_PLANT, REFINERY, MACHINERY_FACTORY,
      MINING_RIG, FOUNDRY, BROKERAGE, MILITARY_FABRICATOR, DRUG_LAB, ORGAN_HARVESTER,
      CLONING_VATS, MARINE_BARRACKS, LOBSTER_FARM));

  public static final List<String> SIZED_SUPPORT = Collections.unmodifiableList(Arrays.asList(
      SURVEY_OFFICE, STEALTH_CONTROL_ROOM, SENSOR_STATION, SLIPSTREAM_CHART_ROOM, REPAIR_YARD,
      RAID_TRAINING_GROUND, FAMILY_CABINS, GUILD_HALL, DAMAGE_CONTROL_ROOM, GUNNERY_RANGE));

  public static final List<String> SINGLE_SUPPORT = Collections.unmodifiableList(Arrays.asList(
      DORMITORY, OFFICER_ACADEMY, HOSPITAL, WATCH_STATIONS, TINKERING_FACILITIES,
      STORM_SHIELDING_WORKSHOP, PASSENGER_TERMINAL, PURCHASING_OFFICE, TUG_BAY, PANOPTICON, RESTORATION_BAY,
      DOMAIN_PARTS_WORKSHOP, GATE_FREIGHT_OFFICE, PATROL_SUPPLY_DEPOT, SCHOOL, CARGO_SHUTTLE_BAY, INSURANCE_OFFICE,
      ASSAULT_COURSE, WELDING_SHOP, SHOPS, THEATER, LUXURY_QUARTERS));

  public static final List<String> AMENITIES = Collections.unmodifiableList(Arrays.asList(
      SHOPS, THEATER, LUXURY_QUARTERS));

  public static final List<String> ALL = Collections.unmodifiableList(Arrays.asList(
      HYDROPONIC_FARM, DOMESTIC_GOODS_PLANT, SUPPLIES_FACTORY, LUXURY_GOODS_FACTORY, FUEL_PLANT, REFINERY, MACHINERY_FACTORY,
      MINING_RIG, FOUNDRY, BROKERAGE, MILITARY_FABRICATOR, DRUG_LAB, ORGAN_HARVESTER,
      CLONING_VATS, MARINE_BARRACKS, LOBSTER_FARM,
      SURVEY_OFFICE, STEALTH_CONTROL_ROOM, SENSOR_STATION, SLIPSTREAM_CHART_ROOM, REPAIR_YARD,
      RAID_TRAINING_GROUND, FAMILY_CABINS, GUILD_HALL, DAMAGE_CONTROL_ROOM, GUNNERY_RANGE,
      DORMITORY, OFFICER_ACADEMY, HOSPITAL, WATCH_STATIONS, TINKERING_FACILITIES,
      STORM_SHIELDING_WORKSHOP, PASSENGER_TERMINAL, PURCHASING_OFFICE, TUG_BAY, PANOPTICON, RESTORATION_BAY,
      DOMAIN_PARTS_WORKSHOP, GATE_FREIGHT_OFFICE, PATROL_SUPPLY_DEPOT, SCHOOL, CARGO_SHUTTLE_BAY, INSURANCE_OFFICE,
      ASSAULT_COURSE, WELDING_SHOP, SHOPS, THEATER, LUXURY_QUARTERS));

  public static final String BASIC_NEEDS = "basic_needs";

  public static final String POINTS = "points";
  public static final String CREDITS = "credits";
  public static final String MARINES = "marines";

  public static final String RESIDENTS = "residents";

  public static final String PARTS = "parts";

  public static final String PARAM_SALE_UNITS = "sale_units";
  public static final String PARAM_EFFICIENCY = "efficiency";
  public static final String PARAM_SPARTAN_EFFICIENCY = "spartan_efficiency";
  public static final String PARAM_BERTHS = "berths";
  public static final String PARAM_GROWTH_PER_YEAR = "growth_per_year";
}
