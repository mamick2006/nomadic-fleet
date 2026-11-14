package nomad.campaign.econ.model;

import java.util.LinkedHashMap;
import java.util.Map;

import nomad.campaign.econ.Commod;
import nomad.campaign.econ.EconConstants;
import nomad.campaign.econ.SurvivalRules;

public final class Rules {

  public float needsSpacePerResident;
  public float needsStaffPerResident;
  public float needsNormalDifficultyWantMult;
  public float needsStoryDifficultyWantMult;

  public float needsTierRelief;
  public float needsReliefCap;
  public float needsItemTiers;
  public float needsRequirementFloor;

  private final float[] provision = new float[NeedsLevel.values().length];
  private final float[] efficiency = new float[NeedsLevel.values().length];
  private final float[] leavePerMonth = new float[NeedsLevel.values().length];
  private final float[] growthPerYear = new float[NeedsLevel.values().length];

  public float storeSpacePerUnit;
  public float fullereneSpoolSpace;

  public float remoteTierMult;
  public float remoteCap;
  public float remoteAsteroidTier;
  public float remoteOnDays;

  public float survivalFuelInputStep;

  public float survivalFuelInputMult = 1f;
  public float survivalFoundryPointsMult = 1f;

  public float productionMult;
  public float foundryMult;

  public float brokerageBonusShare;

  public float supportLimitBase;
  public float supportLimitFirstAt;

  public float prereqResidentsSlack;

  private Rules() {
  }

  public static Rules build(EconConstants.FloatSource src) {
    Rules k = new Rules();
    k.needsSpacePerResident = src.get("nomad_bn_space_per_resident", 1.0f);
    k.needsStaffPerResident = src.get("nomad_bn_staff_per_resident", 0.35f);
    k.needsNormalDifficultyWantMult = src.get("nomad_bn_normal_difficulty_want_mult", 0.80f);
    k.needsStoryDifficultyWantMult = src.get("nomad_bn_story_difficulty_want_mult", 0.64f);
    k.needsTierRelief = src.get("nomad_bn_tier_relief", 0.01f);
    k.needsReliefCap = src.get("nomad_bn_tier_relief_cap", 0.40f);
    k.needsItemTiers = src.get("nomad_bn_item_tiers", 1f);
    k.needsRequirementFloor = src.get("nomad_bn_requirement_floor", 0.25f);

    level(k, src, NeedsLevel.NON_EXISTENT, 0.0f, 0.0f, 0.10f, 0.0f);
    level(k, src, NeedsLevel.DECREPIT, 0.40f, 0.30f, 0.04f, 0.0f);
    level(k, src, NeedsLevel.SPARTAN, 0.70f, 0.70f, 0.0f, 0.0f);
    level(k, src, NeedsLevel.NORMAL, 1.00f, 1.00f, 0.0f, 0.02f);
    level(k, src, NeedsLevel.NICE, 1.25f, 1.05f, 0.0f, 0.04f);
    level(k, src, NeedsLevel.LAVISH, 1.60f, 1.10f, 0.0f, 0.06f);
    level(k, src, NeedsLevel.OPULENT, 2.00f, 1.15f, 0.0f, 0.08f);

    k.survivalFuelInputStep = src.get("nomad_survival_fuel_plant_input_step", 1.1628f);
    k.productionMult = src.get("nomad_yield_mult_override", 1.0f);
    k.foundryMult = src.get("nomad_autoforge_yield_mult_override", 1.0f);

    k.storeSpacePerUnit = src.get("nomad_store_space_per_unit", 1.0f);
    k.fullereneSpoolSpace = src.get("nomad_item_fullerene_spool_space", 0.05f);

    k.remoteTierMult = src.get("nomad_mining_remote_tier_mult", 0.2f);
    k.remoteCap = src.get("nomad_mining_remote_cap", 0.4f);
    k.remoteAsteroidTier = src.get("nomad_mining_remote_asteroid_tier", 0.5f);
    k.remoteOnDays = src.get("nomad_mining_remote_on_days", 10f);

    k.brokerageBonusShare = src.get("nomad_brokerage_bonus_share", 0.5f);

    k.supportLimitBase = src.get("nomad_support_limit_base", 3f);
    k.supportLimitFirstAt = src.get("nomad_support_limit_first_at", 200f);
    k.prereqResidentsSlack = src.get("nomad_prereq_residents_slack", 0.05f);
    return k;
  }

