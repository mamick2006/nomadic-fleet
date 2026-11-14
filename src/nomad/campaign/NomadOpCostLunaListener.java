package nomad.campaign;

import com.fs.starfarer.api.Global;

import lunalib.lunaSettings.LunaSettings;
import lunalib.lunaSettings.LunaSettingsListener;

public class NomadOpCostLunaListener implements LunaSettingsListener {

  public static void registerIfLunaPresent() {
    if (Global.getSettings() == null || Global.getSettings().getModManager() == null
        || !Global.getSettings().getModManager().isModEnabled("lunalib")) {
      return;
    }
    try {
      if (!LunaSettings.hasSettingsListenerOfClass(NomadOpCostLunaListener.class)) {
        LunaSettings.addSettingsListener(new NomadOpCostLunaListener());
      }
    } catch (Throwable t) {
      Global.getLogger(NomadOpCostLunaListener.class).warn(
          "Nomad Flotilla: could not register for LunaLib settings changes (its settings API has "
          + "changed). A changed ordnance point multiplier takes on the next game load.", t);
    }
  }

  @Override
  public void settingsChanged(String modId) {
    if (!NomadSettings.MOD_ID.equals(modId)) {
      return;
    }
    NomadEcon.settingsChanged();
    NomadModel.settingsChanged();
    NomadOpCost.apply();
  }
}
