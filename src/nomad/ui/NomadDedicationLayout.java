package nomad.ui;

import nomad.campaign.econ.model.NeedsLevel;

public final class NomadDedicationLayout {

  private NomadDedicationLayout() {
  }

  public static final float INSET = 12f;
  public static final float TITLE_H = 26f;
  public static final float TITLE_GAP = 12f;
  public static final float ROW_H = 26f;
  public static final float ROW_GAP = 8f;
  public static final float NOTE_GAP = 14f;
  public static final int NOTE_LINES = 2;
  public static final float NOTE_H = 22f + (NOTE_LINES - 1) * 15f;
  public static final int ROWS = 2;

  public static final float OUTCOME_GAP = 10f;
  public static final int OUTCOME_LINES = 2;
  public static final float OUTCOME_LINE_H = 15f;
  public static final float OUTCOME_LINE_PAD = 5f;
  public static final float PICTURE_W = 96f;
  public static final float PICTURE_H = 48f;
  public static final float PICTURE_X = NomadText.INSET;
  public static final float PICTURE_GAP = 12f;
  public static final float OUTCOME_TEXT_X = NomadText.elementX(PICTURE_X + PICTURE_W + PICTURE_GAP);
  public static final float OUTCOME_H = PICTURE_H;

  public static float outcomeTextY(int lines) {
    int n = Math.max(1, Math.min(OUTCOME_LINES, lines));
    return (float) Math.floor(
        (OUTCOME_H - n * OUTCOME_LINE_H - (n - 1) * OUTCOME_LINE_PAD) / 2f);
  }

  public static final int OUTCOME_LINE_CHARS = 74;
  public static final float OUTCOME_TIP_W = 360f;

  public static float outcomeTextW() {
    return contentW() - OUTCOME_TEXT_X;
  }

  public static NeedsLevel pictureLevel(NeedsLevel level) {
    return level == null || level == NeedsLevel.NON_EXISTENT ? NeedsLevel.DECREPIT : level;
  }

  public static boolean pictureDimmed(NeedsLevel level) {
    return level == null || level == NeedsLevel.NON_EXISTENT;
  }

  public static final float WIDTH = 664f;

  public static final float HEIGHT_SLACK = 12f;

  public static final float HEIGHT = 2f * INSET + TITLE_H + TITLE_GAP
      + ROWS * ROW_H + (ROWS - 1) * ROW_GAP + NOTE_GAP + NOTE_H + OUTCOME_GAP + OUTCOME_H
      + HEIGHT_SLACK;

  public static float contentW() {
    return WIDTH - 2f * INSET;
  }

  public static final float CLOSE_SIZE = 20f;
  public static final float CLOSE_INSET = 4f;
  public static final float CLOSE_GLYPH = 18f;

  public static float closeBandH() {
    return CLOSE_INSET + CLOSE_SIZE + CLOSE_INSET;
  }

  public static final int COL_LABEL = 0;
  public static final int COL_SLIDER = 1;
  public static final int COL_PCT = 2;
  public static final int COL_USED = 3;

  private static final float LABEL_W = 76f;
  private static final float SLIDER_W = 200f;
  private static final float PCT_W = 44f;
  private static final float USED_MIN = 240f;

  private static final float GAP = 8f;
  private static final float USED_GAP = 10f;

  public static NomadColumns.Layout row() {
    return NomadColumns.solve(0f, contentW(), GAP,
        NomadColumns.Column.fixed("label", LABEL_W, NomadColumns.Align.LEFT),
        NomadColumns.Column.fixed("slider", SLIDER_W, NomadColumns.Align.LEFT),
        NomadColumns.Column.fixed("pct", PCT_W, NomadColumns.Align.RIGHT),
        NomadColumns.Column.remainder("used", USED_MIN, NomadColumns.Align.LEFT).gap(USED_GAP));
  }

  public static final float KNOB_W = 11f;
  public static final float TRACK_H = 10f;

  public static float pctAt(float x, float left, float width, float minPct) {
    if (width <= 0f) {
      return minPct;
    }
    return Math.max(minPct, Math.round(clamp01((x - left) / width) * 100f));
  }

  public static float fillW(float pct, float width) {
    return Math.max(0f, width) * clamp01(pct / 100f);
  }

  public static float knobX(float pct, float left, float width, float knobW) {
    float centre = left + fillW(pct, width);
    return Math.max(left, Math.min(left + width - knobW, centre - knobW / 2f));
  }

  private static float clamp01(float v) {
    return v < 0f ? 0f : (v > 1f ? 1f : v);
  }
}
