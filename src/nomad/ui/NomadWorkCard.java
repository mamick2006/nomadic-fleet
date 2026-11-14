package nomad.ui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.Fonts;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import nomad.NomadStrings;
import nomad.campaign.econ.model.Capacity;
import nomad.campaign.econ.model.TickSheet;
import nomad.campaign.econ.model.WorkDef;
import nomad.campaign.econ.model.WorkSize;
import nomad.campaign.econ.model.WorkState;
import nomad.campaign.works.WorkInfo;
import nomad.ui.NomadWorkCardModel.Face;
import nomad.ui.NomadWorkCardModel.Flow;
import nomad.ui.NomadWorkCardModel.LineKind;
import nomad.ui.NomadWorkCardModel.ReasonLine;
import nomad.ui.NomadWorkCardModel.Strip;
import nomad.ui.NomadWorkCardModel.Tone;
import nomad.ui.NomadWorkCardModel.WordAt;

public final class NomadWorkCard {

  private NomadWorkCard() {
  }

  private static final Color DIM = new Color(95, 95, 95);
  private static final Color CLEAR = new Color(0, 0, 0, 0);
  private static final int GLOW = 36;

  static final NomadText.Measure WORD = new NomadText.Measure() {
    @Override
    public float width(String s) {
      return s == null || s.isEmpty() ? 0f
          : Global.getSettings().computeStringWidth(s, Fonts.INSIGNIA_VERY_LARGE);
    }
  };

  public static void build(CustomPanelAPI panel, float w, float h, NomadFlotillaView view,
      String workId, NomadGrid.Box hit) {
    WorkState state = view.work(workId);
    if (state == null) {
      return;
    }
    WorkDef def = view.catalog.work(workId);
    NomadWorkCardModel.State st = view.cardState(workId);
    Face face = NomadWorkCardModel.face(st.shown, def == null || def.isProduction(),
        NomadMark.counted(view, workId), NomadFlotillaView.FLOTILLA);
    boolean selected = workId.equals(view.selected);
    float progress = NomadWorkCardModel.progress(state, view.shared.workBuildDays);
    WordAt word = NomadWorkCardModel.wordAt(w, NomadText.need(face.word, WORD), face.mark);
    float markY = NomadWorkCardModel.WORD_Y
        + (NomadWorkCardModel.WORD_H - NomadWorkCardModel.MARK_PX) / 2f;

    Chrome chrome = new Chrome(w, h, true, selected, hit);
    chrome.image = illustration(workId, def, w / NomadWorkCardModel.IMAGE_H);
    chrome.dim = face.dim;
    if (progress >= 0f) {
      chrome.bar = NomadWorkCardModel.bar(w, face.line == LineKind.BAR);
      chrome.progress = progress;
    }
    CustomPanelAPI back = panel.createCustomPanel(w, h, chrome);
    panel.addComponent(back).inTL(0f, 0f);

    nameBar(panel, w, view.workName(workId),
        NomadWorkCardModel.sizeTag(state, def, NomadFlotillaView.FLOTILLA), selected);
    if (face.mark) {
      NomadMark.add(panel, word.markX, markY, NomadWorkCardModel.MARK_PX, NomadUi.warn());
    }
    if (!face.word.isEmpty()) {
      TooltipMakerAPI el = NomadCell.element(panel, word.wordW, NomadWorkCardModel.WORD_H);
      el.setParaInsigniaVeryLarge();
      el.addPara(face.word, wordColor(face.tone), 0f);
      NomadCell.place(panel, el, word.wordX, NomadWorkCardModel.WORD_Y);
    }

    NomadGrid.Box line = NomadWorkCardModel.line(w);
    switch (face.line) {
      case FLOWS:
      case EFFECT:
        line(panel, line.x, line.y, line.w, view, workId, state.size);
        break;
      case REASON:
        reason(panel, line, view, st, toneColor(face.tone));
        break;
      case KEEPS:
        oneLine(panel, line.x, line.y, line.w, NomadWorkCardModel.keepsText(
            NomadPanelFigures.heads(Capacity.spaceHeld(state, view.catalog)),
            NomadFlotillaView.FLOTILLA), toneColor(face.tone));
        break;
      case BAR:
      default:
        break;
    }
    if (chrome.bar != null) {
      barLabel(panel, chrome.bar, barText(state, view.shared.workBuildDays));
    }

    hitArea(panel, hit, NomadWorkActions.id(NomadWorkActions.Verb.SELECT, workId),
        NomadWorkHover.tip(view, workId));
  }

