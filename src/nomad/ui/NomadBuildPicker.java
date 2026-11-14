package nomad.ui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import nomad.NomadStrings;
import nomad.campaign.econ.model.BasicNeeds;
import nomad.campaign.econ.model.NeedsLevel;
import nomad.campaign.econ.model.WorkDef;
import nomad.campaign.econ.model.WorkOrders;
import nomad.campaign.works.WorkPrereqChecks;
import nomad.campaign.econ.model.WorkSize;
import nomad.campaign.works.WorkInfo;
import nomad.ui.NomadWorkCardModel.Flow;
import nomad.ui.NomadWorkCardModel.Strip;

public final class NomadBuildPicker {

  private NomadBuildPicker() {
  }

  private static final float PAD = 3f;
  private static final float OPAD = 10f;

  private static final float SUM_ROW_H = 18f;

  public static void build(CustomPanelAPI panel, float w, float h, NomadFlotillaView view) {
    String noteText = text("ui2_picker_note", NomadUi.i(view.shared.workBuildDays));
    NeedsLevel now = view.sheet == null ? null : view.sheet.needs.level;
    NomadNeedsTag.Common common = common(view, now);
    String once = common.sayOnce()
        ? NomadNeedsTag.once(now, common.after(), common.leave(), common.effNow(),
            common.effAfter(), NomadFlotillaView.FLOTILLA)
        : null;
    int onceLines = once == null ? 0 : 1;
    int noteLines = Math.max(1, Math.min(NomadBuildPickerLayout.NOTE_MAX_LINES - 1 - onceLines,
        NomadText.lines(noteText, NomadBuildPickerLayout.noteBox(w), NomadUi.PARA)));
    NomadBuildPickerLayout.Panel at = NomadBuildPickerLayout.panel(w, h,
        noteLines + onceLines + 1);
    NomadBuildPickerLayout cols = NomadBuildPickerLayout.of(at.rowW);

    panel.addComponent(bar(panel, at.title.w, text("hover_picker_title")))
        .inTL(at.title.x, at.title.y);

    if (at.note.w > 0.5f) {
      float noteH = noteLines * NomadBuildPickerLayout.LINE_H;
      NomadCell.box(panel, at.note.x, at.note.y, at.note.w, noteH,
          NomadText.fitLines(noteText, at.note.w, noteLines, NomadUi.PARA),
          NomadUi.gray(), Alignment.TL, 0f);
      if (once != null) {
        NomadCell.box(panel, at.note.x, at.note.y + noteH, at.note.w,
            NomadBuildPickerLayout.LINE_H, NomadText.fit(once, at.note.w, NomadUi.PARA),
            toneColor(NomadNeedsTag.tone(now, common.after(), common.leave())), Alignment.TL,
            0f);
        noteH += NomadBuildPickerLayout.LINE_H;
      }
      TooltipMakerAPI purse = NomadCell.element(panel, at.note.w, NomadBuildPickerLayout.LINE_H);
      purse.addPara(NomadStrings.get("nomad_flotilla", "screen_credits"), 0f, NomadUi.hl(),
          NomadUi.credits(view.credits));
      NomadCell.place(panel, purse, at.note.x, at.note.y + noteH);
    }

    TooltipMakerAPI back = NomadCell.element(panel, at.back.w, at.back.h);
    back.addButton(NomadWorkActions.label(NomadWorkActions.Verb.PICK_CLOSE),
        NomadWorkActions.id(NomadWorkActions.Verb.PICK_CLOSE), NomadUi.base(), NomadUi.dark(),
        at.back.w, at.back.h, 0f);
    NomadCell.place(panel, back, at.back.x, at.back.y);

    caption(panel, cols, NomadBuildPickerLayout.NAME, at.captions.y,
        text("hover_picker_col_name"), Alignment.TL);
    caption(panel, cols, NomadBuildPickerLayout.COST, at.captions.y,
        text("hover_picker_col_cost"), Alignment.RMID);

    TooltipMakerAPI list = panel.createUIElement(at.list.w + NomadText.INSET, at.list.h, true);
    Map<String, List<WorkDef>> groups = groups(view);
    if (groups.isEmpty()) {
      list.addPara(text("hover_picker_empty"), NomadUi.gray(), OPAD);
    }
    float[][] heights = new float[groups.size()][];
    int g = 0;
    for (Map.Entry<String, List<WorkDef>> group : groups.entrySet()) {
      list.addCustom(bar(panel, at.rowW, group.getKey()),
          g == 0 ? 0f : NomadBuildPickerLayout.GROUP_GAP);
      heights[g] = new float[group.getValue().size()];
      int r = 0;
      for (WorkDef def : group.getValue()) {
        CustomPanelAPI row = row(panel, cols, at.rowW, view, def,
            once != null && at.note.w > 0.5f);
        list.addCustom(row, r == 0 ? NomadBuildPickerLayout.HEAD_GAP : 0f);
        heights[g][r++] = row.getPosition().getHeight();
      }
      g++;
    }
    panel.addUIElement(list).inTL(at.list.x - NomadText.INSET, at.list.y);
    NomadFlotillaTab.placePickerScroll(list, NomadBuildPickerLayout.contentHeight(heights),
        at.list.h);
  }

