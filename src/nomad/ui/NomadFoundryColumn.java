package nomad.ui;

import static nomad.ui.NomadHero.label;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
import com.fs.starfarer.api.util.Misc;

import nomad.NomadStrings;
import nomad.campaign.NomadModel;
import nomad.campaign.econ.Work;
import nomad.campaign.econ.model.Foundry;
import nomad.campaign.econ.model.Rules;
import nomad.campaign.econ.model.Works;
import nomad.data.NomadFleetData;
import nomad.ui.NomadHero.Face;

public final class NomadFoundryColumn {

  private NomadFoundryColumn() {
  }

  private static final String PICTURE = "nomad_illus_autoforge";

  private static String word(String key, Object... fills) {
    return NomadStrings.get("nomad_autoforge", key, fills);
  }

  private static final String NO_VALUE = "---";
  private static final int SHADE_ALPHA = 153;
  private static final float UNBUILT_ALPHA = 0.4f;
  private static final int JOIN_ALPHA = 217;
  private static final float SPRITE_MAX_SCALE = 2f;
  private static final int TRACK_ALPHA = 90;
  private static final int RULE_ALPHA = 70;
  private static final float PAD = 3f;
  private static final float OPAD = 10f;
  private static final float SHIFT = NomadText.INSET;

  static void build(CustomPanelAPI panel, float w, float h, NomadFleetData data,
      NomadAutoforge.Line line, NomadAutoforgeQueue.LineState st,
      List<NomadAutoforge.Stack> stacks) {
    NomadFoundryFigures.Layout at = NomadFoundryFigures.layout(w, h,
        NomadGrid.Screen.heroH(w, h), NomadGrid.Screen.ledgerRowH(h),
        NomadText.need(NomadUi.BUTTON, word("fo_pause"), word("fo_resume")),
        NomadText.need(word("fo_clear"), NomadUi.BUTTON));

    NomadAutoforge.Stack head = stacks.isEmpty() ? null : stacks.get(0);
    NomadAutoforge.Recipe job = head == null ? null
        : NomadAutoforge.recipeFor(data, head.type, head.id);
    float built = head == null ? 0f : clamp01(NomadAutoforge.headFraction(data));
    boolean advancing = NomadAutoforgeQueue.advancing(st);
    float headDays = advancing ? NomadAutoforge.headDaysRemaining(data, line) : Float.NaN;
    boolean dated = advancing && !Float.isNaN(headDays) && !Float.isInfinite(headDays);

    hero(panel, at, line, st, job, built, dated ? days(headDays) : NO_VALUE, dated);
    line(panel, at, data, line, st, head, job);
    progress(panel, at, data, line, st, head != null, built);
    queueHead(panel, at, data, stacks.isEmpty());
    rows(panel, at, data, line, stacks, headDays, dated);
    foot(panel, at, data);
    hover(panel, at.hover, tip(data, line, st));
  }

  static String stateWord(NomadAutoforgeQueue.LineState st) {
    switch (st) {
      case BUILDING:
        return word("fo_word_building");
      case STALLED:
        return word("fo_word_stalled");
      case PAUSED:
        return word("fo_word_paused");
      case IDLE:
        return word("fo_word_idle");
      default:
        return NomadAutoforgeTab.haltWord(st);
    }
  }

  static Color stateColor(NomadAutoforgeQueue.LineState st) {
    switch (st) {
      case BUILDING:
        return NomadUi.good();
      case STALLED:
      case PAUSED:
        return NomadUi.warn();
      case NO_FLAGSHIP:
      case SUSPENDED:
      case UNSTAFFED:
      case LACKING:
      case STOPPED:
        return NomadUi.bad();
      case CONSTRUCTING:
        return NomadUi.base();
      case IDLE:
        return NomadUi.text();
      case NO_FOUNDRY:
      case MOTHBALLED:
      default:
        return NomadUi.gray();
    }
  }

  static String days(float d) {
    int n = NomadFoundryFigures.dayCount(d);
    return word(n == 1 ? "fo_days_one" : "fo_days", Integer.toString(n));
  }

