package nomad.campaign.split;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.VisualPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.util.Misc;

import nomad.NomadStrings;
import nomad.campaign.NomadManager;
import nomad.campaign.NomadModel;
import nomad.campaign.econ.RejoinReport;
import nomad.campaign.econ.model.Detached;
import nomad.campaign.econ.model.NeedsLevel;
import nomad.campaign.econ.model.TickSheet;
import nomad.data.NomadFleetData;
import nomad.ui.NomadDedicationLayout;
import nomad.ui.NomadFlotillaView;
import nomad.ui.NomadNeedsPicture;
import nomad.ui.NomadTopBarTip;
import nomad.ui.NomadUi;

public class NomadFleetInteraction implements InteractionDialogPlugin {

  private static final String OPT_REJOIN = "nomad_rejoin";
  private static final String OPT_LEAVE = "nomad_leave";

  private static final float PICTURE_W = 480f;
  private static final float PICTURE_H = 300f;

  private InteractionDialogAPI dialog;
  private CampaignFleetAPI fleet;

  @Override
  public void init(InteractionDialogAPI dialog) {
    this.dialog = dialog;
    SectorEntityToken target = dialog.getInteractionTarget();
    this.fleet = (target instanceof CampaignFleetAPI) ? (CampaignFleetAPI) target : null;

    TextPanelAPI text = dialog.getTextPanel();
    text.addPara(NomadStrings.get("nomad_split", "dlg_comm_open"));

    int ships = fleet != null ? fleet.getFleetData().getMembersListCopy().size() : 0;
    TickSheet sheet = sheet();
    RejoinReport report = RejoinReport.of(sheet);
    showPicture(sheet == null || sheet.s.paused ? null : sheet.needs.level);
    Detached.Settlement owed = NomadSplitter.owedSettlement();
    if (!hasFlagshipAboard()) {
      text.addPara(NomadStrings.get("nomad_split", "dlg_flagship_gone"));
    } else {
      text.addPara(NomadStrings.get("nomad_split",
          ships == 1 ? "dlg_holding_one" : "dlg_holding_many", ships));
    }
    addReport(text, report);
    addOwedLine(text, owed);

    buildOptions();
  }

  private TickSheet sheet() {
    if (fleet == null || !NomadFleetData.exists()) {
      return null;
    }
    return NomadModel.preview(NomadFleetData.getInstance(), fleet);
  }

  private void showPicture(NeedsLevel level) {
    try {
      VisualPanelAPI visual = dialog.getVisualPanel();
      if (visual == null) {
        return;
      }
      if (NomadDedicationLayout.pictureDimmed(level)) {
        NomadNeedsPicture picture = NomadNeedsPicture.of(level, PICTURE_W / PICTURE_H);
        if (picture != null) {
          visual.showCustomPanel(PICTURE_W, PICTURE_H, picture.asPanel());
        }
        return;
      }
      visual.showImagePortion("illustrations", NomadNeedsPicture.key(level), PICTURE_W, PICTURE_H,
          0f, 0f, PICTURE_W, PICTURE_H);
    } catch (RuntimeException e) {
      Global.getLogger(NomadFleetInteraction.class).warn("Nomad Flotilla: the rejoin dialog could"
          + " not show the Basic Needs picture, so it opens without one.", e);
    }
  }

