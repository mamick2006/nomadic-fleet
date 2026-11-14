package nomad.ui;

import java.util.List;

import nomad.campaign.econ.model.BasicNeeds;

public final class NomadBuildPickerLayout {

  public static final int THUMB = 0;
  public static final int NAME = 1;
  public static final int NEEDS = 2;
  public static final int COST = 3;
  public static final int BUILD = 4;
  public static final int COUNT = 5;

  public static final float GAP = 10f;
  public static final float THUMB_W = 192f;
  public static final float THUMB_H = 96f;
  public static final float THUMB_MIN_W = 88f;
  public static final float ROW_H = THUMB_H;
  public static final float NAME_MIN = 260f;
  public static final float NEEDS_W = 190f;
  public static final float SIDE_W = 110f;
  public static final float BUILD_W = 90f;
  public static final float BUTTON_H = 22f;
  public static final float LINE_H = 15f;

  public static final float FULL_W = THUMB_W + GAP + NAME_MIN + GAP + NEEDS_W + GAP + SIDE_W;

  public static final float NAME_Y = 22f;
  public static final float LINE_GAP = 5f;
  public static final float BODY_Y = NAME_Y + LINE_H + LINE_GAP;
  public static final float BUILD_Y = BODY_Y - 4f;
  public static final float FOOT = 8f;

  public static final float ICON = 24f;
  public static final float FLOW_NUM_PAD = 2f;
  public static final float FLOW_GAP = 12f;
  public static final float FLOW_LINE_H = ICON;
  public static final float FLOW_LINE_GAP = 4f;
  public static final float FLOW_NUM_Y = (ICON - LINE_H) / 2f;

  public final float[] x = new float[COUNT];
  public final float[] w = new float[COUNT];
  public final float thumbH;

  private NomadBuildPickerLayout(float thumbH) {
    this.thumbH = thumbH;
  }

  public boolean shown(int col) {
    return w[col] > 0f;
  }

  public float y(int col) {
    switch (col) {
      case NAME:
      case NEEDS:
      case COST:
        return NAME_Y;
      case BUILD:
        return BUILD_Y;
      default:
        return 0f;
    }
  }

  public float sentenceWidth(boolean tagged) {
    return tagged || !shown(NEEDS) ? w[NAME] : x[NEEDS] + w[NEEDS] - x[NAME];
  }

  public static float bodyY(int nameLines) {
    return NAME_Y + Math.max(1, nameLines) * LINE_H + LINE_GAP;
  }

  public float rowHeight(int nameLines, float bodyH, int tagLines) {
    float low = Math.max(thumbH, BUILD_Y + BUTTON_H);
    low = Math.max(low, bodyY(nameLines) + bodyH + FOOT);
    if (shown(NEEDS)) {
      low = Math.max(low, NAME_Y + Math.max(0, tagLines) * LINE_H + FOOT);
    }
    return Math.max(ROW_H, (float) Math.ceil(low));
  }

  public static NomadBuildPickerLayout of(float rowW) {
    float side = Math.min(SIDE_W, Math.max(0f, rowW));
    float left = rowW - side - GAP;
    boolean tag = left - (THUMB_W + GAP) - (NEEDS_W + GAP) >= NAME_MIN;
    if (tag) {
      left -= NEEDS_W + GAP;
    }
    float thumbW = Math.min(THUMB_W, left - GAP - NAME_MIN);
    if (thumbW < THUMB_MIN_W) {
      thumbW = 0f;
    }
    NomadBuildPickerLayout out = new NomadBuildPickerLayout(thumbW * (THUMB_H / THUMB_W));
    out.x[COST] = rowW - side;
    out.w[COST] = side;
    float buildW = Math.min(BUILD_W, side);
    out.x[BUILD] = rowW - buildW;
    out.w[BUILD] = buildW;
    out.x[NEEDS] = out.x[COST] - GAP - (tag ? NEEDS_W : 0f);
    out.w[NEEDS] = tag ? NEEDS_W : 0f;
    out.x[THUMB] = 0f;
    out.w[THUMB] = thumbW;
    out.x[NAME] = thumbW > 0f ? thumbW + GAP : 0f;
    out.w[NAME] = Math.max(0f, left - out.x[NAME]);
    return out;
  }

  public static final class Flows {

    public final float[] numX;
    public final float[] iconX;
    public final float[] y;
    public final int lines;
    public final float h;

    private Flows(int n, int lines) {
      this.numX = new float[n];
      this.iconX = new float[n];
      this.y = new float[n];
      this.lines = lines;
      this.h = lines == 0 ? 0f : lines * FLOW_LINE_H + (lines - 1) * FLOW_LINE_GAP;
    }
  }

  public static float flowWidth(String figure, NomadText.Measure m) {
    return NomadText.need(figure, m) + FLOW_NUM_PAD + ICON;
  }

