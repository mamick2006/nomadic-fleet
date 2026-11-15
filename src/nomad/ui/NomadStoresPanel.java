package nomad.ui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.lwjgl.opengl.GL11;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import nomad.NomadStrings;
import nomad.campaign.NomadSettings;
import nomad.campaign.econ.Commod;
import nomad.campaign.econ.model.BasicNeeds;
import nomad.campaign.econ.model.StoreFlags;
import nomad.campaign.econ.model.Stores;
import nomad.campaign.econ.model.TickSheet;
import nomad.campaign.econ.model.Works;

public final class NomadStoresPanel {

  private NomadStoresPanel() {
  }

  private static final float PAD = 3f;
  private static final float OPAD = 10f;
  private static final float ROW_TIP_W = 360f;
  private static final Color CLEAR = new Color(0, 0, 0, 0);

  static final int RULE_ALPHA = 110;
  static final int TRACK_ALPHA = 90;
  static final int OPEN_ALPHA = 60;
  static final int HOT_ALPHA = 40;

  private static final NomadTargetRow TARGET = new NomadTargetRow();
  private static final NomadPanelFigures.OpenOrders ORDERS = new NomadPanelFigures.OpenOrders();

  static String typedTarget() {
    return TARGET.typed();
  }

  public static void releaseTargetField() {
    TARGET.release();
    NomadWorkPanel.releaseMarinesField();
  }

  static void forgetTypedTarget() {
    TARGET.forget();
    ORDERS.close();
    NomadWorkPanel.forgetMarinesField();
  }

  static void pressStore(String good) {
    ORDERS.pressStore(good);
    TARGET.reopen();
  }

  static void pressCrew() {
    ORDERS.pressCrew();
    TARGET.reopen();
  }

  static boolean crewOrdersOpen() {
    return ORDERS.crew();
  }

  static final class Paint extends BaseCustomUIPanelPlugin {

    private static final float RECT = 0f;
    private static final float TICK = 1f;

    private final List<float[]> under = new ArrayList<float[]>();
    private final List<float[]> over = new ArrayList<float[]>();
    private final List<float[]> hot = new ArrayList<float[]>();
    private float hotTop = Float.MAX_VALUE;
    private float hotBottom = -Float.MAX_VALUE;
    private final Color glow = NomadUi.base();
    private PositionAPI pos;

    void rect(float x, float y, float w, float h, Color c, int alpha) {
      under.add(op(RECT, x, y, w, h, c, alpha));
    }

    void hot(float x, float y, float w, float h) {
      hot.add(new float[] { x, y, w, h });
      hotTop = Math.min(hotTop, y);
      hotBottom = Math.max(hotBottom, y + h);
    }

    void rectOver(float x, float y, float w, float h, Color c, int alpha) {
      over.add(op(RECT, x, y, w, h, c, alpha));
    }

    void frame(float x, float y, float w, float h, Color c, int alpha) {
      frame(under, x, y, w, h, c, alpha);
    }

    void frameOver(float x, float y, float w, float h, Color c, int alpha) {
      frame(over, x, y, w, h, c, alpha);
    }

    void tick(float x, float y, float px, Color c, int alpha) {
      under.add(op(TICK, x, y, px, px, c, alpha));
    }

    void tickOver(float x, float y, float px, Color c, int alpha) {
      over.add(op(TICK, x, y, px, px, c, alpha));
    }

    private static void frame(List<float[]> to, float x, float y, float w, float h, Color c,
        int alpha) {
      to.add(op(RECT, x, y, w, 1f, c, alpha));
      to.add(op(RECT, x, y + h - 1f, w, 1f, c, alpha));
      to.add(op(RECT, x, y + 1f, 1f, h - 2f, c, alpha));
      to.add(op(RECT, x + w - 1f, y + 1f, 1f, h - 2f, c, alpha));
    }

    private static float[] op(float kind, float x, float y, float w, float h, Color c,
        int alpha) {
      return new float[] { kind, x, y, w, h, c.getRed(), c.getGreen(), c.getBlue(), alpha };
    }