  private static void hero(CustomPanelAPI panel, NomadFoundryFigures.Layout at,
      NomadAutoforge.Line line, NomadAutoforgeQueue.LineState st, NomadAutoforge.Recipe job,
      float built, String next, boolean dated) {
    Slip chrome = new Slip(at);
    chrome.picture = NomadHero.sprite(PICTURE, at.hero.w / at.hero.h);
    chrome.built = built;
    placeJob(chrome, job, at.sprite);
    CustomPanelAPI back = panel.createCustomPanel(at.hero.w, at.hero.h, chrome);
    panel.addComponent(back).inTL(at.hero.x, at.hero.y);

    String size = line.foundry == null ? NO_VALUE
        : Integer.toString(line.foundry.isBuilt() ? line.foundry.size : line.foundry.buildTo);
    String[] caption = NomadFoundryFigures.captionParts(
        NomadStrings.get("nomad_autoforge", "fo_hero_caption", size));
    float left = at.textLeft();
    label(panel, left, at.captionY(), NomadText.need(caption[0], NomadUi.BUTTON),
        NomadHomeFigures.LINE_H, caption[0], NomadUi.base(), Alignment.LMID, Face.CAPTION);
    if (line.foundry != null && !caption[1].isEmpty()) {
      chrome.dotX = left + NomadFoundryFigures.captionDotX(caption, NomadUi.BUTTON);
      chrome.dotY = at.captionY() + Math.round((NomadHomeFigures.LINE_H
          - NomadFoundryFigures.DOT) / 2f);
      label(panel, left + NomadFoundryFigures.captionSecondX(caption, NomadUi.BUTTON),
          at.captionY(), NomadText.need(caption[1], NomadUi.BUTTON), NomadHomeFigures.LINE_H,
          caption[1], NomadUi.base(), Alignment.LMID, Face.CAPTION);
    }

    String nextCaption = NomadStrings.get("nomad_autoforge", "fo_next");
    float nextCaptionW = NomadText.need(nextCaption, NomadUi.BUTTON);
    float nextW = NomadText.need(next, NomadHomeColumn.VALUE);
    label(panel, at.textRight() - nextCaptionW, at.captionY(), nextCaptionW,
        NomadHomeFigures.LINE_H, nextCaption, NomadUi.base(), Alignment.RMID, Face.CAPTION);
    label(panel, at.textRight() - nextW, at.valueY(), nextW, NomadHomeFigures.VALUE_H, next,
        dated ? NomadUi.hl() : NomadUi.gray(), Alignment.RMID, Face.VALUE);

    float wordBox = at.leftBox(Math.max(nextCaptionW, nextW));
    label(panel, left, at.valueY(), wordBox, NomadHomeFigures.VALUE_H,
        NomadText.fit(stateWord(st), wordBox, NomadHomeColumn.VALUE), stateColor(st),
        Alignment.LMID, Face.VALUE);
  }

  private static void placeJob(Slip chrome, NomadAutoforge.Recipe job, NomadGrid.Box square) {
    String name = job == null ? null : NomadAutoforgeTab.spriteName(job);
    if (name == null || name.isEmpty() || square.w <= 0f) {
      return;
    }
    try {
      SpriteAPI s = Global.getSettings().getSprite(name);
      if (s == null || s.getWidth() <= 0f || s.getHeight() <= 0f) {
        return;
      }
      float scale = Math.min(SPRITE_MAX_SCALE,
          Math.min(square.w / s.getWidth(), square.h / s.getHeight()));
      chrome.jobW = Math.round(s.getWidth() * scale);
      chrome.jobH = Math.round(s.getHeight() * scale);
      chrome.jobX = square.x + Math.round((square.w - chrome.jobW) / 2f);
      chrome.jobY = square.y + Math.round((square.h - chrome.jobH) / 2f);
      chrome.job = s;
    } catch (Exception e) {
    }
  }

  private static final class Slip extends BaseCustomUIPanelPlugin {

    private final NomadFoundryFigures.Layout at;
    private final Color edge = NomadUi.dark();
    private final Color join = NomadUi.hl();
    private final Color dot = NomadUi.base();
    SpriteAPI picture;
    SpriteAPI job;
    float jobX;
    float jobY;
    float jobW;
    float jobH;
    float built;
    float dotX = -1f;
    float dotY;
    private PositionAPI pos;

