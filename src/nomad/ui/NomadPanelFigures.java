package nomad.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import nomad.campaign.econ.Commod;
import nomad.campaign.econ.model.NeedsLevel;
import nomad.campaign.econ.model.Residents;
import nomad.campaign.econ.model.Rules;
import nomad.campaign.econ.model.StoreFlags;
import nomad.campaign.econ.model.TickSheet;
import nomad.campaign.econ.model.WorkSize;
import nomad.campaign.econ.model.WorkState;
import nomad.campaign.econ.model.WorkStatus;

public final class NomadPanelFigures {

  private NomadPanelFigures() {
  }

  public static final int[] TARGET_STEPS = { -100, -10, 10, 100 };

  public static final int[] FLAG_COLUMNS = {
      StoreFlags.IN, StoreFlags.OUT, StoreFlags.SELL, StoreFlags.SELL_CARGO };

  private static final float AT_MIN_SLACK = 0.5f;

  public static final class OpenOrders {

    private String store;
    private boolean crew;

    public void pressStore(String good) {
      crew = false;
      store = good == null || good.isEmpty() || good.equals(store) ? null : good;
    }

    public void pressCrew() {
      store = null;
      crew = !crew;
    }

    public void close() {
      store = null;
      crew = false;
    }

    public String store() {
      return store;
    }

    public boolean crew() {
      return crew;
    }
  }

  public static final float STORE_STEP_PAD = 4f;

  public static final class ResidentParts {

    public final int residents;
    public final int working;
    public final int needs;
    public final float workingShare;
    public final float needsShare;

    ResidentParts(int residents, int working, int needs, float workingShare, float needsShare) {
      this.residents = residents;
      this.working = working;
      this.needs = needs;
      this.workingShare = workingShare;
      this.needsShare = needsShare;
    }
  }

  public static ResidentParts residentParts(float residents, float working, float berths) {
    int all = Math.max(0, Math.round(residents));
    int work = Math.max(0, Math.min(all, Math.round(working)));
    int needs = all - work;
    float w = berths <= 0f ? 0f : clamp01(work / berths);
    float n = berths <= 0f ? 0f : Math.min(1f - w, clamp01(needs / berths));
    return new ResidentParts(all, work, needs, w, n);
  }

  public static int overBerths(float residents, float berths) {
    return Math.max(0, Math.round(residents) - Math.round(berths));
  }

  public static final class Staff {

    public final String workId;
    public final float staff;

    Staff(String workId, float staff) {
      this.workId = workId;
      this.staff = staff;
    }
  }

  public static List<Staff> staffByWork(TickSheet sheet) {
    List<Staff> out = new ArrayList<Staff>();
    if (sheet == null) {
      return out;
    }
    for (WorkState w : inCardOrder(sheet)) {
      TickSheet.WorkRun run = sheet.run(w.id);
      if (run != null && run.staffOwn > 0.05f) {
        out.add(new Staff(w.id, run.staffOwn));
      }
    }
    return out;
  }

  public static final class ResidentsLayout {

    public static final float MARK_PX = 16f;
    public static final float MARK_STEP = 20f;
    public static final float LINE_H = 15f;
    public static final float CAPTION_Y = 2f;
    public static final float MOVE_X = 66f;
    public static final float MOVE_MIN_W = 76f;
    public static final float MOVE_H = 18f;
    public static final float VALUE_W = 180f;
    public static final float BAR_Y = MOVE_H + NomadGrid.Screen.TOOLBAR_GAP;
    public static final float BAR_H = 8f;
    public static final float PARTS_Y = 37f;
    public static final float CLOSED_H = PARTS_Y + LINE_H;
    public static final float ORDERS_Y = 56f;
    public static final float ORDERS_H = 52f;
    static final float[] CREW_ROW_Y = { 4f, 27f };
    static final float CREW_LABEL_X = 6f;
    static final float CREW_BUTTON_X = 118f;
    public static final float CREW_BUTTON_W = 42f;
    static final float CREW_BUTTON_H = 20f;
    static final float CREW_GAP = 3f;
    static final float CREW_NOTE_PAD = 6f;

