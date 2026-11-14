package nomad.campaign.econ;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public final class ItemRow {

  public static final List<String> COLUMNS = Arrays.asList("id", "effect", "efficiency", "plugin");

  public final String id;
  public final String effect;
  public final float efficiency;
  public final String plugin;
  public final String sourceMod;
  public final boolean ownRow;

  private ItemRow(String id, String effect, float efficiency, String plugin, String sourceMod,
      boolean ownRow) {
    this.id = id;
    this.effect = effect;
    this.efficiency = efficiency;
    this.plugin = plugin;
    this.sourceMod = sourceMod;
    this.ownRow = ownRow;
  }

  public static ItemRow fromColumns(Map<String, String> cells, String sourceMod, boolean ownRow,
      List<String> problems) {
    String id = str(cells, "id");
    if (id.isEmpty() || id.startsWith("#")) {
      return null;
    }
    String where = id + (sourceMod == null || sourceMod.isEmpty() ? "" : " from " + sourceMod);
    return new ItemRow(id, str(cells, "effect"), num(cells, "efficiency", where, problems),
        str(cells, "plugin"), sourceMod, ownRow);
  }

  private static String str(Map<String, String> cells, String column) {
    String value = cells.get(column);
    return value == null ? "" : value.trim();
  }

  private static float num(Map<String, String> cells, String column, String where,
      List<String> problems) {
    String value = str(cells, column);
    if (value.isEmpty()) {
      return 0f;
    }
    try {
      return Float.parseFloat(value);
    } catch (NumberFormatException e) {
      problems.add(where + ": " + column + " '" + value + "' is not a number");
      return 0f;
    }
  }
}