  private static Map<String, List<WorkDef>> groups(NomadFlotillaView view) {
    Map<String, List<WorkDef>> out = new LinkedHashMap<String, List<WorkDef>>();
    for (WorkDef def : view.unbuilt()) {
      String name = NomadWorkHover.categoryName(def.category);
      List<WorkDef> works = out.get(name);
      if (works == null) {
        works = new ArrayList<WorkDef>();
        out.put(name, works);
      }
      works.add(def);
    }
    return out;
  }

  private static CustomPanelAPI bar(CustomPanelAPI panel, float w, String words) {
    CustomPanelAPI bar = panel.createCustomPanel(w, NomadBuildPickerLayout.HEAD_H, null);
    TooltipMakerAPI el = bar.createUIElement(w, NomadBuildPickerLayout.HEAD_H, false);
    el.addSectionHeading(NomadText.fit(words, NomadText.box(w), NomadUi.BUTTON), NomadUi.base(),
        NomadUi.dark(), Alignment.MID, 0f);
    bar.addUIElement(el).inTL(0f, 0f);
    return bar;
  }

  private static void caption(CustomPanelAPI panel, NomadBuildPickerLayout cols, int col, float y,
      String words, Alignment align) {
    if (!cols.shown(col)) {
      return;
    }
    NomadCell.box(panel, cols.x[col], y, cols.w[col], NomadBuildPickerLayout.LINE_H,
        NomadText.fit(words, cols.w[col], NomadUi.PARA), NomadUi.gray(), align, 0f);
  }

  private static NomadNeedsTag.Common common(NomadFlotillaView view, NeedsLevel now) {
    NomadNeedsTag.Common common = new NomadNeedsTag.Common();
    for (WorkDef def : view.unbuilt()) {
      WorkOrders.Quote q = view.quoteBuild(def.id);
      if (!reasonInstead(q)) {
        common.add(view.paused, now, q.after.level, q.residentsWouldLeave, effNow(view),
            effAt(view, q.after.level));
      }
    }
    return common;
  }

  private static float effNow(NomadFlotillaView view) {
    return view.sheet == null ? 0f : view.sheet.efficiency;
  }

  private static float effAt(NomadFlotillaView view, NeedsLevel level) {
    return view.sheet == null || level == null ? 0f : BasicNeeds.efficiencyAt(view.sheet, level);
  }

  private static boolean reasonInstead(WorkOrders.Quote q) {
    return (q.refusal == WorkOrders.Refusal.NEEDS_PREREQ && !q.unmet.isEmpty())
        || q.refusal == WorkOrders.Refusal.NO_SPACE;
  }