  private static void addReport(TextPanelAPI text, RejoinReport report) {
    if (report.levelLow()) {
      String level = NomadFlotillaView.levelName(report.level);
      String para = NomadStrings.get("nomad_intel", "intel_status_needs", level);
      if (report.residentsLeave()) {
        String leaving = NomadUi.pct(report.leavePerMonth);
        LabelAPI label = text.addPara(
            para + " " + NomadStrings.get("nomad_flotilla", "needs_effect_leave", leaving));
        label.setHighlight(level, leaving);
        label.setHighlightColors(NomadTopBarTip.levelColor(report.level), NomadUi.hl());
      } else {
        LabelAPI label = text.addPara(para);
        label.setHighlight(level);
        label.setHighlightColor(NomadTopBarTip.levelColor(report.level));
      }
    }
    List<String> said = new ArrayList<String>();
    List<String> counts = new ArrayList<String>();
    if (report.suspended > 0) {
      said.add(NomadStrings.get("nomad_split",
          report.suspended == 1 ? "koflow_dlg_suspended_one" : "koflow_dlg_suspended_many",
          Integer.valueOf(report.suspended)));
      counts.add(String.valueOf(report.suspended));
    }
    if (report.unstaffed > 0) {
      said.add(NomadStrings.get("nomad_intel",
          report.unstaffed == 1 ? "intel_status_unstaffed_one" : "intel_status_unstaffed_many",
          Integer.valueOf(report.unstaffed)));
      counts.add(String.valueOf(report.unstaffed));
    }
    if (said.isEmpty()) {
      return;
    }
    StringBuilder para = new StringBuilder();
    for (String sentence : said) {
      para.append(para.length() > 0 ? " " : "").append(sentence);
    }
    LabelAPI label = text.addPara(para.toString());
    label.setHighlight(counts.toArray(new String[0]));
    label.setHighlightColor(NomadUi.hl());
  }

  private static void addOwedLine(TextPanelAPI text, Detached.Settlement owed) {
    if (owed.isNothing()) {
      return;
    }
    String all = Misc.getWithDGS(owed.supplies + owed.rest);
    switch (RejoinReport.owed(owed.supplies, owed.rest)) {
      case CARGO_AND_CREDITS:
        text.addPara(NomadStrings.get("nomad_split", "detach_dlg_owed_credits", all,
            Misc.getWithDGS(owed.supplies), Misc.getDGSCredits(owed.credits)));
        break;
      case CREDITS:
        text.addPara(NomadStrings.get("nomad_split", "detach_dlg_owed_credits_only", all,
            Misc.getDGSCredits(owed.credits)));
        break;
      case CARGO:
        text.addPara(NomadStrings.get("nomad_split", "detach_dlg_owed", all));
        break;
      default:
        break;
    }
    if (owed.forgiven >= 0.5f) {
      text.addPara(NomadStrings.get("nomad_split", "fixglue_dlg_owed_carried",
          Misc.getWithDGS(Math.round(owed.forgiven))));
    }
  }

  private void buildOptions() {
    OptionPanelAPI opts = dialog.getOptionPanel();
    opts.clearOptions();
    opts.addOption("Rejoin the fleet", OPT_REJOIN);
    opts.addOption("Leave", OPT_LEAVE);
    dialog.setOptionOnEscape("Leave", OPT_LEAVE);
  }

  @Override
  public void optionSelected(String optionText, Object optionData) {
    if (OPT_REJOIN.equals(optionData)) {
      dialog.addOptionSelectedText(optionData);
      NomadSplitter.rejoin(fleet);
      dialog.getTextPanel().addPara(NomadStrings.get("nomad_split", "dlg_rejoined"));
      OptionPanelAPI opts = dialog.getOptionPanel();
      opts.clearOptions();
      opts.addOption("Leave", OPT_LEAVE);
      dialog.setOptionOnEscape("Leave", OPT_LEAVE);
    } else if (OPT_LEAVE.equals(optionData)) {
      dialog.dismiss();
    }
  }

  private boolean hasFlagshipAboard() {
    if (fleet == null) {
      return false;
    }
    for (FleetMemberAPI m : fleet.getFleetData().getMembersListCopy()) {
      if (m.getVariant() != null && m.getVariant().hasHullMod(NomadManager.FLAGSHIP_HULLMOD_ID)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void optionMousedOver(String optionText, Object optionData) {
  }

  @Override
  public void advance(float amount) {
  }

  @Override
  public void backFromEngagement(EngagementResultAPI battleResult) {
  }

  @Override
  public Object getContext() {
    return null;
  }

  @Override
  public Map<String, MemoryAPI> getMemoryMap() {
    return new HashMap<String, MemoryAPI>();
  }
}
