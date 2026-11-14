package nomad.campaign.econ;

import java.util.Arrays;
import java.util.List;

public final class Commod {

  private Commod() {
  }

  public static final String FOOD = "food";
  public static final String ORGANICS = "organics";
  public static final String DOMESTIC_GOODS = "domestic_goods";
  public static final String LUXURY_GOODS = "luxury_goods";
  public static final String SUPPLIES = "supplies";
  public static final String FUEL = "fuel";
  public static final String METALS = "metals";
  public static final String RARE_METALS = "rare_metals";
  public static final String ORE = "ore";
  public static final String RARE_ORE = "rare_ore";
  public static final String VOLATILES = "volatiles";
  public static final String HEAVY_MACHINERY = "heavy_machinery";
  public static final String HAND_WEAPONS = "hand_weapons";
  public static final String DRUGS = "drugs";
  public static final String ORGANS = "organs";

  public static final String MINING = "nomad_mining";
  public static final String AUTOFORGE = "nomad_autoforge";
  public static final String CREDITS = "nomad_credits";

  public static final List<String> NEEDS = Arrays.asList(FOOD, DOMESTIC_GOODS, SUPPLIES);

  public static final String OPTIONAL_NEED = LUXURY_GOODS;

  public static final List<String> PRODUCIBLE = Arrays.asList(
      FOOD, DOMESTIC_GOODS, SUPPLIES, LUXURY_GOODS, FUEL, METALS, RARE_METALS, HEAVY_MACHINERY,
      HAND_WEAPONS, DRUGS, ORGANS);

  public static final List<String> ALLOCATABLE = Arrays.asList(
      FOOD, DOMESTIC_GOODS, SUPPLIES, LUXURY_GOODS, FUEL, METALS, RARE_METALS, HEAVY_MACHINERY,
      MINING, AUTOFORGE, CREDITS);

  public static final List<String> MINEABLE = Arrays.asList(ORGANICS, ORE, RARE_ORE, VOLATILES);

  public static final List<String> TRACKED = Arrays.asList(
      FOOD, ORGANICS, DOMESTIC_GOODS, LUXURY_GOODS, SUPPLIES, FUEL,
      METALS, RARE_METALS, ORE, RARE_ORE, VOLATILES, HEAVY_MACHINERY);

  public static final List<String> LISTED_IN_USE = Arrays.asList(HAND_WEAPONS, DRUGS, ORGANS);

  public static final List<String> STORED = Arrays.asList(
      FOOD, ORGANICS, DOMESTIC_GOODS, LUXURY_GOODS, SUPPLIES, FUEL,
      METALS, RARE_METALS, ORE, RARE_ORE, VOLATILES, HEAVY_MACHINERY,
      HAND_WEAPONS, DRUGS, ORGANS);
}
