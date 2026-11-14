package nomad.campaign;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.loading.HullModSpecAPI;

public final class NomadOpCost {

  private NomadOpCost() {
  }

  private static final int FRIGATE = 0;
  private static final int DESTROYER = 1;
  private static final int CRUISER = 2;
  private static final int CAPITAL = 3;

  private static Map<String, int[]> csvBase;

  private static boolean disabled;

  public static void apply() {
    if (disabled) {
      return;
    }
    SettingsAPI settings = Global.getSettings();
    if (settings == null) {
      return;
    }
    try {
      HullModSpecAPI[] specs = new HullModSpecAPI[NomadManager.ALL_HULLMOD_IDS.length];
      for (int i = 0; i < specs.length; i++) {
        specs[i] = settings.getHullModSpec(NomadManager.ALL_HULLMOD_IDS[i]);
        if (specs[i] == null) {
          throw new IllegalStateException("no loaded hullmod spec for " + NomadManager.ALL_HULLMOD_IDS[i]);
        }
      }
      if (csvBase == null) {
        Map<String, int[]> captured = new LinkedHashMap<String, int[]>();
        for (int i = 0; i < specs.length; i++) {
          captured.put(NomadManager.ALL_HULLMOD_IDS[i], costsOf(specs[i]));
        }
        csvBase = captured;
      }
      float mult = NomadSettings.opCostMult();
      int[][] target = new int[specs.length][];
      for (int i = 0; i < specs.length; i++) {
        target[i] = scaled(csvBase.get(NomadManager.ALL_HULLMOD_IDS[i]), mult);
      }
      for (int i = 0; i < specs.length; i++) {
        specs[i].setFrigateCost(target[i][FRIGATE]);
        specs[i].setDestroyerCost(target[i][DESTROYER]);
        specs[i].setCruiserCost(target[i][CRUISER]);
        specs[i].setCapitalCost(target[i][CAPITAL]);
      }
    } catch (Throwable t) {
      disabled = true;
      Global.getLogger(NomadOpCost.class).warn(
          "Nomad Flotilla: could not apply the ordnance point cost multiplier. The Nomad hullmods keep "
          + "the ordnance point costs in data/hullmods/hull_mods.csv for this session.", t);
    }
  }

  private static int[] costsOf(HullModSpecAPI spec) {
    return new int[] { spec.getFrigateCost(), spec.getDestroyerCost(), spec.getCruiserCost(),
        spec.getCapitalCost() };
  }

  private static int[] scaled(int[] base, float mult) {
    int[] out = new int[base.length];
    for (int i = 0; i < base.length; i++) {
      out[i] = Math.max(0, Math.round(base[i] * mult));
    }
    return out;
  }
}
