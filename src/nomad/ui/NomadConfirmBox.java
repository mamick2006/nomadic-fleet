package nomad.ui;

import java.awt.Color;
import java.util.List;

import org.lwjgl.input.Keyboard;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import nomad.campaign.econ.model.WorkDef;
import nomad.campaign.econ.model.WorkState;
import nomad.data.NomadFleetData;

final class NomadConfirmBox {

  private NomadConfirmBox() {
  }

  static final float W = 480f;
  static final float SPLIT_W = 640f;
  static final float EDGE = 16f;
  static final float TITLE_Y = 14f;
  static final float TITLE_LINE = 21f;
  static final float BODY_GAP = 15f;
  static final float SPLIT_GAP = 9f;
  static final float CARD_W = 190f;
  static final float CARD_H = NomadWorkCardModel.NAME_H + NomadWorkCardModel.IMAGE_H;
  static final float COLUMN_X = 222f;
  static final float COLUMN_DOWN = 2f;
  static final float FOOT_GAP = 8f;
  static final float BUTTON_W = 150f;
  static final float BUTTON_H = 24f;
  static final float BUTTON_GAP = 16f;
  static final float BUTTON_PAD = 16f;
  private static final float ROOM = 10f;
  private static final int DIM = 168;
  private static final Color GROUND = new Color(3, 7, 10);

  static CustomPanelAPI build(CustomPanelAPI content, float cw, float ch, Object buttonId,
      NomadFleetData data) {
    NomadConfirm.Ask ask = NomadConfirm.ask(buttonId, data);
    if (ask == null) {
      return null;
    }
    Veil veil = new Veil();
    CustomPanelAPI over = content.createCustomPanel(cw, ch, veil);
    float w = Math.min(ask.split ? SPLIT_W : W, cw);
    float inner = w - 2f * EDGE;

    float titleH = NomadText.lines(ask.question, inner, NomadConfirm.TITLE) * TITLE_LINE;
    TooltipMakerAPI title = NomadCell.element(over, inner, titleH);
    NomadConfirm.question(title, ask);
    float y = TITLE_Y + titleH;

    CustomPanelAPI card = null;
    float cardY = 0f;
    TooltipMakerAPI body;
    float bodyX = EDGE;
    float bodyY;
    TooltipMakerAPI foot = null;
    float footY = 0f;
    float bottom;
    if (ask.split) {
      bodyY = y + SPLIT_GAP;
      body = NomadCell.element(over, inner, ROOM);
      NomadSplitConfirm.body(body, 0f, NomadSplitConfirm.BLEED_BOX);
      bottom = bodyY + body.getHeightSoFar();
    } else if (ask.workId != null) {
      cardY = y + BODY_GAP;
      card = card(over, ask);
      bodyX = COLUMN_X;
      bodyY = cardY + COLUMN_DOWN;
      float columnW = w - EDGE - COLUMN_X;
      boolean beside = NomadConfirm.leaveFits(ask, columnW);
      body = NomadCell.element(over, columnW, ROOM);
      NomadConfirm.column(body, ask, 0f, beside);
      bottom = Math.max(cardY + CARD_H, bodyY + body.getHeightSoFar());
      if (!beside) {
        footY = bottom + FOOT_GAP;
        foot = NomadCell.element(over, inner, ROOM);
        NomadConfirm.marked(foot, ask.leaveText(), NomadUi.bad(), 0f);
        bottom = footY + foot.getHeightSoFar();
      }
    } else {
      bodyY = y + BODY_GAP;
      body = NomadCell.element(over, inner, ROOM);
      NomadConfirm.column(body, ask, 0f, true);
      float bodyH = body.getHeightSoFar();
      bottom = bodyH > 0f ? bodyY + bodyH : y;
    }

    float buttonsY = bottom + BUTTON_PAD;
    float h = buttonsY + BUTTON_H + BUTTON_PAD;
    float bx = Math.round((cw - w) / 2f);
    float by = Math.round(Math.max(0f, (ch - h) / 2f));
    veil.box = new NomadGrid.Box(bx, by, w, h);

    NomadCell.place(over, title, bx + EDGE, by + TITLE_Y);
    if (card != null) {
      over.addComponent(card).inTL(bx + EDGE, by + cardY);
    }
    NomadCell.place(over, body, bx + bodyX, by + bodyY);
    if (foot != null) {
      NomadCell.place(over, foot, bx + EDGE, by + footY);
    }
    float acceptX = (w - 2f * BUTTON_W - BUTTON_GAP) / 2f;
    button(over, NomadConfirm.acceptLabel(buttonId), NomadConsolePanel.CONFIRM_OK,
        ask.warns ? NomadUi.warn() : NomadUi.base(), bx + acceptX, by + buttonsY);
    button(over, NomadConfirm.cancelLabel(buttonId), NomadConsolePanel.CONFIRM_CANCEL,
        NomadUi.base(), bx + acceptX + BUTTON_W + BUTTON_GAP, by + buttonsY);
    return over;
  }