  private static CustomPanelAPI row(CustomPanelAPI panel, NomadBuildPickerLayout cols, float rowW,
      final NomadFlotillaView view, final WorkDef def, boolean saidOnce) {
    WorkSize first = def.size(1);
    final WorkOrders.Quote q = view.quoteBuild(def.id);
    final boolean canBuild = view.live && q.allowed();
    NeedsLevel now = view.sheet == null ? null : view.sheet.needs.level;
    boolean lacking = q.refusal == WorkOrders.Refusal.NEEDS_PREREQ && !q.unmet.isEmpty();
    boolean noSpace = q.refusal == WorkOrders.Refusal.NO_SPACE;
    NomadNeedsTag level = lacking || noSpace ? null
        : NomadNeedsTag.of(view.paused, now, q.after.level, q.residentsWouldLeave,
            effNow(view), effAt(view, q.after.level), NomadFlotillaView.FLOTILLA, NomadUi.PARA,
            NomadBuildPickerLayout.NEEDS_W);
    TooltipMakerAPI.TooltipCreator hover = level == null ? NomadWorkHover.tip(view, def.id)
        : rowTip(view, def, q);
    if (saidOnce) {
      level = null;
    }
    boolean tagged = cols.shown(NomadBuildPickerLayout.NEEDS)
        && (lacking || noSpace || level != null);

    float nameX = cols.x[NomadBuildPickerLayout.NAME];
    float nameW = cols.w[NomadBuildPickerLayout.NAME];
    String name = view.workName(def.id);
    int nameLines = NomadText.lines(name, nameW, NomadUi.PARA);
    float bodyY = NomadBuildPickerLayout.bodyY(nameLines);
    Body body = body(view, def, first, cols, tagged);
    String reason = !tagged ? null
        : lacking ? NomadPrereqText.line(q.unmet.get(0), WorkPrereqChecks.NAMES,
            NomadText.box(cols.w[NomadBuildPickerLayout.NEEDS]), NomadUi.PARA,
            NomadFlotillaView.FLOTILLA)
        : noSpace ? text("ui2_picker_needs_space",
            NomadPanelFigures.heads(Math.max(1f, (float) Math.ceil(q.space - q.spaceFree))))
        : null;
    int tagLines = !tagged ? 0
        : reason != null ? NomadText.lines(reason, cols.w[NomadBuildPickerLayout.NEEDS],
            NomadUi.PARA)
        : level.lines();
    final float rowH = cols.rowHeight(nameLines, body.h, tagLines);

    CustomPanelAPI row = panel.createCustomPanel(rowW, rowH,
        NomadRowChrome.rule(null, NomadUi.dark(), 55));

    if (cols.shown(NomadBuildPickerLayout.THUMB)) {
      float thumbW = cols.w[NomadBuildPickerLayout.THUMB];
      row.addComponent(row.createCustomPanel(thumbW, cols.thumbH,
          new Thumb(NomadWorkCard.illustration(def.id, def, thumbW / cols.thumbH), thumbW,
              cols.thumbH)))
          .inTL(cols.x[NomadBuildPickerLayout.THUMB], 0f);
    }

    NomadCell.box(row, nameX, NomadBuildPickerLayout.NAME_Y, nameW,
        nameLines * NomadBuildPickerLayout.LINE_H, name, NomadUi.base(), Alignment.TL, 0f);
    body.draw(row, nameX, bodyY);

    if (reason != null) {
      words(row, cols, NomadBuildPickerLayout.NEEDS, NomadBuildPickerLayout.NAME_Y, reason,
          tagLines, NomadUi.bad(), null);
    } else if (tagged) {
      Color c = toneColor(level.tone);
      words(row, cols, NomadBuildPickerLayout.NEEDS, NomadBuildPickerLayout.NAME_Y, level.top, 1,
          c, null);
      words(row, cols, NomadBuildPickerLayout.NEEDS, NomadBuildPickerLayout.BODY_Y,
          level.bottom, 1, c, null);
      if (level.efficiency != null) {
        words(row, cols, NomadBuildPickerLayout.NEEDS,
            NomadBuildPickerLayout.BODY_Y + NomadBuildPickerLayout.LINE_H, level.efficiency, 1, c,
            null);
      }
    }
    String cost = NomadUi.credits(first.cost);
    words(row, cols, NomadBuildPickerLayout.COST, cols.y(NomadBuildPickerLayout.COST), cost,
        Math.max(1, NomadText.lines(cost, cols.w[NomadBuildPickerLayout.COST], NomadUi.PARA)),
        q.refusal == WorkOrders.Refusal.NO_CREDITS ? NomadUi.bad() : NomadUi.hl(), hover);

    float coverW = Math.max(0f, cols.x[NomadBuildPickerLayout.COST] - NomadBuildPickerLayout.GAP);
    if (coverW > 0.5f) {
      TooltipMakerAPI cover = NomadCell.element(row, coverW, rowH);
      cover.addCustom(row.createCustomPanel(coverW, rowH, null), 0f);
      cover.addTooltipToPrevious(hover, TooltipMakerAPI.TooltipLocation.BELOW, false);
      NomadCell.place(row, cover, 0f, 0f);
    }

    float buildW = cols.w[NomadBuildPickerLayout.BUILD];
    TooltipMakerAPI btnEl = NomadCell.element(row, buildW, rowH);
    ButtonAPI b = btnEl.addButton(NomadWorkActions.label(NomadWorkActions.Verb.BUILD),
        NomadWorkActions.id(NomadWorkActions.Verb.BUILD, def.id), NomadUi.base(), NomadUi.dark(),
        buildW, NomadBuildPickerLayout.BUTTON_H, cols.y(NomadBuildPickerLayout.BUILD));
    NomadUi.setEnabledSayingWhy(b, canBuild);
    final float tipW = Math.max(NomadBuildPickerLayout.TIP_W,
        NomadWorkHover.width(def, view.work(def.id)));
    btnEl.addTooltipToPrevious(NomadTip.guard(new NomadTip(tipW) {
      @Override
      public void createTooltip(TooltipMakerAPI t, boolean expanded, Object param) {
        buildTip(t, view, def, q, canBuild, tipW);
      }
    }), TooltipMakerAPI.TooltipLocation.LEFT, false);
    NomadCell.place(row, btnEl, cols.x[NomadBuildPickerLayout.BUILD], 0f);
    return row;
  }