    Slip(NomadFoundryFigures.Layout at) {
      this.at = at;
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
      float y = pos.getY();
      float w = at.hero.w;
      float h = at.hero.h;
      NomadHero.picture(x, y, w, h, picture, Color.WHITE, alphaMult);
      NomadGl.fillRect(x + 1f, y + 1f, w - 2f, h - 2f, Color.BLACK, SHADE_ALPHA, alphaMult);
      if (job != null) {
        float sx = x + jobX;
        float sy = y + h - jobY - jobH;
        job.setSize(jobW, jobH);
        job.setAlphaMult(alphaMult * UNBUILT_ALPHA);
        job.render(sx, sy);
        if (built > 0f) {
          job.setAlphaMult(alphaMult);
          job.renderRegion(sx, sy, 0f, 0f, 1f, built);
          if (built < 1f) {
            NomadGl.fillRect(sx - NomadFoundryFigures.JOIN_PAST, sy + Math.round(jobH * built),
                jobW + 2f * NomadFoundryFigures.JOIN_PAST, 1f, join, JOIN_ALPHA, alphaMult);
          }
        }
      }
      NomadHero.band(x, y, w, alphaMult);
      if (dotX >= 0f) {
        NomadGl.fillRect(x + dotX, y + h - dotY - NomadFoundryFigures.DOT,
            NomadFoundryFigures.DOT, NomadFoundryFigures.DOT, dot, 255, alphaMult);
      }
      NomadHero.border(x, y, w, h, edge, alphaMult);
    }
  }

  private static void line(CustomPanelAPI panel, NomadFoundryFigures.Layout at,
      NomadFleetData data, NomadAutoforge.Line line, NomadAutoforgeQueue.LineState st,
      NomadAutoforge.Stack head, NomadAutoforge.Recipe job) {
    String text;
    Color color = NomadUi.text();
    boolean marked = false;
    String picked = null;
    switch (st) {
      case BUILDING: {
        if (head == null) {
          text = NomadStrings.get("nomad_autoforge", "fo_line_idle");
          break;
        }
        int more = NomadFoundryFigures.moreAfterHead(head.count);
        String n = Integer.toString(more);
        float rest = NomadUi.PARA.width(more > 0
            ? NomadStrings.get("nomad_autoforge", "fo_line_building_more", "", n)
            : NomadStrings.get("nomad_autoforge", "fo_line_building_one", ""));
        picked = NomadText.fit(head.name,
            Math.max(0f, at.line.w - rest - 2f * NomadText.MARGIN), NomadUi.PARA);
        text = more > 0 ? NomadStrings.get("nomad_autoforge", "fo_line_building_more", picked, n)
            : NomadStrings.get("nomad_autoforge", "fo_line_building_one", picked);
        break;
      }
      case STALLED:
        if (!NomadAutoforge.waitsOnBench(line)) {
          text = NomadStrings.get("nomad_autoforge", "fo_line_stalled",
              NomadUi.commodityName(shortGood(data, line)));
        } else if (line.benchOn) {
          text = NomadStrings.get("nomad_autoforge", "fo_line_stops_bench");
        } else {
          text = NomadStrings.get("nomad_autoforge", "fo_line_waiting_bench");
        }
        color = NomadUi.warn();
        marked = true;
        break;
      case IDLE:
        text = NomadStrings.get("nomad_autoforge", "fo_line_idle");
        break;
      case PAUSED:
        text = NomadStrings.get("nomad_autoforge", "fo_line_paused");
        break;
      case NO_FOUNDRY:
        text = NomadStrings.get("nomad_autoforge", "fo_line_none");
        break;
      case CONSTRUCTING:
        text = NomadStrings.get("nomad_autoforge", "fo_line_constructing");
        break;
      case MOTHBALLED:
        text = NomadStrings.get("nomad_autoforge", "fo_line_mothballed");
        break;
      case SUSPENDED:
        text = NomadStrings.get("nomad_autoforge", "fo_line_suspended");
        break;
      case UNSTAFFED:
        text = NomadStrings.get("nomad_autoforge", "fo_line_unstaffed");
        break;
      case LACKING:
        text = NomadStrings.get("nomad_autoforge", "fo_line_lacking");
        break;
      case NO_FLAGSHIP:
        text = NomadStrings.get("nomad_flotilla", "ui2_needs_paused_cure");
        break;
      case STOPPED:
      default:
        text = word("fo_line_stopped");
        break;
    }
    NomadGrid.Box box = at.lineBox(marked);
    if (marked) {
      NomadMark.add(panel, at.line.x, at.line.y - 1f, NomadHomeFigures.MARK, NomadUi.warn());
    }
    LabelAPI l = label(panel, box.x, box.y, box.w, box.h,
        NomadText.fit(text, box.w, NomadUi.PARA), color, Alignment.LMID, Face.PARA);
    if (picked != null && !picked.isEmpty()) {
      l.setHighlight(picked);
      l.setHighlightColor(job == null ? NomadUi.base() : job.nameColor);
    }
  }

