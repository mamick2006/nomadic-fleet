package nomad;

import com.fs.starfarer.api.Global;

public final class NomadStrings {

  private NomadStrings() {
  }

  public static String get(String category, String key) {
    String s = Global.getSettings().getString(category, key);
    if (s == null) {
      throw new IllegalStateException("Missing strings.json entry " + category + ":" + key);
    }
    return s;
  }

  public static String get(String category, String key, Object... args) {
    return format(get(category, key), args);
  }

  public static String format(String template, Object... args) {
    StringBuilder sb = new StringBuilder(template);
    int from = 0;
    for (Object arg : args) {
      int at = sb.indexOf("%s", from);
      if (at < 0) {
        throw new IllegalStateException("Template has fewer %s slots than arguments: " + template);
      }
      String s = String.valueOf(arg);
      sb.replace(at, at + 2, s);
      from = at + s.length();
    }
    return sb.toString();
  }
}
