package nomad.ui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import nomad.NomadStrings;
import nomad.campaign.econ.model.Marines;
import nomad.campaign.econ.model.Mining;
import nomad.campaign.econ.model.NeedsLevel;
import nomad.campaign.econ.model.Priority;
import nomad.campaign.econ.model.WorkDef;
import nomad.campaign.econ.model.WorkOrders;
import nomad.campaign.econ.model.WorkState;
import nomad.campaign.econ.model.Works;
import nomad.campaign.works.WorkPrereqChecks;
import nomad.ui.NomadWorkActions.Verb;
import nomad.ui.NomadWorkCardModel.Row;

public final class NomadWorkPanel {

  private NomadWorkPanel() {
  }

  private static final float PAD_X = NomadWorkCardModel.PANEL_PAD_X;
  private static final float PRIORITY_GAP = 4f;
  private static final float TIP_W = 300f;
  private static final Color GROUND = new Color(8, 10, 14);

  public static float height(NomadFlotillaView view, String workId) {
    return NomadWorkCardModel.panelHeight(rows(view, workId));
  }

  public static void build(CustomPanelAPI panel, float w, float h, NomadFlotillaView view,
      String workId) {
    WorkState state = view.work(workId);
    if (state == null) {
      return;
    }
    CustomPanelAPI back = panel.createCustomPanel(w, h, new Plate(w, h));
    panel.addComponent(back).inTL(0f, 0f);

    List<Row> rows = rows(view, workId);
    for (int i = 0; i < rows.size(); i++) {
      Row row = rows.get(i);
      float y = NomadWorkCardModel.rowY(rows, i);
      if (y + NomadWorkCardModel.rowHeight(row) > h + 0.5f) {
        break;
      }
      switch (row) {
        case PRIORITY:
          priorityRow(panel, w, y, view, workId, state);
          break;
        case CLOSE:
          closeRow(panel, w, y, workId);
          break;
        case MARINES:
          marinesRow(panel, w, y, view);
          break;
        case UPGRADE_WHY:
          upgradeWhyRow(panel, w, y, view, workId);
          break;
        default:
          orderRow(panel, w, y, view, workId, state, row);
          break;
      }
    }
  }

  private static List<Row> rows(NomadFlotillaView view, String workId) {
    WorkState state = view.work(workId);
    if (state == null) {
      return new ArrayList<Row>();
    }
    WorkDef def = view.catalog.work(workId);
    List<Row> plain = NomadWorkCardModel.rows(state, def);
    if (upgradeWhy(view, workId) == null) {
      return plain;
    }
    List<Row> withWhy = NomadWorkCardModel.rows(state, def, true);
    return NomadWorkCardModel.panelHeight(withWhy) < NomadGrid.PANEL_MAX_H - 0.5f ? withWhy
        : plain;
  }

  private static String upgradeWhy(NomadFlotillaView view, String workId) {
    WorkState w = view.work(workId);
    WorkDef def = view.catalog.work(workId);
    if (!view.live || w == null || w.isBuilding() || def == null || !def.isSized()) {
      return null;
    }
    WorkOrders.Quote q = view.quoteUpgrade(workId);
    if (q.refusal == WorkOrders.Refusal.NEEDS_PREREQ && !q.unmet.isEmpty()) {
      return NomadPrereqText.line(q.unmet.get(0), WorkPrereqChecks.NAMES,
          NomadGrid.CARD_W - 2f * PAD_X, NomadUi.PARA, NomadFlotillaView.FLOTILLA);
    }
    if (q.refusal == WorkOrders.Refusal.NO_SPACE) {
      return NomadStrings.get("nomad_flotilla", "ui2_picker_needs_space",
          NomadPanelFigures.heads(Math.max(1f, (float) Math.ceil(q.space - q.spaceFree))));
    }
    return null;
  }

  private static void upgradeWhyRow(CustomPanelAPI panel, float w, float y,
      NomadFlotillaView view, String workId) {
    String why = upgradeWhy(view, workId);
    if (why == null) {
      return;
    }
    float inner = w - 2f * PAD_X;
    NomadCell.box(panel, PAD_X, y, inner, NomadWorkCardModel.REASON_H,
        NomadText.fit(why, inner, NomadUi.PARA), NomadUi.bad(), Alignment.TL, 0f);
  }

