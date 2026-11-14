package nomad.ui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.Fonts;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import nomad.NomadStrings;
import nomad.campaign.NomadCrew;
import nomad.campaign.econ.model.BasicNeeds;
import nomad.campaign.econ.model.NeedsLevel;
import nomad.campaign.econ.model.WorkOrders;
import nomad.campaign.econ.model.WorkState;
import nomad.data.NomadFleetData;

public final class NomadConfirm {

  private NomadConfirm() {
  }

  private static final float PAD = 3f;
  private static final float OPAD = 10f;

  private static final float VALUE_W = 64f;
  private static final float VALUE_GAP = 10f;
  private static final float FIGURES_GAP = 11f;
  static final boolean LEVEL_CAPTION = true;
  private static final float CAPTION_GAP = 8f;
  private static final float ROW_H = 15f;
  private static final float LEVEL_ROW_GAP = 4f;
  private static final float LEVEL_WORD_W = 42f;
  private static final float LEVEL_NAME_GAP = 6f;
  private static final float PIP_DOWN = 5f;
  private static final float MARKED_GAP = 8f;

  static final NomadText.Measure TITLE = new NomadText.Measure() {
    @Override
    public float width(String s) {
      return s == null || s.isEmpty() ? 0f
          : Global.getSettings().computeStringWidth(s, Fonts.INSIGNIA_LARGE);
    }
  };

  public static boolean needed(Object buttonId) {
    NomadWorkActions.Press p = orderPress(buttonId);
    if (p != null) {
      Order o = NomadFleetData.exists() ? order(p, NomadFleetData.getInstance()) : null;
      return o != null && o.asks();
    }
    return NomadFlotillaTab.needsConfirm(buttonId)
        || NomadAutoforgeTab.needsConfirm(buttonId);
  }

  public static void prompt(Object buttonId, TooltipMakerAPI prompt, NomadFleetData data) {
    Ask a = ask(buttonId, data);
    if (a == null) {
      return;
    }
    question(prompt, a);
    if (a.split) {
      NomadSplitConfirm.body(prompt, OPAD, NomadSplitConfirm.BLEED_FLOW);
      return;
    }
    String picture = a.workId == null ? null
        : NomadWorkCard.illustrationPath(a.workId, a.view.catalog.work(a.workId));
    if (picture == null) {
      column(prompt, a, OPAD, true);
      return;
    }
    TooltipMakerAPI beside = prompt.beginImageWithText(picture, NomadWorkCardModel.IMAGE_H);
    column(beside, a, 0f, true);
    prompt.addImageWithText(OPAD);
  }

  public static String acceptLabel(Object buttonId) {
    NomadWorkActions.Press p = NomadWorkActions.parse(buttonId);
    if (p != null) {
      if (p.verb == NomadWorkActions.Verb.REMOTE) {
        return NomadStrings.get("nomad_flotilla", "screen_verb_confirm");
      }
      if (p.verb == NomadWorkActions.Verb.CANCEL) {
        return NomadWorkActions.label(NomadWorkActions.Verb.CANCEL);
      }
      return NomadFlotillaTab.confirmLabel(buttonId);
    }
    if (NomadAutoforgeTab.needsConfirm(buttonId)) {
      return "Clear";
    }
    return "Confirm";
  }

  public static String cancelLabel(Object buttonId) {
    NomadWorkActions.Press p = NomadWorkActions.parse(buttonId);
    if (p != null && p.verb == NomadWorkActions.Verb.CANCEL) {
      return NomadWorkActions.label(NomadWorkActions.Verb.PICK_CLOSE);
    }
    return "Cancel";
  }

  static final class Figure {

    final String value;
    final String name;
    final Color color;

    Figure(String value, String name, Color color) {
      this.value = value;
      this.name = name;
      this.color = color;
    }
  }

  static final class Line {

    final String text;
    final String[] values;

    Line(String text, String... values) {
      this.text = text;
      this.values = values;
    }
  }

  static final class Ask {

    final String question;
    final String[] names;
    String workId;
    NomadFlotillaView view;
    final List<Figure> figures = new ArrayList<Figure>();
    final List<Line> lines = new ArrayList<Line>();
    NeedsLevel now;
    NeedsLevel after;
    String leave;
    String disables;
    boolean split;
    boolean warns;

