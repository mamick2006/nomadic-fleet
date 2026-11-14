package nomad.ui;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import nomad.NomadStrings;
import nomad.campaign.NomadMiningResolver;
import nomad.campaign.econ.model.FlotillaState;
import nomad.campaign.econ.model.Mining;
import nomad.campaign.econ.model.MiningSystem;
import nomad.campaign.econ.model.Rules;
import nomad.campaign.econ.model.WorkState;
import nomad.campaign.econ.model.Works;

public final class NomadMining {

  private NomadMining() {
  }

  public static final class State {
    public boolean active;
    public String bodyName = "";
    public final Map<String, Float> tiers = new LinkedHashMap<String, Float>();
  }

  public static State resolve(CampaignFleetAPI fleet) {
    State st = new State();
    NomadMiningResolver.Result r = NomadMiningResolver.resolve(fleet);
    st.active = r.active;
    st.bodyName = r.bodyName;
    st.tiers.putAll(r.deposits);
    return st;
  }

  public static State resolveCurrent() {
    return resolve(NomadUi.flotillaFleet());
  }

  public static PlanetAPI nearestBody(CampaignFleetAPI fleet) {
    return NomadMiningResolver.nearestEligibleBody(fleet, Float.MAX_VALUE);
  }

  public static boolean inRange(CampaignFleetAPI fleet, PlanetAPI body, float radius) {
    return NomadMiningResolver.inRange(fleet, body, radius);
  }

  public static State stateFor(PlanetAPI body) {
    State st = new State();
    NomadMiningResolver.Result r = NomadMiningResolver.resolveFor(body);
    st.active = r.active;
    st.bodyName = r.bodyName;
    st.tiers.putAll(r.deposits);
    return st;
  }

  public static MiningSystem.Readout readout(CampaignFleetAPI fleet, FlotillaState s, Rules k) {
    boolean hyperspace = fleet == null || fleet.getContainingLocation() == null
        || fleet.getContainingLocation().isHyperspace();
    WorkState rig = s == null ? null : s.work(Works.MINING_RIG);
    return MiningSystem.readout(s == null ? null : s.site, hyperspace, rig != null && rig.remote, k);
  }

  public static void addReadout(TooltipMakerAPI t, MiningSystem.Readout r, WorkState rig, Rules k,
      float pad) {
    if (t == null || r == null) {
      return;
    }
    switch (r.kind) {
      case IN_RANGE:
        para(t, "mining_in_range", NomadUi.hl(), pad, r.body);
        break;
      case REMOTE:
        para(t, "mining_remote", NomadUi.hl(), pad, r.body);
        break;
      case REMOTE_ASTEROIDS:
        t.addPara(NomadStrings.get("nomad_flotilla", "mining_remote_asteroids"), pad);
        break;
      case IDLE_HYPERSPACE:
        t.addPara(NomadStrings.get("nomad_flotilla", "mining_idle_hyperspace"), NomadUi.warn(),
            pad);
        break;
      case IDLE_EMPTY:
        t.addPara(NomadStrings.get("nomad_flotilla", "mining_idle_empty"), NomadUi.warn(), pad);
        break;
      default:
        t.addPara(NomadStrings.get("nomad_flotilla", "mining_idle_out_of_range"), NomadUi.warn(),
            pad);
        break;
    }
    if (!r.idle()) {
      StringBuilder list = new StringBuilder();
      for (Map.Entry<String, Float> e : r.shares.entrySet()) {
        if (list.length() > 0) {
          list.append(", ");
        }
        list.append(NomadStrings.get("nomad_flotilla", "mining_share", goodName(e.getKey()),
            NomadUi.f1(e.getValue().floatValue())));
      }
      para(t, "mining_shares", NomadUi.hl(), 3f, list.toString());
    }
    float left = Mining.remoteDaysLeft(rig, k);
    if (left > 0f) {
      para(t, "act_remote_turning_on", NomadUi.hl(), 3f, NomadUi.i((float) Math.ceil(left)));
    }
  }

  private static void para(TooltipMakerAPI t, String key, Color hl, float pad, String... args) {
    LabelAPI line = t.addPara(NomadStrings.get("nomad_flotilla", key, (Object[]) args), pad);
    line.setHighlight(args);
    line.setHighlightColor(hl);
  }

  private static String goodName(String good) {
    try {
      return Global.getSettings().getCommoditySpec(good).getName();
    } catch (RuntimeException e) {
      return good.replace('_', ' ');
    }
  }

  public static String tierLabel(float mult) {
    if (mult >= 3.0f) {
      return "ultrarich";
    }
    if (mult >= 2.0f) {
      return "rich";
    }
    if (mult >= 1.5f) {
      return "abundant";
    }
    if (mult >= 1.0f) {
      return "moderate";
    }
    return "sparse";
  }

  public static Color tierColor(float mult) {
    if (mult >= 1.5f) {
      return NomadUi.good();
    }
    if (mult >= 1.0f) {
      return NomadUi.hl();
    }
    return NomadUi.warn();
  }
}
