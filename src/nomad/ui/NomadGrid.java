package nomad.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import nomad.campaign.econ.model.TickSheet;
import nomad.campaign.econ.model.WorkDef;

public final class NomadGrid {

  public static final float CARD_W = 190f;
  public static final float CARD_H = 134f;
  public static final float ADD_H = 110f;
  public static final int ADD_BELOW = 12;
  public static final float GAP = 20f;
  public static final float GUTTER = 12f;
  public static final float NAME_H = 15f;
  public static final float PANEL_MAX_H = 284f;
  public static final float HEAD_H = 18f;
  public static final float HEAD_GAP = 6f;
  public static final float HEAD_ABOVE = 26f;
  public static final float FOOT = 14f;
  public static final float PANEL_FOOT = 8f;

  public static final class Box {

    public final float x;
    public final float y;
    public final float w;
    public final float h;

    public Box(float x, float y, float w, float h) {
      this.x = x;
      this.y = y;
      this.w = w;
      this.h = h;
    }

    public float right() {
      return x + w;
    }

    public float bottom() {
      return y + h;
    }
  }

  public static boolean production(WorkDef def) {
    return def == null || def.isProduction();
  }

  public static final class Screen {

    public static final float INSET = 10f;
    public static final float HOME_W = 360f;
    public static final float HOME_WIDE_W = 480f;
    public static final float HOME_WIDE_SPARE = 120f;
    public static final float HERO_ASPECT = 300f / 480f;
    public static final float HERO_SHORT_H = 135f;
    public static final float HERO_MIN_H = 97f;
    public static final float SHORT_LAYOUT_CLOSED_END = 595f;
    public static final float SHORT_LAYOUT_END = 651f;
    public static final float LEDGER_ROW_H = 26f;
    public static final float LEDGER_ROW_SHORT_H = 21f;
    public static final int LEDGER_ROWS = 12;
    public static final float HOME_FULL_H = 863f;
    public static final float HOME_SCROLL_BELOW = 612f;
    public static final float HOME_MIN_H = 652f;
    public static final float TOOLBAR_GAP = 8f;
    public static final float PICKER_MIN_W =
        NomadBuildPickerLayout.FULL_W + NomadBuildPickerLayout.GUTTER;

    public static float heroH(float homeW, float homeH) {
      return homeH >= HOME_FULL_H ? Math.round(homeW * HERO_ASPECT) : HERO_SHORT_H;
    }

    public static float heroH(float homeW, float homeH, int ledgerRows) {
      float extra = Math.max(0, ledgerRows - LEDGER_ROWS) * LEDGER_ROW_SHORT_H;
      if (homeH >= HOME_FULL_H || extra <= 0f) {
        return heroH(homeW, homeH);
      }
      float over = SHORT_LAYOUT_CLOSED_END + extra - HOME_MIN_H;
      return over <= 0f ? HERO_SHORT_H : Math.max(HERO_MIN_H, HERO_SHORT_H - over);
    }

    public static float ledgerRowH(float homeH) {
      return homeH >= HOME_FULL_H ? LEDGER_ROW_H : LEDGER_ROW_SHORT_H;
    }

    public final int columns;
    public final Box home;
    public final boolean homeScrolls;
    public final float homeLayoutH;
    public final boolean homeFollowsOrders;
    public final Box toolbar;
    public final Box wall;
    public final Box picker;

    public static Screen of(float w, float h) {
      return new Screen(w, h, LEDGER_ROWS);
    }

    public static Screen of(float w, float h, int ledgerRows) {
      return new Screen(w, h, ledgerRows);
    }

    private Screen(float w, float h, int ledgerRows) {
      float avail = Math.max(0f, w - 2f * INSET - HOME_W - GAP);
      this.columns = columnsFor(avail);
      float columnsW = widthOf(columns);
      float wallW = columnsW + GUTTER;
      float homeW = avail - wallW >= HOME_WIDE_SPARE ? HOME_WIDE_W : HOME_W;
      float wallX = INSET + homeW + GAP;

      this.home = new Box(INSET, 0f, homeW, h);
      float extra = Math.max(0, ledgerRows - LEDGER_ROWS) * LEDGER_ROW_SHORT_H;
      float openEnd = SHORT_LAYOUT_END + extra
          - (HERO_SHORT_H - heroH(homeW, HOME_MIN_H, ledgerRows));
      boolean forOrders = extra > 0f && h >= HOME_SCROLL_BELOW && h < HOME_FULL_H && h < openEnd;
      this.homeScrolls = h < HOME_SCROLL_BELOW || forOrders;
      this.homeLayoutH = homeScrolls ? openEnd + (HOME_MIN_H - SHORT_LAYOUT_END) : h;
      this.homeFollowsOrders = forOrders;
      this.toolbar = new Box(wallX, 0f, columnsW, Toolbar.of(columnsW).h);
      float wallY = toolbar.bottom() + TOOLBAR_GAP;
      this.wall = new Box(wallX, wallY, wallW, Math.max(0f, h - wallY));
      this.picker = new Box(wallX, 0f, wallW, h);
    }
  }

