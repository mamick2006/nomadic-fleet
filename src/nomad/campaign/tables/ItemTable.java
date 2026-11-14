package nomad.campaign.tables;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import nomad.campaign.econ.ItemRow;
import nomad.campaign.econ.ItemTableText;
import nomad.campaign.econ.ModTables;
import nomad.campaign.econ.Work;

public final class ItemTable {

  private ItemTable() {
  }

  private static volatile boolean readWarned;
  private static volatile boolean columnsWarned;

  private static volatile Map<String, ItemRow> byId;
  private static volatile List<ItemRow> rowsCache;

  public static void onGameLoad() {
    rowsCache = null;
    byId = null;
  }

  public static ItemRow row(String id) {
    return id == null ? null : rows().get(id);
  }

  public static List<String> ids() {
    return Collections.unmodifiableList(new ArrayList<String>(rows().keySet()));
  }

  public static boolean isEmpty() {
    return rows().isEmpty();
  }

  public static List<ItemRow> read() {
    List<ItemRow> held = rowsCache;
    if (held != null) {
      return held;
    }
    List<ItemRow> rows = readRows();
    Map<String, ItemRow> index = new LinkedHashMap<String, ItemRow>();
    for (ItemRow row : rows) {
      index.put(row.id, row);
    }
    byId = Collections.unmodifiableMap(index);
    rowsCache = Collections.unmodifiableList(rows);
    return rowsCache;
  }

  private static Map<String, ItemRow> rows() {
    Map<String, ItemRow> cached = byId;
    if (cached == null) {
      read();
      cached = byId;
    }
    return cached;
  }

  private static List<ItemRow> readRows() {
    List<String> problems = new ArrayList<String>();
    List<ModTables.Row> merged = MergedTable.read(ItemTableText.PATH, Work.MOD_ID, problems);
    List<ItemRow> rows = new ArrayList<ItemRow>();
    Map<String, ItemRow> byId = new LinkedHashMap<String, ItemRow>();
    for (ModTables.Row row : merged) {
      ItemRow parsed = ItemRow.fromColumns(row.cells, row.sourceMod, row.own, problems);
      if (parsed != null) {
        byId.put(parsed.id, parsed);
      }
    }
    rows.addAll(byId.values());
    warnUnreadColumns(merged);
    if (rows.isEmpty()) {
      warnOnce("could not read any colony-items table; this mod's own item roster is in use and no"
          + " item from another mod is available this session", problems);
      return rows;
    }
    if (!problems.isEmpty()) {
      warnOnce(problems.size() + " colony-item row(s) had problems", problems);
    }
    return rows;
  }

  private static void warnUnreadColumns(List<ModTables.Row> merged) {
    if (columnsWarned) {
      return;
    }
    List<String> notes = MergedTable.unreadColumnNotes(merged, ItemRow.COLUMNS, ItemTableText.PATH);
    if (notes.isEmpty()) {
      return;
    }
    columnsWarned = true;
    MergedTable.warn(ItemTable.class, notes.size() + " colony-item column(s) had no effect", notes);
  }

  private static void warnOnce(String what, List<String> problems) {
    if (readWarned) {
      return;
    }
    readWarned = true;
    MergedTable.warn(ItemTable.class, what, problems);
  }
}
