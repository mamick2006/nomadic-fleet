package nomad.ui;

import java.awt.Color;

import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

final class NomadCell {

  private NomadCell() {
  }

  static LabelAPI text(CustomPanelAPI row, float x, float y, float w, float h, String text,
      Color c, Alignment align, float pad) {
    TooltipMakerAPI el = row.createUIElement(w, h, false);
    LabelAPI l = el.addPara(text, c, pad);
    l.setAlignment(align);
    row.addUIElement(el).inTL(x, y);
    return l;
  }

  static LabelAPI box(CustomPanelAPI row, float boxX, float y, float boxW, float h, String text,
      Color c, Alignment align, float pad) {
    return text(row, NomadText.elementX(boxX), y, NomadText.element(boxW), h, text, c, align,
        pad);
  }

  static TooltipMakerAPI element(CustomPanelAPI row, float boxW, float h) {
    return row.createUIElement(NomadText.element(boxW), h, false);
  }

  static void place(CustomPanelAPI row, TooltipMakerAPI el, float boxX, float y) {
    row.addUIElement(el).inTL(NomadText.elementX(boxX), y);
  }

  private static final float HEAD_BAR_TEXT_DOWN = 1f;

  static CustomPanelAPI headBar(CustomPanelAPI maker, float w, String text, float textX) {
    CustomPanelAPI head = maker.createCustomPanel(w, NomadGrid.HEAD_H, NomadRowChrome.NONE);
    TooltipMakerAPI bar = head.createUIElement(w, NomadGrid.HEAD_H, false);
    bar.addSectionHeading(" ", NomadUi.base(), NomadUi.dark(), Alignment.MID, 0f);
    head.addUIElement(bar).inTL(0f, 0f);
    float box = Math.max(0f, w - 2f * textX);
    TooltipMakerAPI words = element(head, box, NomadGrid.HEAD_H);
    words.setParaFontOrbitron();
    words.addPara(NomadText.fit(text, box, NomadUi.BUTTON), NomadUi.base(), HEAD_BAR_TEXT_DOWN);
    place(head, words, textX, 0f);
    return head;
  }
}