    Ask(String question, String... names) {
      this.question = question;
      this.names = names;
    }

    boolean levelChanges() {
      return now != null && after != null && now != after;
    }

    boolean stopsEveryWork() {
      return NomadNeedsTag.stopsEveryWork(now, after);
    }

    String leaveText() {
      return leave == null ? null : NomadStrings.get("nomad_flotilla", "ui4_leave", leave);
    }

    void figure(String value, String name, Color color) {
      figures.add(new Figure(value, name, color));
    }
  }

  static Ask ask(Object buttonId, NomadFleetData data) {
    if (data == null) {
      return null;
    }
    NomadWorkActions.Press p = NomadWorkActions.parse(buttonId);
    if (p == null) {
      return NomadAutoforgeTab.needsConfirm(buttonId)
          ? new Ask(NomadStrings.get("nomad_autoforge", "af_clear_confirm")) : null;
    }
    if (orderPress(buttonId) != null) {
      Order o = order(p, data);
      return o == null ? null : orderAsk(o);
    }
    switch (p.verb) {
      case SPLIT: {
        Ask a = new Ask(NomadStrings.get("nomad_flotilla", "ui4_ask_split"));
        a.split = true;
        return a;
      }
      case TARGET: {
        NomadFlotillaView view = NomadFlotillaView.forOrders(data);
        String good = p.arg(0);
        String name = NomadUi.commodityName(good);
        String to = NomadUi.i(NomadWorkActions.targetAfter(view, good, p.arg(1)));
        Ask a = new Ask(NomadStrings.get("nomad_flotilla", "confirm_target", name, to), name, to);
        levelAfter(a, view, NomadWorkActions.needsAfterTarget(view, good, p.arg(1)));
        return a;
      }
      case CREW: {
        NomadFlotillaView view = NomadFlotillaView.forOrders(data);
        int n = NomadWorkActions.crewMoved(view, p.arg(0));
        String heads = "" + Math.abs(n);
        Ask a = new Ask(n >= 0 ? NomadStrings.get("nomad_flotilla", "confirm_crew_in", heads)
            : NomadStrings.get("nomad_flotilla", "screen_confirm_crew_out", heads), heads);
        if (n < 0) {
          a.figure(NomadUi.credits(NomadCrew.priceToTake(-n)),
              NomadStrings.get("nomad_flotilla", "ui4_cost"), NomadUi.hl());
        }
        levelAfter(a, view, NomadWorkActions.needsAfterCrew(view, p.arg(0)));
        return a;
      }
      default:
        return null;
    }
  }

  private static void levelAfter(Ask a, NomadFlotillaView view, BasicNeeds.Reading after) {
    if (!view.live || after == null || after.level == null) {
      return;
    }
    a.now = view.sheet.needs.level;
    a.after = after.level;
    if (view.wouldStartLeaving(after)) {
      a.leave = NomadUi.pct(view.rules.leavePerMonth(after.level));
    }
    a.warns |= a.stopsEveryWork();
  }

  private static NomadWorkActions.Press orderPress(Object buttonId) {
    NomadWorkActions.Press p = NomadWorkActions.parse(buttonId);
    if (p == null) {
      return null;
    }
    switch (p.verb) {
      case BUILD:
      case UPGRADE:
      case DOWNGRADE:
      case SCRAP:
      case CANCEL:
      case MOTHBALL:
      case REVIVE:
      case REMOTE:
        return p;
      default:
        return null;
    }
  }

  static final class Order {

    final NomadWorkActions.Verb verb;
    final String workId;
    final boolean on;
    final NomadFlotillaView view;
    final WorkOrders.Quote quote;

    Order(NomadWorkActions.Verb verb, String workId, boolean on, NomadFlotillaView view,
        WorkOrders.Quote quote) {
      this.verb = verb;
      this.workId = workId;
      this.on = on;
      this.view = view;
      this.quote = quote;
    }

    NeedsLevel now() {
      return view.sheet.needs.level;
    }

    NeedsLevel after() {
      return quote.after.level;
    }

    boolean lowersLevel() {
      return after().ordinal() < now().ordinal();
    }

