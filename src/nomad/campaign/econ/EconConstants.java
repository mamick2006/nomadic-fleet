package nomad.campaign.econ;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import nomad.campaign.econ.model.WorkCatalog;
import nomad.campaign.econ.model.WorkDef;
import nomad.campaign.econ.model.Works;

public class EconConstants {

  public interface FloatSource {
    float get(String key, float fallback);
  }

  public float itemEffectMultSurvival, itemEffectMultNormalDifficulty;
  public float normalDifficultyEfficiencyMult;
  public float storyDifficultyEfficiencyMult;
  public float storyDifficultyStorageSpaceMult;
  public float efficiencyWasteFloor;

  public float pausedDecay;

  public float crewExtractReserve;
  public float marinesTargetDefault;
  public float creditsSalePriceFrac;
  public float creditsSaleSurvivalGap;

  public boolean survivalCampaign;
  public boolean survivalAdjusted;
  private final float[] hullValueMult = { 1f, 1f, 1f, 1f };
  private final float[] codexHullValueMult = { 1f, 1f, 1f, 1f };
  private float hullDmodValueMult = 1f;
  private float weaponSpecMult = 1f, wingSpecMult = 1f;

  public final Map<String, Float> basePrice = new LinkedHashMap<String, Float>();

  public float autoforgeDmodBase, autoforgeDmodCorrupted, autoforgeDmodPristine;

  public float dormitoryMult;

  public final Map<String, WorkDef> workPlugins = new LinkedHashMap<String, WorkDef>();

  public float workBuildDays, workScrapRefund;

  public float itemSoilNanitesFood;
  public float itemCatalyticCoreOreInput;
  public float itemSynchrotronFuelInput;
  public float itemCorruptedNanoforgeSupplies;
  public float itemPristineNanoforgeSupplies;
  public float itemCorruptedNanoforgeAutoforge, itemPristineNanoforgeAutoforge;
  public float itemPristineNanoforgeEfficiency;
  public float itemBiofactoryFood, itemBiofactoryLuxury;
  public float itemBiofactoryHarvesterResidents, itemBiofactoryVats;
  public float itemFullereneSpoolEfficiency;
  public float itemOrbitalFusionLampBerths;
  public float itemMantleBoreMining, itemPlasmaDynamoVolatiles;
  public float itemCryoarithmeticAutoforgeSpeed, itemDealmakerAutoforgeCost;
  public float itemCryoarithmeticEfficiency;
  public float itemCoronalPortalEfficiency;
  public float itemDealmakerCredits;
  public float itemDroneReplicatorStaff;
  public float itemDroneReplicatorArmaments;
  public float itemCoreStaffAlpha, itemCoreStaffBeta, itemCoreStaffGamma;
  public float cqTallyBoardEfficiency;

  public static final String CQ_BOARD_KEY = "nomad_cq_tally_board_efficiency";
  public static final float CQ_BOARD_DEFAULT = 0.01f;
  public final Map<String, Float> itemEfficiency = new LinkedHashMap<String, Float>();
  public final Map<String, ItemRow> itemPlugins = new LinkedHashMap<String, ItemRow>();

  public float foundingResidents, foundingBerthFrac;
  public float foundingStoreTarget;
  public float foundingSpacePerGood;
  public float pickupMin, pickupBase, pickupSizeCoef, pickupPopFrac, pickupCooldownDays;

