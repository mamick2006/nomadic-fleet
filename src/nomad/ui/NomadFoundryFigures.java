package nomad.ui;

import java.util.List;

public final class NomadFoundryFigures {

  private NomadFoundryFigures() {
  }

  public static final float SPRITE_LESS = 56f;
  public static final float SPRITE_TOP = 3f;
  public static final float JOIN_PAST = 8f;
  public static final float DOT = 2f;
  public static final float DOT_GAP = 5f;
  public static final char CAPTION_DOT = '·';

  public static final float LINE_DOWN = 6f;
  public static final float BAR_DOWN = 28f;
  public static final float BAR_H = 8f;
  public static final float FIGURES_DOWN = 39f;
  public static final float FIGURES_GAP = 10f;
  public static final float HEAD_DOWN = 64f;
  public static final float HEAD_H = 18f;
  public static final float BUTTONS_DOWN = 62f;
  public static final float BUTTON_H = 22f;
  public static final float PAUSE_MIN_W = 58f;
  public static final float CLEAR_MIN_W = 60f;
  public static final float BUTTON_PAD = 4f;
  public static final float HEAD_GAP = 6f;
  public static final float BUTTON_GAP = 4f;
  public static final float DELIVERED_DOWN = 87f;
  public static final float ROWS_DOWN = 104f;
  public static final float DAYS_RIGHT = 4f;
  public static final float ROW_SPRITE = 18f;
  public static final float ROW_NAME_X = 26f;
  public static final float ROW_GAP = 8f;
  public static final float FOOT_UP = 20f;
  public static final float FOOT_GAP = 4f;
  public static final float TIP_W = 450f;
  public static final float TIP_VALUE_W = 64f;
  public static final float TIP_VALUE_GAP = 10f;
  public static final float TIP_ROW_H = 18f;

  public static final class Layout {

    public final float w;
    public final float h;
    public final float rowH;
    public final NomadGrid.Box hero;
    public final NomadGrid.Box band;
    public final NomadGrid.Box sprite;
    public final NomadGrid.Box line;
    public final NomadGrid.Box bar;
    public final NomadGrid.Box figures;
    public final NomadGrid.Box head;
    public final NomadGrid.Box pause;
    public final NomadGrid.Box clear;
    public final NomadGrid.Box delivered;
    public final NomadGrid.Box rows;
    public final NomadGrid.Box foot;
    public final NomadGrid.Box hover;

    Layout(float w, float h, float heroH, float rowH, float pauseW, float clearW) {
      this.w = w;
      this.h = h;
      this.rowH = rowH;
      hero = new NomadGrid.Box(0f, 0f, w, heroH);
      band = new NomadGrid.Box(1f, heroH - NomadHomeFigures.BAND_UP, w - 2f,
          NomadHomeFigures.BAND_H);
      float side = Math.max(0f, heroH - SPRITE_LESS);
      sprite = new NomadGrid.Box(Math.round((w - side) / 2f), SPRITE_TOP, side, side);
      line = new NomadGrid.Box(0f, heroH + LINE_DOWN, w, NomadHomeFigures.LINE_H);
      bar = new NomadGrid.Box(0f, heroH + BAR_DOWN, w, BAR_H);
      figures = new NomadGrid.Box(0f, heroH + FIGURES_DOWN, w, NomadHomeFigures.LINE_H);
      clear = new NomadGrid.Box(w - clearW, heroH + BUTTONS_DOWN, clearW, BUTTON_H);
      pause = new NomadGrid.Box(clear.x - BUTTON_GAP - pauseW, heroH + BUTTONS_DOWN, pauseW,
          BUTTON_H);
      head = new NomadGrid.Box(0f, heroH + HEAD_DOWN, Math.max(0f, pause.x - HEAD_GAP), HEAD_H);
      delivered = new NomadGrid.Box(0f, heroH + DELIVERED_DOWN, w - DAYS_RIGHT,
          NomadHomeFigures.LINE_H);
      float rowsY = heroH + ROWS_DOWN;
      foot = new NomadGrid.Box(0f, h - FOOT_UP, w, NomadHomeFigures.LINE_H);
      rows = new NomadGrid.Box(0f, rowsY, w, Math.max(0f, foot.y - FOOT_GAP - rowsY));
      hover = new NomadGrid.Box(0f, 0f, w, figures.bottom());
    }