    boolean asks() {
      if (!view.live || !quote.allowed()) {
        return false;
      }
      if (verb == NomadWorkActions.Verb.REMOTE && turnsRemoteOff()) {
        return true;
      }
      return asks(verb, quote.days, lowersLevel(), quote.residentsWouldLeave);
    }

    boolean turnsRemoteOff() {
      if (on) {
        return false;
      }
      nomad.campaign.econ.model.WorkState rig =
          view.work(nomad.campaign.econ.model.Works.MINING_RIG);
      return rig != null && rig.remote && !rig.remoteSwitching;
    }

    static boolean asks(NomadWorkActions.Verb verb, float days, boolean lowersLevel,
        boolean residentsLeave) {
      switch (verb) {
        case DOWNGRADE:
        case SCRAP:
        case CANCEL:
          return true;
        case REMOTE:
          return days > 0f;
        default:
          return lowersLevel || residentsLeave;
      }
    }
  }

  static Order order(NomadWorkActions.Press p, NomadFleetData data) {
    NomadFlotillaView view = NomadFlotillaView.forOrders(data);
    String id = p.arg(0);
    switch (p.verb) {
      case BUILD:
        return new Order(p.verb, id, false, view, view.quoteBuild(id));
      case UPGRADE:
        return new Order(p.verb, id, false, view, view.quoteUpgrade(id));
      case DOWNGRADE:
        return new Order(p.verb, id, false, view, view.quoteDowngrade(id));
      case SCRAP:
        return new Order(p.verb, id, false, view, view.quoteScrap(id));
      case CANCEL:
        return new Order(p.verb, id, false, view, view.quoteCancel(id));
      case MOTHBALL:
        return new Order(p.verb, id, false, view, view.quoteMothball(id, true));
      case REVIVE:
        return new Order(p.verb, id, false, view, view.quoteMothball(id, false));
      case REMOTE: {
        boolean on = NomadWorkActions.ON.equals(id);
        return new Order(p.verb, nomad.campaign.econ.model.Works.MINING_RIG, on, view,
            view.quoteRemote(on));
      }
      default:
        return null;
    }
  }

  private static Ask orderAsk(Order o) {
    NomadFlotillaView view = o.view;
    WorkOrders.Quote q = o.quote;
    String name = view.workName(o.workId);
    Ask a;
    switch (o.verb) {
      case BUILD:
        a = new Ask(NomadStrings.get("nomad_flotilla", "confirm_build", name), name);
        costAndDays(a, q);
        break;
      case UPGRADE: {
        String size = "" + (sizeOf(view, o.workId) + 1);
        a = new Ask(NomadStrings.get("nomad_flotilla", "confirm_upgrade", name, size), name,
            size);
        costAndDays(a, q);
        break;
      }
      case DOWNGRADE: {
        String size = "" + (sizeOf(view, o.workId) - 1);
        a = new Ask(NomadStrings.get("nomad_flotilla", "ui4_ask_downgrade", name, size), name,
            size);
        back(a, q);
        disables(a, view, o.workId, sizeOf(view, o.workId) - 1);
        break;
      }
      case SCRAP:
        a = new Ask(NomadStrings.get("nomad_flotilla", "ui4_ask_scrap", name), name);
        back(a, q);
        ofCost(a, view.shared.workScrapRefund);
        disables(a, view, o.workId, 0);
        a.warns = true;
        break;
      case CANCEL:
        a = new Ask(NomadStrings.get("nomad_flotilla", "ui4_ask_cancel", name), name);
        back(a, q);
        ofCost(a, 1f);
        break;
      case MOTHBALL:
        a = new Ask(NomadStrings.get("nomad_flotilla", "shell_confirm_mothball", name), name);
        break;
      case REVIVE:
        a = new Ask(NomadStrings.get("nomad_flotilla", "screen_confirm_revive", name), name);
        break;
      case REMOTE: {
        if (!o.on) {
          a = new Ask(NomadStrings.get("nomad_flotilla", "shell_confirm_remote_off"));
          String back = NomadUi.i(view.rules.remoteOnDays);
          a.lines.add(new Line(NomadStrings.get("nomad_flotilla", "shell_confirm_remote_back_on",
              back), back));
          return a;
        }
        a = new Ask(NomadStrings.get("nomad_flotilla", "shell_confirm_remote_on"));
        String days = NomadUi.i(q.days);
        a.lines.add(new Line(NomadStrings.get("nomad_flotilla", "act_remote_turning_on", days),
            days));
        a.lines.add(new Line(NomadStrings.get("nomad_flotilla", "shell_confirm_remote_wait")));
        return a;
      }
      default:
        return null;
    }
    a.workId = o.workId;
    a.view = view;
    a.now = o.now();
    a.after = o.after();
    String needs = NomadWorkPanel.needsLine(q);
    if (needs != null) {
      a.lines.add(new Line(needs));
    }
    if (q.residentsWouldLeave) {
      a.leave = NomadUi.pct(view.rules.leavePerMonth(o.after()));
    }
    a.warns |= a.stopsEveryWork();
    return a;
  }

