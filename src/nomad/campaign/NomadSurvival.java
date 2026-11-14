package nomad.campaign;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.util.Misc;

import nomad.campaign.econ.SurvivalRules;

public final class NomadSurvival {

  private NomadSurvival() {
  }

  public static final String ADJUSTMENTS_SETTING = "nomad_survival_adjustments";

  public static boolean isSurvivalCampaign() {
    SectorAPI sector = Global.getSector();
    return sector != null && sector.isExtMode();
  }

  public static boolean adjustmentsOn() {
    return NomadSettings.tunableBool(ADJUSTMENTS_SETTING, true);
  }

  public static SurvivalRules rules(Collection<String> commodityIds) {
    return new SurvivalRules(priceMults(commodityIds), shipBuyPriceMults(),
        NomadSettings.getFloat("ext_shipBuyPriceMultPerDMod", 1f),
        NomadSettings.getFloat("ext_normalTariff", 0.30f),
        NomadSettings.getFloat("ext_playerIndustryCustomProductionMaxValueMult", 1f),
        NomadSettings.getFloat("ext_weaponPriceMult", 1f),
        NomadSettings.getFloat("ext_fighterPriceMult", 1f));
  }

  public static boolean specValuesScaled() {
    return Misc.isExtended();
  }

  private static Map<String, Float> priceMults(Collection<String> commodityIds) {
    Map<String, Float> out = new LinkedHashMap<String, Float>();
    for (String id : commodityIds) {
      out.put(id, NomadSettings.getFloat("ext_" + id + "PriceMult", 1f));
    }
    return out;
  }

  private static float[] shipBuyPriceMults() {
    float[] out = new float[4];
    for (int i = 0; i < out.length; i++) {
      out[i] = Global.getSettings() == null ? 1f
          : Global.getSettings().optFloatFromArray("ext_shipBuyPriceMult", i, 1f);
    }
    return out;
  }

  public static int sizeIndex(HullSize size) {
    return size == null ? -1 : size.ordinal() - 2;
  }
}