  private static void hitArea(CustomPanelAPI panel, NomadGrid.Box hit, String id,
      TooltipMakerAPI.TooltipCreator hover) {
    if (hit == null || hit.h <= 0.5f || hit.w <= 0.5f) {
      return;
    }
    TooltipMakerAPI el = panel.createUIElement(hit.w, hit.h, false);
    el.addButton("", id, CLEAR, CLEAR, Alignment.MID, (CutStyle) null, hit.w, hit.h, 0f);
    if (hover != null) {
      el.addTooltipToPrevious(hover, TooltipMakerAPI.TooltipLocation.RIGHT, false);
    }
    NomadCell.place(panel, el, hit.x, hit.y);
  }

  public static void buildAdd(CustomPanelAPI panel, float w, float h, NomadFlotillaView view,
      NomadGrid.Box hit) {
    float boxH = Math.min(h, NomadWorkCardModel.ADD_H);
    NomadGrid.Box press = hit == null ? null
        : new NomadGrid.Box(hit.x, hit.y, hit.w, Math.min(hit.h, boxH - hit.y));
    Chrome chrome = new Chrome(w, h, false, false, press);
    chrome.boxH = boxH;
    CustomPanelAPI back = panel.createCustomPanel(w, h, chrome);
    panel.addComponent(back).inTL(0f, 0f);

    String face = NomadWorkActions.label(NomadWorkActions.Verb.PICK);
    TooltipMakerAPI title = panel.createUIElement(w, 30f, false);
    title.setParaInsigniaLarge();
    LabelAPI t = title.addPara(face, NomadUi.base(), 0f);
    t.setAlignment(Alignment.MID);
    panel.addUIElement(title).inTL(0f, boxH / 2f - 26f);

    TooltipMakerAPI count = panel.createUIElement(w, NomadWorkCardModel.LINE_H, false);
    LabelAPI c = count.addPara(
        NomadStrings.get("nomad_flotilla", "cards_add_count", view.unbuilt().size()),
        NomadUi.gray(), 0f);
    c.setAlignment(Alignment.MID);
    panel.addUIElement(count).inTL(0f, boxH / 2f + 8f);

    hitArea(panel, press, NomadWorkActions.id(NomadWorkActions.Verb.PICK), null);
  }

  private static void nameBar(CustomPanelAPI panel, float w, String name, String tag,
      boolean selected) {
    NomadWorkCardModel.NameLine at = NomadWorkCardModel.nameLine(w,
        NomadText.need(name, NomadUi.PARA), NomadText.need(tag, NomadUi.PARA));
    NomadCell.box(panel, at.nameX, 0f, at.nameW, NomadWorkCardModel.NAME_H,
        NomadText.fit(name, at.nameW, NomadUi.PARA),
        selected ? NomadUi.bright() : NomadUi.base(), Alignment.TL, 0f);
    if (!tag.isEmpty()) {
      NomadCell.box(panel, at.tagX, 0f, at.tagW, NomadWorkCardModel.NAME_H, tag, sizeColor(),
          Alignment.RMID, 0f);
    }
  }

  private static void barLabel(CustomPanelAPI panel, NomadGrid.Box bar, String text) {
    TooltipMakerAPI el = panel.createUIElement(bar.w, bar.h, false);
    el.setParaFont(Fonts.VICTOR_10);
    LabelAPI l = el.addPara(text, NomadUi.text(), 0f);
    l.setAlignment(Alignment.MID);
    panel.addUIElement(el).inTL(bar.x, bar.y + 1f);
  }