  private static void orderRow(CustomPanelAPI panel, float w, float y, NomadFlotillaView view,
      String workId, WorkState state, Row row) {
    Verb verb = verbOf(row);
    boolean remoteOn = Mining.remotePress(state);
    boolean remoteStop = row == Row.REMOTE && Mining.remoteStarting(state);
    WorkOrders.Quote q = quoteOf(view, workId, row, remoteOn);
    Object[] args = row == Row.REMOTE
        ? new Object[] {remoteOn ? NomadWorkActions.ON : NomadWorkActions.OFF}
        : new Object[] {workId};

    float buttonW = NomadWorkCardModel.orderButtonW(w);
    TooltipMakerAPI el = NomadCell.element(panel, buttonW, NomadWorkCardModel.BUTTON_H);
    boolean enabled = view.live && q.allowed();
    String face = NomadStrings.get("nomad_flotilla", NomadWorkCardModel.orderFaceKey(row, remoteStop));
    ButtonAPI b = el.addButton(face, NomadWorkActions.id(verb, args),
        !enabled ? NomadUi.gray() : row == Row.SCRAP ? NomadUi.warn() : NomadUi.base(),
        NomadUi.dark(), Alignment.MID, CutStyle.C2_MENU, buttonW, NomadWorkCardModel.BUTTON_H, 0f);
    NomadUi.setEnabledSayingWhy(b, enabled);
    el.addTooltipToPrevious(orderTip(view, workId, state, row, q),
        TooltipMakerAPI.TooltipLocation.RIGHT, false);
    NomadCell.place(panel, el, PAD_X, y);

    float box = NomadWorkCardModel.PRICE_BOX;
    String text = row == Row.REMOTE ? remoteState(state) : price(q);
    NomadCell.box(panel, w - PAD_X - box, y, box, NomadWorkCardModel.BUTTON_H,
        NomadText.fit(text, box, NomadUi.PARA), row == Row.REMOTE ? NomadUi.text() : priceColor(q),
        Alignment.RMID, 4f);
  }

  private static void priorityRow(CustomPanelAPI panel, float w, float y,
      final NomadFlotillaView view, String workId, WorkState state) {
    float inner = w - 2f * PAD_X;
    NomadCell.box(panel, PAD_X, y, inner, NomadWorkCardModel.CAPTION_H,
        NomadStrings.get("nomad_flotilla", "cards_priority_caption"), NomadUi.gray(),
        Alignment.TL, 0f);

    Priority current = state == null ? Priority.MEDIUM : state.priority();
    Priority[] all = Priority.values();
    float bw = (inner - (all.length - 1) * PRIORITY_GAP) / all.length;
    float by = y + NomadWorkCardModel.CAPTION_H;
    for (int i = 0; i < all.length; i++) {
      final Priority p = all[i];
      final boolean isCurrent = p == current;
      TooltipMakerAPI el = NomadCell.element(panel, bw, NomadWorkCardModel.BUTTON_H);
      ButtonAPI b = el.addAreaCheckbox(NomadFlotillaView.priorityName(p),
          NomadWorkActions.id(Verb.PRIORITY, workId, p.name()), NomadUi.base(), NomadUi.dark(),
          NomadUi.bright(), bw, NomadWorkCardModel.BUTTON_H, 0f);
      b.setChecked(isCurrent);
      NomadUi.setEnabledSayingWhy(b, view.live);
      final String notLive = view.live ? "" : view.notLiveReason();
      final String name = NomadFlotillaView.priorityName(p);
      el.addTooltipToPrevious(NomadTip.guard(new NomadTip(TIP_W) {
        @Override
        public void createTooltip(TooltipMakerAPI t, boolean expanded, Object param) {
          if (!notLive.isEmpty()) {
            t.addPara(notLive, NomadUi.bad(), 0f);
            return;
          }
          if (isCurrent) {
            t.addPara(NomadStrings.get("nomad_flotilla", "cards_tip_priority_current", name),
                NomadUi.hl(), 0f);
          } else {
            t.addPara(NomadStrings.get("nomad_flotilla", "cards_tip_priority_set", name),
                NomadUi.text(), 0f);
          }
          t.addPara(NomadStrings.get("nomad_flotilla", "cards_tip_priority"), NomadUi.gray(), 6f);
        }
      }), TooltipMakerAPI.TooltipLocation.RIGHT, false);
      NomadCell.place(panel, el, PAD_X + i * (bw + PRIORITY_GAP), by);
    }
  }

  private static final NomadTargetRow MARINES_TARGET = new NomadTargetRow();