  public static Flows flows(List<String> figures, float box, NomadText.Measure m) {
    int n = figures.size();
    int[] line = new int[n];
    float at = 0f;
    int lines = n == 0 ? 0 : 1;
    for (int i = 0; i < n; i++) {
      float fw = flowWidth(figures.get(i), m);
      float start = at == 0f ? 0f : at + FLOW_GAP;
      if (at > 0f && start + fw > box) {
        lines++;
        start = 0f;
      }
      line[i] = lines - 1;
      at = start + fw;
    }
    Flows out = new Flows(n, lines);
    at = 0f;
    int current = 0;
    for (int i = 0; i < n; i++) {
      if (line[i] != current) {
        current = line[i];
        at = 0f;
      }
      float start = at == 0f ? 0f : at + FLOW_GAP;
      out.numX[i] = start;
      out.iconX[i] = start + NomadText.need(figures.get(i), m) + FLOW_NUM_PAD;
      out.y[i] = line[i] * (FLOW_LINE_H + FLOW_LINE_GAP);
      at = out.iconX[i] + ICON;
    }
    return out;
  }

  public static float textHeight(String text, float box, NomadText.Measure m) {
    if (text == null || text.isEmpty()) {
      return 0f;
    }
    return NomadText.lines(text, box, m) * LINE_H;
  }

  public static final float GUTTER = 12f;
  public static final float HEAD_H = 18f;
  public static final float BACK_W = 120f;
  private static final float BAND_Y = 26f;
  private static final float NOTE_DROP = 4f;
  private static final float CAPTION_PAD = 8f;
  private static final float LIST_PAD = 5f;
  public static final float HEAD_GAP = 4f;
  public static final float GROUP_GAP = 6f;
  public static final int NOTE_MAX_LINES = 3;

  public static final class Panel {

    public final float rowW;
    public final NomadGrid.Box title;
    public final NomadGrid.Box note;
    public final NomadGrid.Box back;
    public final NomadGrid.Box captions;
    public final NomadGrid.Box list;

    private Panel(float rowW, NomadGrid.Box title, NomadGrid.Box note, NomadGrid.Box back,
        NomadGrid.Box captions, NomadGrid.Box list) {
      this.rowW = rowW;
      this.title = title;
      this.note = note;
      this.back = back;
      this.captions = captions;
      this.list = list;
    }
  }

  public static float noteBox(float w) {
    float rowW = Math.max(0f, w - GUTTER);
    return Math.max(0f, rowW - Math.min(BACK_W, rowW) - GAP);
  }

  public static Panel panel(float w, float h, int noteLines) {
    float rowW = Math.max(0f, w - GUTTER);
    int lines = Math.max(1, Math.min(NOTE_MAX_LINES, noteLines));
    float backW = Math.min(BACK_W, rowW);
    NomadGrid.Box title = new NomadGrid.Box(0f, 0f, rowW, HEAD_H);
    NomadGrid.Box back = new NomadGrid.Box(rowW - backW, BAND_Y, backW, BUTTON_H);
    float noteY = lines == 1 ? BAND_Y + NOTE_DROP : BAND_Y;
    NomadGrid.Box note = new NomadGrid.Box(0f, noteY, noteBox(w), lines * LINE_H);
    float captionY = Math.max(back.bottom(), note.bottom()) + CAPTION_PAD;
    NomadGrid.Box captions = new NomadGrid.Box(0f, captionY, rowW, LINE_H);
    float listY = captions.bottom() + LIST_PAD;
    NomadGrid.Box list = new NomadGrid.Box(0f, listY, Math.max(0f, w), Math.max(0f, h - listY));
    return new Panel(rowW, title, note, back, captions, list);
  }

  public static float contentHeight(float[][] rowHeights) {
    float total = 0f;
    for (int i = 0; i < rowHeights.length; i++) {
      total += (i == 0 ? 0f : GROUP_GAP) + HEAD_H + HEAD_GAP;
      for (float h : rowHeights[i]) {
        total += h;
      }
    }
    return total;
  }

  public static final float TIP_W = 450f;
  public static final float SUM_FIGURE_W = 70f;
  public static final float SUM_REQUIRES_W = 160f;

  public static float sumLabelWidth(float gridW) {
    return Math.max(0f, gridW - 2f * SUM_FIGURE_W - SUM_REQUIRES_W);
  }

  public static final class Sum {

    public final float spaceNow;
    public final float spaceAfter;
    public final float spaceRequired;
    public final float residentsNow;
    public final float residentsAfter;
    public final float residentsRequired;

    private Sum(BasicNeeds.Reading now, BasicNeeds.Reading after) {
      this.spaceNow = now.haveSpace;
      this.spaceAfter = after.haveSpace;
      this.spaceRequired = after.wantSpace;
      this.residentsNow = now.haveResidents;
      this.residentsAfter = after.haveResidents;
      this.residentsRequired = after.wantResidents;
    }
  }

  public static Sum sum(BasicNeeds.Reading now, BasicNeeds.Reading after) {
    return new Sum(now, after);
  }
}
