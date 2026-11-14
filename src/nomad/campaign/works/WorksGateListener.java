package nomad.campaign.works;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.listeners.GateTransitListener;

import nomad.campaign.NomadModel;
import nomad.data.NomadFleetData;

public class WorksGateListener implements GateTransitListener {

  @Override
  public void reportFleetTransitingGate(CampaignFleetAPI fleet, SectorEntityToken gateFrom,
      SectorEntityToken gateTo) {
    if (fleet == null || !NomadFleetData.exists()) {
      return;
    }
    CampaignFleetAPI carrier = NomadModel.flotillaFleet();
    if (carrier == null || carrier != fleet) {
      return;
    }
    NomadFleetData data = NomadFleetData.getInstance();
    if (!data.isGateTransitThisMonth()) {
      data.setGateTransitThisMonth(true);
      data.markChanged();
    }
  }
}
