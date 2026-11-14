package nomad.crossmod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fs.starfarer.api.Global;

import nomad.campaign.econ.Work;

public final class NomadCqFurnishing {

  private static final String CQ_MOD_ID = "captains_quarters";

  public static final String FOUNDED_FLAG = "$nomad_founded";

  private static boolean warned;

  private NomadCqFurnishing() {
  }

  public static List<String> owned() {
    if (!isOwned()) {
      return Collections.<String>emptyList();
    }
    List<String> out = new ArrayList<String>(1);
    out.add(Work.CQ_TALLY_BOARD);
    return out;
  }

  public static boolean isOwned() {
    if (!isCqEnabled()) {
      return false;
    }
    if (NomadCqSeam.isBroken()) {
      warnOnce();
      return false;
    }
    return NomadCqSeam.isOwned(Work.CQ_TALLY_BOARD);
  }

  public static void markFounded() {
    try {
      if (Global.getSector() == null
          || Global.getSector().getMemoryWithoutUpdate().getBoolean(FOUNDED_FLAG)) {
        return;
      }
      Global.getSector().getMemoryWithoutUpdate().set(FOUNDED_FLAG, true);
    } catch (Throwable t) {
    }
  }

  public static void unmarkFounded() {
    try {
      if (Global.getSector() != null) {
        Global.getSector().getMemoryWithoutUpdate().unset(FOUNDED_FLAG);
      }
    } catch (Throwable t) {
    }
  }

  private static boolean isCqEnabled() {
    try {
      return Global.getSettings().getModManager().isModEnabled(CQ_MOD_ID);
    } catch (Throwable t) {
      return false;
    }
  }

  private static void warnOnce() {
    if (warned) {
      return;
    }
    warned = true;
    Global.getLogger(NomadCqFurnishing.class).warn("Nomad Flotilla: Captain's Quarters is installed"
        + " but cq.api.CaptainsQuartersAPI is not the shape this mod expects, most likely a version"
        + " mismatch between the two. The Flotilla Tally Board's efficiency bonus is off for this"
        + " session; nothing else is affected.");
  }
}
