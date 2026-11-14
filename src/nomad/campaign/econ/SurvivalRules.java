package nomad.campaign.econ;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SurvivalRules {

  private final Map<String, Float> priceMult;
  private final float[] shipBuyPriceMult;
  public final float shipBuyPriceMultPerDMod;
  public final float normalTariff;
  public final float customProductionMult;
  public final float weaponPriceMult, fighterPriceMult;

  public SurvivalRules(Map<String, Float> priceMult, float[] shipBuyPriceMult, float shipBuyPriceMultPerDMod,
      float normalTariff, float customProductionMult, float weaponPriceMult, float fighterPriceMult) {
    this.priceMult = priceMult == null ? new LinkedHashMap<String, Float>()
        : new LinkedHashMap<String, Float>(priceMult);
    this.shipBuyPriceMult = new float[4];
    for (int i = 0; i < 4; i++) {
      this.shipBuyPriceMult[i] = shipBuyPriceMult != null && i < shipBuyPriceMult.length ? shipBuyPriceMult[i] : 1f;
    }
    this.shipBuyPriceMultPerDMod = shipBuyPriceMultPerDMod;
    this.normalTariff = normalTariff;
    this.customProductionMult = customProductionMult;
    this.weaponPriceMult = weaponPriceMult;
    this.fighterPriceMult = fighterPriceMult;
  }

  public float priceMult(String commodity) {
    Float m = priceMult.get(commodity);
    return m == null || m <= 0f ? 1f : m;
  }

  public float shipBuyPriceMult(int sizeIndex) {
    float m = sizeIndex >= 0 && sizeIndex < 4 ? shipBuyPriceMult[sizeIndex] : 1f;
    return m > 0f ? m : 1f;
  }
}
