package nomad.ui;

public final class NomadReadingLayout {

  private NomadReadingLayout() {
  }

  public static final float INSET = NomadGrid.Screen.INSET;
  public static final float MANUAL_SECTION_W = 700f;
  public static final float MANUAL_GAP = 20f;
  public static final float MANUAL_SECTION_MIN_W = 200f;
  public static final float HISTORY_W = 800f;
  public static final float PICTURE_ASPECT = 500f / 1600f;
  public static final float PICTURE_GAP = 10f;
  public static final float TEXT_MIN_H = 320f;
  public static final float SCROLLER_INSET = 5f;
  public static final float SCROLLBAR = 5f;

  public static float pictureH(float w, float bodyH) {
    float whole = Math.round(Math.max(0f, w) * PICTURE_ASPECT);
    return bodyH - whole - PICTURE_GAP >= TEXT_MIN_H ? whole : 0f;
  }

  public static final class Column {

    public final float x;
    public final float w;
    public final float pictureH;
    public final float scrollX;
    public final float scrollW;

    Column(float x, float w, float bodyW, float bodyH, float scrollX) {
      this.x = x;
      this.w = w;
      this.pictureH = NomadReadingLayout.pictureH(w, bodyH);
      this.scrollX = scrollX;
      this.scrollW = Math.max(0f, bodyW - scrollX);
    }

    public float right() {
      return x + w;
    }

    public float holderW() {
      return Math.max(0f, scrollW - SCROLLER_INSET - SCROLLBAR);
    }

    public float holderX() {
      return x - scrollX - 2f * SCROLLER_INSET;
    }
  }

  public static final class Manual {

    public final float indexX;
    public final float indexW;
    public final Column section;

    Manual(float indexX, float indexW, Column section) {
      this.indexX = indexX;
      this.indexW = indexW;
      this.section = section;
    }

    public float right() {
      return section.right();
    }
  }

  public static Manual manual(float bodyW, float bodyH, float indexW) {
    float avail = Math.max(0f, bodyW - 2f * INSET);
    float lead = indexW > 0f ? indexW + MANUAL_GAP : 0f;
    float sectionW = Math.max(MANUAL_SECTION_MIN_W,
        Math.min(MANUAL_SECTION_W, (float) Math.floor(avail - lead)));
    float left = INSET + (float) Math.floor(Math.max(0f, avail - lead - sectionW) / 2f);
    float sectionX = left + lead;
    return new Manual(left, Math.max(0f, indexW),
        new Column(sectionX, sectionW, bodyW, bodyH, sectionX - 2f * SCROLLER_INSET));
  }

  public static Column history(float bodyW, float bodyH) {
    float avail = Math.max(0f, bodyW - 2f * INSET);
    float w = Math.min(HISTORY_W, (float) Math.floor(avail));
    return new Column(INSET + (float) Math.floor((avail - w) / 2f), w, bodyW, bodyH, 0f);
  }
}