  private static String shortGood(NomadFleetData data, NomadAutoforge.Line line) {
    String good = NomadAutoforge.shortMaterial(data, line);
    if (good != null || line.row == null) {
      return good == null ? "" : good;
    }
    for (String first : line.row.uses.keySet()) {
      return first;
    }
    return "";
  }

  private static void progress(CustomPanelAPI panel, NomadFoundryFigures.Layout at,
      NomadFleetData data, NomadAutoforge.Line line, NomadAutoforgeQueue.LineState st,
      boolean hasJob, float built) {
    boolean advancing = NomadAutoforgeQueue.advancing(st);
    CustomPanelAPI bar = panel.createCustomPanel(at.bar.w, at.bar.h,
        new Bar(Math.round(at.bar.w * built), advancing ? NomadUi.base() : NomadUi.gray()));
    panel.addComponent(bar).inTL(at.bar.x, at.bar.y);
    if (!hasJob) {
      return;
    }
    String pct = NomadUi.pct(built);
    float pctW = NomadText.need(pct, NomadUi.PARA);
    label(panel, at.figures.x, at.figures.y, pctW, at.figures.h, pct, NomadUi.hl(),
        Alignment.LMID, Face.PARA);

    if (!advancing && st != NomadAutoforgeQueue.LineState.STALLED) {
      return;
    }
    Map<String, Float> uses = NomadAutoforge.materialsPerMonth(data, line);
    if (uses.isEmpty()) {
      return;
    }
    List<String> goods = new ArrayList<String>();
    List<String> figures = new ArrayList<String>();
    for (Map.Entry<String, Float> use : uses.entrySet()) {
      goods.add(use.getKey());
      figures.add(NomadWorkCardModel.compact(-Math.abs(use.getValue().floatValue())));
    }
    String tail = NomadStrings.get("nomad_autoforge", "fo_a_month");
    float tailW = NomadText.need(tail, NomadUi.PARA);
    int shown = NomadFoundryFigures.flowsShown(figures, tailW, at.flowsBox(pctW), NomadUi.PARA);
    if (shown <= 0) {
      return;
    }
    float right = at.figures.x + at.figures.w;
    float x = right - tailW - NomadWorkCardModel.FLOW_GAP
        - NomadFoundryFigures.flowsWidth(figures.subList(0, shown), NomadUi.PARA);
    for (int i = 0; i < shown; i++) {
      float numW = NomadText.need(figures.get(i), NomadUi.PARA);
      label(panel, x, at.figures.y, numW, at.figures.h, figures.get(i), NomadUi.gray(),
          Alignment.LMID, Face.PARA);
      x += numW + NomadWorkCardModel.FLOW_NUM_PAD;
      icon(panel, goods.get(i), x, at.figures.y);
      x += NomadWorkCardModel.ICON_PX + NomadWorkCardModel.FLOW_GAP;
    }
    label(panel, right - tailW, at.figures.y, tailW, at.figures.h, tail, NomadUi.gray(),
        Alignment.RMID, Face.PARA);
  }

  private static void icon(CustomPanelAPI panel, String good, float x, float y) {
    TooltipMakerAPI ic = panel.createUIElement(NomadWorkCardModel.ICON_PX,
        NomadWorkCardModel.ICON_PX, false);
    NomadUi.iconInline(ic, good, NomadWorkCardModel.ICON_PX, 0f);
    NomadCell.place(panel, ic, x, y);
  }