  public static EconConstants build(FloatSource src, List<ItemRow> itemRows) {
    EconConstants k = new EconConstants();

    k.itemEffectMultSurvival = src.get("nomad_item_effect_mult_survival", 1.0f);
    k.itemEffectMultNormalDifficulty = src.get("nomad_item_effect_mult_normal_difficulty", 1.0f);
    k.normalDifficultyEfficiencyMult = src.get("nomad_normal_difficulty_efficiency_mult", 1.25f);
    k.storyDifficultyEfficiencyMult = src.get("nomad_story_difficulty_efficiency_mult", 1.25f);
    k.storyDifficultyStorageSpaceMult = src.get("nomad_story_difficulty_storage_space_mult", 0.5f);
    k.efficiencyWasteFloor = src.get("nomad_efficiency_waste_floor", 0.75f);

    k.pausedDecay = src.get("nomad_paused_decay", 0.30f);

    k.creditsSalePriceFrac = src.get("nomad_credits_sale_price_frac", 0.60f);
    k.creditsSaleSurvivalGap = src.get("nomad_credits_sale_survival_gap", 0.10f);
    k.crewExtractReserve = src.get("nomad_crew_extract_reserve", 10f);
    k.marinesTargetDefault = src.get("nomad_marines_target_default", 100f);

    k.basePrice.put(Commod.FOOD, src.get("nomad_price_food", 20f));
    k.basePrice.put(Commod.ORGANICS, src.get("nomad_price_organics", 30f));
    k.basePrice.put(Commod.DOMESTIC_GOODS, src.get("nomad_price_domestic_goods", 50f));
    k.basePrice.put(Commod.LUXURY_GOODS, src.get("nomad_price_luxury_goods", 100f));
    k.basePrice.put(Commod.SUPPLIES, src.get("nomad_price_supplies", 100f));
    k.basePrice.put(Commod.FUEL, src.get("nomad_price_fuel", 25f));
    k.basePrice.put(Commod.METALS, src.get("nomad_price_metals", 30f));
    k.basePrice.put(Commod.RARE_METALS, src.get("nomad_price_rare_metals", 200f));
    k.basePrice.put(Commod.ORE, src.get("nomad_price_ore", 10f));
    k.basePrice.put(Commod.RARE_ORE, src.get("nomad_price_rare_ore", 75f));
    k.basePrice.put(Commod.VOLATILES, src.get("nomad_price_volatiles", 250f));
    k.basePrice.put(Commod.HEAVY_MACHINERY, src.get("nomad_price_heavy_machinery", 150f));
    k.basePrice.put(Commod.HAND_WEAPONS, src.get("nomad_price_hand_weapons", 500f));
    k.basePrice.put(Commod.DRUGS, src.get("nomad_price_drugs", 200f));
    k.basePrice.put(Commod.ORGANS, src.get("nomad_price_organs", 300f));
    k.basePrice.put(Works.RESIDENTS, src.get("nomad_price_residents", 50f));
    k.basePrice.put(Works.MARINES, src.get("nomad_price_marines", 200f));

    k.autoforgeDmodBase = src.get("nomad_autoforge_dmod_base", 4f);
    k.autoforgeDmodCorrupted = src.get("nomad_autoforge_dmod_corrupted", 2f);
    k.autoforgeDmodPristine = src.get("nomad_autoforge_dmod_pristine", 1f);

    k.dormitoryMult = src.get("nomad_work_dormitory_mult", 3.0f);

    k.workBuildDays = src.get("nomad_work_build_days", 15f);
    k.workScrapRefund = src.get("nomad_work_scrap_refund", 0.25f);

    k.itemSoilNanitesFood = src.get("nomad_item_soil_nanites_food", 0.50f);
    k.itemCatalyticCoreOreInput = src.get("nomad_item_catalytic_core_ore_input", 0.20f);
    k.itemSynchrotronFuelInput = src.get("nomad_item_synchrotron_fuel_input", 0.40f);
    k.itemCorruptedNanoforgeSupplies = src.get("nomad_item_corrupted_nanoforge_supplies", 0.10f);
    k.itemPristineNanoforgeSupplies = src.get("nomad_item_pristine_nanoforge_supplies", 0.45f);
    k.itemCorruptedNanoforgeAutoforge = src.get("nomad_item_corrupted_nanoforge_autoforge", 0.10f);
    k.itemPristineNanoforgeAutoforge = src.get("nomad_item_pristine_nanoforge_autoforge", 0.20f);
    k.itemPristineNanoforgeEfficiency = src.get("nomad_item_pristine_nanoforge_efficiency", 0.03f);
    k.itemBiofactoryFood = src.get("nomad_item_biofactory_food", 0.15f);
    k.itemBiofactoryLuxury = src.get("nomad_item_biofactory_luxury", 0.40f);
    k.itemBiofactoryHarvesterResidents =
        src.get("nomad_item_biofactory_harvester_residents", 1.0f);
    k.itemBiofactoryVats = src.get("nomad_item_biofactory_vats", 0.25f);
    k.itemFullereneSpoolEfficiency = src.get("nomad_item_fullerene_spool_efficiency", 0.03f);
    k.itemOrbitalFusionLampBerths = src.get("nomad_item_orbital_fusion_lamp_berths", 0.10f);
    k.itemMantleBoreMining = src.get("nomad_item_mantle_bore_mining", 0.40f);
    k.itemPlasmaDynamoVolatiles = src.get("nomad_item_plasma_dynamo_volatiles", 0.50f);
    k.itemCryoarithmeticAutoforgeSpeed = src.get("nomad_item_cryoarithmetic_autoforge_speed", 0.20f);
    k.itemCryoarithmeticEfficiency = src.get("nomad_item_cryoarithmetic_engine_efficiency", 0.02f);
    k.itemCoronalPortalEfficiency = src.get("nomad_item_coronal_portal_efficiency", 0.05f);
    k.itemDealmakerAutoforgeCost = src.get("nomad_item_dealmaker_autoforge_cost", 0.10f);
    k.itemDealmakerCredits = src.get("nomad_item_dealmaker_credits", 0.20f);
    k.itemDroneReplicatorStaff = src.get("nomad_item_drone_replicator_staff", 0.50f);
    k.itemDroneReplicatorArmaments = src.get("nomad_item_drone_replicator_armaments", 0.25f);
    k.itemCoreStaffAlpha = src.get("nomad_item_core_staff_alpha", 0.07f);
    k.itemCoreStaffBeta = src.get("nomad_item_core_staff_beta", 0.05f);
    k.itemCoreStaffGamma = src.get("nomad_item_core_staff_gamma", 0.03f);

    if (itemRows != null) {
      for (ItemRow row : itemRows) {
        float bonus = src.get("nomad_item_" + row.id + "_efficiency", row.efficiency);
        if (bonus != 0f) {
          k.itemEfficiency.put(row.id, bonus);
        }
        if (!row.plugin.isEmpty()) {
          k.itemPlugins.put(row.id, row);
        }
      }
    }
    if (k.itemEfficiency.isEmpty()) {
      k.itemEfficiency.put(Work.FULLERENE_SPOOL, k.itemFullereneSpoolEfficiency);
      k.itemEfficiency.put(Work.CRYOARITHMETIC_ENGINE, k.itemCryoarithmeticEfficiency);
      k.itemEfficiency.put(Work.PRISTINE_NANOFORGE, k.itemPristineNanoforgeEfficiency);
      k.itemEfficiency.put(Work.CORONAL_PORTAL, k.itemCoronalPortalEfficiency);
    }
    k.cqTallyBoardEfficiency = src.get(CQ_BOARD_KEY, CQ_BOARD_DEFAULT);
    if (k.cqTallyBoardEfficiency != 0f) {
      k.itemEfficiency.put(Work.CQ_TALLY_BOARD, k.cqTallyBoardEfficiency);
    }

    k.foundingResidents = src.get("nomad_founding_residents", 25f);
    k.foundingBerthFrac = src.get("nomad_founding_berth_frac", 1.0f);
    k.foundingStoreTarget = src.get("nomad_founding_store_target", 5f);
    k.foundingSpacePerGood = src.get("nomad_founding_space_per_good", 5f);
    k.pickupMin = src.get("nomad_pickup_min", 1f);
    k.pickupBase = src.get("nomad_pickup_base", 10f);
    k.pickupSizeCoef = src.get("nomad_pickup_size_coef", 0.0001f);
    k.pickupPopFrac = src.get("nomad_pickup_pop_frac", 0.05f);
    k.pickupCooldownDays = src.get("nomad_pickup_cooldown_days", 180f);

    return k;
  }