  private static void costAndDays(Ask a, WorkOrders.Quote q) {
    if (q.credits > 0f) {
      a.figure(NomadUi.credits(q.credits), NomadStrings.get("nomad_flotilla", "ui4_cost"),
          NomadUi.hl());
    }
    if (q.days > 0f) {
      a.figure(NomadUi.i(q.days), NomadStrings.get("nomad_flotilla", "ui4_days"), NomadUi.hl());
    }
  }

  private static void back(Ask a, WorkOrders.Quote q) {
    a.figure("+" + NomadUi.credits(-q.credits), NomadStrings.get("nomad_flotilla", "ui4_back"),
        NomadUi.good());
  }

  private static void disables(Ask a, NomadFlotillaView view, String workId, int toSize) {
    a.disables = NomadWorkActions.lowerWarning(view, workId, toSize);
  }

  private static void ofCost(Ask a, float share) {
    a.figure(NomadUi.pct(share), NomadStrings.get("nomad_flotilla", "ui4_of_cost"),
        NomadUi.hl());
  }

  private static int sizeOf(NomadFlotillaView view, String workId) {
    WorkState w = view.work(workId);
    return w == null ? 0 : w.size;
  }

  static void question(TooltipMakerAPI t, Ask a) {
    t.setParaInsigniaLarge();
    LabelAPI q = t.addPara(a.question, Misc.getTooltipTitleAndLightHighlightColor(), 0f);
    if (a.names.length > 0) {
      q.setHighlight(a.names);
      q.setHighlightColor(NomadUi.hl());
    }
    t.setParaFontDefault();
  }

  static boolean leaveFits(Ask a, float width) {
    String text = a.leaveText();
    return text == null
        || NomadHomeFigures.MARK_STEP + NomadText.need(text, NomadUi.PARA) <= width;
  }

  static void column(TooltipMakerAPI t, Ask a, float pad, boolean leaveToo) {
    float w = t.getWidthSoFar();
    float next = pad;
    if (!a.figures.isEmpty()) {
      float valueW = VALUE_W;
      for (Figure f : a.figures) {
        valueW = Math.max(valueW, NomadText.need(f.value, NomadUi.PARA));
      }
      t.beginGridFlipped(w, 1, NomadUi.text(), valueW, VALUE_GAP);
      int row = 0;
      for (Figure f : a.figures) {
        t.addToGrid(0, row++, f.name, f.value, f.color);
      }
      t.addGrid(next);
      next = FIGURES_GAP;
    }
    for (Line l : a.lines) {
      LabelAPI p = t.addPara(l.text, next);
      if (l.values.length > 0) {
        p.setHighlight(l.values);
        p.setHighlightColor(NomadUi.hl());
      }
      next = PAD;
    }
    if (a.disables != null) {
      marked(t, a.disables, NomadUi.warn(), next == PAD ? MARKED_GAP : next);
      next = MARKED_GAP;
    }
    if (a.levelChanges()) {
      levels(t, a.now, a.after, next);
      next = MARKED_GAP;
    }
    if (a.stopsEveryWork()) {
      marked(t, NomadStrings.get("nomad_flotilla", "needs_effect_none_run"), NomadUi.bad(), next);
      next = PAD;
    }
    if (leaveToo && a.leave != null) {
      marked(t, a.leaveText(), NomadUi.bad(), next);
    }
  }

