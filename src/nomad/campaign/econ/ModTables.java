package nomad.campaign.econ;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ModTables {

  private ModTables() {
  }

  public static final class Copy {

    public final String mod;
    public final boolean own;
    public final List<Map<String, String>> rows;
    public final String error;

    private Copy(String mod, boolean own, List<Map<String, String>> rows, String error) {
      this.mod = mod == null ? "" : mod;
      this.own = own;
      this.rows = rows;
      this.error = error;
    }

    public static Copy of(String mod, boolean own, List<Map<String, String>> rows) {
      return new Copy(mod, own,
          rows == null ? Collections.<Map<String, String>>emptyList() : rows, null);
    }

    public static Copy unreadable(String mod, boolean own, String why) {
      return new Copy(mod, own, Collections.<Map<String, String>>emptyList(),
          why == null ? "no reason given" : why);
    }
  }

  public static final class Row {

    public final Map<String, String> cells;
    public final String sourceMod;
    public final boolean own;

    public Row(Map<String, String> cells, String sourceMod, boolean own) {
      this.cells = cells;
      this.sourceMod = sourceMod == null ? "" : sourceMod;
      this.own = own;
    }

    public String id() {
      String id = cells.get("id");
      return id == null ? "" : id.trim();
    }
  }

  public static List<Row> rows(List<Copy> copies, String path, List<String> problems) {
    List<Row> out = new ArrayList<Row>();
    if (copies == null) {
      return out;
    }
    for (int pass = 0; pass < 2; pass++) {
      for (Copy copy : copies) {
        if (copy != null && copy.own == (pass == 0)) {
          take(copy, path, out, problems);
        }
      }
    }
    return out;
  }

  private static void take(Copy copy, String path, List<Row> out, List<String> problems) {
    String whose = copy.own ? "this mod's own copy of " + path
        : copy.mod + "'s copy of " + path;
    if (copy.error != null) {
      problems.add(whose + " could not be read (" + copy.error + "), so none of its rows are in"
          + " use");
      return;
    }
    boolean anyId = false;
    for (Map<String, String> cells : copy.rows) {
      if (cells != null && cells.containsKey("id")) {
        anyId = true;
        break;
      }
    }
    if (!anyId) {
      if (!copy.rows.isEmpty()) {
        problems.add(whose + " has no id column, so none of its " + copy.rows.size()
            + " row(s) are in use");
      }
      return;
    }
    for (Map<String, String> cells : copy.rows) {
      if (cells == null) {
        continue;
      }
      Row row = new Row(cells, copy.mod, copy.own);
      String id = row.id();
      if (!id.isEmpty() && !id.startsWith("#")) {
        out.add(row);
      }
    }
  }

  public static List<Row> latestById(List<Row> rows) {
    Map<String, Row> byId = new LinkedHashMap<String, Row>();
    for (Row row : rows) {
      byId.put(row.id(), row);
    }
    return new ArrayList<Row>(byId.values());
  }

  public static String shadowedBy(String mod, String text, Map<String, String> textByMod) {
    if (mod == null || text == null || textByMod == null) {
      return null;
    }
    for (Map.Entry<String, String> other : textByMod.entrySet()) {
      String name = other.getKey();
      if (name != null && !name.equals(mod) && name.endsWith(mod)
          && text.equals(other.getValue())) {
        return name;
      }
    }
    return null;
  }
}