  static String typedMarines() {
    return MARINES_TARGET.typed();
  }

  static void releaseMarinesField() {
    MARINES_TARGET.release();
  }

  static void forgetMarinesField() {
    MARINES_TARGET.forget();
  }

  private static void marinesRow(CustomPanelAPI panel, float w, float y,
      final NomadFlotillaView view) {
    MARINES_TARGET.release();
    String[] words = NomadTargetRow.stepWords();
    float[] wordW = new float[words.length];
    for (int i = 0; i < words.length; i++) {
      wordW[i] = NomadUi.BUTTON.width(words[i]);
    }
    NomadWorkCardModel.MarinesBlock at = NomadWorkCardModel.MarinesBlock.of(w, wordW);
    final float target = Marines.target(view.state, view.shared);

    TooltipMakerAPI cap = NomadCell.element(panel, at.caption.w, at.caption.h);
    cap.addPara(NomadStrings.get("nomad_flotilla", "marines_target_caption"), NomadUi.gray(), 0f);
    cap.addTooltipToPrevious(NomadTip.guard(new NomadTip(TIP_W) {
      @Override
      public void createTooltip(TooltipMakerAPI t, boolean expanded, Object param) {
        t.addPara(NomadStrings.get("nomad_flotilla", "marines_target_tip"), 0f);
      }
    }), TooltipMakerAPI.TooltipLocation.RIGHT, false);
    NomadCell.place(panel, cap, at.caption.x, y + at.caption.y);

    TooltipMakerAPI line = NomadCell.element(panel, at.line.w, at.line.h);
    marinesLine(line, view.fleetMarines, target, 0f);
    NomadCell.place(panel, line, at.line.x, y + at.line.y);

    NomadTargetRow.Target t = marinesTarget(view, target);
    for (int i = 0; i < words.length; i++) {
      MARINES_TARGET.step(panel, at.step[i], y, t, NomadTargetRow.stepArg(i), words[i]);
    }
    MARINES_TARGET.field(panel, at.field, y, t);
    MARINES_TARGET.set(panel, at.set, y, t);
  }

  static void marinesLine(TooltipMakerAPI t, float fleetMarines, float target, float pad) {
    String have = NomadUi.i(fleetMarines);
    String want = NomadUi.i(target);
    if (Math.round(fleetMarines) == 1) {
      t.addPara(NomadStrings.get("nomad_flotilla", "marines_target_line_one"), pad, NomadUi.hl(),
          have, want);
    } else {
      t.addPara(NomadStrings.get("nomad_flotilla", "marines_target_line"), pad, NomadUi.hl(),
          have, want);
    }
  }

  private static NomadTargetRow.Target marinesTarget(final NomadFlotillaView view,
      final float target) {
    return new NomadTargetRow.Target() {
      @Override
      public String id() {
        return Works.MARINES;
      }

      @Override
      public String name() {
        return NomadStrings.get("nomad_flotilla", "marines_target_name");
      }

      @Override
      public float now() {
        return target;
      }

      @Override
      public float min() {
        return 0f;
      }

      @Override
      public float after(String arg) {
        return NomadWorkActions.targetAfter(view, Works.MARINES, arg);
      }

      @Override
      public boolean live() {
        return view.live;
      }

      @Override
      public String notLive() {
        return view.notLiveReason();
      }

      @Override
      public void atMinLine(TooltipMakerAPI t) {
        t.addPara(NomadStrings.get("nomad_flotilla", "marines_target_at_zero"), NomadUi.warn(),
            6f);
      }

      @Override
      public void cannotRaiseLine(TooltipMakerAPI t) {
      }

      @Override
      public void moveLines(TooltipMakerAPI t, float after, boolean down, String arg) {
      }

      @Override
      public String fieldTip() {
        return NomadStrings.get("nomad_flotilla", "marines_target_field_tip");
      }
    };
  }

  private static void closeRow(CustomPanelAPI panel, float w, float y, String workId) {
    float inner = w - 2f * PAD_X;
    TooltipMakerAPI el = NomadCell.element(panel, inner, NomadWorkCardModel.BUTTON_H);
    el.addButton(NomadStrings.get("nomad_flotilla", "cards_close"),
        NomadWorkActions.id(Verb.SELECT, workId),
        NomadUi.gray(), NomadUi.dark(), Alignment.MID, CutStyle.C2_MENU, inner,
        NomadWorkCardModel.BUTTON_H, 0f);
    NomadCell.place(panel, el, PAD_X, y);
  }

