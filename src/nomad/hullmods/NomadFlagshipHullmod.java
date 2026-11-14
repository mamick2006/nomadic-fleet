package nomad.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.combat.HullModFleetEffect;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.util.Misc;

import nomad.NomadStrings;
import nomad.campaign.NomadHistory;
import nomad.campaign.NomadManager;
import nomad.campaign.econ.ShellRules;
import nomad.campaign.split.NomadSplitter;
import nomad.data.NomadFleetData;
import nomad.intel.NomadIntel;

public class NomadFlagshipHullmod extends NomadHullmodBase implements HullModFleetEffect {

  private static boolean warnedSync;

  @Override
  public void onFleetSync(CampaignFleetAPI fleet) {
    try {
      if (fleet == null || Global.getSector() == null || !NomadFleetData.exists()) {
        return;
      }
      NomadManager m = NomadManager.getInstance();
      if (m != null) {
        m.fleetSynced(fleet);
      }
      NomadFleetData data = NomadFleetData.getInstance();
      if (data.isFoundersPending() && fleet == nomad.campaign.NomadModel.flotillaFleet()) {
        nomad.campaign.NomadModel.seatFoundersNow(data);
      }
    } catch (Throwable t) {
      if (!warnedSync) {
        warnedSync = true;
        Global.getLogger(NomadFlagshipHullmod.class).warn("Nomad Flotilla: reading the fleet"
            + " after a change to its ships failed (" + t + "). The top bar and the Flotilla"
            + " screen may show the old state until the next economy tick.");
      }
    }
  }

  @Override
  public boolean withOnFleetSync() {
    return true;
  }

  @Override
  public void advanceInCampaign(CampaignFleetAPI fleet) {
  }

  @Override
  public boolean withAdvanceInCampaign() {
    return false;
  }

  @Override
  public void advanceInCampaign(FleetMemberAPI member, float amount) {
    if (Global.getSector() == null || member == null) {
      return;
    }
    NomadManager m = NomadManager.getInstance();
    if (m != null) {
      m.eagerUpdate(member, true);
    }
    NomadFleetData data = NomadFleetData.getInstance();
    if (!data.isFounded()) {
      data.setFounded(true);
      NomadHistory.add(data, NomadStrings.get("nomad_history", "hist_founded"));
      data.awaitFounders(Global.getSector().getClock().getTimestamp());
      nomad.campaign.NomadModel.openStores(data);
      nomad.campaign.NomadModel.seatFoundersNow(data);
      data.markChanged();
      NomadIntel.ensureExists(data);
      data.setNeedsFoundingDialog(true);
      announceFounding();
    }
  }

  @Override
  public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
    NomadMemberHullmod.applyDedication(stats, id, "nomad_member_supplies_penalty");
    nomad.campaign.works.WorksEffects.applyShipStats(stats);
  }

  @Override
  public boolean isApplicableToShip(ShipAPI ship) {
    if (Global.getSector() == null) {
      return true;
    }
    if (isBelowDestroyer(ship) || carriesOtherNomadMod(ship, NomadManager.FLAGSHIP_HULLMOD_ID)) {
      return false;
    }
    NomadManager m = NomadManager.getInstance();
    boolean flagshipExists = m != null && m.flagshipExists();
    if (remnantHoldsFlotilla(alreadyHasFlagshipMod(ship), flagshipExists,
        NomadSplitter.detachmentOut())) {
      return false;
    }
    return canInstall(alreadyHasFlagshipMod(ship), flagshipExists, isCurrentFlagshipShip(m, ship));
  }

  static boolean remnantHoldsFlotilla(boolean shipHasMod, boolean flagshipExists,
      boolean detachmentOut) {
    return ShellRules.remnantHoldsFlotilla(shipHasMod, flagshipExists, detachmentOut);
  }

  static boolean canInstall(boolean shipHasMod, boolean flagshipExists, boolean isCurrentFlagshipShip) {
    return shipHasMod || !flagshipExists || isCurrentFlagshipShip;
  }

  @Override
  public String getUnapplicableReason(ShipAPI ship) {
    if (isBelowDestroyer(ship)) {
      return NomadStrings.get("nomad_hullmods", "hm_flagship_too_small");
    }
    if (carriesOtherNomadMod(ship, NomadManager.FLAGSHIP_HULLMOD_ID)) {
      return NomadStrings.get("nomad_hullmods", "hm_one_per_hull");
    }
    NomadManager m = NomadManager.getInstance();
    if (m != null && m.flagshipExists()) {
      FleetMemberAPI flagship = m.getFlagshipMember();
      String name = flagship != null ? flagship.getShipName() : null;
      if (name != null) {
        return NomadStrings.get("nomad_hullmods", "hm_flagship_exists", name);
      }
      return NomadStrings.get("nomad_hullmods", "hm_flagship_exists_unnamed");
    }
    if (NomadSplitter.detachmentOut()) {
      return NomadStrings.get("nomad_hullmods", "fixglue_hm_flagship_remnant");
    }
    return NomadStrings.get("nomad_hullmods", "hm_flagship_fallback");
  }

  @Override
  public String getDescriptionParam(int index, HullSize hullSize) {
    if (index == 0) {
      return pct("nomad_dedication_default_cargo", nomad.campaign.econ.NomadCapacity.DEFAULT_PCT);
    }
    if (index == 1) {
      return pct("nomad_member_supplies_penalty", 10f);
    }
    return null;
  }

  private static boolean isBelowDestroyer(ShipAPI ship) {
    HullSize size = ship.getHullSize();
    return size == HullSize.DEFAULT || size == HullSize.FIGHTER || size == HullSize.FRIGATE;
  }

  private static boolean alreadyHasFlagshipMod(ShipAPI ship) {
    return ship.getVariant() != null && ship.getVariant().hasHullMod(NomadManager.FLAGSHIP_HULLMOD_ID);
  }

  private static boolean isCurrentFlagshipShip(NomadManager m, ShipAPI ship) {
    if (m == null || ship == null) {
      return false;
    }
    FleetMemberAPI current = m.getFlagshipMember();
    if (current == null) {
      return false;
    }
    String shipMemberId = ship.getFleetMemberId();
    if (shipMemberId != null && shipMemberId.equals(current.getId())) {
      return true;
    }
    return ship.getMutableStats() != null && ship.getMutableStats().getFleetMember() == current;
  }

  private static void announceFounding() {
    if (Global.getSector() == null || Global.getSector().getCampaignUI() == null) {
      return;
    }
    MessageIntel intel = new MessageIntel(NomadStrings.get("nomad_messages", "msg_founded"),
        Misc.getBasePlayerColor());
    intel.setIcon(Global.getSettings().getSpriteName("icons", "nomad_intel"));
    Global.getSector().getCampaignUI().addMessage(intel);
  }
}
