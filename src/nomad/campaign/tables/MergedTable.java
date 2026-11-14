package nomad.campaign.tables;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModSpecAPI;
import com.fs.starfarer.api.SettingsAPI;

import nomad.campaign.econ.ModTables;

public final class MergedTable {

  private MergedTable() {
  }

  private static final String SOURCE_COLUMN = "fs_rowSource";
  private static final String NO_MASTER = "<none>";
  private static final int MAX_REASON = 200;

  public static List<ModTables.Row> read(String path, String ownModId, List<String> problems) {
    List<ModTables.Copy> copies = new ArrayList<ModTables.Copy>();
    try {
      copies(path, ownModId, copies);
    } catch (Throwable t) {
      problems.add("reading " + path + " stopped early (" + reason(t) + ")");
    }
    return ModTables.rows(copies, path, problems);
  }

  public static List<String> shippers(String path) {
    List<String> out = new ArrayList<String>();
    try {
      List<ModSpecAPI> found = discover(path, enabledMods());
      if (found != null) {
        for (ModSpecAPI mod : found) {
          out.add(mod.getDirName());
        }
      }
    } catch (Throwable ignore) {
    }
    return out;
  }

  private static void copies(String path, String ownModId, List<ModTables.Copy> out) {
    final List<ModSpecAPI> enabled = enabledMods();
    ModSpecAPI own = null;
    for (ModSpecAPI mod : enabled) {
      if (ownModId.equals(mod.getId())) {
        own = mod;
      }
    }
    if (own == null) {
      out.add(ModTables.Copy.unreadable(ownModId, true, "the game does not list this mod as"
          + " enabled"));
    } else {
      out.add(copy(path, own, true, true, enabled));
    }

    List<ModSpecAPI> found = discover(path, enabled);
    boolean known = found != null;
    List<ModSpecAPI> others = new ArrayList<ModSpecAPI>(known ? found : enabled);
    Collections.sort(others, new Comparator<ModSpecAPI>() {
      @Override
      public int compare(ModSpecAPI a, ModSpecAPI b) {
        return String.valueOf(a.getId()).compareTo(String.valueOf(b.getId()));
      }
    });
    for (ModSpecAPI mod : others) {
      if (mod == own) {
        continue;
      }
      if (!known && text(path, mod.getId()) == null) {
        continue;
      }
      ModTables.Copy copy = copy(path, mod, false, known, enabled);
      if (copy != null) {
        out.add(copy);
      }
    }
  }

  private static List<ModSpecAPI> discover(String path, List<ModSpecAPI> enabled) {
    JSONArray files;
    try {
      files = Global.getSettings().getMergedSpreadsheetDataForMod(SOURCE_COLUMN, path, NO_MASTER);
    } catch (Throwable t) {
      return null;
    }
    List<ModSpecAPI> found = new ArrayList<ModSpecAPI>();
    for (int i = 0; i < files.length(); i++) {
      JSONObject row = files.optJSONObject(i);
      String source = row == null ? "" : row.optString(SOURCE_COLUMN, "");
      for (ModSpecAPI mod : enabled) {
        if (source.equals(mod.getPath() + "/" + path) && !found.contains(mod)) {
          found.add(mod);
        }
      }
    }
    return found;
  }

  private static ModTables.Copy copy(String path, ModSpecAPI mod, boolean own, boolean ships,
      List<ModSpecAPI> enabled) {
    String dir = mod.getDirName();
    Map<String, String> longer = null;
    for (ModSpecAPI other : enabled) {
      String name = other.getDirName();
      if (other != mod && name != null && dir != null && !name.equals(dir) && name.endsWith(dir)) {
        String theirs = text(path, other.getId());
        if (theirs != null) {
          if (longer == null) {
            longer = new LinkedHashMap<String, String>();
          }
          longer.put(name, theirs);
        }
      }
    }
    if (longer != null) {
      String by = ModTables.shadowedBy(dir, text(path, mod.getId()), longer);
      if (by != null) {
        return shadowed(path, mod, own, ships, by);
      }
    }
    try {
      return ModTables.Copy.of(dir, own, maps(Global.getSettings().loadCSV(path, mod.getId())));
    } catch (Throwable t) {
      return ModTables.Copy.unreadable(dir, own, reason(t));
    }
  }