  public void addWorkPlugins(WorkCatalog catalog) {
    if (catalog == null) {
      return;
    }
    for (WorkDef def : catalog.works()) {
      if (!def.plugin.isEmpty()) {
        workPlugins.put(def.id, def);
      }
    }
  }

  public void applySurvival(SurvivalRules rules, boolean adjust) {
    if (survivalCampaign || rules == null) {
      return;
    }
    survivalCampaign = true;
    for (int i = 0; i < codexHullValueMult.length; i++) {
      codexHullValueMult[i] = rules.shipBuyPriceMult(i);
    }
    weaponSpecMult = rules.weaponPriceMult > 0f ? rules.weaponPriceMult : 1f;
    wingSpecMult = rules.fighterPriceMult > 0f ? rules.fighterPriceMult : 1f;
    if (adjust) {
      creditsSalePriceFrac = survivalSalePriceFrac(rules.normalTariff, creditsSaleSurvivalGap);
      for (int i = 0; i < hullValueMult.length; i++) {
        hullValueMult[i] = rules.shipBuyPriceMult(i);
      }
      if (rules.shipBuyPriceMultPerDMod > 0f) {
        hullDmodValueMult = rules.shipBuyPriceMultPerDMod;
      }
      survivalAdjusted = true;
    }
    for (String c : new ArrayList<String>(basePrice.keySet())) {
      basePrice.put(c, basePrice.get(c) * rules.priceMult(c));
    }
  }