  private static Body body(NomadFlotillaView view, WorkDef def, WorkSize first,
      NomadBuildPickerLayout cols, boolean tagged) {
    float nameW = cols.w[NomadBuildPickerLayout.NAME];
    if (first == null) {
      return Body.NONE;
    }
    if (def.isProduction()) {
      Strip strip = NomadWorkCardModel.strip(first.makes, first.uses);
      if (strip.flows.isEmpty()) {
        return Body.NONE;
      }
      List<String> figures = new ArrayList<String>();
      for (Flow f : strip.flows) {
        figures.add(NomadWorkCardModel.compact(f.amount));
      }
      return new Body(strip.flows, figures,
          NomadBuildPickerLayout.flows(figures, nameW, NomadUi.PARA));
    }
    float box = cols.sentenceWidth(tagged);
    if (box <= 0.5f) {
      return Body.NONE;
    }
    String says = !def.ownRow ? def.effect : WorkInfo.effectLine(def.id, 1);
    if (says != null && !says.isEmpty()) {
      return new Body(says, null, box);
    }
    String key = NomadWorkCardModel.firstParam(first);
    if (key == null) {
      return Body.NONE;
    }
    String value = NomadWorkHoverRows.param(key, first.param(key, 0f),
        WorkInfo.tierRelief());
    return new Body(NomadWorkCardModel.effectText(NomadWorkHover.paramName(key), value,
        NomadFlotillaView.FLOTILLA), value, box);
  }

  private static final class Body {

    static final Body NONE = new Body(null, null, 0f);

    private final String words;
    private final String value;
    private final float box;
    private final List<Flow> goods;
    private final List<String> figures;
    private final NomadBuildPickerLayout.Flows at;
    final float h;

    Body(String words, String value, float box) {
      this.words = words;
      this.value = value;
      this.box = box;
      this.goods = null;
      this.figures = null;
      this.at = null;
      this.h = NomadBuildPickerLayout.textHeight(words, box, NomadUi.PARA);
    }

    Body(List<Flow> goods, List<String> figures, NomadBuildPickerLayout.Flows at) {
      this.words = null;
      this.value = null;
      this.box = 0f;
      this.goods = goods;
      this.figures = figures;
      this.at = at;
      this.h = at.h;
    }

