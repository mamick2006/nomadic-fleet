package nomad.ui;

public final class NomadColumns {

  private NomadColumns() {
  }

  public enum Align {
    LEFT, MID, RIGHT
  }

  public static final class Column {
    final String header;
    final Align align;
    final float fixedW;
    final float minW;
    final boolean remainder;
    final float leadGap;

    private Column(String header, Align align, float fixedW, float minW, boolean remainder, float leadGap) {
      this.header = header;
      this.align = align;
      this.fixedW = fixedW;
      this.minW = minW;
      this.remainder = remainder;
      this.leadGap = leadGap;
    }

    public static Column fixed(String header, float w, Align a) {
      return new Column(header, a, w, 0f, false, -1f);
    }

    public static Column remainder(String header, float minW, Align a) {
      return new Column(header, a, 0f, minW, true, -1f);
    }

    public Column gap(float g) {
      return new Column(header, align, fixedW, minW, remainder, g);
    }
  }

  public static final class Layout {
    private final float[] x;
    private final float[] w;
    private final String[] header;
    private final Align[] align;

    private Layout(float[] x, float[] w, String[] header, Align[] align) {
      this.x = x;
      this.w = w;
      this.header = header;
      this.align = align;
    }

    public int count() {
      return x.length;
    }

    public float x(int col) {
      return x[col];
    }

    public float w(int col) {
      return w[col];
    }

    public String header(int col) {
      return header[col];
    }

    public Align align(int col) {
      return align[col];
    }
  }

  public static Layout solve(float leftInset, float usableW, float gap, Column... cols) {
    int n = cols.length;
    float[] leadGap = new float[n];
    float fixedSum = 0f;
    float gapSum = 0f;
    int remIdx = -1;
    for (int i = 0; i < n; i++) {
      leadGap[i] = i == 0 ? 0f : (cols[i].leadGap >= 0f ? cols[i].leadGap : gap);
      gapSum += leadGap[i];
      if (cols[i].remainder) {
        remIdx = i;
      } else {
        fixedSum += cols[i].fixedW;
      }
    }
    float leftover = Math.max(0f, usableW - gapSum - fixedSum);

    float[] xs = new float[n];
    float[] ws = new float[n];
    String[] hs = new String[n];
    Align[] al = new Align[n];
    float cursor = leftInset;
    for (int i = 0; i < n; i++) {
      cursor += leadGap[i];
      xs[i] = cursor;
      if (!cols[i].remainder) {
        ws[i] = cols[i].fixedW;
      } else if (i == remIdx) {
        ws[i] = Math.max(cols[i].minW, leftover);
      }
      hs[i] = cols[i].header;
      al[i] = cols[i].align;
      cursor += ws[i];
    }
    return new Layout(xs, ws, hs, al);
  }
}
