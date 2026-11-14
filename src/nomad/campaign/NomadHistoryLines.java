package nomad.campaign;

import java.util.ArrayList;
import java.util.List;

public final class NomadHistoryLines {

  private NomadHistoryLines() {
  }

  public static final char DIGEST = (char) 0xE000;

  private static final String DATE_END = ": ";

  public static String digest(String line) {
    return DIGEST + line;
  }

  public static boolean isDigest(String line) {
    return line != null && !line.isEmpty() && line.charAt(0) == DIGEST;
  }

  public static boolean hasUnheadedLines(List<String> history) {
    return history != null && !history.isEmpty() && !isDigest(history.get(0));
  }

  public enum Kind {
    NOW,
    MONTH,
    ROW
  }

  public static final class Entry {

    public final Kind kind;
    public final String left;
    public final String right;

    Entry(Kind kind, String left, String right) {
      this.kind = kind;
      this.left = left;
      this.right = right;
    }
  }

  public static List<Entry> entries(List<String> history) {
    List<Entry> out = new ArrayList<Entry>();
    if (history == null) {
      return out;
    }
    for (String line : history) {
      if (line == null || line.isEmpty()) {
        continue;
      }
      if (isDigest(line)) {
        String text = line.substring(1);
        int cut = text.indexOf(DATE_END);
        out.add(cut < 0 ? new Entry(Kind.MONTH, text, "")
            : new Entry(Kind.MONTH, text.substring(0, cut),
                text.substring(cut + DATE_END.length())));
        continue;
      }
      if (out.isEmpty()) {
        out.add(new Entry(Kind.NOW, "", ""));
      }
      out.add(new Entry(Kind.ROW, line, ""));
    }
    return out;
  }
}
