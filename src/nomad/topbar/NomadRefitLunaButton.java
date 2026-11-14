package nomad.topbar;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import lunalib.lunaRefit.BaseRefitButton;
import lunalib.lunaRefit.LunaRefitManager;

import nomad.NomadStrings;
import nomad.campaign.NomadCapacityUtil;
import nomad.campaign.NomadDedication;
import nomad.campaign.econ.NomadCapacity;
import nomad.data.NomadFleetData;
import nomad.ui.NomadDedicationPanel;
import nomad.ui.NomadUi;

public class NomadRefitLunaButton extends BaseRefitButton {

  private CustomPanelAPI screen;

  public static void registerIfLunaPresent() {
    if (Global.getSettings() == null || Global.getSettings().getModManager() == null
        || !Global.getSettings().getModManager().isModEnabled("lunalib")) {
      return;
    }
    try {
      if (!LunaRefitManager.hasButtonOfClass(NomadRefitLunaButton.class)) {
        LunaRefitManager.addRefitButton(new NomadRefitLunaButton());
      }
    } catch (Throwable t) {
      Global.getLogger(NomadRefitLunaButton.class).warn(
          "Nomad Flotilla: could not register the dedication panel with LunaLib's refit list (its refit API "
          + "has changed). Use the refit screen's own Flotilla button instead.", t);
    }
  }

  @Override
  public String getButtonName(FleetMemberAPI member, ShipVariantAPI variant) {
    return NomadStrings.get("nomad_dedication", "ded_button");
  }

  @Override
  public String getIconName(FleetMemberAPI member, ShipVariantAPI variant) {
    String sprite;
    try {
      sprite = Global.getSettings().getSpriteName("nomad", "nomad_member");
    } catch (Exception e) {
      sprite = null;
    }
    return sprite == null || sprite.isEmpty() ? super.getIconName(member, variant) : sprite;
  }

  @Override
  public boolean shouldShow(FleetMemberAPI member, ShipVariantAPI variant, MarketAPI market) {
    if (member == null) {
      return false;
    }
    return NomadFleetData.exists() && NomadFleetData.getInstance().isFounded()
        || variant != null && variant.hasHullMod(nomad.campaign.NomadManager.FLAGSHIP_HULLMOD_ID);
  }

  @Override
  public boolean isClickable(FleetMemberAPI member, ShipVariantAPI variant, MarketAPI market) {
    return NomadDedication.of(member, variant) != null;
  }

  @Override
  public boolean hasPanel(FleetMemberAPI member, ShipVariantAPI variant, MarketAPI market) {
    return NomadDedication.of(member, variant) != null;
  }

  @Override
  public float getPanelWidth(FleetMemberAPI member, ShipVariantAPI variant) {
    return NomadDedicationPanel.WIDTH;
  }

  @Override
  public float getPanelHeight(FleetMemberAPI member, ShipVariantAPI variant) {
    return NomadDedicationPanel.HEIGHT;
  }

  @Override
  public void initPanel(CustomPanelAPI backgroundPanel, FleetMemberAPI member, ShipVariantAPI variant,
      MarketAPI market) {
    if (backgroundPanel == null || member == null) {
      return;
    }
    screen = NomadDedicationPanel.create(member, variant);
    backgroundPanel.addComponent(screen).inTL(0f, 0f);
  }

  @Override
  public void advance(FleetMemberAPI member, ShipVariantAPI variant, Float amount, MarketAPI market) {
    if (screen != null && NomadDedicationPanel.consumeCloseRequest(screen)) {
      closePanel();
    }
  }

  @Override
  public void onPanelClose(FleetMemberAPI member, ShipVariantAPI variant, MarketAPI market) {
    screen = null;
  }

  @Override
  public boolean hasTooltip(FleetMemberAPI member, ShipVariantAPI variant, MarketAPI market) {
    return true;
  }

  @Override
  public void addTooltip(TooltipMakerAPI tooltip, FleetMemberAPI member, ShipVariantAPI variant,
      MarketAPI market) {
    tooltip.addPara(NomadStrings.get("nomad_dedication", "ded_button"), NomadUi.base(), 0f);
    float[] pair = NomadDedication.of(member, variant);
    if (pair == null) {
      tooltip.addPara(NomadStrings.get("nomad_dedication", "ded_needs_mod"), NomadUi.gray(), 3f);
      return;
    }
    NomadCapacity.Capacity gives = NomadCapacityUtil.forShip(member.getStats(), variant, pair[0], pair[1]);
    NomadCapacity.Capacity fitted = NomadCapacityUtil.fittedCapacity(member.getStats(), variant);
    tooltip.addPara(NomadStrings.get("nomad_hullmods", "hm_dedication"), 3f, NomadUi.hl(),
        (int) pair[0] + "%", (int) pair[1] + "%");
    tooltip.addPara(NomadStrings.get("nomad_hullmods", "hm_contribution"), 3f, NomadUi.hl(),
        NomadUi.i(gives.space), NomadUi.i(gives.berths));
    tooltip.addPara(NomadStrings.get("nomad_dedication", "ded_row_keeps"), 3f, NomadUi.hl(),
        NomadUi.i(Math.max(0f, fitted.space - gives.space)),
        NomadUi.i(Math.max(0f, fitted.berths - gives.berths)));
    if (!NomadUi.playerIsDocked()) {
      tooltip.addPara(NomadStrings.get("nomad_dedication", "ded_needs_dock"), NomadUi.warn(), 3f);
    }
  }
}