  private static String barText(WorkState state, float buildDays) {
    int days = NomadWorkCardModel.daysLeft(buildDays - state.buildDays);
    int to = NomadWorkCardModel.upgradingTo(state);
    return to > 0
        ? NomadStrings.get("nomad_flotilla", "cards_bar_upgrade", to, days)
        : NomadStrings.get("nomad_flotilla", "cards_bar_build", days);
  }

  private static void reason(CustomPanelAPI panel, NomadGrid.Box line, NomadFlotillaView view,
      NomadWorkCardModel.State st, Color c) {
    String good = NomadFlotillaView.reasonName(st, line.w);
    String text = NomadWorkCardModel.lineReason(st.why, good,
        NomadFlotillaView.levelName(view.sheet.needs.level), NomadFlotillaView.FLOTILLA);
    ReasonLine at = NomadWorkCardModel.reasonLine(text, good,
        st.good != null && NomadWorkHover.goodIcon(st.good) != null, line.w, NomadUi.PARA);
    if (!at.before.isEmpty()) {
      NomadCell.box(panel, line.x, line.y, NomadText.need(at.before, NomadUi.PARA), line.h,
          at.before, c, Alignment.TL, 0f);
    }
    if (at.icon) {
      icon(panel, st.good, line.x + at.iconX, line.y);
    }
    if (!at.after.isEmpty()) {
      NomadCell.box(panel, line.x + at.afterX, line.y, NomadText.need(at.after, NomadUi.PARA),
          line.h, at.after, c, Alignment.TL, 0f);
    }
  }

  private static void icon(CustomPanelAPI panel, String good, float x, float y) {
    TooltipMakerAPI ic = panel.createUIElement(NomadWorkCardModel.ICON_PX,
        NomadWorkCardModel.ICON_PX, false);
    String pseudo = NomadWorkCardModel.pseudoIcon(good);
    NomadUi.iconInline(ic, pseudo != null ? pseudo : good, NomadWorkCardModel.ICON_PX, 0f);
    NomadCell.place(panel, ic, x, y);
  }

  public static boolean line(CustomPanelAPI panel, float x, float y, float w,
      NomadFlotillaView view, String workId, int size) {
    WorkDef def = view.catalog.work(workId);
    WorkSize row = def == null ? null : def.size(size);
    if (row == null) {
      return false;
    }
    WorkState state = view.work(workId);
    TickSheet.WorkRun run = view.sheet.run(workId);
    boolean live = state != null && run != null && state.size == size
        && NomadWorkCardModel.works(view.cardState(workId).shown);
    if (!def.isProduction()) {
      return effect(panel, x, y, w, row, live);
    }

    Strip strip = live ? NomadWorkCardModel.strip(run)
        : NomadWorkCardModel.strip(row.makes, row.uses);
    if (strip.flows.isEmpty()) {
      if (live) {
        oneLine(panel, x, y, w, NomadStrings.get("nomad_flotilla", "cards_no_flow"),
            NomadUi.gray());
      }
      return live;
    }

    List<String> figures = new ArrayList<String>();
    for (Flow f : strip.flows) {
      figures.add(NomadWorkCardModel.compact(f.amount));
    }
    int shown = NomadWorkCardModel.flowsShown(w, figures, strip.more, NomadUi.PARA);
    float at = x;
    for (int i = 0; i < shown; i++) {
      Flow f = strip.flows.get(i);
      float numW = NomadText.need(figures.get(i), NomadUi.PARA);
      NomadCell.box(panel, at, y, numW, NomadWorkCardModel.LINE_H, figures.get(i),
          f.amount < 0f ? NomadUi.gray() : NomadUi.text(), Alignment.TL, 0f);
      at += numW + NomadWorkCardModel.FLOW_NUM_PAD;
      icon(panel, f.good, at, y);
      at += NomadWorkCardModel.ICON_PX + NomadWorkCardModel.FLOW_GAP;
    }
    int more = strip.more + strip.flows.size() - shown;
    if (more > 0) {
      oneLine(panel, at, y, Math.min(NomadWorkCardModel.MORE_W, Math.max(1f, x + w - at)),
          NomadWorkCardModel.moreText(more, NomadFlotillaView.FLOTILLA), NomadUi.gray());
    }
    return true;
  }

