package nomad.campaign;

import java.util.ArrayList;

import com.fs.starfarer.api.Global;

import nomad.campaign.econ.EconConstants;
import nomad.campaign.tables.ItemTable;
import nomad.campaign.works.WorkCatalogTable;

public final class NomadEcon {

  private NomadEcon() {
  }

  private static final EconConstants.FloatSource SOURCE = new EconConstants.FloatSource() {
    @Override
    public float get(String key, float fallback) {
      return NomadSettings.getFloat(key, fallback);
    }
  };

  public static EconConstants constants() {
    EconConstants held = cached;
    if (held != null) {
      return held;
    }
    EconConstants built = build();
    cached = built;
    return built;
  }

  public static void onGameLoad() {
    cached = null;
  }

  public static void settingsChanged() {
    cached = null;
  }

  private static volatile EconConstants cached;

  private static EconConstants build() {
    EconConstants k = EconConstants.build(SOURCE, ItemTable.read());
    k.addWorkPlugins(WorkCatalogTable.read());

    k.pickupCooldownDays = NomadSettings.tunableInt(
        "nomad_pickup_cooldown_days", "nomad_pickup_cooldown_days", Math.round(k.pickupCooldownDays));

    for (String c : new ArrayList<String>(k.basePrice.keySet())) {
      float base = unmodifiedBasePrice(c);
      if (base > 0f) {
        k.basePrice.put(c, base);
      }
    }
    if (NomadSurvival.isSurvivalCampaign()) {
      k.applySurvival(NomadSurvival.rules(k.basePrice.keySet()), NomadSurvival.adjustmentsOn());
    }
    return k;
  }

  private static float unmodifiedBasePrice(String commodityId) {
    try {
      return Global.getSettings().getCommoditySpec(commodityId).getUnmodifiedBasePrice();
    } catch (Exception e) {
      return 0f;
    }
  }
}