  public static float survivalSalePriceFrac(float tariff, float gap) {
    float marketNet = Math.max(0f, Math.min(1f, 1f - tariff));
    return Math.max(0f, Math.min(marketNet, marketNet - gap));
  }

  public static float survivalFuelInputRatio(float ratio, float fuelPrice, float volatilesPrice,
      float fuelPriceMult, float volatilesPriceMult) {
    float volatilesSurvival = volatilesPrice * volatilesPriceMult;
    if (ratio <= 0f || volatilesSurvival <= 0f) {
      return ratio;
    }
    float margin = fuelPrice - ratio * volatilesPrice;
    return Math.max(0f, (fuelPrice * fuelPriceMult - margin) / volatilesSurvival);
  }

  public float hullValueMult(int sizeIndex) {
    return sizeIndex >= 0 && sizeIndex < hullValueMult.length ? hullValueMult[sizeIndex] : 1f;
  }

  public float autoforgeSpecValue(float specValue, boolean wing, boolean specScaled) {
    if (specScaled == survivalAdjusted) {
      return specValue;
    }
    float mult = wing ? wingSpecMult : weaponSpecMult;
    return survivalAdjusted ? specValue * mult : specValue / mult;
  }

  public float codexHullValueMult(int sizeIndex) {
    return sizeIndex >= 0 && sizeIndex < codexHullValueMult.length ? codexHullValueMult[sizeIndex] : 1f;
  }

  public float hullDmodValueMult(int dmods) {
    float m = 1f;
    for (int i = 0; i < dmods; i++) {
      m *= hullDmodValueMult;
    }
    return m;
  }