  static Verb verbOf(Row row) {
    switch (row) {
      case UPGRADE:
        return Verb.UPGRADE;
      case CANCEL:
        return Verb.CANCEL;
      case DOWNGRADE:
        return Verb.DOWNGRADE;
      case MOTHBALL:
        return Verb.MOTHBALL;
      case REVIVE:
        return Verb.REVIVE;
      case REMOTE:
        return Verb.REMOTE;
      case SCRAP:
        return Verb.SCRAP;
      case PRIORITY:
        return Verb.PRIORITY;
      case CLOSE:
      default:
        return Verb.SELECT;
    }
  }

  private static WorkOrders.Quote quoteOf(NomadFlotillaView view, String workId, Row row,
      boolean remoteOn) {
    switch (row) {
      case UPGRADE:
        return view.quoteUpgrade(workId);
      case CANCEL:
        return view.quoteCancel(workId);
      case DOWNGRADE:
        return view.quoteDowngrade(workId);
      case MOTHBALL:
        return view.quoteMothball(workId, true);
      case REVIVE:
        return view.quoteMothball(workId, false);
      case REMOTE:
        return view.quoteRemote(remoteOn);
      case SCRAP:
      default:
        return view.quoteScrap(workId);
    }
  }

  private static String price(WorkOrders.Quote q) {
    if (q.credits > 0.5f) {
      return NomadUi.credits(q.credits);
    }
    if (q.credits < -0.5f) {
      return "+" + NomadUi.credits(-q.credits);
    }
    return NomadStrings.get("nomad_flotilla", "cards_free");
  }

  private static Color priceColor(WorkOrders.Quote q) {
    if (q.credits > 0.5f) {
      return NomadUi.hl();
    }
    if (q.credits < -0.5f) {
      return NomadUi.good();
    }
    return NomadUi.gray();
  }

  private static String remoteState(WorkState state) {
    if (state == null) {
      return "";
    }
    if (state.remoteSwitching) {
      return NomadStrings.get("nomad_flotilla", "cards_remote_switching");
    }
    return state.remote ? NomadStrings.get("nomad_flotilla", "cards_remote_on")
        : NomadStrings.get("nomad_flotilla", "cards_remote_off");
  }

  private static TooltipMakerAPI.TooltipCreator orderTip(NomadFlotillaView view, String workId,
      WorkState state, Row row, WorkOrders.Quote q) {
    if (row == Row.UPGRADE) {
      return NomadSizeTip.tip(view, workId, true, q);
    }
    if (row == Row.DOWNGRADE) {
      return withDisables(NomadSizeTip.tip(view, workId, false, q),
          NomadWorkActions.lowerWarning(view, workId, state == null ? 0 : state.size - 1));
    }
    final List<String> lines = new ArrayList<String>();
    final List<Color> colors = new ArrayList<Color>();

    if (!view.live) {
      lines.add(view.notLiveReason());
      colors.add(NomadUi.bad());
    } else if (!q.allowed()) {
      lines.add(NomadFlotillaView.refusalText(q));
      colors.add(NomadUi.bad());
    }

    lines.add(row == Row.REMOTE && Mining.remoteStarting(state)
        ? NomadStrings.get("nomad_flotilla", "cards_tip_remote_stop") : what(view, row));
    colors.add(NomadUi.text());
    if (row == Row.SCRAP) {
      String disables = NomadWorkActions.lowerWarning(view, workId, 0);
      if (disables != null) {
        lines.add(disables);
        colors.add(NomadUi.warn());
      }
    }

    if (q.credits > 0.5f) {
      lines.add(NomadStrings.get("nomad_flotilla", "cards_tip_cost", NomadUi.credits(q.credits)));
      colors.add(NomadUi.hl());
    } else if (q.credits < -0.5f) {
      lines.add(NomadStrings.get("nomad_flotilla", "cards_tip_refund",
          NomadUi.credits(-q.credits)));
      colors.add(NomadUi.good());
    } else {
      lines.add(NomadStrings.get("nomad_flotilla", "cards_tip_free"));
      colors.add(NomadUi.gray());
    }
    if (q.days > 0.5f) {
      lines.add(NomadStrings.get("nomad_flotilla", "cards_tip_days", Math.round(q.days)));
      colors.add(NomadUi.text());
    }
    if (row != Row.REMOTE) {
      NeedsLevel now = NomadHomeColumn.level(view.sheet.needs);
      NeedsLevel then = NomadHomeColumn.level(q.after);
      lines.add(levelLine(now, then));
      colors.add(NomadUi.text());
      if (NomadNeedsTag.stopsEveryWork(now, then)) {
        lines.add(NomadStrings.get("nomad_flotilla", "needs_effect_none_run"));
        colors.add(NomadUi.bad());
      }
      if (q.residentsWouldLeave) {
        lines.add(NomadStrings.get("nomad_flotilla", "cards_tip_leave"));
        colors.add(NomadUi.bad());
      }
      String needs = needsLine(q);
      if (needs != null) {
        lines.add(needs);
        colors.add(NomadUi.text());
      }
    }

    final boolean remote = row == Row.REMOTE;
    final NomadFlotillaView v = view;
    final WorkState rig = state;
    return NomadTip.guard(new NomadTip(TIP_W) {
      @Override
      public void createTooltip(TooltipMakerAPI t, boolean expanded, Object param) {
        for (int i = 0; i < lines.size(); i++) {
          t.addPara(lines.get(i), colors.get(i), i == 0 ? 0f : 6f);
        }
        if (remote) {
          NomadMining.addReadout(t, v.mining(), rig, v.rules, 10f);
        }
      }
    });
  }

