package nomad.topbar;

import java.util.Locale;

import nomad.ui.NomadText;

public final class NomadTopBarLayout {

  private NomadTopBarLayout() {
  }

  public static final float GRIP_W = 12f;
  public static final float GRIP_LINE_1 = 4f;
  public static final float GRIP_LINE_2 = 8f;
  public static final float GRIP_INSET_V = 8f;

  public static final float H = 30f;
  public static final float PAD = 8f;
  public static final float GLYPH = 20f;
  public static final float GAP = 8f;

  public static final float STATE_PAUSED_W = NomadText.element(188f + NomadText.MARGIN);
  public static final float STATE_DETACHED_W = NomadText.element(72f + NomadText.MARGIN);
  public static final float NEEDS_W = NomadText.element(205f + NomadText.MARGIN);
  public static final float NEEDS_NAME_W = NomadText.element(179f + NomadText.MARGIN);
  public static final float ATTENTION_W = NomadText.element(145f + NomadText.MARGIN);
  public static final float OWED_W = NomadText.element(169f + NomadText.MARGIN);
  public static final float COVER_W = NomadText.element(173f + NomadText.MARGIN);
  public static final float MINING_LABEL_W = NomadText.element(48f + NomadText.MARGIN);
  public static final float REMOTE_LABEL_W = NomadText.element(105f + NomadText.MARGIN);
  public static final float MINING_ICON_W = 22f;
  public static final float MINING_ICON_GAP = 3f;
  public static final int MINING_MAX_ICONS = 4;
  public static final float BTN_OPEN_W = 78f;
  public static final float BTN_LIM_W = 92f;

  public enum State {
    RUNNING,
    PAUSED,
    DETACHED
  }

  public enum Mining {
    NONE, IN_RANGE, REMOTE
  }

  public static final class Cells {

    public final State state;
    public final boolean needs;
    public final boolean needsName;
    public final boolean attention;
    public final boolean owed;
    public final boolean cover;
    public final Mining mining;
    public final int miningIcons;
    public final boolean liminal;

    Cells(State state, boolean needs, boolean needsName, boolean attention, boolean owed,
        boolean cover, Mining mining, int miningIcons, boolean liminal) {
      this.state = state;
      this.needs = needs;
      this.needsName = needsName;
      this.attention = attention;
      this.owed = owed;
      this.cover = cover;
      this.mining = mining;
      this.miningIcons = mining == Mining.NONE ? 0 : miningIcons;
      this.liminal = liminal;
    }
  }

  public static final float OWED_SHOWN_FROM = 0.5f;

  public static Cells cells(State state, int attention, float owed, boolean hasCover,
      Mining mining, int miningIcons, boolean liminal) {
    Mining m = mining == null ? Mining.NONE : mining;
    int icons = Math.max(0, Math.min(MINING_MAX_ICONS, miningIcons));
    switch (state) {
      case PAUSED:
        return new Cells(state, false, false, false, false, false, Mining.NONE, 0, liminal);
      case DETACHED:
        return new Cells(state, false, true, false, owed >= OWED_SHOWN_FROM, false, m, icons,
            liminal);
      case RUNNING:
      default:
        return new Cells(State.RUNNING, true, false, attention > 0, false, hasCover, m, icons,
            liminal);
    }
  }

  public static float stateWidth(State state) {
    switch (state) {
      case PAUSED:
        return STATE_PAUSED_W;
      case DETACHED:
        return STATE_DETACHED_W;
      default:
        return 0f;
    }
  }

  public static float miningWidth(Mining mining, int icons) {
    if (mining == null || mining == Mining.NONE) {
      return 0f;
    }
    float label = mining == Mining.REMOTE ? REMOTE_LABEL_W : MINING_LABEL_W;
    return label + Math.max(0, icons) * (MINING_ICON_GAP + MINING_ICON_W);
  }

  public static float dragWidth(Cells c) {
    float w = GRIP_W + PAD + GLYPH;
    w += cell(stateWidth(c.state));
    w += cell(c.needs ? NEEDS_W : 0f);
    w += cell(c.needsName ? NEEDS_NAME_W : 0f);
    w += cell(c.attention ? ATTENTION_W : 0f);
    w += cell(c.owed ? OWED_W : 0f);
    w += cell(c.cover ? COVER_W : 0f);
    w += cell(miningWidth(c.mining, c.miningIcons));
    return w + GAP;
  }

  public static float stripWidth(Cells c) {
    return dragWidth(c) + BTN_OPEN_W + (c.liminal ? GAP + BTN_LIM_W : 0f) + PAD;
  }

  private static float cell(float w) {
    return w > 0f ? GAP + w : 0f;
  }

  public static final float SIDE_CLEAR = 478f;

  public static float budget(float screenW) {
    return screenW - SIDE_CLEAR;
  }

  public static Cells fit(Cells c, float screenW) {
    float budget = budget(screenW);
    Cells out = c;
    if (stripWidth(out) > budget && out.miningIcons > 0) {
      out = new Cells(out.state, out.needs, out.needsName, out.attention, out.owed, out.cover,
          out.mining, 0, out.liminal);
    }
    if (stripWidth(out) > budget && out.mining != Mining.NONE) {
      out = new Cells(out.state, out.needs, out.needsName, out.attention, out.owed, out.cover,
          Mining.NONE, 0, out.liminal);
    }
    if (stripWidth(out) > budget && out.cover) {
      out = new Cells(out.state, out.needs, out.needsName, out.attention, out.owed, false,
          out.mining, out.miningIcons, out.liminal);
    }
    return out;
  }

  public static float widest() {
    return stripWidth(new Cells(State.RUNNING, true, false, true, false, true, Mining.REMOTE,
        MINING_MAX_ICONS, true));
  }

  public static String owedText(float owed) {
    return String.format(Locale.ROOT, "%,d", Integer.valueOf(Math.round(Math.max(0f, owed))));
  }

  public static float defaultLeft(float screenW, float usedWidth, float pad) {
    return Math.max(pad, (screenW - usedWidth) / 2f);
  }

  public static float clampLeft(float left, float width, float screenW, float pad) {
    float max = screenW - width - pad;
    if (max <= pad) {
      return pad;
    }
    return Math.max(pad, Math.min(left, max));
  }

  public static float clampTop(float top, float height, float screenH, float pad) {
    float max = screenH - height - pad;
    if (max <= pad) {
      return pad;
    }
    return Math.max(pad, Math.min(top, max));
  }

  public static float topFromBottom(float bottom, float height, float screenH) {
    return screenH - bottom - height;
  }

  public static float cursorTop(float cursorY, float screenH) {
    return screenH - cursorY;
  }

  public static float grabOffsetX(float pressX, float left) {
    return pressX - left;
  }

  public static float grabOffsetY(float pressY, float top, float screenH) {
    return cursorTop(pressY, screenH) - top;
  }

  public static float dragLeft(float cursorX, float grabX) {
    return cursorX - grabX;
  }

  public static float dragTop(float cursorY, float grabY, float screenH) {
    return cursorTop(cursorY, screenH) - grabY;
  }
}