  private static final class Bar extends BaseCustomUIPanelPlugin {

    private final float filled;
    private final Color fill;
    private final Color track = NomadUi.dark();
    private PositionAPI pos;

    Bar(float filled, Color fill) {
      this.filled = filled;
      this.fill = fill;
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
      NomadGl.fillRect(pos.getX(), pos.getY(), pos.getWidth(), pos.getHeight(), track,
          TRACK_ALPHA, alphaMult);
      if (filled > 0f) {
        NomadGl.fillRect(pos.getX(), pos.getY(), filled, pos.getHeight(), fill, 255, alphaMult);
      }
    }
  }

  private static void queueHead(CustomPanelAPI panel, NomadFoundryFigures.Layout at,
      NomadFleetData data, boolean empty) {
    TooltipMakerAPI head = panel.createUIElement(at.head.w, at.head.h, false);
    head.addSectionHeading(NomadStrings.get("nomad_autoforge", "fo_queue_head",
        Integer.toString(data.getAutoforgeQueue().size()),
        Integer.toString(NomadAutoforge.QUEUE_MAX)), NomadUi.base(), NomadUi.dark(),
        Alignment.MID, at.head.w - 2f * NomadText.INSET, 0f);
    panel.addUIElement(head).inTL(at.head.x, at.head.y);

    boolean paused = NomadAutoforge.isPaused();
    TooltipMakerAPI pauseEl = NomadCell.element(panel, at.pause.w, at.pause.h);
    pauseEl.addButton(word(paused ? "fo_resume" : "fo_pause"),
        NomadAutoforgeTab.P + "pause", NomadUi.base(), NomadUi.dark(), at.pause.w, at.pause.h,
        0f);
    pauseEl.addTooltipToPrevious(NomadAutoforgeTab.tip(paused
        ? NomadStrings.get("nomad_autoforge", "af_paused_tip")
        : NomadStrings.get("nomad_autoforge", "af_pause_tip")),
        TooltipMakerAPI.TooltipLocation.BELOW);
    NomadCell.place(panel, pauseEl, at.pause.x, at.pause.y);

    TooltipMakerAPI clearEl = NomadCell.element(panel, at.clear.w, at.clear.h);
    ButtonAPI clear = clearEl.addButton(word("fo_clear"), NomadAutoforgeTab.P + "clear",
        NomadUi.text(), NomadUi.dark(), at.clear.w, at.clear.h, 0f);
    NomadUi.setEnabledSayingWhy(clear, !empty);
    clearEl.addTooltipToPrevious(NomadAutoforgeTab.tip(empty
        ? NomadStrings.get("nomad_autoforge", "af_clear_empty_tip")
        : NomadStrings.get("nomad_autoforge", "af_clear_tip")),
        TooltipMakerAPI.TooltipLocation.BELOW);
    NomadCell.place(panel, clearEl, at.clear.x, at.clear.y);
  }