  public Map<String, Float> flatten() {
    Map<String, Float> m = new LinkedHashMap<String, Float>();
    m.put("nomad_item_effect_mult_survival", itemEffectMultSurvival);
    m.put("nomad_item_effect_mult_normal_difficulty", itemEffectMultNormalDifficulty);
    m.put("nomad_normal_difficulty_efficiency_mult", normalDifficultyEfficiencyMult);
    m.put("nomad_story_difficulty_efficiency_mult", storyDifficultyEfficiencyMult);
    m.put("nomad_story_difficulty_storage_space_mult", storyDifficultyStorageSpaceMult);
    m.put("nomad_efficiency_waste_floor", efficiencyWasteFloor);
    m.put("nomad_paused_decay", pausedDecay);
    m.put("nomad_credits_sale_price_frac", creditsSalePriceFrac);
    m.put("nomad_credits_sale_survival_gap", creditsSaleSurvivalGap);
    m.put("nomad_crew_extract_reserve", crewExtractReserve);
    m.put("nomad_marines_target_default", marinesTargetDefault);
    m.put("nomad_price_food", price(Commod.FOOD));
    m.put("nomad_price_organics", price(Commod.ORGANICS));
    m.put("nomad_price_domestic_goods", price(Commod.DOMESTIC_GOODS));
    m.put("nomad_price_luxury_goods", price(Commod.LUXURY_GOODS));
    m.put("nomad_price_supplies", price(Commod.SUPPLIES));
    m.put("nomad_price_fuel", price(Commod.FUEL));
    m.put("nomad_price_metals", price(Commod.METALS));
    m.put("nomad_price_rare_metals", price(Commod.RARE_METALS));
    m.put("nomad_price_ore", price(Commod.ORE));
    m.put("nomad_price_rare_ore", price(Commod.RARE_ORE));
    m.put("nomad_price_volatiles", price(Commod.VOLATILES));
    m.put("nomad_price_heavy_machinery", price(Commod.HEAVY_MACHINERY));
    m.put("nomad_price_hand_weapons", price(Commod.HAND_WEAPONS));
    m.put("nomad_price_drugs", price(Commod.DRUGS));
    m.put("nomad_price_organs", price(Commod.ORGANS));
    m.put("nomad_price_residents", price(Works.RESIDENTS));
    m.put("nomad_price_marines", price(Works.MARINES));
    m.put("nomad_autoforge_dmod_base", autoforgeDmodBase);
    m.put("nomad_autoforge_dmod_corrupted", autoforgeDmodCorrupted);
    m.put("nomad_autoforge_dmod_pristine", autoforgeDmodPristine);
    m.put("nomad_work_dormitory_mult", dormitoryMult);
    m.put("nomad_work_build_days", workBuildDays);
    m.put("nomad_work_scrap_refund", workScrapRefund);
    m.put("nomad_item_soil_nanites_food", itemSoilNanitesFood);
    m.put("nomad_item_catalytic_core_ore_input", itemCatalyticCoreOreInput);
    m.put("nomad_item_synchrotron_fuel_input", itemSynchrotronFuelInput);
    m.put("nomad_item_corrupted_nanoforge_supplies", itemCorruptedNanoforgeSupplies);
    m.put("nomad_item_pristine_nanoforge_supplies", itemPristineNanoforgeSupplies);
    m.put("nomad_item_corrupted_nanoforge_autoforge", itemCorruptedNanoforgeAutoforge);
    m.put("nomad_item_pristine_nanoforge_autoforge", itemPristineNanoforgeAutoforge);
    m.put("nomad_item_pristine_nanoforge_efficiency", itemPristineNanoforgeEfficiency);
    m.put("nomad_item_biofactory_food", itemBiofactoryFood);
    m.put("nomad_item_biofactory_luxury", itemBiofactoryLuxury);
    m.put("nomad_item_biofactory_harvester_residents", itemBiofactoryHarvesterResidents);
    m.put("nomad_item_biofactory_vats", itemBiofactoryVats);
    m.put("nomad_item_fullerene_spool_efficiency", itemFullereneSpoolEfficiency);
    m.put("nomad_item_orbital_fusion_lamp_berths", itemOrbitalFusionLampBerths);
    m.put("nomad_item_mantle_bore_mining", itemMantleBoreMining);
    m.put("nomad_item_plasma_dynamo_volatiles", itemPlasmaDynamoVolatiles);
    m.put("nomad_item_cryoarithmetic_autoforge_speed", itemCryoarithmeticAutoforgeSpeed);
    m.put("nomad_item_cryoarithmetic_engine_efficiency", itemCryoarithmeticEfficiency);
    m.put("nomad_item_coronal_portal_efficiency", itemCoronalPortalEfficiency);
    m.put(CQ_BOARD_KEY, cqTallyBoardEfficiency);
    m.put("nomad_item_dealmaker_autoforge_cost", itemDealmakerAutoforgeCost);
    m.put("nomad_item_dealmaker_credits", itemDealmakerCredits);
    m.put("nomad_item_drone_replicator_staff", itemDroneReplicatorStaff);
    m.put("nomad_item_drone_replicator_armaments", itemDroneReplicatorArmaments);
    m.put("nomad_item_core_staff_alpha", itemCoreStaffAlpha);
    m.put("nomad_item_core_staff_beta", itemCoreStaffBeta);
    m.put("nomad_item_core_staff_gamma", itemCoreStaffGamma);
    m.put("nomad_founding_residents", foundingResidents);
    m.put("nomad_founding_berth_frac", foundingBerthFrac);
    m.put("nomad_founding_store_target", foundingStoreTarget);
    m.put("nomad_founding_space_per_good", foundingSpacePerGood);
    m.put("nomad_pickup_min", pickupMin);
    m.put("nomad_pickup_base", pickupBase);
    m.put("nomad_pickup_size_coef", pickupSizeCoef);
    m.put("nomad_pickup_pop_frac", pickupPopFrac);
    m.put("nomad_pickup_cooldown_days", pickupCooldownDays);
    return m;
  }

  public float price(String c) {
    Float v = basePrice.get(c);
    return v == null ? 0f : v;
  }

  public float difficultyProductionMult(boolean normalOrStory, boolean storyDifficulty) {
    float m = normalOrStory ? normalDifficultyEfficiencyMult : 1f;
    return storyDifficulty ? m * storyDifficultyEfficiencyMult : m;
  }

  public float itemEffectMult(boolean normalOrStory) {
    return normalOrStory ? itemEffectMultNormalDifficulty : itemEffectMultSurvival;
  }

  public float itemEfficiencyBonus(String id) {
    Float bonus = id == null ? null : itemEfficiency.get(id);
    return bonus == null ? 0f : bonus;
  }
}