  public static final class Toolbar {

    public static final float ROW_H = 22f;
    public static final float ROW_GAP = 4f;
    public static final float MARK_PX = 16f;
    public static final float MARK_Y = 3f;
    public static final float TEXT_X = 20f;
    public static final float TEXT_Y = 4f;
    public static final float TEXT_H = 15f;
    public static final float NAMES_GAP = 10f;
    public static final float NAMES_END = 470f;
    public static final float CREDITS_FROM_RIGHT = 518f;
    public static final float CREDITS_W = 200f;
    public static final float ADD_FROM_RIGHT = 306f;
    public static final float ADD_W = 120f;
    public static final float SPLIT_W = 170f;
    public static final float ONE_ROW_MIN_W = 670f;
    public static final float FULL_BUTTONS_MIN_W = 518f;
    public static final float ADD_NARROW_W = 80f;
    public static final float SPLIT_NARROW_W = 140f;
    public static final float NARROW_GAP = 8f;

    public final float w;
    public final float h;
    public final int rows;
    public final Box mark;
    public final Box credits;
    public final boolean creditsRight;
    public final Box add;
    public final Box split;
    public final float namesEnd;

    public static Toolbar of(float w) {
      return new Toolbar(w);
    }

    private Toolbar(float w) {
      this.w = w;
      this.rows = w >= ONE_ROW_MIN_W ? 1 : 2;
      this.h = rows * ROW_H + (rows - 1) * ROW_GAP;
      this.mark = new Box(0f, MARK_Y, MARK_PX, MARK_PX);
      float y = (rows - 1) * (ROW_H + ROW_GAP);
      if (w >= FULL_BUTTONS_MIN_W) {
        this.credits = new Box(w - CREDITS_FROM_RIGHT, y + TEXT_Y, CREDITS_W, TEXT_H);
        this.creditsRight = true;
        this.add = new Box(w - ADD_FROM_RIGHT, y, ADD_W, ROW_H);
        this.split = new Box(w - SPLIT_W, y, SPLIT_W, ROW_H);
      } else {
        this.split = new Box(Math.max(0f, w - SPLIT_NARROW_W), y, SPLIT_NARROW_W, ROW_H);
        this.add = new Box(Math.max(0f, split.x - NARROW_GAP - ADD_NARROW_W), y, ADD_NARROW_W,
            ROW_H);
        this.credits = new Box(0f, y + TEXT_Y, Math.max(0f, add.x - NAMES_GAP), TEXT_H);
        this.creditsRight = false;
      }
      this.namesEnd = rows == 1 ? w - NAMES_END : w;
    }

    public float countX(boolean marked) {
      return marked ? TEXT_X : 0f;
    }

    public Box names(float countRight) {
      float x = countRight + NAMES_GAP;
      return new Box(x, TEXT_Y, Math.max(0f, namesEnd - x), TEXT_H);
    }

    public interface More {
      String text(String names, int rest);
    }

    public static final class Names {

      public final String text;
      public final int shown;
      public final int rest;

      Names(String text, int shown, int rest) {
        this.text = text;
        this.shown = shown;
        this.rest = rest;
      }
    }

    public static Names fit(List<String> names, float box, NomadText.Measure m, More more) {
      int n = names == null ? 0 : names.size();
      if (n == 0) {
        return new Names("", 0, 0);
      }
      String all = join(names, n);
      if (NomadText.need(all, m) <= box) {
        return new Names(all, n, 0);
      }
      for (int k = n - 1; k >= 1; k--) {
        String text = more.text(join(names, k), n - k);
        if (NomadText.need(text, m) <= box) {
          return new Names(text, k, n - k);
        }
      }
      return new Names("", 0, n);
    }