  private static void rows(CustomPanelAPI panel, NomadFoundryFigures.Layout at,
      NomadFleetData data, NomadAutoforge.Line line, List<NomadAutoforge.Stack> stacks,
      float headDays, boolean dated) {
    List<NomadAutoforge.Stack> shown = new ArrayList<NomadAutoforge.Stack>();
    List<Integer> counted = new ArrayList<Integer>();
    for (int i = 0; i < stacks.size(); i++) {
      int count = stacks.get(i).count - (i == 0 ? 1 : 0);
      if (count > 0) {
        shown.add(stacks.get(i));
        counted.add(Integer.valueOf(count));
      }
    }
    int n = shown.size();
    if (n == 0) {
      return;
    }
    String caption = NomadStrings.get("nomad_autoforge", "fo_col_delivered");
    float captionW = NomadText.need(caption, NomadUi.PARA);
    label(panel, at.delivered.x + at.delivered.w - captionW, at.delivered.y, captionW,
        at.delivered.h, caption, NomadUi.gray(), Alignment.RMID, Face.PARA);
    NomadAutoforge.Recipe[] recipes = new NomadAutoforge.Recipe[n];
    float[] unitDays = new float[n];
    int[] counts = new int[n];
    for (int i = 0; i < n; i++) {
      NomadAutoforge.Stack s = shown.get(i);
      recipes[i] = NomadAutoforge.recipeFor(data, s.type, s.id);
      unitDays[i] = recipes[i] == null || !dated ? 0f
          : NomadAutoforge.daysFor(data, recipes[i], line);
      counts[i] = counted.get(i).intValue();
    }
    float[] sums = NomadFoundryFigures.deliveryDays(dated ? headDays : 0f, unitDays, counts);
    String[] dayText = new String[n];
    float daysW = 0f;
    for (int i = 0; i < n; i++) {
      dayText[i] = dated ? days(sums[i]) : NO_VALUE;
      daysW = Math.max(daysW, NomadText.need(dayText[i], NomadUi.PARA));
    }
    float nameBox = at.rowNameBox(daysW);
    float rowH = at.rowH;
    float contentH = n * rowH;
    float textY = (float) Math.floor((rowH - 1f - NomadHomeFigures.LINE_H) / 2f);
    float spriteY = (float) Math.floor((rowH - 1f - NomadFoundryFigures.ROW_SPRITE) / 2f);

    CustomPanelAPI list = panel.createCustomPanel(at.w, contentH, new RowRules(n, rowH));
    for (int i = 0; i < n; i++) {
      float y = i * rowH;
      NomadAutoforge.Stack s = shown.get(i);
      if (recipes[i] != null) {
        NomadAutoforgeTab.sprite(list, NomadAutoforgeTab.spriteName(recipes[i]), 1f,
            y + spriteY, NomadFoundryFigures.ROW_SPRITE);
      }
      String times = counts[i] > 1 ? " x" + counts[i] : "";
      String name = NomadText.fit(s.name,
          Math.max(0f, nameBox - NomadUi.PARA.width(times) - NomadText.MARGIN), NomadUi.PARA)
          + times;
      label(list, NomadFoundryFigures.ROW_NAME_X, y + textY, nameBox, NomadHomeFigures.LINE_H,
          name, recipes[i] == null ? NomadUi.base() : recipes[i].nameColor, Alignment.LMID,
          Face.PARA);
      label(list, at.w - NomadFoundryFigures.DAYS_RIGHT - daysW, y + textY, daysW,
          NomadHomeFigures.LINE_H, dayText[i], dated ? NomadUi.text() : NomadUi.gray(),
          Alignment.RMID, Face.PARA);
      if (recipes[i] != null) {
        NomadAutoforgeTab.hoverArea(list, 0f, y, at.w, rowH, recipes[i],
            NomadAutoforgeTab.statTip(recipes[i], data, line),
            TooltipMakerAPI.TooltipLocation.RIGHT);
      }
    }

    if (contentH <= at.rows.h) {
      panel.addComponent(list).inTL(at.rows.x, at.rows.y);
      return;
    }
    TooltipMakerAPI scroll = panel.createUIElement(at.w + SHIFT + NomadGrid.GUTTER, at.rows.h,
        true);
    scroll.addCustom(list, 0f);
    panel.addUIElement(scroll).inTL(at.rows.x - SHIFT, at.rows.y);
    NomadAutoforgeTab.QUEUE.place(scroll, contentH, at.rows.h);
  }

  private static final class RowRules extends BaseCustomUIPanelPlugin {

    private final int rows;
    private final float rowH;
    private final Color rule = NomadUi.dark();
    private PositionAPI pos;

    RowRules(int rows, float rowH) {
      this.rows = rows;
      this.rowH = rowH;
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
      float top = pos.getY() + pos.getHeight();
      for (int i = 1; i <= rows; i++) {
        NomadGl.fillRect(pos.getX(), top - i * rowH, pos.getWidth(), 1f, rule, RULE_ALPHA,
            alphaMult);
      }
    }
  }