    void draw(CustomPanelAPI row, float x, float y) {
      if (at != null) {
        for (int i = 0; i < goods.size(); i++) {
          Flow f = goods.get(i);
          String fig = figures.get(i);
          NomadCell.box(row, x + at.numX[i], y + at.y[i] + NomadBuildPickerLayout.FLOW_NUM_Y,
              NomadText.need(fig, NomadUi.PARA), NomadBuildPickerLayout.LINE_H, fig,
              f.amount < 0f ? NomadUi.gray() : NomadUi.text(), Alignment.TL, 0f);
          icon(row, f.good, x + at.iconX[i], y + at.y[i]);
        }
        return;
      }
      if (words == null || h <= 0f) {
        return;
      }
      LabelAPI l = NomadCell.box(row, x, y, box, h, words, NomadUi.text(), Alignment.TL, 0f);
      if (value != null) {
        l.setHighlight(value);
        l.setHighlightColor(NomadUi.hl());
      } else {
        NomadTable.highlightNumbers(l, words, NomadUi.hl());
      }
    }
  }

  private static void icon(CustomPanelAPI row, String good, float x, float y) {
    float px = NomadBuildPickerLayout.ICON;
    TooltipMakerAPI ic = row.createUIElement(px, px, false);
    String pseudo = NomadWorkCardModel.pseudoIcon(good);
    NomadUi.iconInline(ic, pseudo != null ? pseudo : good, px, 0f);
    NomadCell.place(row, ic, x, y);
  }

  private static void words(CustomPanelAPI row, NomadBuildPickerLayout cols, int col, float y,
      String words, int lines, Color c, TooltipMakerAPI.TooltipCreator hover) {
    if (!cols.shown(col)) {
      return;
    }
    TooltipMakerAPI el = NomadCell.element(row, cols.w[col],
        Math.max(1, lines) * NomadBuildPickerLayout.LINE_H);
    el.addPara(words, c, 0f).setAlignment(Alignment.RMID);
    if (hover != null) {
      el.addTooltipToPrevious(hover, TooltipMakerAPI.TooltipLocation.BELOW, false);
    }
    NomadCell.place(row, el, cols.x[col], y);
  }

  private static Color toneColor(NomadNeedsTag.Tone tone) {
    switch (tone) {
      case GOOD:
        return NomadUi.good();
      case BAD:
        return NomadUi.bad();
      case PLAIN:
        return NomadUi.text();
      default:
        return NomadUi.warn();
    }
  }

  private static TooltipMakerAPI.TooltipCreator rowTip(final NomadFlotillaView view,
      final WorkDef def, final WorkOrders.Quote q) {
    return NomadTip.guard(new NomadTip(NomadWorkHover.width(def, view.work(def.id))) {
      @Override
      public void createTooltip(TooltipMakerAPI t, boolean expanded, Object param) {
        NomadWorkHover.write(t, view, def.id);
        t.addSectionHeading(text("hover_picker_col_needs"), NomadUi.base(), NomadUi.dark(),
            Alignment.MID, OPAD);
        cause(t, view, q);
        NomadCrewPanel.levelAfter(t, view, q.after);
      }
    });
  }

  private static void cause(TooltipMakerAPI t, NomadFlotillaView view, WorkOrders.Quote q) {
    NomadNeedsTag.Taken taken = NomadNeedsTag.taken(
        NomadBuildPickerLayout.sum(view.sheet.needs, q.after), q.space);
    if (taken == null) {
      return;
    }
    String space = NomadPanelFigures.heads(taken.space);
    String residents = NomadPanelFigures.heads(taken.residents);
    LabelAPI l;
    if (taken.split()) {
      String work = NomadPanelFigures.heads(taken.work);
      String stores = NomadPanelFigures.heads(taken.stores);
      l = t.addPara(text("ui2_picker_needs_cause_stores", space, work, stores, residents), OPAD);
      l.setHighlight(space, work, stores, residents);
    } else {
      l = t.addPara(text("ui2_picker_needs_cause", space, residents), OPAD);
      l.setHighlight(space, residents);
    }
    l.setHighlightColor(NomadUi.hl());
  }