    @Override
    public void positionChanged(PositionAPI position) {
      this.pos = position;
    }

    @Override
    public void buttonPressed(Object buttonId) {
      NomadConsolePanel.routeButton(buttonId);
    }

    @Override
    public void renderBelow(float alphaMult) {
      draw(under, alphaMult);
      lightHot(alphaMult);
    }

    private void lightHot(float mult) {
      if (pos == null || hot.isEmpty() || NomadConsolePanel.confirmBoxOfLive() != null) {
        return;
      }
      float top = pos.getY() + pos.getHeight();
      float my = top - Global.getSettings().getMouseY();
      if (my < hotTop || my >= hotBottom) {
        return;
      }
      float mx = Global.getSettings().getMouseX() - pos.getX();
      for (int i = 0; i < hot.size(); i++) {
        float[] r = hot.get(i);
        if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
          NomadGl.fillRect(pos.getX() + r[0], top - r[1] - r[3], r[2], r[3], glow, HOT_ALPHA,
              mult);
          return;
        }
      }
    }

    @Override
    public void render(float alphaMult) {
      draw(over, alphaMult);
    }

    private void draw(List<float[]> ops, float mult) {
      if (pos == null || ops.isEmpty()) {
        return;
      }
      float left = pos.getX();
      float top = pos.getY() + pos.getHeight();
      GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_LINE_BIT);
      GL11.glDisable(GL11.GL_TEXTURE_2D);
      GL11.glEnable(GL11.GL_BLEND);
      GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
      GL11.glEnable(GL11.GL_LINE_SMOOTH);
      GL11.glLineWidth(1.8f);
      for (int i = 0; i < ops.size(); i++) {
        float[] o = ops.get(i);
        GL11.glColor4ub((byte) o[5], (byte) o[6], (byte) o[7], (byte) (o[8] * mult));
        float x = left + o[1];
        float y = top - o[2];
        if (o[0] == RECT) {
          GL11.glBegin(GL11.GL_QUADS);
          GL11.glVertex2f(x, y - o[4]);
          GL11.glVertex2f(x, y);
          GL11.glVertex2f(x + o[3], y);
          GL11.glVertex2f(x + o[3], y - o[4]);
          GL11.glEnd();
        } else {
          float px = o[3];
          GL11.glBegin(GL11.GL_LINE_STRIP);
          GL11.glVertex2f(x + 0.15f * px, y - 0.52f * px);
          GL11.glVertex2f(x + 0.40f * px, y - 0.78f * px);
          GL11.glVertex2f(x + 0.86f * px, y - 0.22f * px);
          GL11.glEnd();
        }
      }
      GL11.glPopAttrib();
    }
  }

  static Color openEdge() {
    return mix(NomadUi.dark(), NomadUi.base(), 0.2f);
  }

  private static Color meterFill() {
    return mix(NomadUi.base(), NomadUi.dark(), 0.35f);
  }

  private static Color mix(Color a, Color b, float t) {
    return new Color(Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
        Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
        Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
  }

  public static float build(CustomPanelAPI panel, float w, float maxH, NomadFlotillaView view) {
    TARGET.release();
    List<TickSheet.Store> stores = NomadPanelFigures.listed(view.standing);
    boolean crewOpen = NomadCrewPanel.ordersOpen(view);
    String openGood = crewOpen ? null : ORDERS.store();
    int open = -1;
    for (int i = 0; i < stores.size(); i++) {
      if (stores.get(i).good.equals(openGood)) {
        open = i;
      }
    }
    if (open < 0) {
      TARGET.reopen();
    }

    String[] words = NomadTargetRow.stepWords();
    float[] wordW = new float[words.length];
    for (int i = 0; i < words.length; i++) {
      wordW[i] = NomadUi.BUTTON.width(words[i]);
    }
    float rowH = NomadPanelFigures.Ledger.rowHeight(w, maxH, stores.size(), crewOpen);
    NomadPanelFigures.Ledger at = NomadPanelFigures.Ledger.of(w, rowH, stores.size(), open,
        wordW);

    Paint paint = new Paint();
    CustomPanelAPI own = panel.createCustomPanel(w, at.height, paint);
    panel.addComponent(own).inTL(0f, 0f);

    heading(own, at, view);
    fillButton(own, at, view);
    captions(own, at);
    Map<String, NomadPanelFigures.Stopped> stopped = NomadPanelFigures.stopped(view.sheet);
    for (int i = 0; i < stores.size(); i++) {
      row(own, paint, at, i, stores.get(i), stopped.get(stores.get(i).good), view);
    }
    if (open >= 0) {
      orders(own, paint, at, stores.get(open), words, view);
    }
    return at.height;
  }

  private static LabelAPI label(CustomPanelAPI own, NomadGrid.Box box, float y, String text,
      Color c, Alignment align) {
    return NomadCell.box(own, box.x, y + box.y, box.w, box.h, text, c, align, 0f);
  }

  private static void heading(CustomPanelAPI own, NomadPanelFigures.Ledger at,
      NomadFlotillaView view) {
    TooltipMakerAPI head = own.createUIElement(at.head.w, at.head.h, false);
    head.addSectionHeading(NomadStrings.get("nomad_flotilla", "panels_stores_space",
        NomadPanelFigures.heads(view.standing.spaceStores)), NomadUi.base(), NomadUi.dark(),
        Alignment.MID, at.head.w - 2f * NomadText.INSET, 0f);
    own.addUIElement(head).inTL(at.head.x, at.head.y);
  }

  private static void fillButton(CustomPanelAPI own, NomadPanelFigures.Ledger at,
      final NomadFlotillaView view) {
    final Map<String, Float> take = Stores.fill(view.state, view.catalog, view.rules, view.shared);
    boolean any = false;
    for (Float v : take.values()) {
      if (v != null && v.floatValue() >= 0.5f) {
        any = true;
      }
    }
    final boolean enabled = view.live && any;
    TooltipMakerAPI cell = NomadCell.element(own, at.fill.w, at.fill.h);
    ButtonAPI b = cell.addButton(NomadWorkActions.label(NomadWorkActions.Verb.FILL),
        NomadWorkActions.id(NomadWorkActions.Verb.FILL), NomadUi.base(), NomadUi.dark(),
        Alignment.MID, CutStyle.C2_MENU, at.fill.w, at.fill.h, 0f);
    NomadUi.setEnabledSayingWhy(b, enabled);
    cell.addTooltipToPrevious(NomadTip.guard(new NomadTip() {
      @Override
      public void createTooltip(TooltipMakerAPI t, boolean expanded, Object param) {
        t.addPara(NomadStrings.get("nomad_flotilla", "store_fill_tip"), 0f);
        reserveLine(t, Commod.SUPPLIES);
        reserveLine(t, Commod.FUEL);
        if (!view.live) {
          t.addPara(view.notLiveReason(), NomadUi.warn(), PAD * 2f);
          return;
        }
        if (!enabled) {
          t.addPara(NomadStrings.get("nomad_flotilla", "store_fill_none"), NomadUi.warn(),
              PAD * 2f);
          return;
        }
        t.addPara(NomadStrings.get("nomad_flotilla", "panels_fill_tip_takes"), NomadUi.gray(),
            PAD * 2f);
        for (Map.Entry<String, Float> e : take.entrySet()) {
          if (e.getValue() != null && e.getValue().floatValue() >= 0.5f) {
            t.addPara(NomadStrings.get("nomad_flotilla", "panels_fill_tip_line"), PAD,
                NomadUi.hl(), NomadUi.i(e.getValue().floatValue()),
                NomadUi.commodityName(e.getKey()));
          }
        }
      }
    }), TooltipMakerAPI.TooltipLocation.BELOW, false);
    NomadCell.place(own, cell, at.fill.x, at.fill.y);
  }

  private static void reserveLine(TooltipMakerAPI t, String good) {
    if (Commod.SUPPLIES.equals(good)) {
      t.addPara(NomadStrings.get("nomad_flotilla", "store_reserve_supplies"), PAD * 2f,
          NomadUi.hl(),
          NomadUi.i(NomadSettings.getFloat("nomad_autotransfer_reserve_days_supplies", 30f)));
    } else if (Commod.FUEL.equals(good)) {
      t.addPara(NomadStrings.get("nomad_flotilla", "store_reserve_fuel"), PAD * 2f, NomadUi.hl(),
          NomadUi.i(NomadSettings.getFloat("nomad_autotransfer_reserve_ly_fuel", 10f)));
    }
  }

  private static void captions(CustomPanelAPI own, NomadPanelFigures.Ledger at) {
    label(own, at.capStock, 0f, NomadStrings.get("nomad_flotilla", "panels_col_stock"),
        NomadUi.gray(), Alignment.RMID);
    label(own, at.capNet, 0f, NomadStrings.get("nomad_flotilla", "ui2_stores_col_net"),
        NomadUi.gray(), Alignment.RMID);
    for (int i = 0; i < at.capFlag.length; i++) {
      int bit = NomadPanelFigures.FLAG_COLUMNS[i];
      label(own, at.capFlag[i], 0f, at.wide ? flagName(bit) : flagLetter(bit), NomadUi.gray(),
          Alignment.MID);
    }
  }

  private static String flagLetter(int bit) {
    if (bit == StoreFlags.IN) {
      return NomadStrings.get("nomad_flotilla", "store_flag_in_letter");
    }
    if (bit == StoreFlags.OUT) {
      return NomadStrings.get("nomad_flotilla", "store_flag_out_letter");
    }
    if (bit == StoreFlags.SELL) {
      return NomadStrings.get("nomad_flotilla", "store_flag_sell_letter");
    }
    return NomadStrings.get("nomad_flotilla", "store_flag_sell_cargo_letter");
  }

  private static void row(CustomPanelAPI own, Paint paint, NomadPanelFigures.Ledger at, int i,
      final TickSheet.Store store, final NomadPanelFigures.Stopped stopped,
      final NomadFlotillaView view) {
    final String good = store.good;
    float y = at.rowY(i);
    final float stock = view.sheet.stock(good);
    final float target = NomadPanelFigures.keptTarget(view.state.target(good), store.minTarget);
    final boolean atMin = NomadPanelFigures.atMinimum(target, store.minTarget);
    TickSheet.Store doing = view.sheet.stores.get(good);
    final float net = doing == null ? 0f : doing.netPerMonth;
    final boolean open = i == at.open;
    boolean outOf = stopped != null;

    if (open) {
      float boxH = at.rowH + NomadPanelFigures.Ledger.ORDERS_H;
      paint.rect(0f, y, at.w, boxH, NomadUi.dark(), OPEN_ALPHA);
      paint.frame(0f, y, at.w, boxH, openEdge(), 255);
    } else {
      paint.rect(0f, y + at.rowH - 1f, at.w, 1f, NomadUi.dark(), RULE_ALPHA);
    }
    float fill = NomadPanelFigures.meterFill(stock, target);
    paint.rect(at.meter.x, y + at.meter.y, at.meter.w, at.meter.h, NomadUi.dark(), TRACK_ALPHA);
    if (fill > 0f) {
      paint.rect(at.meter.x, y + at.meter.y, Math.max(1f, Math.round(at.meter.w * fill)),
          at.meter.h, fill >= 1f ? NomadUi.base() : meterFill(), 255);
    }

    TooltipMakerAPI icon = NomadCell.element(own, at.icon.w, at.icon.h);
    NomadUi.iconInline(icon, good, at.icon.w, 0f);
    NomadCell.place(own, icon, at.icon.x, y + at.icon.y);

    boolean unused = NomadPanelFigures.unused(view.sheet, good, net);
    label(own, at.name, y, NomadText.fit(NomadUi.commodityName(good), at.name.w, NomadUi.PARA),
        unused ? NomadUi.gray() : NomadUi.text(), Alignment.LMID);
    if (stopped != null) {
      NomadMark.add(own, at.mark.x, y + at.mark.y, NomadPanelFigures.Ledger.MARK_PX,
          NomadUi.warn());
    }

    boolean bad = NomadPanelFigures.stockBad(outOf, stock, store.minTarget, net);
    TooltipMakerAPI amounts = NomadCell.element(own, at.stock.w, at.stock.h);
    LabelAPI figures = amounts.addPara(NomadStrings.get("nomad_flotilla", "panels_store_amounts"),
        0f, new Color[] { bad ? NomadUi.bad() : NomadUi.hl(),
            atMin ? NomadUi.gray() : NomadUi.hl() },
        NomadPanelFigures.heads(stock), NomadPanelFigures.heads(target));
    figures.setAlignment(Alignment.RMID);
    NomadCell.place(own, amounts, at.stock.x, y + at.stock.y);

    label(own, at.net, y, NomadPanelFigures.flowText(net),
        net > 0.05f ? NomadUi.good() : net < -0.05f ? NomadUi.bad() : NomadUi.gray(),
        Alignment.RMID);

    boolean[] on = NomadPanelFigures.rowFlags(view.state.flags(good));
    Color c = view.live && view.state.flagsAct() ? NomadUi.base() : NomadUi.gray();
    float px = NomadPanelFigures.Ledger.TICK_PX;
    float by = y + (float) Math.floor((at.rowH - px) / 2f);
    for (int k = 0; k < at.flag.length; k++) {
      float bx = at.flag[k].x + (float) Math.floor((at.flag[k].w - px) / 2f);
      if (on[k]) {
        paint.rectOver(bx, by, px, px, c, 255);
        paint.tickOver(bx, by, px, Color.BLACK, 255);
      } else {
        paint.rectOver(bx, by, px, px, Color.BLACK, 200);
        paint.frameOver(bx, by, px, px, c, 200);
      }
    }

    paint.hot(0f, y, at.w, at.rowH);
    TooltipMakerAPI hit = NomadCell.element(own, at.w, at.rowH);
    hit.addButton("", NomadWorkActions.id(NomadWorkActions.Verb.STORE_OPEN, good), CLEAR, CLEAR,
        Alignment.MID, (CutStyle) null, at.w, at.rowH, 0f);
    hit.addTooltipToPrevious(NomadTip.guard(new NomadTip(ROW_TIP_W) {
      @Override
      public void createTooltip(TooltipMakerAPI t, boolean expanded, Object param) {
        rowTip(t, view, store, stopped, stock, target, atMin, net, open);
      }
    }), TooltipMakerAPI.TooltipLocation.RIGHT, false);
    NomadCell.place(own, hit, 0f, y);

    int flags = view.state.flags(good);
    boolean brokerage = view.work(Works.BROKERAGE) != null;
    for (int k = 0; k < at.flagHit.length; k++) {
      rowFlag(own, at.flagHit[k], y, good, NomadPanelFigures.FLAG_COLUMNS[k], on[k], brokerage,
          view);
    }
  }

  private static void rowFlag(CustomPanelAPI own, NomadGrid.Box at, float y, final String good,
      final int bit, final boolean on, final boolean brokerage, final NomadFlotillaView view) {
    TooltipMakerAPI cell = NomadCell.element(own, at.w, at.h);
    ButtonAPI b = cell.addButton("",
        NomadWorkActions.id(NomadWorkActions.Verb.FLAG, good, flagArg(bit)), CLEAR, CLEAR,
        Alignment.MID, (CutStyle) null, at.w, at.h, 0f);
    NomadUi.setEnabledSayingWhy(b, view.live);
    cell.addTooltipToPrevious(NomadTip.guard(new NomadTip() {
      @Override
      public void createTooltip(TooltipMakerAPI t, boolean expanded, Object param) {
        flagTipLines(t, bit, good, brokerage, view);
        if (view.live) {
          t.addPara(NomadStrings.get("nomad_flotilla",
              on ? "store_flag_row_on" : "store_flag_row_off"), NomadUi.good(), OPAD);
        }
      }
    }), TooltipMakerAPI.TooltipLocation.BELOW, false);
    NomadCell.place(own, cell, at.x, y + at.y);
  }

  private static void flagTipLines(TooltipMakerAPI t, int bit, String good, boolean brokerage,
      NomadFlotillaView view) {
    flagTip(t, bit, NomadUi.commodityName(good));
    if (NomadPanelFigures.showsReserve(bit, good)) {
      reserveLine(t, good);
    }
    boolean sale = bit == StoreFlags.SELL || bit == StoreFlags.SELL_CARGO;
    if (sale && !brokerage) {
      t.addPara(NomadStrings.get("nomad_flotilla", "store_flag_no_brokerage"),
          NomadUi.warn(), PAD * 2f);
    }
    if (!view.state.flagsAct()) {
      t.addPara(NomadStrings.get("nomad_flotilla", "panels_store_tip_flags_idle"),
          NomadUi.warn(), PAD * 2f);
    }
    if (!view.live) {
      t.addPara(view.notLiveReason(), NomadUi.warn(), PAD * 2f);
    }
  }

  private static void orders(CustomPanelAPI own, Paint paint, NomadPanelFigures.Ledger at,
      TickSheet.Store store, String[] words, NomadFlotillaView view) {
    float y = at.ordersY();
    String good = store.good;

    label(own, at.targetLabel, y, NomadStrings.get("nomad_flotilla", "ui3_store_target"),
        NomadUi.gray(), Alignment.LMID);
    NomadTargetRow.Target target = storeTarget(store, view);
    for (int i = 0; i < words.length; i++) {
      TARGET.step(own, at.step[i], y, target, NomadTargetRow.stepArg(i), words[i]);
    }
    TARGET.field(own, at.field, y, target);
    TARGET.set(own, at.set, y, target);

    label(own, at.flagsLabel, y, NomadStrings.get("nomad_flotilla", "ui3_store_flags"),
        NomadUi.gray(), Alignment.LMID);
    int flags = view.state.flags(good);
    boolean brokerage = view.work(Works.BROKERAGE) != null;
    for (int i = 0; i < at.check.length; i++) {
      flag(own, paint, at.check[i], y, good, NomadPanelFigures.FLAG_COLUMNS[i], flags, brokerage,
          view);
    }
  }

  private static NomadTargetRow.Target storeTarget(final TickSheet.Store store,
      final NomadFlotillaView view) {
    final String good = store.good;
    return new NomadTargetRow.Target() {
      @Override
      public String id() {
        return good;
      }

      @Override
      public String name() {
        return NomadUi.commodityName(good);
      }

      @Override
      public float now() {
        return NomadPanelFigures.keptTarget(view.state.target(good), store.minTarget);
      }

      @Override
      public float min() {
        return store.minTarget;
      }

      @Override
      public float after(String arg) {
        return NomadWorkActions.targetAfter(view, good, arg);
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
        t.addPara(NomadStrings.get("nomad_flotilla", "store_target_at_min"), PAD * 2f,
            NomadUi.warn(), NomadUi.hl(), NomadUi.i(store.minTarget));
      }

      @Override
      public void cannotRaiseLine(TooltipMakerAPI t) {
        t.addPara(NomadStrings.get("nomad_flotilla", "screen_refusal_no_space"),
            NomadUi.warn(), PAD * 2f);
      }

      @Override
      public void moveLines(TooltipMakerAPI t, float after, boolean down, String arg) {
        if (down && NomadPanelFigures.atMinimum(after, store.minTarget)) {
          t.addPara(NomadStrings.get("nomad_flotilla", "store_target_at_min"), PAD * 2f,
              NomadUi.gray(), NomadUi.hl(), NomadUi.i(store.minTarget));
        }
        BasicNeeds.Reading r = NomadWorkActions.needsAfterTarget(view, good, arg);
        NomadCrewPanel.levelAfter(t, view, r);
      }

      @Override
      public String fieldTip() {
        return NomadStrings.get("nomad_flotilla", "uifix_target_field_tip");
      }
    };
  }

  private static void flag(CustomPanelAPI own, Paint paint, NomadGrid.Box at, float y,
      final String good, final int bit, int flags, final boolean brokerage,
      final NomadFlotillaView view) {
    boolean on = NomadPanelFigures.flagOn(flags, bit);
    TooltipMakerAPI cell = NomadCell.element(own, at.w, at.h);
    ButtonAPI box = cell.addAreaCheckbox(flagName(bit),
        NomadWorkActions.id(NomadWorkActions.Verb.FLAG, good, flagArg(bit)), NomadUi.base(),
        NomadUi.dark(), NomadUi.bright(), at.w, at.h, 0f);
    box.setChecked(on);
    NomadUi.setEnabledSayingWhy(box, view.live);
    cell.addTooltipToPrevious(NomadTip.guard(new NomadTip() {
      @Override
      public void createTooltip(TooltipMakerAPI t, boolean expanded, Object param) {
        flagTipLines(t, bit, good, brokerage, view);
      }
    }), TooltipMakerAPI.TooltipLocation.BELOW, false);
    NomadCell.place(own, cell, at.x, y + at.y);

    float px = NomadPanelFigures.Ledger.TICK_PX;
    float bx = at.x + NomadPanelFigures.Ledger.TICK_X;
    float by = y + at.y + (float) Math.floor((at.h - px) / 2f);
    Color c = view.live ? NomadUi.base() : NomadUi.gray();
    if (on) {
      paint.rectOver(bx, by, px, px, c, 255);
      paint.tickOver(bx, by, px, Color.BLACK, 255);
    } else {
      paint.rectOver(bx, by, px, px, Color.BLACK, 200);
      paint.frameOver(bx, by, px, px, c, 200);
    }
  }

  static String flagArg(int bit) {
    if (bit == StoreFlags.IN) {
      return NomadWorkActions.FLAG_IN;
    }
    if (bit == StoreFlags.OUT) {
      return NomadWorkActions.FLAG_OUT;
    }
    if (bit == StoreFlags.SELL) {
      return NomadWorkActions.FLAG_SELL;
    }
    return NomadWorkActions.FLAG_SELL_CARGO;
  }

  private static String flagName(int bit) {
    if (bit == StoreFlags.IN) {
      return NomadStrings.get("nomad_flotilla", "store_flag_in");
    }
    if (bit == StoreFlags.OUT) {
      return NomadStrings.get("nomad_flotilla", "store_flag_out");
    }
    if (bit == StoreFlags.SELL) {
      return NomadStrings.get("nomad_flotilla", "store_flag_sell");
    }
    return NomadStrings.get("nomad_flotilla", "store_flag_sell_cargo");
  }

  private static void flagTip(TooltipMakerAPI t, int bit, String goodName) {
    if (bit == StoreFlags.IN) {
      t.addPara(NomadStrings.get("nomad_flotilla", "store_flag_in_tip"), 0f, NomadUi.hl(),
          goodName);
    } else if (bit == StoreFlags.OUT) {
      t.addPara(NomadStrings.get("nomad_flotilla", "store_flag_out_tip"), 0f, NomadUi.hl(),
          goodName);
    } else if (bit == StoreFlags.SELL) {
      t.addPara(NomadStrings.get("nomad_flotilla", "store_flag_sell_tip"), 0f, NomadUi.hl(),
          goodName);
    } else {
      t.addPara(NomadStrings.get("nomad_flotilla", "store_flag_sell_cargo_tip"), 0f,
          NomadUi.hl(), goodName);
    }
  }

  private static void rowTip(TooltipMakerAPI t, NomadFlotillaView view, TickSheet.Store store,
      NomadPanelFigures.Stopped stopped, float stock, float target, boolean atMin, float net,
      boolean open) {
    String good = store.good;
    t.addTitle(NomadUi.commodityName(good), NomadUi.base());
    t.addPara(NomadStrings.get("nomad_flotilla", "store_row_tip"), OPAD,
        new Color[] { NomadUi.hl(), NomadUi.hl(), NomadUi.colorFor(net) },
        NomadPanelFigures.heads(stock), NomadPanelFigures.heads(target),
        NomadPanelFigures.flowText(net));
    if (stopped != null) {
      for (String workId : stopped.outOf) {
        stoppedLine(t, view, workId);
      }
    }

    List<NomadPanelFigures.NetLine> lines = NomadPanelFigures.netLines(view.sheet, good);
    if (lines.isEmpty()) {
      t.addPara(NomadStrings.get("nomad_flotilla", "panels_store_tip_flow"), PAD,
          new Color[] { NomadUi.good(), NomadUi.bad() }, NomadWorkHoverRows.amount(0f),
          NomadWorkHoverRows.amount(0f));
    } else {
      t.beginGridFlipped(ROW_TIP_W - 2f * NomadText.INSET, 1, NomadUi.text(), 50f, OPAD);
      int at = 0;
      for (NomadPanelFigures.NetLine line : lines) {
        String who = line.workId == null ? NomadStrings.get("nomad_flotilla", "ui3_tip_ships")
            : view.workName(line.workId);
        t.addToGrid(0, at++, who, NomadPanelFigures.flowText(line.perMonth),
            NomadUi.colorFor(line.perMonth));
      }
      t.addGrid(PAD);
    }

    t.addPara(NomadStrings.get("nomad_flotilla", "store_row_space"), OPAD, NomadUi.hl(),
        NomadPanelFigures.heads(store.reserved));
    if (atMin) {
      t.addPara(NomadStrings.get("nomad_flotilla", "store_target_at_min"), PAD, NomadUi.hl(),
          NomadUi.i(store.minTarget));
    } else {
      t.addPara(NomadStrings.get("nomad_flotilla", "panels_store_tip_min"), PAD, NomadUi.hl(),
          NomadUi.i(store.minTarget));
    }
    if (stock > target + 0.5f) {
      t.addPara(NomadStrings.get("nomad_flotilla", "store_row_over"), NomadUi.gray(), PAD);
    }

    boolean[] on = NomadPanelFigures.rowFlags(view.state.flags(good));
    StringBuilder names = new StringBuilder();
    for (int i = 0; i < on.length; i++) {
      if (on[i]) {
        names.append(names.length() == 0 ? "" : ", ")
            .append(flagName(NomadPanelFigures.FLAG_COLUMNS[i]));
      }
    }
    if (names.length() == 0) {
      t.addPara(NomadStrings.get("nomad_flotilla", "ui2_tip_flags_none"), PAD);
    } else {
      t.addPara(NomadStrings.get("nomad_flotilla", "ui2_tip_flags_on"), PAD, NomadUi.hl(),
          names.toString());
    }
    if (!view.state.flagsAct()) {
      t.addPara(NomadStrings.get("nomad_flotilla", "panels_store_tip_flags_idle"),
          NomadUi.warn(), PAD);
    }
    if (!open) {
      t.addPara(NomadStrings.get("nomad_flotilla", "ui2_store_click"), NomadUi.good(), OPAD);
    }
  }

  private static void stoppedLine(TooltipMakerAPI t, NomadFlotillaView view, String workId) {
    t.addPara(NomadStrings.get("nomad_topbar", "topbar_tip_work", view.workName(workId),
        view.stateText(workId)), NomadUi.warn(), PAD);
  }
}