  private static boolean effect(CustomPanelAPI panel, float x, float y, float w, WorkSize row,
      boolean live) {
    String key = NomadWorkCardModel.firstParam(row);
    if (key == null) {
      if (live) {
        String text = NomadWorkCardModel.keylessText(WorkInfo.cardLabel(row.work, row.size), w,
            NomadUi.PARA, NomadFlotillaView.FLOTILLA);
        LabelAPI l = NomadCell.box(panel, x, y, w, NomadWorkCardModel.LINE_H, text,
            NomadUi.text(), Alignment.TL, 0f);
        NomadTable.highlightNumbers(l, text, NomadUi.hl());
      }
      return live;
    }
    String value = NomadWorkHoverRows.param(key, row.param(key, 0f),
        WorkInfo.tierRelief());
    float labelBox = w - NomadText.need(" " + value, NomadUi.PARA);
    String label = NomadText.fit(
        NomadWorkHover.paramName(NomadWorkHoverRows.labelKey(row.work, key)), labelBox,
        NomadUi.PARA);
    LabelAPI l = NomadCell.box(panel, x, y, w, NomadWorkCardModel.LINE_H,
        NomadWorkCardModel.effectText(label, value, NomadFlotillaView.FLOTILLA), NomadUi.text(),
        Alignment.TL, 0f);
    l.setHighlight(value);
    l.setHighlightColor(NomadUi.hl());
    return true;
  }

  private static void oneLine(CustomPanelAPI panel, float x, float y, float box, String text,
      Color c) {
    NomadCell.box(panel, x, y, box, NomadWorkCardModel.LINE_H,
        NomadText.fit(text, box, NomadUi.PARA), c, Alignment.TL, 0f);
  }

  static Color toneColor(Tone tone) {
    switch (tone) {
      case GOOD:
        return NomadUi.good();
      case WARN:
        return NomadUi.warn();
      case BAD:
        return NomadUi.bad();
      case BUILD:
        return NomadUi.hl();
      case MUTED:
      default:
        return NomadUi.gray();
    }
  }

  private static Color wordColor(Tone tone) {
    return tone == Tone.BUILD ? NomadUi.base() : toneColor(tone);
  }

  private static Color sizeColor() {
    return mix(NomadUi.base(), NomadUi.dark(), 0.3f);
  }

  private static Color barColor() {
    return mix(NomadUi.base(), NomadUi.dark(), 0.5f);
  }

  private static Color mix(Color a, Color b, float toB) {
    float toA = 1f - toB;
    return new Color(Math.round(a.getRed() * toA + b.getRed() * toB),
        Math.round(a.getGreen() * toA + b.getGreen() * toB),
        Math.round(a.getBlue() * toA + b.getBlue() * toB));
  }

  static SpriteAPI illustration(String workId, WorkDef def, float slotAspect) {
    String path = illustrationPath(workId, def);
    if (path == null) {
      return null;
    }
    SpriteAPI s;
    try {
      s = Global.getSettings().getSprite(path);
    } catch (Exception e) {
      return null;
    }
    if (s == null || s.getWidth() <= 0f || s.getHeight() <= 0f || slotAspect <= 0f) {
      return s;
    }
    float keep = Math.min(1f, (s.getWidth() / s.getHeight()) / slotAspect);
    float th = s.getTexHeight();
    s.setTexY(s.getTexY() + th * (1f - keep) / 2f);
    s.setTexHeight(th * keep);
    return s;
  }

