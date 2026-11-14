package nomad.plugins;

import com.fs.starfarer.api.PluginPick;
import com.fs.starfarer.api.campaign.BaseCampaignPlugin;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.SectorEntityToken;

import nomad.campaign.split.NomadFleetInteraction;
import nomad.campaign.split.NomadSplitter;

public class NomadCampaignPlugin extends BaseCampaignPlugin {

  @Override
  public String getId() {
    return "nomad_campaignPlugin";
  }

  @Override
  public boolean isTransient() {
    return true;
  }

  @Override
  public PluginPick<InteractionDialogPlugin> pickInteractionDialogPlugin(SectorEntityToken target) {
    if (target instanceof CampaignFleetAPI && target.hasTag(NomadSplitter.FLEET_TAG)) {
      return new PluginPick<InteractionDialogPlugin>(new NomadFleetInteraction(), PickPriority.MOD_SPECIFIC);
    }
    return null;
  }
}
