package nomad.campaign.works;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.EngagementResultForFleetAPI;
import com.fs.starfarer.api.campaign.FleetEncounterContextPlugin;
import com.fs.starfarer.api.campaign.PlayerMarketTransaction;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.CrewCompositionAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.util.Misc;

import nomad.NomadStrings;
import nomad.campaign.NomadHistory;
import nomad.campaign.NomadManager;
import nomad.campaign.NomadModel;
import nomad.campaign.econ.Arrivals;
import nomad.campaign.econ.SupportPay;
import nomad.data.NomadFleetData;

public class WorksCampaignEventListener extends BaseCampaignEventListener {

  private static final float REBATE_HISTORY_MIN = 10f;

  public WorksCampaignEventListener() {
    super(false);
  }

  @Override
  public void reportPlayerMarketTransaction(PlayerMarketTransaction transaction) {
    if (transaction == null) {
      return;
    }
    float frac = WorksEffects.levels().rebateFrac;
    if (frac <= 0f) {
      return;
    }
    float spent = Math.max(0f, -transaction.getCreditValue());
    if (spent <= 0f) {
      return;
    }
    float rebate = spent * frac;
    if (rebate < 1f) {
      return;
    }
    Global.getSector().getPlayerFleet().getCargo().getCredits().add(rebate);
    if (rebate >= REBATE_HISTORY_MIN) {
      NomadHistory.add(NomadFleetData.getInstance(),
          NomadStrings.get("nomad_history", "hist_rebate", Math.round(rebate)));
    }
  }

  @Override
  public void reportPlayerEngagement(EngagementResultAPI result) {
    if (result == null || !NomadFleetData.exists()) {
      return;
    }
    float share = WorksEffects.levels().insureFrac;
    if (share <= 0f) {
      return;
    }
    NomadFleetData data = NomadFleetData.getInstance();
    if (data.isDetached()) {
      return;
    }
    try {
      EngagementResultForFleetAPI side = result.getWinnerResult() != null
          && result.getWinnerResult().isPlayer() ? result.getWinnerResult()
              : result.getLoserResult();
      if (side == null || !side.isPlayer()) {
        return;
      }
      claimAll(data, side.getDestroyed(), share);
      claimAll(data, side.getDisabled(), share);
    } catch (Throwable ignore) {
    }
  }

  private static void claimAll(NomadFleetData data, List<FleetMemberAPI> lost, float share) {
    if (lost == null) {
      return;
    }
    for (FleetMemberAPI member : lost) {
      if (member == null || member.isFighterWing() || member.getHullSpec() == null
          || !isNomad(member.getVariant())) {
        continue;
      }
      float credits = SupportPay.insurancePayout(member.getHullSpec().getBaseValue(), share);
      data.addInsuranceClaim(member.getId(), member.getShipName(), credits);
    }
  }

  private static boolean isNomad(ShipVariantAPI variant) {
    if (variant == null) {
      return false;
    }
    for (String id : NomadManager.ALL_HULLMOD_IDS) {
      if (variant.hasHullMod(id)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void reportBattleFinished(CampaignFleetAPI primaryWinner, BattleAPI battle) {
    if (battle == null || !battle.isPlayerInvolved() || !NomadFleetData.exists()) {
      return;
    }
    NomadFleetData data = NomadFleetData.getInstance();
    if (data.hasInsuranceClaims()) {
      settleInsurance(data);
    }
  }

  static void settleInsurance(NomadFleetData data) {
    try {
      CampaignFleetAPI player = Global.getSector().getPlayerFleet();
      if (player == null) {
        return;
      }
      Set<String> inFleet = new HashSet<String>();
      for (FleetMemberAPI m : player.getFleetData().getMembersListCopy()) {
        inFleet.add(m.getId());
      }
      Map<String, Float> pay = SupportPay.settle(data.getInsuranceClaims(), inFleet);
      for (Map.Entry<String, Float> e : pay.entrySet()) {
        float credits = e.getValue().floatValue();
        player.getCargo().getCredits().add(credits);
        String name = data.getInsuranceName(e.getKey());
        NomadHistory.add(data, NomadStrings.get("nomad_history", "hist_insurance_paid",
            Misc.getWithDGS(Math.round(credits)), name != null ? name : "?"));
      }
      data.clearInsuranceClaims();
      if (!pay.isEmpty()) {
        data.markChanged();
      }
    } catch (Throwable ignore) {
      data.clearInsuranceClaims();
    }
  }

  @Override
  public void reportEncounterLootGenerated(FleetEncounterContextPlugin plugin, CargoAPI loot) {
    if (plugin == null) {
      return;
    }
    float frac = WorksEffects.levels().crewReturnFrac;
    if (frac <= 0f) {
      return;
    }
    try {
      CampaignFleetAPI player = Global.getSector().getPlayerFleet();
      FleetEncounterContextPlugin.DataForEncounterSide side = plugin.getDataFor(player);
      if (side == null) {
        return;
      }
      CrewCompositionAPI losses = side.getCrewLossesDuringLastEngagement();
      if (losses == null) {
        return;
      }
      int crewLost = losses.getCrewInt();
      if (crewLost <= 0) {
        return;
      }
      NomadFleetData data = NomadFleetData.getInstance();
      float room = Arrivals.room(NomadModel.preview(data, null));
      float returned = Arrivals.take(room, crewLost * frac);
      if (returned >= 1f) {
        data.setResidents(data.getResidents() + returned);
        data.markChanged();
        NomadHistory.add(data,
            NomadStrings.get("nomad_history", "hist_hospital_return", Math.round(returned)));
      }
    } catch (Throwable ignore) {
    }
  }
}