  static String illustrationPath(String workId, WorkDef def) {
    String path = spritePath(NomadWorkCardModel.illustrationKey(workId, def));
    if (path == null) {
      String standIn = NomadWorkCardModel.standIn(workId);
      path = standIn == null ? null : spritePath(standIn);
    }
    return path;
  }

  private static String spritePath(String key) {
    try {
      String s = Global.getSettings().getSpriteName("illustrations", key);
      return s == null || s.isEmpty() ? null : s;
    } catch (Exception e) {
      return null;
    }
  }

  private static final class Chrome extends BaseCustomUIPanelPlugin {

    private final float w;
    private final float h;
    private final boolean framed;
    private final boolean selected;
    private final NomadGrid.Box hot;
    private final Color barFill = barColor();
    SpriteAPI image;
    boolean dim;
    float progress = -1f;
    NomadGrid.Box bar;
    float boxH;
    private PositionAPI pos;

    Chrome(float w, float h, boolean framed, boolean selected, NomadGrid.Box hot) {
      this.w = w;
      this.h = h;
      this.framed = framed;
      this.selected = selected;
      this.hot = hot != null && hot.w > 0.5f && hot.h > 0.5f ? hot : null;
      this.boxH = h;
    }

    @Override
    public void positionChanged(PositionAPI position) {
      this.pos = position;
    }

    @Override
    public void render(float alphaMult) {
      if (pos == null) {
        return;
      }
      float x = pos.getX();
      float top = pos.getY() + h;
      boolean over = pointerOver(x, top);
      Color edge = selected ? NomadUi.bright() : NomadUi.dark();
      if (!framed) {
        NomadGl.box(x, top - boxH, w, boxH, Color.BLACK, 120, edge, 255, alphaMult);
        if (over) {
          NomadGl.fillRect(x, top - boxH, w, boxH, Color.WHITE, GLOW / 2, alphaMult);
        }
        return;
      }
      float nameY = top - NomadWorkCardModel.NAME_H;
      float imageY = nameY - NomadWorkCardModel.IMAGE_H;
      Color dark = NomadUi.dark();
      NomadGl.fillRect(x, nameY, w, NomadWorkCardModel.NAME_H, Color.BLACK, 255, alphaMult);
      NomadGl.fillRect(x, nameY, w, NomadWorkCardModel.NAME_H, dark, dark.getAlpha(), alphaMult);
      NomadGl.fillRect(x, imageY, w, NomadWorkCardModel.IMAGE_H, Color.BLACK, 200, alphaMult);
      if (image != null) {
        image.setSize(w, NomadWorkCardModel.IMAGE_H);
        image.setColor(dim ? DIM : Color.WHITE);
        image.setAlphaMult(alphaMult);
        image.render(x, imageY);
      }
      if (over) {
        NomadGl.fillRect(x, imageY, w, NomadWorkCardModel.IMAGE_H, Color.WHITE, GLOW, alphaMult);
      }
      if (bar != null) {
        float barY = top - bar.bottom();
        NomadGl.box(x + bar.x, barY, bar.w, bar.h, Color.BLACK, 220, NomadUi.dark(), 255,
            alphaMult);
        NomadGl.fillRect(x + bar.x + 1f, barY + 1f,
            (bar.w - 2f) * Math.max(0f, Math.min(1f, progress)), bar.h - 2f, barFill, 255,
            alphaMult);
      }
      NomadGl.box(x, imageY, w, NomadWorkCardModel.IMAGE_H, Color.BLACK, 0, edge, 255,
          alphaMult);
    }

    private boolean pointerOver(float x, float top) {
      if (hot == null || NomadConsolePanel.confirmBoxOfLive() != null) {
        return false;
      }
      float mx = Global.getSettings().getMouseX();
      float my = Global.getSettings().getMouseY();
      return mx >= x + hot.x && mx < x + hot.right() && my <= top - hot.y
          && my > top - hot.bottom();
    }
  }
}