  public static void levels(TooltipMakerAPI t, NeedsLevel now, NeedsLevel after, float pad) {
    float w = t.getWidthSoFar();
    String nowWord = NomadStrings.get("nomad_flotilla", "ui4_now");
    String afterWord = NomadStrings.get("nomad_flotilla", "ui4_after");
    float wordW = Math.max(LEVEL_WORD_W,
        NomadText.need(NomadUi.PARA, nowWord, afterWord) + LEVEL_NAME_GAP);
    float first = pad;
    if (LEVEL_CAPTION) {
      t.setParaFontOrbitron();
      t.addPara(NomadStrings.get("nomad_flotilla", "panels_needs_title"), NomadUi.base(),
          pad > 0f ? pad + CAPTION_GAP : 0f);
      t.setParaFontDefault();
      first = LEVEL_ROW_GAP;
    }
    level(t, w, wordW, nowWord, now, first);
    level(t, w, wordW, afterWord, after, LEVEL_ROW_GAP);
  }

  private static void level(TooltipMakerAPI t, float w, float wordW, String word,
      NeedsLevel level, float pad) {
    Color color = NomadHomeColumn.levelColor(level);
    Pips pips = new Pips(wordW, NomadHomeFigures.pipsLit(level, false), color);
    CustomPanelAPI row = Global.getSettings().createCustom(w, ROW_H, pips);
    NomadCell.box(row, 0f, 0f, wordW, ROW_H, word, NomadUi.gray(), Alignment.LMID, 0f);
    float nameX = wordW + Pips.WIDTH + LEVEL_NAME_GAP;
    float nameW = Math.max(1f, w - nameX);
    NomadCell.box(row, nameX, 0f, nameW, ROW_H,
        NomadText.fit(NomadFlotillaView.levelName(level), nameW, NomadUi.PARA), color,
        Alignment.LMID, 0f);
    t.addCustom(row, pad);
  }

  public static void marked(TooltipMakerAPI t, String text, Color color, float pad) {
    float w = t.getWidthSoFar();
    float box = Math.max(1f, w - NomadHomeFigures.MARK_STEP);
    int lines = NomadText.lines(text, box, NomadUi.PARA);
    float h = Math.max(NomadHomeFigures.MARK, lines * ROW_H);
    CustomPanelAPI row = Global.getSettings().createCustom(w, h, null);
    NomadMark.add(row, 0f, 0f, NomadHomeFigures.MARK, color);
    NomadCell.box(row, NomadHomeFigures.MARK_STEP, 0f, box, h, text, color, Alignment.LMID, 0f);
    t.addCustom(row, pad);
  }

  private static final class Pips extends BaseCustomUIPanelPlugin {

    static final float WIDTH = NomadHomeFigures.PIPS * NomadHomeFigures.PIP_W
        + (NomadHomeFigures.PIPS - 1) * NomadHomeFigures.PIP_GAP;

    private final float x;
    private final int lit;
    private final Color color;
    private final Color edge = mix(NomadUi.dark(), NomadUi.base(), 0.25f);
    private final Color ground = mix(Color.BLACK, NomadUi.dark(), 0.3f);
    private PositionAPI pos;

    Pips(float x, int lit, Color color) {
      this.x = x;
      this.lit = lit;
      this.color = color;
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
      float py = pos.getY() + ROW_H - PIP_DOWN - NomadHomeFigures.PIP_H;
      for (int i = 0; i < NomadHomeFigures.PIPS; i++) {
        float px = pos.getX() + x + i * (NomadHomeFigures.PIP_W + NomadHomeFigures.PIP_GAP);
        if (i < lit) {
          NomadGl.fillRect(px, py, NomadHomeFigures.PIP_W, NomadHomeFigures.PIP_H, color, 255,
              alphaMult);
        } else {
          NomadGl.box(px, py, NomadHomeFigures.PIP_W, NomadHomeFigures.PIP_H, ground, 255, edge,
              255, alphaMult);
        }
      }
    }
  }

  static Color mix(Color a, Color b, float toB) {
    float toA = 1f - toB;
    return new Color(Math.round(a.getRed() * toA + b.getRed() * toB),
        Math.round(a.getGreen() * toA + b.getGreen() * toB),
        Math.round(a.getBlue() * toA + b.getBlue() * toB));
  }
}
