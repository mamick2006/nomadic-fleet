package nomad.crossmod;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.impl.items.BaseSpecialItemPlugin;
import com.fs.starfarer.api.util.Misc;

import nomad.NomadStrings;

public class NomadFurnishingItem extends BaseSpecialItemPlugin {

  @Override
  public boolean hasRightClickAction() {
    return NomadCqSeam.isKnownFurnishing(getId());
  }

  @Override
  public boolean shouldRemoveOnRightClickAction() {
    return NomadCqSeam.isAvailable() && NomadCqSeam.isKnownFurnishing(getId())
        && !NomadCqSeam.isOwned(getId());
  }

  @Override
  public void performRightClickAction() {
    if (NomadCqSeam.grant(getId())) {
      return;
    }
    say(NomadCqSeam.isInstalled() ? "cq_click_owned" : "cq_click_absent",
        !NomadCqSeam.isInstalled());
  }

  private void say(String key, boolean negative) {
    try {
      Global.getSector().getCampaignUI().getMessageDisplay().addMessage(
          NomadStrings.get("nomad_crossmod", key),
          negative ? Misc.getNegativeHighlightColor() : Misc.getGrayColor());
    } catch (Throwable t) {
    }
  }
}