  private static void level(Rules k, EconConstants.FloatSource src, NeedsLevel level,
      float provision, float efficiency, float leave, float growth) {
    String p = "nomad_bn_" + level.key;
    int i = level.ordinal();
    k.provision[i] = src.get(p + "_provision", provision);
    k.efficiency[i] = src.get(p + "_efficiency", efficiency);
    k.leavePerMonth[i] = src.get(p + "_leave_per_month", leave);
    k.growthPerYear[i] = src.get(p + "_growth_per_year", growth);
  }

  public Map<String, Float> flatten() {
    Map<String, Float> m = new LinkedHashMap<String, Float>();
    m.put("nomad_bn_space_per_resident", needsSpacePerResident);
    m.put("nomad_bn_staff_per_resident", needsStaffPerResident);
    m.put("nomad_bn_normal_difficulty_want_mult", needsNormalDifficultyWantMult);
    m.put("nomad_bn_story_difficulty_want_mult", needsStoryDifficultyWantMult);
    m.put("nomad_bn_tier_relief", needsTierRelief);
    m.put("nomad_bn_tier_relief_cap", needsReliefCap);
    m.put("nomad_bn_item_tiers", needsItemTiers);
    m.put("nomad_bn_requirement_floor", needsRequirementFloor);
    for (NeedsLevel level : NeedsLevel.values()) {
      String p = "nomad_bn_" + level.key;
      int i = level.ordinal();
      m.put(p + "_provision", provision[i]);
      m.put(p + "_efficiency", efficiency[i]);
      m.put(p + "_leave_per_month", leavePerMonth[i]);
      m.put(p + "_growth_per_year", growthPerYear[i]);
    }
    m.put("nomad_survival_fuel_plant_input_step", survivalFuelInputStep);
    m.put("nomad_yield_mult_override", productionMult);
    m.put("nomad_autoforge_yield_mult_override", foundryMult);
    m.put("nomad_store_space_per_unit", storeSpacePerUnit);
    m.put("nomad_item_fullerene_spool_space", fullereneSpoolSpace);
    m.put("nomad_mining_remote_tier_mult", remoteTierMult);
    m.put("nomad_mining_remote_cap", remoteCap);
    m.put("nomad_mining_remote_asteroid_tier", remoteAsteroidTier);
    m.put("nomad_mining_remote_on_days", remoteOnDays);
    m.put("nomad_brokerage_bonus_share", brokerageBonusShare);
    m.put("nomad_support_limit_base", supportLimitBase);
    m.put("nomad_support_limit_first_at", supportLimitFirstAt);
    m.put("nomad_prereq_residents_slack", prereqResidentsSlack);
    return m;
  }

  public float provision(NeedsLevel level) {
    return provision[level.ordinal()];
  }

  public float efficiency(NeedsLevel level) {
    return efficiency[level.ordinal()];
  }

  public float leavePerMonth(NeedsLevel level) {
    return leavePerMonth[level.ordinal()];
  }

  public float growthPerYear(NeedsLevel level) {
    return growthPerYear[level.ordinal()];
  }

  public NeedsLevel levelFor(float provisionValue) {
    NeedsLevel[] levels = NeedsLevel.values();
    NeedsLevel found = NeedsLevel.NON_EXISTENT;
    for (NeedsLevel level : levels) {
      if (provisionValue >= provision[level.ordinal()]) {
        found = level;
      }
    }
    return found;
  }

  public float wantMult(boolean normalOrStory, boolean storyDifficulty) {
    return storyDifficulty ? needsStoryDifficultyWantMult
        : normalOrStory ? needsNormalDifficultyWantMult : 1f;
  }

  public void applyPlayerSettings(float productionMult, float foundryMult,
      float normalGrowthPerYear) {
    this.productionMult = productionMult;
    this.foundryMult = foundryMult;
    float normal = growthPerYear[NeedsLevel.NORMAL.ordinal()];
    if (normal > 0f && normalGrowthPerYear >= 0f) {
      float factor = normalGrowthPerYear / normal;
      for (int i = 0; i < growthPerYear.length; i++) {
        growthPerYear[i] *= factor;
      }
    }
  }

  public void applySurvival(SurvivalRules rules, boolean adjust) {
    if (rules == null || !adjust) {
      return;
    }
    survivalFuelInputMult = survivalFuelInputMult(survivalFuelInputStep,
        rules.priceMult(Commod.FUEL), rules.priceMult(Commod.VOLATILES));
    if (rules.customProductionMult > 0f) {
      survivalFoundryPointsMult = rules.customProductionMult;
    }
  }

  public static float survivalFuelInputMult(float step, float fuelPriceMult,
      float volatilesPriceMult) {
    if (volatilesPriceMult <= 0f) {
      return 1f;
    }
    return Math.max(0f, (1f + step * (fuelPriceMult - 1f)) / volatilesPriceMult);
  }
}