    public final float w;
    public final float height;
    public final NomadGrid.Box mark;
    public final NomadGrid.Box caption;
    public final NomadGrid.Box move;
    public final NomadGrid.Box value;
    public final NomadGrid.Box bar;
    public final NomadGrid.Box parts;
    public final NomadGrid.Box orders;
    public final NomadGrid.Box[] crewLabel;
    public final NomadGrid.Box[][] crewButton;
    public final NomadGrid.Box[] crewNote;

    private ResidentsLayout(float w, boolean marked, boolean open, float moveWordW) {
      this.w = w;
      float shift = marked ? MARK_STEP : 0f;
      this.mark = marked ? new NomadGrid.Box(0f, CAPTION_Y - 1f, MARK_PX, MARK_PX) : null;
      this.caption = new NomadGrid.Box(shift, CAPTION_Y, MOVE_X - 2f, LINE_H);
      this.move = new NomadGrid.Box(shift + MOVE_X, 0f,
          Math.max(MOVE_MIN_W, moveWordW + STORE_STEP_PAD), MOVE_H);
      this.value = new NomadGrid.Box(w - VALUE_W, CAPTION_Y, VALUE_W, LINE_H);
      this.bar = new NomadGrid.Box(0f, BAR_Y, w, BAR_H);
      this.parts = new NomadGrid.Box(0f, PARTS_Y, w, LINE_H);
      this.orders = open ? new NomadGrid.Box(0f, ORDERS_Y, w, ORDERS_H) : null;
      this.height = open ? ORDERS_Y + ORDERS_H : CLOSED_H;
      int rows = open ? CREW_ROW_Y.length : 0;
      this.crewLabel = new NomadGrid.Box[rows];
      this.crewButton = new NomadGrid.Box[rows][3];
      this.crewNote = new NomadGrid.Box[rows];
      for (int r = 0; r < rows; r++) {
        float y = ORDERS_Y + CREW_ROW_Y[r];
        float textY = y + (CREW_BUTTON_H - LINE_H) / 2f;
        crewLabel[r] = new NomadGrid.Box(CREW_LABEL_X, textY,
            CREW_BUTTON_X - 2f - CREW_LABEL_X, LINE_H);
        float x = CREW_BUTTON_X;
        for (int b = 0; b < 3; b++) {
          crewButton[r][b] = new NomadGrid.Box(x, y, CREW_BUTTON_W, CREW_BUTTON_H);
          x += CREW_BUTTON_W + CREW_GAP;
        }
        crewNote[r] = new NomadGrid.Box(x, textY, Math.max(0f, w - CREW_NOTE_PAD - x), LINE_H);
      }
    }

    public static ResidentsLayout of(float w, boolean marked, boolean open, float moveWordW) {
      return new ResidentsLayout(w, marked, open, moveWordW);
    }
  }

  public static final float STORE_ROW_H = NomadGrid.Screen.LEDGER_ROW_H;

  public static final class Ledger {

    public static final float LINE_H = 15f;
    public static final float HEAD_Y = 2f;
    public static final float HEAD_H = 18f;
    public static final float FILL_W = 120f;
    public static final float FILL_H = 22f;
    public static final float HEAD_GAP = 8f;
    static final float CAPTION_Y = 25f;
    static final float ROWS_Y = 42f;
    static final float CAPTION_EXTRA = 15f;
    public static final float ORDERS_H = 56f;

    static final float NAME_X = 26f;
    static final float MARK_X = 152f;
    public static final float MARK_PX = 16f;
    static final float STOCK_X = 172f;
    static final float STOCK_W = 112f;
    public static final float METER_H = 3f;
    static final float NET_W = 60f;
    static final float NET_PAD = 4f;
    static final float WIDE_NET_RIGHT = 344f;
    static final float[] FLAG_X = { 356f, 384f, 416f, 444f };
    static final float[] FLAG_W = { 24f, 28f, 28f, 36f };
    static final float MARK_GAP = 2f;
    public static final float NARROW_FLAG_W = 16f;
    static final float NARROW_NET_W = 38f;
    static final float NARROW_GAP = 4f;
    static final float NARROW_STOCK_W = 84f;
    static final float NARROW_CAP_NET_W = 60f;

