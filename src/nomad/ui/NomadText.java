package nomad.ui;

public final class NomadText {

  private NomadText() {
  }

  public interface Measure {
    float width(String s);
  }

  public static final float INSET = 5f;
  public static final float MARGIN = 1f;

  private static final String DOTS = "...";

  public static float element(float box) {
    return box + 2f * INSET;
  }

  public static float elementX(float boxX) {
    return boxX - INSET;
  }

  public static float box(float elementW) {
    return elementW - 2f * INSET;
  }

  public static float need(String s, Measure m) {
    return s == null || s.isEmpty() ? 0f : m.width(s) + MARGIN;
  }

  public static float need(Measure m, String... strings) {
    float widest = 0f;
    for (String s : strings) {
      widest = Math.max(widest, need(s, m));
    }
    return widest;
  }

  public static boolean fits(String s, float box, Measure m) {
    return s == null || s.isEmpty() || m.width(s) <= box;
  }

  public static boolean breaksWord(String s, float box, Measure m) {
    if (s == null) {
      return false;
    }
    for (String word : s.split("[ \n]+")) {
      if (!word.isEmpty() && m.width(word) > box) {
        return true;
      }
    }
    return false;
  }

  public static int lines(String s, float box, Measure m) {
    if (s == null || s.isEmpty()) {
      return 1;
    }
    int lines = 0;
    for (String para : s.split("\n", -1)) {
      lines += paraLines(para, box, m);
    }
    return lines;
  }

  private static int paraLines(String para, float box, Measure m) {
    int lines = 1;
    String line = "";
    for (String word : para.trim().split(" +")) {
      String tried = line.isEmpty() ? word : line + " " + word;
      if (m.width(tried) <= box) {
        line = tried;
        continue;
      }
      if (!line.isEmpty()) {
        lines++;
      }
      line = word;
      while (line.length() > 1 && m.width(line) > box) {
        int n = line.length() - 1;
        while (n > 1 && m.width(line.substring(0, n)) > box) {
          n--;
        }
        line = line.substring(n);
        lines++;
      }
    }
    return lines;
  }

  public static String fit(String s, float box, Measure m) {
    if (fits(s, box, m)) {
      return s;
    }
    int n = s.length();
    while (n > 1 && m.width(s.substring(0, n).trim() + DOTS) > box) {
      n--;
    }
    return s.substring(0, n).trim() + DOTS;
  }

  public static String fitLines(String s, float box, int max, Measure m) {
    if (s == null || max <= 1) {
      return fit(s, box, m);
    }
    if (lines(s, box, m) <= max && !breaksWord(s, box, m)) {
      return s;
    }
    int n = s.length();
    while (n > 1) {
      String cut = s.substring(0, n).trim() + DOTS;
      if (lines(cut, box, m) <= max && !breaksWord(cut, box, m)) {
        return cut;
      }
      n--;
    }
    return fit(s, box, m);
  }
}
