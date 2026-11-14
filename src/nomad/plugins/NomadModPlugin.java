package nomad.plugins;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;

import nomad.campaign.NomadEconomyScript;
import nomad.campaign.NomadOpCost;
import nomad.campaign.NomadOpCostLunaListener;
import nomad.campaign.NomadPickupListener;
import nomad.campaign.NomadSettings;
import nomad.campaign.NomadUniquenessScript;
import nomad.campaign.split.NomadSplitter;
import nomad.data.NomadFleetData;
import nomad.topbar.NomadMgmtInjector;
import nomad.topbar.NomadRefitInjector;
import nomad.topbar.NomadRefitLunaButton;
import nomad.ui.NomadMgmtScreen;
import nomad.topbar.NomadTopBarScript;
import nomad.ui.NomadConsolePanel;
import nomad.ui.NomadShipHover;

public class NomadModPlugin extends BaseModPlugin {

  @Override
  public void onNewGame() {
  }

  @Override
  public void onApplicationLoad() {
    nomad.api.NomadWorks.install(new WorksReader());
    try {
      NomadRefitLunaButton.registerIfLunaPresent();
    } catch (Throwable ignore) {
    }
    try {
      NomadOpCostLunaListener.registerIfLunaPresent();
    } catch (Throwable ignore) {
    }
    NomadOpCost.apply();
  }

  @Override
  public void onDevModeF8Reload() {
    nomad.campaign.NomadEcon.settingsChanged();
    nomad.campaign.NomadModel.settingsChanged();
    NomadOpCost.apply();
  }

  @Override
  public void onGameLoad(boolean newGame) {
    NomadOpCost.apply();
    SectorAPI sector = Global.getSector();
    if (sector == null) {
      return;
    }
    NomadConsolePanel.resetStaticsForLoad();
    NomadMgmtInjector.resetStaticsForLoad();
    NomadMgmtScreen.resetStaticsForLoad();
    NomadPickupListener.resetStaticsForLoad();
    NomadShipHover.resetStaticsForLoad();
    NomadRefitInjector.resetStaticsForLoad();
    for (String problem : nomad.campaign.tables.TableEffects.onGameLoad()) {
      Global.getLogger(NomadModPlugin.class).warn("Nomad Flotilla: " + problem);
    }
    nomad.campaign.NomadModel.onGameLoad();
    nomad.campaign.tables.ItemTable.onGameLoad();
    nomad.campaign.NomadEcon.onGameLoad();
    if (!sector.hasTransientScript(NomadUniquenessScript.class)) {
      sector.addTransientScript(new NomadUniquenessScript());
    }
    if (!sector.hasTransientScript(nomad.campaign.works.WorksAssertScript.class)) {
      sector.addTransientScript(new nomad.campaign.works.WorksAssertScript());
    }
    ListenerManagerAPI lm = sector.getListenerManager();
    if (!lm.hasListenerOfClass(NomadEconomyScript.class)) {
      lm.addListener(new NomadEconomyScript());
    }
    if (!lm.hasListenerOfClass(NomadPickupListener.class)) {
      lm.addListener(new NomadPickupListener(), true);
    }
    if (!lm.hasListenerOfClass(nomad.campaign.NomadDedicationHeal.class)) {
      lm.addListener(new nomad.campaign.NomadDedicationHeal(), true);
    }
    if (!lm.hasListenerOfClass(nomad.campaign.works.WorksGateListener.class)) {
      lm.addListener(new nomad.campaign.works.WorksGateListener(), true);
    }
    boolean hasWorksListener = false;
    for (com.fs.starfarer.api.campaign.CampaignEventListener l : sector.getAllListeners()) {
      if (l instanceof nomad.campaign.works.WorksCampaignEventListener) {
        hasWorksListener = true;
        break;
      }
    }
    if (!hasWorksListener) {
      sector.addTransientListener(new nomad.campaign.works.WorksCampaignEventListener());
    }
    sector.registerPlugin(new NomadCampaignPlugin());
    NomadSplitter.onGameLoad();
    nomad.campaign.NomadDedicationHeal.reconcileOnLoad();
    if (NomadFleetData.exists()) {
      nomad.campaign.NomadManager manager = nomad.campaign.NomadManager.getInstance();
      if (manager != null) {
        manager.rescanForFlagship();
      }
      restorePluginEffects(NomadFleetData.getInstance());
    }
    try {
      if (!sector.hasTransientScript(NomadTopBarScript.class)) {
        sector.addTransientScript(new NomadTopBarScript());
      }
    } catch (Throwable ignore) {
    }
    if (!NomadSettings.tunableBool("nomad_mgmt_intel_only", false)) {
      try {
        if (!sector.hasTransientScript(NomadMgmtInjector.class)) {
          sector.addTransientScript(new NomadMgmtInjector());
        }
      } catch (Throwable ignore) {
      }
    }
    try {
      if (!sector.hasTransientScript(NomadRefitInjector.class)) {
        sector.addTransientScript(new NomadRefitInjector());
      }
    } catch (Throwable ignore) {
    }
  }

  private static final class WorksReader implements nomad.api.NomadWorks.Source {
    @Override
    public java.util.List<nomad.campaign.econ.model.WorkState> works() {
      NomadFleetData data = NomadFleetData.getOrNull();
      return data != null && data.isFounded() ? data.getWorkStates() : null;
    }
  }

  private static void restorePluginEffects(NomadFleetData data) {
    nomad.campaign.econ.EconConstants k = nomad.campaign.NomadEcon.constants();
    if (k.workPlugins.isEmpty() && k.itemPlugins.isEmpty()) {
      return;
    }
    try {
      for (String problem : nomad.campaign.tables.TableEffects.restoreActive(
          nomad.campaign.NomadModel.preview(data, null), data.getActiveWorkEffects())) {
        Global.getLogger(NomadModPlugin.class).warn("Nomad Flotilla: " + problem);
      }
    } catch (RuntimeException e) {
      Global.getLogger(NomadModPlugin.class).warn("Nomad Flotilla: could not restore other mods'"
          + " Work and item effects on load (" + e + "). They stay off until the next economy tick,"
          + " so a price one of them lowers reads full until then.");
    }
  }
}
