package nomad.campaign.split;

import java.util.ArrayList;
import java.util.List;

import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignEventListener.FleetDespawnReason;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import com.fs.starfarer.api.fleet.FleetMemberAPI;

public class NomadFleetEventListener implements FleetEventListener {

  private final List<FleetMemberAPI> roster = new ArrayList<FleetMemberAPI>();

  public NomadFleetEventListener() {
  }

  public NomadFleetEventListener(CampaignFleetAPI fleet) {
    refreshRoster(fleet);
  }

  public void refreshRoster(CampaignFleetAPI fleet) {
    roster.clear();
    if (fleet != null) {
      roster.addAll(fleet.getFleetData().getMembersListCopy());
    }
  }

  @Override
  public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner, BattleAPI battle) {
    if (fleet == null) {
      return;
    }
    List<FleetMemberAPI> current = fleet.getFleetData().getMembersListCopy();
    if (current.isEmpty()) {
      return;
    }
    List<FleetMemberAPI> lost = new ArrayList<FleetMemberAPI>();
    for (FleetMemberAPI m : roster) {
      if (!current.contains(m)) {
        lost.add(m);
      }
    }
    if (!lost.isEmpty()) {
      NomadSplitter.handlePartialLoss(fleet, lost);
    }
    refreshRoster(fleet);
    NomadSplitter.holdStation(fleet);
  }

  @Override
  public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, FleetDespawnReason reason, Object param) {
    boolean rejoining = fleet != null
        && fleet.getMemoryWithoutUpdate().getBoolean(NomadSplitter.REJOINING_FLAG);
    if (NomadSplitter.endsFlotilla(fleet, rejoining)) {
      NomadSplitter.handleTotalLoss(fleet, roster);
    } else if (!rejoining) {
      NomadSplitter.handleRemnantLoss(fleet, roster);
    }
    roster.clear();
  }
}