    static final float ORDER_LABEL_X = 6f;
    static final float STEP_X = 50f;
    static final float STEP_END = 262f;
    static final float STEP_Y = 5f;
    static final float STEP_H = 20f;
    static final float STEP_GAP = 3f;
    static final float FIELD_X = 265f;
    public static final float FIELD_W = 50f;
    static final float SET_X = 318f;
    static final float SET_W = 40f;
    static final float CHECK_Y = 31f;
    static final float CHECK_H = 19f;
    static final float CHECK_GAP = 4f;
    static final float[] CHECK_W = { 57f, 65f, 67f, 107f };
    public static final float TICK_X = 5f;
    public static final float TICK_PX = 12f;

    public final float w;
    public final float rowH;
    public final boolean wide;
    public final int rows;
    public final int open;
    public final float height;

    public final NomadGrid.Box head;
    public final NomadGrid.Box fill;
    public final NomadGrid.Box capStock;
    public final NomadGrid.Box capNet;
    public final NomadGrid.Box[] capFlag;

    public final NomadGrid.Box icon;
    public final NomadGrid.Box name;
    public final NomadGrid.Box mark;
    public final NomadGrid.Box stock;
    public final NomadGrid.Box meter;
    public final NomadGrid.Box net;
    public final NomadGrid.Box[] flag;
    public final NomadGrid.Box[] flagHit;

    public final NomadGrid.Box targetLabel;
    public final NomadGrid.Box[] step;
    public final NomadGrid.Box field;
    public final NomadGrid.Box set;
    public final NomadGrid.Box flagsLabel;
    public final NomadGrid.Box[] check;

    private final float rowsY;

    private Ledger(float w, float rowH, int rows, int open, float[] stepWordW) {
      this.w = w;
      this.rowH = rowH;
      this.wide = isWide(w);
      this.rows = Math.max(0, rows);
      this.open = open >= 0 && open < this.rows ? open : -1;
      this.rowsY = top(w);
      this.height = rowsY + this.rows * rowH + (this.open >= 0 ? ORDERS_H : 0f);

      int flags = FLAG_COLUMNS.length;
      float[] flagX = new float[flags];
      float[] flagW = new float[flags];
      for (int i = 0; i < flags; i++) {
        flagX[i] = wide ? FLAG_X[i] : w - (flags - i) * NARROW_FLAG_W;
        flagW[i] = wide ? FLAG_W[i] : NARROW_FLAG_W;
      }
      float netRight = wide ? WIDE_NET_RIGHT : flagX[0] - NARROW_GAP;
      float netW = wide ? NET_W : NARROW_NET_W;
      float stockW = wide ? STOCK_W : NARROW_STOCK_W;
      float markX = wide ? MARK_X : netRight - netW - MARK_GAP - MARK_PX;
      float stockX = wide ? STOCK_X : markX - MARK_GAP - stockW;
      float nameRight = (wide ? markX : stockX) - MARK_GAP;
      this.head = new NomadGrid.Box(0f, HEAD_Y, w - FILL_W - HEAD_GAP, HEAD_H);
      this.fill = new NomadGrid.Box(w - FILL_W, 0f, FILL_W, FILL_H);
      this.capStock = new NomadGrid.Box(stockX + stockW - STOCK_W, CAPTION_Y, STOCK_W,
          2f * LINE_H);
      float capNetW = wide ? NET_W : NARROW_CAP_NET_W;
      this.capNet = new NomadGrid.Box(netRight - netW / 2f - capNetW / 2f, CAPTION_Y, capNetW,
          2f * LINE_H);
      this.capFlag = new NomadGrid.Box[flags];
      this.flag = new NomadGrid.Box[flags];
      this.flagHit = new NomadGrid.Box[flags];
      for (int i = 0; i < flags; i++) {
        boolean two = wide && i == flags - 1;
        capFlag[i] = new NomadGrid.Box(flagX[i], CAPTION_Y + (two ? 0f : CAPTION_EXTRA),
            flagW[i], two ? 2f * LINE_H : LINE_H);
        flag[i] = new NomadGrid.Box(flagX[i], 1f, flagW[i], LINE_H);
        flagHit[i] = new NomadGrid.Box(flagX[i], 0f, flagW[i], rowH);
      }

      float iconPx = rowH >= NomadGrid.Screen.LEDGER_ROW_H ? 20f : 18f;
      this.icon = new NomadGrid.Box(0f, 1f, iconPx, iconPx);
      this.name = new NomadGrid.Box(NAME_X, 1f, nameRight - NAME_X, LINE_H);
      this.mark = new NomadGrid.Box(markX, 1f, MARK_PX, MARK_PX);
      this.stock = new NomadGrid.Box(stockX, 1f, stockW, LINE_H);
      this.meter = new NomadGrid.Box(stockX, rowH - METER_H - 1f, stockW, METER_H);
      this.net = new NomadGrid.Box(netRight - netW, 1f, netW, LINE_H);

      float textY = STEP_Y + (STEP_H - LINE_H) / 2f;
      this.targetLabel = new NomadGrid.Box(ORDER_LABEL_X, textY, STEP_X - 2f - ORDER_LABEL_X,
          LINE_H);
      this.step = steps(stepWordW);
      this.field = new NomadGrid.Box(FIELD_X, STEP_Y, FIELD_W, STEP_H);
      this.set = new NomadGrid.Box(SET_X, STEP_Y, SET_W, STEP_H);
      this.flagsLabel = new NomadGrid.Box(ORDER_LABEL_X, CHECK_Y + (CHECK_H - LINE_H) / 2f,
          STEP_X - 2f - ORDER_LABEL_X, LINE_H);
      this.check = new NomadGrid.Box[CHECK_W.length];
      float x = STEP_X;
      for (int i = 0; i < CHECK_W.length; i++) {
        check[i] = new NomadGrid.Box(x, CHECK_Y, CHECK_W[i], CHECK_H);
        x += CHECK_W[i] + CHECK_GAP;
      }
    }