  private static void button(CustomPanelAPI over, String face, String id, Color color, float x,
      float y) {
    TooltipMakerAPI el = NomadCell.element(over, BUTTON_W, BUTTON_H);
    el.addButton(face, id, color, NomadUi.dark(), Alignment.MID, CutStyle.C2_MENU, BUTTON_W,
        BUTTON_H, 0f);
    NomadCell.place(over, el, x, y);
  }

  private static CustomPanelAPI card(CustomPanelAPI over, NomadConfirm.Ask ask) {
    NomadFlotillaView view = ask.view;
    WorkDef def = view.catalog.work(ask.workId);
    WorkState state = view.work(ask.workId);
    SpriteAPI picture = NomadWorkCard.illustration(ask.workId, def,
        CARD_W / NomadWorkCardModel.IMAGE_H);
    CustomPanelAPI card = over.createCustomPanel(CARD_W, CARD_H, new Card(picture));

    String name = view.workName(ask.workId);
    String tag = state == null ? ""
        : NomadWorkCardModel.sizeTag(state, def, NomadFlotillaView.FLOTILLA);
    NomadWorkCardModel.NameLine at = NomadWorkCardModel.nameLine(CARD_W,
        NomadText.need(name, NomadUi.PARA), NomadText.need(tag, NomadUi.PARA));
    NomadCell.box(card, at.nameX, 0f, at.nameW, NomadWorkCardModel.NAME_H,
        NomadText.fit(name, at.nameW, NomadUi.PARA), NomadUi.base(), Alignment.TL, 0f);
    if (!tag.isEmpty()) {
      NomadCell.box(card, at.tagX, 0f, at.tagW, NomadWorkCardModel.NAME_H, tag,
          NomadConfirm.mix(NomadUi.base(), NomadUi.dark(), 0.3f), Alignment.RMID, 0f);
    }
    return card;
  }

  private static final class Card extends BaseCustomUIPanelPlugin {

    private final SpriteAPI picture;
    private final Color dark = NomadUi.dark();
    private PositionAPI pos;

    Card(SpriteAPI picture) {
      this.picture = picture;
    }

    @Override
    public void positionChanged(PositionAPI position) {
      this.pos = position;
    }

    @Override
    public void renderBelow(float alphaMult) {
      if (pos == null) {
        return;
      }
      float x = pos.getX();
      float nameY = pos.getY() + CARD_H - NomadWorkCardModel.NAME_H;
      float imageY = pos.getY();
      float imageH = NomadWorkCardModel.IMAGE_H;
      NomadGl.fillRect(x, nameY, CARD_W, NomadWorkCardModel.NAME_H, Color.BLACK, 255, alphaMult);
      NomadGl.fillRect(x, nameY, CARD_W, NomadWorkCardModel.NAME_H, dark, dark.getAlpha(),
          alphaMult);
      NomadGl.fillRect(x, imageY, CARD_W, imageH, Color.BLACK, 200, alphaMult);
      if (picture != null) {
        picture.setSize(CARD_W, imageH);
        picture.setColor(Color.WHITE);
        picture.setAlphaMult(alphaMult);
        picture.render(x, imageY);
      }
      NomadGl.box(x, imageY, CARD_W, imageH, Color.BLACK, 0, dark, 255, alphaMult);
    }
  }

  private static final class Veil extends BaseCustomUIPanelPlugin {

    NomadGrid.Box box;
    private final Color edge = NomadConfirm.mix(NomadUi.dark(), NomadUi.base(), 0.6f);
    private PositionAPI pos;

    @Override
    public void positionChanged(PositionAPI position) {
      this.pos = position;
    }

    @Override
    public void renderBelow(float alphaMult) {
      if (pos == null || box == null) {
        return;
      }
      float x = pos.getX();
      float y = pos.getY();
      NomadGl.fillRect(x, y, pos.getWidth(), pos.getHeight(), Color.BLACK, DIM, alphaMult);
      float bx = x + box.x;
      float by = y + pos.getHeight() - box.bottom();
      NomadGl.opaqueRect(bx - 1f, by - 1f, box.w + 2f, box.h + 2f, Color.BLACK);
      NomadGl.opaqueBox(bx, by, box.w, box.h, GROUND, edge);
    }

    @Override
    public void processInput(List<InputEventAPI> events) {
      if (pos == null || events == null) {
        return;
      }
      boolean cancel = false;
      for (InputEventAPI e : events) {
        if (e.isConsumed()) {
          continue;
        }
        if (e.isMouseEvent()) {
          if (pos.containsEvent(e)) {
            e.consume();
          }
        } else if (e.isKeyboardEvent() && e.getEventValue() == Keyboard.KEY_ESCAPE) {
          cancel |= e.isKeyDownEvent();
          e.consume();
        }
      }
      if (cancel) {
        NomadConsolePanel.routeButton(NomadConsolePanel.CONFIRM_CANCEL);
      }
    }

    @Override
    public void buttonPressed(Object buttonId) {
      NomadConsolePanel.routeButton(buttonId);
    }
  }
}