    public float captionY() {
      return hero.h - NomadHomeFigures.CAPTION_UP;
    }

    public float valueY() {
      return hero.h - NomadHomeFigures.VALUE_UP;
    }

    public float textLeft() {
      return NomadHomeFigures.HERO_INSET;
    }

    public float textRight() {
      return w - NomadHomeFigures.HERO_INSET;
    }

    public float leftBox(float rightW) {
      return Math.max(0f, w - 2f * NomadHomeFigures.HERO_INSET - rightW
          - (rightW > 0f ? NomadHomeFigures.HERO_GAP : 0f));
    }

    public NomadGrid.Box lineBox(boolean marked) {
      float step = marked ? NomadHomeFigures.MARK_STEP : 0f;
      return new NomadGrid.Box(line.x + step, line.y, Math.max(0f, line.w - step), line.h);
    }

    public float rowNameBox(float daysW) {
      return Math.max(0f, w - DAYS_RIGHT - daysW - ROW_GAP - ROW_NAME_X);
    }

    public float flowsBox(float percentW) {
      return Math.max(0f, w - percentW - FIGURES_GAP);
    }
  }

  public static Layout layout(float w, float h, float heroH, float rowH, float pauseWordW,
      float clearWordW) {
    return new Layout(w, h, heroH, rowH,
        Math.max(PAUSE_MIN_W, (float) Math.ceil(pauseWordW) + 2f * BUTTON_PAD),
        Math.max(CLEAR_MIN_W, (float) Math.ceil(clearWordW) + 2f * BUTTON_PAD));
  }

  public static String[] captionParts(String caption) {
    String all = caption == null ? "" : caption;
    int at = all.indexOf(CAPTION_DOT);
    if (at < 0) {
      return new String[] { all.trim(), "" };
    }
    return new String[] { all.substring(0, at).trim(), all.substring(at + 1).trim() };
  }

  public static float captionDotX(String[] parts, NomadText.Measure m) {
    return NomadText.need(parts[0], m) + DOT_GAP;
  }

  public static float captionSecondX(String[] parts, NomadText.Measure m) {
    return captionDotX(parts, m) + DOT + DOT_GAP;
  }

  public static float flowsWidth(List<String> figures, NomadText.Measure m) {
    float at = 0f;
    for (int i = 0; i < figures.size(); i++) {
      at += (i > 0 ? NomadWorkCardModel.FLOW_GAP : 0f)
          + NomadWorkCardModel.flowWidth(figures.get(i), m);
    }
    return at;
  }

  public static int flowsShown(List<String> figures, float tailW, float box,
      NomadText.Measure m) {
    for (int n = figures.size(); n > 0; n--) {
      if (flowsWidth(figures.subList(0, n), m) + NomadWorkCardModel.FLOW_GAP + tailW <= box) {
        return n;
      }
    }
    return 0;
  }

  public static float[] deliveryDays(float headDays, float[] unitDays, int[] counts) {
    float[] out = new float[unitDays.length];
    float sum = positive(headDays);
    for (int i = 0; i < unitDays.length; i++) {
      sum += Math.max(0, counts[i]) * positive(unitDays[i]);
      out[i] = sum;
    }
    return out;
  }

  private static float positive(float v) {
    return Float.isNaN(v) || Float.isInfinite(v) || v < 0f ? 0f : v;
  }

  public static int dayCount(float days) {
    return Math.max(1, Math.round(positive(days)));
  }

  public static int moreAfterHead(int stackCount) {
    return Math.max(0, stackCount - 1);
  }

  public static final float CELL_H = 44f;
  public static final float CELL_GAP_X = 12f;
  public static final float CELL_GAP_Y = 4f;
  public static final float CELL_MIN_W = 404f;

  public static int wallColumns(float v) {
    return Math.max(1, (int) Math.floor((v + CELL_GAP_X) / (CELL_MIN_W + CELL_GAP_X)));
  }

  public static float cellWidth(float v, int n) {
    return (float) Math.floor((v - CELL_GAP_X * (n - 1)) / n);
  }
}