    public static Ledger of(float w, float rowH, int rows, int open, float[] stepWordW) {
      return new Ledger(w, rowH, rows, open, stepWordW);
    }

    public static boolean isWide(float w) {
      return w >= NomadGrid.Screen.HOME_WIDE_W;
    }

    public static float top(float w) {
      return ROWS_Y + CAPTION_EXTRA;
    }

    public static float rowHeight(float w, float maxH, int rows, boolean crewOpen) {
      float full = top(w) + rows * NomadGrid.Screen.LEDGER_ROW_H + (crewOpen ? 0f : ORDERS_H);
      return full <= maxH ? NomadGrid.Screen.LEDGER_ROW_H : NomadGrid.Screen.LEDGER_ROW_SHORT_H;
    }

    public float rowY(int i) {
      return rowsY + i * rowH + (open >= 0 && i > open ? ORDERS_H : 0f);
    }

    public float ordersY() {
      return rowY(Math.max(0, open)) + rowH;
    }

    private static NomadGrid.Box[] steps(float[] wordW) {
      int n = wordW.length;
      float span = STEP_END - STEP_X - (n - 1) * STEP_GAP;
      float sum = 0f;
      float longest = 0f;
      for (float word : wordW) {
        sum += word;
        longest = Math.max(longest, word);
      }
      boolean equal = span / n >= longest + STORE_STEP_PAD;
      float spare = (span - sum) / n;
      NomadGrid.Box[] out = new NomadGrid.Box[n];
      float x = STEP_X;
      for (int i = 0; i < n; i++) {
        float bw = equal ? span / n : wordW[i] + spare;
        out[i] = new NomadGrid.Box(x, STEP_Y, bw, STEP_H);
        x += bw + STEP_GAP;
      }
      return out;
    }
  }

  public static float meterFill(float stock, float target) {
    if (target <= 0f) {
      return stock > 0f ? 1f : 0f;
    }
    return clamp01(stock / target);
  }

  public static boolean stockBad(boolean outOf, float stock, float minTarget, float net) {
    return outOf || stock < minTarget && net < -0.05f;
  }

  public static final class Stopped {

    public final List<String> outOf = new ArrayList<String>();
  }

  public static Map<String, Stopped> stopped(TickSheet sheet) {
    Map<String, Stopped> out = new LinkedHashMap<String, Stopped>();
    for (Attention a : attention(sheet)) {
      String good = a.state.good;
      if (good == null) {
        continue;
      }
      Stopped s = out.get(good);
      if (s == null) {
        s = new Stopped();
        out.put(good, s);
      }
      s.outOf.add(a.workId);
    }
    return out;
  }

