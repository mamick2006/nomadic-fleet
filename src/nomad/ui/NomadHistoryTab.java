package nomad.ui;

import java.util.List;

import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import nomad.NomadStrings;
import nomad.campaign.NomadHistoryLines;
import nomad.data.NomadFleetData;
import nomad.intel.NomadIntel;

public final class NomadHistoryTab {

  private NomadHistoryTab() {
  }

  private static final String PICTURE = "nomad_illus_history";

  private static final float BAR_H = 18f;
  private static final float GROUP_GAP = 12f;
  private static final float HEAD_GAP = 2f;
  private static final float BAR_INSET = 8f;
  private static final float BAR_BETWEEN = 20f;
  private static final float BAR_TEXT_PAD = 1f;

  private static final float ROW_H = 22f;
  private static final float LINE_H = 15f;
  private static final float TEXT_X = 6f;
  private static final float TEXT_PAD = 3f;

  public static TooltipMakerAPI build(CustomPanelAPI panel, float width, float bodyHeight,
      NomadIntel intel, NomadFleetData data) {
    NomadReadingLayout.Column at = NomadReadingLayout.history(width, bodyHeight);
    float rowW = Math.max(LINE_H, at.w);
    NomadReadingColumn column = new NomadReadingColumn(panel, at, PICTURE, bodyHeight);
    TooltipMakerAPI body = column.text;

    List<NomadHistoryLines.Entry> entries = NomadHistoryLines.entries(data.getHistory());
    if (entries.isEmpty()) {
      body.addPara(NomadStrings.get("nomad_history", "hist_empty"), NomadUi.gray(), 0f);
    }
    boolean first = true;
    boolean afterHead = false;
    for (NomadHistoryLines.Entry e : entries) {
      if (e.kind == NomadHistoryLines.Kind.ROW) {
        row(panel, body, rowW, e.left, afterHead ? HEAD_GAP : 0f);
        afterHead = false;
      } else {
        String left = e.kind == NomadHistoryLines.Kind.NOW
            ? NomadStrings.get("nomad_history", "hist_head_now") : e.left;
        heading(panel, body, rowW, left, e.right, first ? 0f : GROUP_GAP);
        afterHead = true;
      }
      first = false;
    }

    return column.place(panel, NomadTabbedPanels.BODY_TOP, bodyHeight);
  }

  private static void heading(CustomPanelAPI panel, TooltipMakerAPI body, float rowW, String left,
      String right, float pad) {
    CustomPanelAPI head = NomadCell.headBar(panel, rowW, left, TEXT_X);

    float box = NomadText.need(right, NomadUi.PARA);
    float room = rowW - BAR_INSET - BAR_BETWEEN - NomadText.need(left, NomadUi.BUTTON)
        - TEXT_X;
    if (box > 0f && box <= room) {
      LabelAPI l = NomadCell.box(head, rowW - BAR_INSET - box, 0f, box, BAR_H, right,
          NomadUi.text(), Alignment.RMID, BAR_TEXT_PAD);
      NomadTable.highlightNumbers(l, right, NomadUi.hl());
    }
    body.addCustom(head, pad);
  }

  private static void row(CustomPanelAPI panel, TooltipMakerAPI body, float rowW, String line,
      float pad) {
    float box = rowW - 2f * TEXT_X;
    int lines = NomadText.fits(line, box, NomadUi.PARA) ? 1
        : NomadText.lines(line, box, NomadUi.PARA);
    float rowH = ROW_H + (lines - 1) * LINE_H;
    CustomPanelAPI row = panel.createCustomPanel(rowW, rowH,
        NomadRowChrome.rule(null, NomadUi.dark(), 55));
    LabelAPI l = NomadCell.box(row, TEXT_X, 0f, box, rowH, line, NomadUi.text(), Alignment.LMID,
        TEXT_PAD);
    NomadTable.highlightNumbers(l, line, NomadUi.hl());
    body.addCustom(row, pad);
  }
}