  static String needsLine(WorkOrders.Quote q) {
    NomadHomeFigures.OrderNeeds n = NomadHomeFigures.orderNeeds(q.needsBefore, q.needsAfter,
        q.before, q.after);
    if (!n.shows) {
      return null;
    }
    return NomadStrings.get("nomad_flotilla", n.less ? "order_needs_less" : "order_needs_more",
        n.percent, n.space, n.residents);
  }

  static String levelLine(NeedsLevel now, NeedsLevel then) {
    String name = NomadFlotillaView.levelName(then);
    switch (NomadNeedsTag.Direction.of(now, then)) {
      case UP:
        return NomadStrings.get("nomad_flotilla", "needs_after_up", name);
      case DOWN:
        return NomadStrings.get("nomad_flotilla", "needs_after_down", name);
      default:
        return NomadStrings.get("nomad_flotilla", "needs_after_same", name);
    }
  }

  private static TooltipMakerAPI.TooltipCreator withDisables(
      final TooltipMakerAPI.TooltipCreator inner, final String disables) {
    if (disables == null) {
      return inner;
    }
    return NomadTip.guard(new TooltipMakerAPI.TooltipCreator() {
      @Override
      public boolean isTooltipExpandable(Object param) {
        return inner.isTooltipExpandable(param);
      }

      @Override
      public float getTooltipWidth(Object param) {
        return inner.getTooltipWidth(param);
      }

      @Override
      public void createTooltip(TooltipMakerAPI t, boolean expanded, Object param) {
        inner.createTooltip(t, expanded, param);
        t.addPara(disables, NomadUi.warn(), 6f);
      }
    });
  }

  private static String what(NomadFlotillaView view, Row row) {
    switch (row) {
      case CANCEL:
        return NomadStrings.get("nomad_flotilla", "cards_tip_cancel");
      case MOTHBALL:
        return NomadStrings.get("nomad_flotilla", "cards_tip_mothball");
      case REVIVE:
        return NomadStrings.get("nomad_flotilla", "cards_tip_revive");
      case REMOTE:
        return NomadStrings.get("nomad_flotilla", "cards_tip_remote",
            Math.round(view.rules.remoteOnDays));
      case SCRAP:
      default:
        return NomadStrings.get("nomad_flotilla", "cards_tip_scrap");
    }
  }

  private static final class Plate extends BaseCustomUIPanelPlugin {

    private final float w;
    private final float h;
    private PositionAPI pos;

    Plate(float w, float h) {
      this.w = w;
      this.h = h;
    }

    @Override
    public void positionChanged(PositionAPI position) {
      this.pos = position;
    }

    @Override
    public void render(float alphaMult) {
      if (pos != null) {
        NomadGl.opaqueBox(pos.getX(), pos.getY(), w, h, GROUND, NomadUi.bright());
      }
    }

    @Override
    public void processInput(List<InputEventAPI> events) {
      if (pos == null || events == null) {
        return;
      }
      for (InputEventAPI e : events) {
        if (!e.isConsumed() && e.isMouseDownEvent() && pos.containsEvent(e)) {
          e.consume();
        }
      }
    }
  }
}