  private static void foot(CustomPanelAPI panel, NomadFoundryFigures.Layout at,
      NomadFleetData data) {
    int count = NomadAutoforge.dModsFor(data);
    String n = Integer.toString(count);
    LabelAPI l = label(panel, at.foot.x, at.foot.y, at.foot.w, at.foot.h,
        NomadText.fit(word(count == 1 ? "fo_foot_dmods_one" : "fo_foot_dmods", n), at.foot.w,
            NomadUi.PARA),
        NomadUi.gray(), Alignment.LMID, Face.PARA);
    l.setHighlight(n);
    l.setHighlightColor(NomadUi.hl());
  }

  private static TooltipMakerAPI.TooltipCreator tip(final NomadFleetData data,
      final NomadAutoforge.Line line, final NomadAutoforgeQueue.LineState st) {
    return NomadTip.guard(new NomadTip(NomadFoundryFigures.TIP_W) {
      @Override
      public void createTooltip(TooltipMakerAPI t, boolean expanded, Object param) {
        writeTip(t, data, line, st);
      }
    });
  }

  private static void writeTip(TooltipMakerAPI t, NomadFleetData data, NomadAutoforge.Line line,
      NomadAutoforgeQueue.LineState st) {
    t.addTitle(line.foundry == null ? stateWord(st)
        : NomadStrings.get("nomad_autoforge", "fo_tip_title", stateWord(st)), stateColor(st));
    String halt = NomadAutoforgeTab.haltLine(st);
    if (halt != null) {
      t.addPara(halt, NomadUi.warn(), OPAD);
    }
    t.addPara(NomadStrings.get("nomad_autoforge", "fo_tip_what"), OPAD);

    if (line.row != null && st != NomadAutoforgeQueue.LineState.NO_FLAGSHIP) {
      rate(t, line);
      Map<String, Float> uses = Foundry.materials(line.row, line.pointsPerMonth, line.items,
          line.normalOrStory, line.k);
      if (!uses.isEmpty()) {
        heading(t, NomadStrings.get("nomad_autoforge", "fo_tip_uses"));
        for (Map.Entry<String, Float> use : uses.entrySet()) {
          tipRow(t, Misc.getWithDGS(Math.round(use.getValue().floatValue())), NomadUi.hl(),
              use.getKey(), NomadUi.commodityName(use.getKey()), NomadUi.text());
        }
      }
    }

    heading(t, NomadStrings.get("nomad_autoforge", "fo_tip_out"));
    int count = NomadAutoforge.dModsFor(data);
    String dmods = Integer.toString(count);
    LabelAPI l = t.addPara(word(count == 1 ? "fo_tip_dmods_one" : "fo_tip_dmods", dmods),
        NomadUi.text(), PAD);
    l.setHighlight(dmods);
    l.setHighlightColor(NomadUi.hl());
    t.addSpacer(PAD);
    List<String> items = data.getInstalledItems();
    boolean pristine = items.contains(Work.PRISTINE_NANOFORGE);
    boolean corrupted = items.contains(Work.CORRUPTED_NANOFORGE);
    String corruptedName = NomadItems.displayName(Work.CORRUPTED_NANOFORGE);
    String pristineName = NomadItems.displayName(Work.PRISTINE_NANOFORGE);
    forgeRow(t, NomadAutoforge.dModsBase(), word("fo_tip_no_nanoforge"), !pristine && !corrupted);
    forgeRow(t, NomadAutoforge.dModsCorrupted(),
        corrupted ? word("fo_tip_installed", corruptedName) : corruptedName,
        corrupted && !pristine);
    forgeRow(t, NomadAutoforge.dModsPristine(),
        pristine ? word("fo_tip_installed", pristineName) : pristineName, pristine);
  }

