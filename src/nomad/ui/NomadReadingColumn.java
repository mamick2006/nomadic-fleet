package nomad.ui;

import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

final class NomadReadingColumn {

  private final NomadReadingLayout.Column at;
  private final CustomPanelAPI holder;
  final TooltipMakerAPI text;

  NomadReadingColumn(CustomPanelAPI panel, NomadReadingLayout.Column at, String pictureKey,
      float viewH) {
    this.at = at;
    this.holder = panel.createCustomPanel(at.holderW(), viewH, null);
    this.text = holder.createUIElement(NomadText.element(at.w), viewH, false);
    CustomPanelAPI picture = NomadHero.whole(panel, pictureKey, at.w, at.pictureH);
    if (picture != null) {
      text.addCustom(picture, 0f);
      text.addSpacer(NomadReadingLayout.PICTURE_GAP);
    }
  }

  TooltipMakerAPI place(CustomPanelAPI panel, float top, float viewH) {
    float h = text.getHeightSoFar();
    holder.addUIElement(text).inTL(at.holderX(), 0f).setSize(NomadText.element(at.w), h);
    holder.getPosition().setSize(at.holderW(), h);
    TooltipMakerAPI scroller = panel.createUIElement(at.scrollW, viewH, true);
    scroller.addCustom(holder, 0f);
    panel.addUIElement(scroller).inTL(at.scrollX, top);
    return scroller;
  }
}