  public static List<TickSheet.Store> listed(TickSheet sheet) {
    List<TickSheet.Store> out = new ArrayList<TickSheet.Store>();
    if (sheet == null) {
      return out;
    }
    for (TickSheet.Store store : sheet.stores.values()) {
      if (!Commod.LISTED_IN_USE.contains(store.good) || inUse(sheet, store)) {
        out.add(store);
      }
    }
    return out;
  }

  private static boolean inUse(TickSheet sheet, TickSheet.Store store) {
    String good = store.good;
    if (sheet.stock(good) >= 1f || store.target > 0f
        || sheet.s.flags(good) != StoreFlags.DEFAULT) {
      return true;
    }
    for (WorkState w : sheet.works) {
      for (int size : new int[] { w.size, w.buildTo }) {
        WorkSize row = size > 0 ? sheet.catalog.size(w.id, size) : null;
        if (row != null && (row.makes(good) > 0f || row.uses(good) > 0f)) {
          return true;
        }
      }
    }
    return false;
  }

  public static boolean unused(TickSheet sheet, String good, float net) {
    if (Math.abs(net) >= 0.05f) {
      return false;
    }
    if (sheet == null) {
      return true;
    }
    for (WorkState w : sheet.works) {
      WorkSize row = w.size > 0 ? sheet.catalog.size(w.id, w.size) : null;
      if (row != null && (row.makes(good) > 0f || row.uses(good) > 0f)) {
        return false;
      }
    }
    return true;
  }

  public static final class NetLine {

    public final String workId;
    public final float perMonth;

    NetLine(String workId, float perMonth) {
      this.workId = workId;
      this.perMonth = perMonth;
    }
  }

  public static List<NetLine> netLines(TickSheet sheet, String good) {
    List<NetLine> out = new ArrayList<NetLine>();
    if (sheet == null) {
      return out;
    }
    for (WorkState w : inCardOrder(sheet)) {
      TickSheet.WorkRun run = sheet.run(w.id);
      if (run == null) {
        continue;
      }
      Float made = run.makesPerMonth.get(good);
      Float used = run.usesPerMonth.get(good);
      float v = (made == null ? 0f : made.floatValue()) - (used == null ? 0f : used.floatValue());
      if (Math.abs(v) > 1e-4f) {
        out.add(new NetLine(w.id, v));
      }
    }
    if (sheet.s.detached && Commod.SUPPLIES.equals(good) && sheet.stores.containsKey(good)) {
      float ships = Math.max(0f, sheet.s.detachedSuppliesPerMonth);
      if (ships > 1e-4f) {
        out.add(new NetLine(null, -ships));
      }
    }
    return out;
  }

  public static boolean[] rowFlags(int flags) {
    boolean[] out = new boolean[FLAG_COLUMNS.length];
    for (int i = 0; i < out.length; i++) {
      out[i] = StoreFlags.has(flags, FLAG_COLUMNS[i]);
    }
    return out;
  }

  private static List<WorkState> inCardOrder(TickSheet sheet) {
    List<WorkState> works = new ArrayList<WorkState>(sheet.works);
    Collections.sort(works, new Comparator<WorkState>() {
      @Override
      public int compare(WorkState a, WorkState b) {
        return Integer.compare(a.order, b.order);
      }
    });
    return works;
  }

  public static String stepLabel(int step) {
    return (step > 0 ? "+" : "") + step;
  }

  public static String flowText(float v) {
    return Math.abs(v) < 0.05f ? "0" : NomadWorkCardModel.compact(v);
  }

  public static float total(Iterable<Map<String, Float>> perWork, String good) {
    float sum = 0f;
    for (Map<String, Float> m : perWork) {
      Float v = m == null ? null : m.get(good);
      if (v != null) {
        sum += v.floatValue();
      }
    }
    return sum;
  }

  public static float madePerMonth(TickSheet sheet, String good) {
    List<Map<String, Float>> maps = new ArrayList<Map<String, Float>>();
    for (TickSheet.WorkRun run : sheet.runs.values()) {
      maps.add(run.makesPerMonth);
    }
    return total(maps, good);
  }

  public static float usedPerMonth(TickSheet sheet, String good) {
    List<Map<String, Float>> maps = new ArrayList<Map<String, Float>>();
    for (TickSheet.WorkRun run : sheet.runs.values()) {
      maps.add(run.usesPerMonth);
    }
    return total(maps, good);
  }

