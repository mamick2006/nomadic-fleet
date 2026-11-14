package nomad.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlayerMarketTransaction;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ColonyInteractionListener;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.util.Misc;

import nomad.NomadStrings;
import nomad.campaign.econ.Arrivals;
import nomad.campaign.econ.EconConstants;
import nomad.campaign.econ.NomadPickup;
import nomad.data.NomadFleetData;

public class NomadPickupListener implements ColonyInteractionListener {

  private static final String LAST_PICKUP_KEY = "$nomad_lastPickupTs";

  private static MarketAPI eligibleMarket;

  public static void resetStaticsForLoad() {
    eligibleMarket = null;
  }

  public static boolean isEligible(MarketAPI market) {
    if (market == null || market.getSize() <= 0 || market.isPlanetConditionMarketOnly()) {
      return false;
    }
    NomadFleetData data = NomadFleetData.getInstance();
    if (!data.isFounded() || data.isPaused() || data.isDetached()) {
      return false;
    }
    if (!colonyOffCooldown(market)) {
      return false;
    }
    return room(data) >= 1f;
  }

  private static boolean colonyOffCooldown(MarketAPI market) {
    if (!market.getMemoryWithoutUpdate().contains(LAST_PICKUP_KEY)) {
      return true;
    }
    long last = market.getMemoryWithoutUpdate().getLong(LAST_PICKUP_KEY);
    float cooldown = NomadEcon.constants().pickupCooldownDays;
    return Global.getSector().getClock().getElapsedDaysSince(last) >= cooldown;
  }

  public static int tryPickup(MarketAPI market) {
    if (!isEligible(market)) {
      return 0;
    }
    NomadFleetData data = NomadFleetData.getInstance();
    EconConstants k = NomadEcon.constants();
    float offered = NomadPickup.compute(market.getSize(), data.getResidents(), room(data), k);
    int n = (int) Math.floor(offered);
    if (n < 1) {
      return 0;
    }
    data.setResidents(data.getResidents() + n);
    data.markChanged();
    market.getMemoryWithoutUpdate().set(LAST_PICKUP_KEY, Global.getSector().getClock().getTimestamp());
    NomadHistory.add(data, NomadStrings.get("nomad_history",
        n == 1 ? "hist_migrants_one" : "hist_migrants_many", n, market.getName()));
    return n;
  }

  private static float room(NomadFleetData data) {
    return Arrivals.room(NomadModel.preview(data, null));
  }

  @Override
  public void reportPlayerOpenedMarket(MarketAPI market) {
    if (isEligible(market)) {
      eligibleMarket = market;
      nudge();
    } else {
      eligibleMarket = null;
    }
  }

  @Override
  public void reportPlayerClosedMarket(MarketAPI market) {
    eligibleMarket = null;
    int n = tryPickup(market);
    if (n > 0) {
      MessageIntel intel = new MessageIntel(
          NomadStrings.get("nomad_messages",
              n == 1 ? "msg_migrants_one" : "msg_migrants_many", n, market.getName()),
          Misc.getBasePlayerColor());
      intel.setIcon(Global.getSettings().getSpriteName("icons", "nomad_intel"));
      Global.getSector().getCampaignUI().addMessage(intel);
    }
  }

  @Override
  public void reportPlayerOpenedMarketAndCargoUpdated(MarketAPI market) {
  }

  @Override
  public void reportPlayerMarketTransaction(PlayerMarketTransaction transaction) {
  }

  private void nudge() {
    MessageIntel intel = new MessageIntel(NomadStrings.get("nomad_messages", "msg_migrants_available"),
        Misc.getBasePlayerColor());
    intel.setIcon(Global.getSettings().getSpriteName("icons", "nomad_intel"));
    Global.getSector().getCampaignUI().addMessage(intel);
  }
}