  private static void buildTip(TooltipMakerAPI t, NomadFlotillaView view, WorkDef def,
      WorkOrders.Quote q, boolean canBuild, float tipW) {
    String name = view.workName(def.id);
    t.addTitle(name, NomadUi.base());
    String kind = def.isProduction() ? text("hover_kind_production") : text("hover_kind_support");
    t.addPara(text("hover_kind_line", kind, NomadWorkHover.categoryName(def.category)),
        NomadUi.gray(), PAD);
    String desc = NomadWorkHover.description(def);
    if (!desc.isEmpty()) {
      t.addPara(desc, OPAD);
    }

    String cost = NomadUi.credits(def.size(1).cost);
    String days = NomadUi.days(view.shared.workBuildDays);
    LabelAPI does = t.addPara(text("hover_picker_build_tip", name, cost, days), OPAD);
    does.setHighlight(cost, days);
    does.setHighlightColor(NomadUi.hl());

    if (!view.live) {
      t.addPara(view.notLiveReason(), NomadUi.warn(), PAD);
      return;
    }
    if (!canBuild) {
      t.addPara(NomadFlotillaView.refusalText(q), NomadUi.warn(), PAD);
    }
    if (canBuild || q.refusal == WorkOrders.Refusal.NO_CREDITS) {
      sum(t, view, q, tipW - 10f);
    }
  }

  private static void sum(TooltipMakerAPI t, NomadFlotillaView view, WorkOrders.Quote q,
      float width) {
    NomadBuildPickerLayout.Sum s = NomadBuildPickerLayout.sum(view.sheet.needs, q.after);
    t.addSectionHeading(text("hover_picker_col_needs"), NomadUi.base(), NomadUi.dark(),
        Alignment.MID, OPAD);
    String normal = NomadFlotillaView.levelName(NeedsLevel.NORMAL);
    sumRow(t, width, PAD, "", NomadUi.gray(), text("ui2_tip_col_now"), NomadUi.gray(),
        text("ui2_tip_col_after"), NomadUi.gray(), text("ui2_tip_col_requires", normal),
        NomadUi.gray());
    sumRow(t, width, 0f, text("panels_space_title"), NomadUi.text(),
        NomadPanelFigures.heads(s.spaceNow), NomadUi.text(),
        NomadPanelFigures.heads(s.spaceAfter), NomadUi.hl(),
        NomadPanelFigures.heads(s.spaceRequired), NomadUi.text());
    sumRow(t, width, 0f, text("panels_residents_title"), NomadUi.text(),
        NomadPanelFigures.heads(s.residentsNow), NomadUi.text(),
        NomadPanelFigures.heads(s.residentsAfter), NomadUi.hl(),
        NomadPanelFigures.heads(s.residentsRequired), NomadUi.text());
    NomadCrewPanel.levelAfter(t, view, q.after);
    String needs = NomadWorkPanel.needsLine(q);
    if (needs != null) {
      t.addPara(needs, NomadUi.text(), PAD);
    }
  }

  private static void sumRow(TooltipMakerAPI t, float width, float pad, String label,
      Color labelColor, String now, Color nowColor, String after, Color afterColor,
      String requires, Color requiresColor) {
    CustomPanelAPI line = Global.getSettings().createCustom(width, SUM_ROW_H, null);
    float figureW = NomadBuildPickerLayout.SUM_FIGURE_W;
    float requiresW = NomadBuildPickerLayout.SUM_REQUIRES_W;
    float labelW = NomadBuildPickerLayout.sumLabelWidth(width);
    NomadCell.text(line, 0f, 0f, labelW, SUM_ROW_H, label, labelColor, Alignment.LMID, 1f);
    NomadCell.text(line, labelW, 0f, figureW, SUM_ROW_H, now, nowColor, Alignment.RMID, 1f);
    NomadCell.text(line, labelW + figureW, 0f, figureW, SUM_ROW_H, after, afterColor,
        Alignment.RMID, 1f);
    NomadCell.text(line, labelW + 2f * figureW, 0f, requiresW, SUM_ROW_H, requires,
        requiresColor, Alignment.RMID, 1f);
    t.addCustom(line, pad);
  }

  private static String text(String key, Object... args) {
    return NomadStrings.get("nomad_flotilla", key, args);
  }

  private static final class Thumb extends BaseCustomUIPanelPlugin {

    private final SpriteAPI image;
    private final float w;
    private final float h;
    private PositionAPI pos;

    Thumb(SpriteAPI image, float w, float h) {
      this.image = image;
      this.w = w;
      this.h = h;
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
      NomadGl.fillRect(pos.getX(), pos.getY(), w, h, Color.BLACK, 200, alphaMult);
      if (image != null) {
        image.setSize(w, h);
        image.setAlphaMult(alphaMult);
        image.render(pos.getX(), pos.getY());
      }
    }
  }
}