  public static float keptTarget(float playerTarget, float minTarget) {
    float asked = playerTarget > 0f ? (float) Math.ceil(playerTarget) : 0f;
    return Math.max(asked, Math.max(0f, minTarget));
  }

  public static boolean atMinimum(float target, float minTarget) {
    return target <= minTarget + AT_MIN_SLACK;
  }

  public static float targetAfter(float now, float stock, float minTarget, float spaceFree,
      float perUnit, boolean exact, float n) {
    float held = Math.max(now, stock);
    float highest = perUnit <= 0f ? Float.MAX_VALUE
        : (float) Math.floor(held + Math.max(0f, spaceFree) / perUnit);
    float want = exact ? n : now + n;
    return Math.max(minTarget, Math.min(highest, want));
  }

  public static long parseTarget(String typed) {
    if (typed == null) {
      return -1L;
    }
    String s = typed.replace(",", "").replace(" ", "").trim();
    if (s.isEmpty() || !s.matches("[0-9]*\\.?[0-9]*") || ".".equals(s)) {
      return -1L;
    }
    double v;
    try {
      v = Double.parseDouble(s);
    } catch (NumberFormatException e) {
      return -1L;
    }
    if (Double.isNaN(v) || v > MAX_TYPED_TARGET) {
      return -1L;
    }
    return Math.round(v);
  }

  public static final double MAX_TYPED_TARGET = 1.0e7;

  public static boolean flagOn(int flags, int flag) {
    return StoreFlags.has(flags, flag);
  }

  public static boolean showsReserve(int flag, String good) {
    boolean fromCargo = flag == StoreFlags.IN || flag == StoreFlags.SELL_CARGO;
    return fromCargo && (Commod.SUPPLIES.equals(good) || Commod.FUEL.equals(good));
  }

  public static float residentsPerMonth(float residents, float room, NeedsLevel level, Rules k) {
    return residentsPerMonth(residents, room, level, k, 0f);
  }

  public static float residentsPerMonth(float residents, float room, NeedsLevel level, Rules k,
      float schoolGrowth) {
    float leave = Math.max(0f, Math.min(1f, k.leavePerMonth(level)));
    if (leave > 0f) {
      return -residents * leave;
    }
    float g = Residents.growthPerYear(k, level, schoolGrowth);
    float grow = residents * ((float) Math.pow(1.0 + g, 1.0 / 12.0) - 1f);
    return Math.max(0f, Math.min(grow, Math.max(0f, room)));
  }

  public static float miningSpace(TickSheet.WorkRun rig, float efficiency) {
    if (rig == null || rig.def == null || efficiency <= 0f) {
      return 0f;
    }
    boolean canMine = rig.status == WorkStatus.RUNNING || rig.status == WorkStatus.IDLE;
    return canMine ? rig.space : 0f;
  }

  public static boolean needsPlayer(NomadWorkCardModel.Shown shown, NomadWorkCardModel.Why why) {
    if (shown == null) {
      return false;
    }
    switch (shown) {
      case UNSTAFFED:
      case SUSPENDED:
        return true;
      case IDLE:
        return why == NomadWorkCardModel.Why.INPUT_EMPTY
            || why == NomadWorkCardModel.Why.HOLD_FULL
            || why == NomadWorkCardModel.Why.MARINES_NO_ROOM;
      case LACKING:
        return true;
      case STANDING_DOWN:
        return false;
      default:
        return false;
    }
  }

  public static String heads(float residents) {
    return String.format(Locale.ROOT, "%,d", Long.valueOf(Math.round((double) residents)));
  }

  public static final class Attention {

    public final String workId;
    public final NomadWorkCardModel.State state;

    Attention(String workId, NomadWorkCardModel.State state) {
      this.workId = workId;
      this.state = state;
    }
  }

  public static List<Attention> attention(TickSheet sheet) {
    List<Attention> out = new ArrayList<Attention>();
    if (sheet == null || sheet.s.paused) {
      return out;
    }
    for (WorkState w : inCardOrder(sheet)) {
      NomadWorkCardModel.State st = NomadWorkCardModel.state(sheet, w, sheet.catalog.work(w.id),
          false, null);
      if (needsPlayer(st.shown, st.why)) {
        out.add(new Attention(w.id, st));
      }
    }
    return out;
  }