  private static ModTables.Copy shadowed(String path, ModSpecAPI mod, boolean own, boolean ships,
      String by) {
    try {
      JSONArray merged = Global.getSettings().getMergedSpreadsheetDataForMod("id", path, NO_MASTER);
      String file = mod.getPath() + "/" + path;
      List<Map<String, String>> rows = new ArrayList<Map<String, String>>();
      for (int i = 0; i < merged.length(); i++) {
        JSONObject row = merged.getJSONObject(i);
        if (file.equals(row.optString(SOURCE_COLUMN, ""))) {
          rows.add(cells(row));
        }
      }
      if (!rows.isEmpty() || !ships) {
        return rows.isEmpty() ? null : ModTables.Copy.of(mod.getDirName(), own, rows);
      }
    } catch (Throwable ignore) {
    }
    if (!ships) {
      return null;
    }
    return ModTables.Copy.unreadable(mod.getDirName(), own, "the game cannot tell it from " + by
        + "'s copy, because that folder's name ends with this one's. Rename one of the two"
        + " folders");
  }

  private static String text(String path, String modId) {
    try {
      return Global.getSettings().loadText(path, modId);
    } catch (Throwable absent) {
      return null;
    }
  }

  private static List<ModSpecAPI> enabledMods() {
    SettingsAPI settings = Global.getSettings();
    List<ModSpecAPI> enabled = settings.getModManager().getEnabledModsCopy();
    return enabled == null ? new ArrayList<ModSpecAPI>() : enabled;
  }

  private static List<Map<String, String>> maps(JSONArray table) throws Exception {
    List<Map<String, String>> rows = new ArrayList<Map<String, String>>();
    for (int i = 0; i < table.length(); i++) {
      rows.add(cells(table.getJSONObject(i)));
    }
    return rows;
  }

  private static Map<String, String> cells(JSONObject json) {
    Map<String, String> cells = new LinkedHashMap<String, String>();
    for (java.util.Iterator<?> it = json.keys(); it.hasNext();) {
      String key = String.valueOf(it.next());
      cells.put(key, json.optString(key, ""));
    }
    return cells;
  }

  private static String reason(Throwable t) {
    String text = String.valueOf(t);
    return text.length() <= MAX_REASON ? text : text.substring(0, MAX_REASON) + "...";
  }

  public static List<String> unreadColumnNotes(List<ModTables.Row> rows, List<String> known,
      String path) {
    Set<String> seen = new LinkedHashSet<String>();
    List<String> notes = new ArrayList<String>();
    for (ModTables.Row row : rows) {
      for (Map.Entry<String, String> cell : row.cells.entrySet()) {
        String column = cell.getKey();
        if (column == null || column.isEmpty() || column.startsWith("fs_")
            || known.contains(column)) {
          continue;
        }
        if (cell.getValue() == null || cell.getValue().trim().isEmpty()) {
          continue;
        }
        String mod = row.sourceMod.isEmpty() ? "an unnamed mod" : row.sourceMod;
        if (seen.add(mod + "\n" + column)) {
          notes.add(mod + " ships column '" + column + "' in " + path
              + ", which this version does not read, so it had no effect");
        }
      }
    }
    return notes;
  }

  private static final int MAX_LINES = 20;

  public static void warn(Class<?> owner, String what, List<String> problems) {
    org.apache.log4j.Logger log = Global.getLogger(owner);
    log.warn("Nomad Flotilla: " + what + ".");
    for (int i = 0; i < problems.size() && i < MAX_LINES; i++) {
      log.warn("Nomad Flotilla:   " + problems.get(i) + ".");
    }
    if (problems.size() > MAX_LINES) {
      log.warn("Nomad Flotilla:   and " + (problems.size() - MAX_LINES) + " more.");
    }
  }
}
