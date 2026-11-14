package nomad.ui;

import java.util.Map;

public final class NomadManualText {

  private NomadManualText() {
  }

  public static String fill(String text, Map<String, String> fills) {
    if (text == null || text.indexOf('{') < 0) {
      return text;
    }
    StringBuilder out = new StringBuilder(text.length());
    int n = text.length();
    int i = 0;
    while (i < n) {
      int open = text.indexOf('{', i);
      if (open < 0) {
        out.append(text, i, n);
        break;
      }
      int nameAt = open;
      while (nameAt < n && text.charAt(nameAt) == '{') {
        nameAt++;
      }
      int nameEnd = nameAt;
      while (nameEnd < n && isNameChar(text.charAt(nameEnd))) {
        nameEnd++;
      }
      int closeEnd = nameEnd;
      while (closeEnd < n && text.charAt(closeEnd) == '}') {
        closeEnd++;
      }
      int opens = nameAt - open;
      int closes = closeEnd - nameEnd;
      String value = nameEnd > nameAt && opens % 2 == 1 && closes % 2 == 1
          ? fills.get(text.substring(nameAt, nameEnd)) : null;
      if (value == null) {
        out.append(text, i, nameAt);
        i = nameAt;
        continue;
      }
      out.append(text, i, nameAt - 1).append(value);
      i = nameEnd + 1;
    }
    return out.toString();
  }

  private static boolean isNameChar(char c) {
    return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_';
  }
}