    private static String join(List<String> names, int k) {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < k; i++) {
        if (i > 0) {
          sb.append(", ");
        }
        sb.append(names.get(i));
      }
      return sb.toString();
    }
  }

  public static final class Counted {

    public final List<String> ids;

    private Counted(List<String> ids) {
      this.ids = Collections.unmodifiableList(ids);
    }

    public static Counted of(TickSheet sheet) {
      List<String> production = new ArrayList<String>();
      List<String> support = new ArrayList<String>();
      if (sheet != null) {
        for (NomadPanelFigures.Attention a : NomadPanelFigures.attention(sheet)) {
          (production(sheet.catalog.work(a.workId)) ? production : support).add(a.workId);
        }
      }
      production.addAll(support);
      return new Counted(production);
    }

    public int count() {
      return ids.size();
    }

    public boolean has(String workId) {
      return workId != null && ids.contains(workId);
    }
  }

  public final int production;
  public final int support;
  public final boolean add;
  public final int count;
  public final int columns;
  public final float width;
  public final float viewH;
  public final float contentH;
  public final int open;
  private final float openH;
  private final int firstCards;
  private final float[] headY = { -1f, -1f };
  private final float[] rowsY = { 0f, 0f };

  private NomadGrid(int production, int support, boolean add, int columns, float viewH, int open,
      float openH) {
    this.production = Math.max(0, production);
    this.support = Math.max(0, support);
    this.add = add;
    this.firstCards = this.production + (add ? 1 : 0);
    this.count = firstCards + this.support;
    this.columns = Math.max(1, columns);
    this.width = widthOf(this.columns);
    this.viewH = Math.max(0f, viewH);

    float y = 0f;
    if (firstCards > 0) {
      if (this.production > 0) {
        headY[0] = y;
        y += HEAD_H + HEAD_GAP;
      }
      rowsY[0] = y;
      y += rowsHeight(firstCards);
    }
    if (this.support > 0) {
      if (firstCards > 0) {
        y += HEAD_ABOVE;
      }
      headY[1] = y;
      y += HEAD_H + HEAD_GAP;
      rowsY[1] = y;
      y += rowsHeight(this.support);
    }
    float rowsBottom = count == 0 ? 0f : y + FOOT;

    boolean valid = open >= 0 && open < count && open != addIndex();
    this.open = valid ? open : -1;
    this.openH = valid ? Math.max(0f, Math.min(PANEL_MAX_H, openH)) : 0f;
    Box panel = panel();
    this.contentH = panel == null ? rowsBottom
        : Math.max(rowsBottom, panel.bottom() + PANEL_FOOT);
  }

  private float rowsHeight(int cards) {
    int rows = (cards + columns - 1) / columns;
    return rows == 0 ? 0f : rows * CARD_H + (rows - 1) * GAP;
  }

  public static int columnsFor(float availW) {
    int n = (int) Math.floor((availW - GUTTER + GAP) / (CARD_W + GAP));
    return Math.max(1, n);
  }

  public static float widthOf(int columns) {
    return columns * CARD_W + (columns - 1) * GAP;
  }

  public static boolean addCard(int production, int support, int unbuilt, int columns) {
    if (unbuilt <= 0 || production + support >= ADD_BELOW) {
      return false;
    }
    return production + support == 0 || production % Math.max(1, columns) != 0;
  }

  public static NomadGrid wall(int production, int support, boolean add, float availW,
      float viewH) {
    return new NomadGrid(production, support, add, columnsFor(availW), viewH, -1, 0f);
  }

  public NomadGrid withPanel(int index, float panelH) {
    return new NomadGrid(production, support, add, columns, viewH, index, panelH);
  }

  public int addIndex() {
    return add ? production : -1;
  }

  public int supportStart() {
    return firstCards;
  }

  public int group(int index) {
    return index < firstCards ? 0 : 1;
  }

  public int row(int index) {
    return (index - (group(index) == 0 ? 0 : firstCards)) / columns;
  }

  public int column(int index) {
    return (index - (group(index) == 0 ? 0 : firstCards)) % columns;
  }

  public Box heading(int group) {
    return group < 0 || group > 1 || headY[group] < 0f ? null
        : new Box(0f, headY[group], width, HEAD_H);
  }

  public Box card(int index) {
    return new Box(column(index) * (CARD_W + GAP),
        rowsY[group(index)] + row(index) * (CARD_H + GAP), CARD_W,
        index == addIndex() ? ADD_H : CARD_H);
  }

  public Box panel() {
    if (open < 0) {
      return null;
    }
    Box card = card(open);
    return new Box(card.x, card.y + NAME_H, CARD_W, openH);
  }

  public Box hit(int index) {
    Box card = card(index);
    Box whole = new Box(0f, 0f, CARD_W, card.h);
    Box panel = panel();
    if (panel == null || column(index) != column(open)) {
      return whole;
    }
    if (index == open) {
      return new Box(0f, 0f, CARD_W, NAME_H);
    }
    float covered = panel.bottom() - card.y;
    if (card.y < panel.y || covered <= 0f) {
      return whole;
    }
    float top = Math.min(card.h, covered);
    return new Box(0f, top, CARD_W, card.h - top);
  }

  public boolean scrolls() {
    return contentH > viewH;
  }

  public float maxScroll() {
    return Math.max(0f, contentH - viewH);
  }

  public float scrollToShow(int index, float offset) {
    Box card = card(index);
    Box head = row(index) == 0 ? heading(group(index)) : null;
    float top = head == null ? card.y : head.y;
    float bottom = card.bottom();
    Box panel = index == open ? panel() : null;
    if (panel != null) {
      bottom = Math.max(bottom, panel.bottom() + PANEL_FOOT);
    }
    float out = offset;
    if (bottom > out + viewH) {
      out = bottom - viewH;
    }
    if (top < out) {
      out = top;
    }
    return Math.max(0f, Math.min(maxScroll(), out));
  }

  public static float clampScroll(float offset, float contentH, float viewH) {
    return Math.max(0f, Math.min(Math.max(0f, contentH - viewH), offset));
  }
}