  public enum Worst {
    PAUSED,
    DETACHED,
    LEVEL_LOW,
    SUSPENDED,
    UNSTAFFED,
    LACKING,
    STARVED,
    WELL
  }

  public static final class Standing {

    public final Worst worst;
    public final int suspended;
    public final int unstaffed;
    public final int starved;
    public final int lacking;

    Standing(Worst worst, int suspended, int unstaffed, int starved, int lacking) {
      this.worst = worst;
      this.suspended = suspended;
      this.unstaffed = unstaffed;
      this.starved = starved;
      this.lacking = lacking;
    }

    public int attention() {
      return suspended + unstaffed + lacking + starved;
    }

    public boolean needsOrder() {
      return worst == Worst.LEVEL_LOW || worst == Worst.SUSPENDED || worst == Worst.UNSTAFFED
          || worst == Worst.LACKING || worst == Worst.STARVED;
    }
  }

  public static Standing standing(TickSheet sheet) {
    if (sheet == null) {
      return new Standing(Worst.WELL, 0, 0, 0, 0);
    }
    int suspended = 0;
    int unstaffed = 0;
    int starved = 0;
    int lacking = 0;
    for (Attention a : attention(sheet)) {
      switch (a.state.shown) {
        case SUSPENDED:
          suspended++;
          break;
        case UNSTAFFED:
          unstaffed++;
          break;
        case LACKING:
          lacking++;
          break;
        default:
          starved++;
          break;
      }
    }
    NeedsLevel level = sheet.needs == null || sheet.needs.level == null ? NeedsLevel.NORMAL
        : sheet.needs.level;
    Worst worst;
    if (sheet.s.paused) {
      worst = Worst.PAUSED;
    } else if (sheet.s.detached) {
      worst = Worst.DETACHED;
    } else if (!level.atLeastNormal()) {
      worst = Worst.LEVEL_LOW;
    } else if (suspended > 0) {
      worst = Worst.SUSPENDED;
    } else if (unstaffed > 0) {
      worst = Worst.UNSTAFFED;
    } else if (lacking > 0) {
      worst = Worst.LACKING;
    } else if (starved > 0) {
      worst = Worst.STARVED;
    } else {
      worst = Worst.WELL;
    }
    return new Standing(worst, suspended, unstaffed, starved, lacking);
  }

  public enum StatusSentence {
    LEVEL_LOW,
    SUSPENDED,
    UNSTAFFED,
    LACKING,
    STARVED,
    WELL
  }

  public static List<StatusSentence> statusSentences(Standing st) {
    List<StatusSentence> out = new ArrayList<StatusSentence>();
    if (st.worst == Worst.PAUSED || st.worst == Worst.DETACHED) {
      return out;
    }
    if (st.worst == Worst.LEVEL_LOW) {
      out.add(StatusSentence.LEVEL_LOW);
    }
    if (st.suspended > 0) {
      out.add(StatusSentence.SUSPENDED);
    }
    if (st.unstaffed > 0) {
      out.add(StatusSentence.UNSTAFFED);
    }
    if (st.lacking > 0) {
      out.add(StatusSentence.LACKING);
    }
    if (st.starved > 0) {
      out.add(StatusSentence.STARVED);
    }
    if (out.isEmpty()) {
      out.add(StatusSentence.WELL);
    }
    return out;
  }

  public static boolean hasWorkMaking(TickSheet sheet, String good) {
    if (sheet == null) {
      return false;
    }
    for (WorkState w : sheet.works) {
      WorkSize row = w.size > 0 ? sheet.catalog.size(w.id, w.size) : null;
      if (row != null && row.makes(good) > 0f) {
        return true;
      }
    }
    return false;
  }

  public static float suppliesCover(float madePerMonth, float usedPerMonth, float fleetPerMonth) {
    if (fleetPerMonth <= 0f) {
      return -1f;
    }
    return Math.max(0f, madePerMonth - usedPerMonth) / fleetPerMonth;
  }

  public static int coverPercent(float cover) {
    return Math.max(0, Math.min(999, Math.round(cover * 100f)));
  }

  private static float clamp01(float v) {
    return v < 0f ? 0f : v > 1f ? 1f : v;
  }
}