  private static void rate(TooltipMakerAPI t, NomadAutoforge.Line line) {
    String total = Misc.getWithDGS(Math.round(line.pointsPerMonth));
    LabelAPI l = t.addPara(NomadStrings.get("nomad_autoforge", "fo_tip_rate", total),
        NomadUi.text(), OPAD);
    l.setHighlight(total);
    l.setHighlightColor(NomadUi.hl());
    t.addSpacer(PAD);
    tipRow(t, Misc.getWithDGS(Math.round(line.row.makes(Works.POINTS))), NomadUi.hl(), null,
        NomadStrings.get("nomad_flotilla", "cards_size", Integer.toString(line.row.size)),
        NomadUi.text());
    tipRow(t, times(line.efficiency), NomadHomeColumn.efficiencyColor(line.efficiency), null,
        NomadStrings.get("nomad_flotilla", "panels_eff_title"), NomadUi.text());
    float scale = line.k.itemEffectMult(line.normalOrStory);
    if (line.items.contains(Work.CRYOARITHMETIC_ENGINE)) {
      share(t, Work.CRYOARITHMETIC_ENGINE, line.k.itemCryoarithmeticAutoforgeSpeed * scale);
    }
    if (line.items.contains(Work.PRISTINE_NANOFORGE)) {
      share(t, Work.PRISTINE_NANOFORGE, line.k.itemPristineNanoforgeAutoforge * scale);
    } else if (line.items.contains(Work.CORRUPTED_NANOFORGE)) {
      share(t, Work.CORRUPTED_NANOFORGE, line.k.itemCorruptedNanoforgeAutoforge * scale);
    }
    Rules k = NomadModel.rules();
    if (k.survivalFoundryPointsMult != 1f) {
      tipRow(t, times(k.survivalFoundryPointsMult), NomadUi.hl(), null,
          word("fo_tip_survival"), NomadUi.text());
    }
    if (k.productionMult != 1f) {
      tipRow(t, times(k.productionMult), NomadUi.hl(), null, word("fo_tip_production_mult"),
          NomadUi.text());
    }
    if (k.foundryMult != 1f) {
      tipRow(t, times(k.foundryMult), NomadUi.hl(), null, word("fo_tip_foundry_mult"),
          NomadUi.text());
    }
  }

  private static void share(TooltipMakerAPI t, String item, float share) {
    tipRow(t, "+" + NomadUi.pct(share), NomadUi.good(), null, NomadItems.displayName(item),
        NomadUi.text());
  }

  private static String times(float v) {
    return "x" + String.format(Locale.ROOT, "%.2f", v);
  }

  private static void forgeRow(TooltipMakerAPI t, int dmods, String name, boolean inForce) {
    tipRow(t, Integer.toString(dmods), inForce ? NomadUi.hl() : NomadUi.gray(), null, name,
        inForce ? NomadUi.text() : NomadUi.gray());
  }

  private static void tipRow(TooltipMakerAPI t, String value, Color valueColor, String good,
      String label, Color labelColor) {
    float width = NomadFoundryFigures.TIP_W - 2f * NomadText.INSET;
    CustomPanelAPI row = Global.getSettings().createCustom(width, NomadFoundryFigures.TIP_ROW_H,
        null);
    NomadCell.box(row, 0f, 0f, NomadFoundryFigures.TIP_VALUE_W, NomadFoundryFigures.TIP_ROW_H,
        value, valueColor, Alignment.RMID, 1f);
    float x = NomadFoundryFigures.TIP_VALUE_W + NomadFoundryFigures.TIP_VALUE_GAP;
    if (good != null) {
      icon(row, good, x, 1f);
      x += NomadWorkCardModel.ICON_PX + NomadWorkCardModel.FLOW_GAP;
    }
    NomadCell.box(row, x, 0f, width - x, NomadFoundryFigures.TIP_ROW_H, label, labelColor,
        Alignment.LMID, 1f);
    t.addCustom(row, 0f);
  }

  private static void heading(TooltipMakerAPI t, String text) {
    t.addSectionHeading(text, NomadUi.base(), NomadUi.dark(), Alignment.MID, OPAD);
  }

  private static void hover(CustomPanelAPI panel, NomadGrid.Box box,
      TooltipMakerAPI.TooltipCreator tip) {
    TooltipMakerAPI el = NomadCell.element(panel, box.w, box.h);
    CustomPanelAPI area = panel.createCustomPanel(box.w, box.h, new BaseCustomUIPanelPlugin());
    el.addCustom(area, 0f);
    el.addTooltipToPrevious(tip, TooltipMakerAPI.TooltipLocation.RIGHT, false);
    NomadCell.place(panel, el, box.x, box.y);
  }

  private static float clamp01(float v) {
    return v < 0f ? 0f : (v > 1f ? 1f : v);
  }
}
