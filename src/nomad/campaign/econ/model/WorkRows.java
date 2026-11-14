package nomad.campaign.econ.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import nomad.campaign.econ.Commod;
import nomad.campaign.econ.ModTables;

public final class WorkRows {

  private WorkRows() {
  }

  public static final class Result {

    public final List<WorkSize> rows;
    public final Set<String> named;

    Result(List<WorkSize> rows, Set<String> named) {
      this.rows = Collections.unmodifiableList(rows);
      this.named = Collections.unmodifiableSet(named);
    }
  }

  public static Result read(List<ModTables.Row> rows, List<String> problems) {
    Set<String> named = new LinkedHashSet<String>();
    Map<String, WorkSize> byRowId = new LinkedHashMap<String, WorkSize>();
    boolean anyOwn = false;
    if (rows != null) {
      for (ModTables.Row raw : rows) {
        anyOwn |= raw.own;
        String work = raw.cells.get("work");
        if (work != null && !work.trim().isEmpty()) {
          named.add(work.trim());
        }
        WorkSize row = WorkSize.fromColumns(raw.cells, raw.sourceMod, raw.own, problems);
        if (row == null || !usable(row, problems)) {
          continue;
        }
        WorkSize held = byRowId.get(row.rowId);
        if (held != null && !(held.work.equals(row.work) && held.size == row.size)) {
          problems.add(where(row) + ": this id already belongs to the row for " + held.work
              + " size " + held.size + owner(held) + ", so the row is ignored. A row that reuses"
              + " an id must keep that row's work and size; give this one an id of its own");
          continue;
        }
        if (held != null && held.ownRow && !row.ownRow) {
          row = WorkSize.fromColumns(raw.cells, raw.sourceMod, true, new ArrayList<String>());
        }
        byRowId.put(row.rowId, row);
      }
    }
    if (!anyOwn) {
      problems.add("none of this mod's own Works rows could be read, so no Work is available"
          + " until that is fixed");
      return new Result(new ArrayList<WorkSize>(), named);
    }

    Map<String, Map<Integer, WorkSize>> byWork = new LinkedHashMap<String, Map<Integer, WorkSize>>();
    for (WorkSize row : byRowId.values()) {
      Map<Integer, WorkSize> sizes = byWork.get(row.work);
      if (sizes == null) {
        sizes = new LinkedHashMap<Integer, WorkSize>();
        byWork.put(row.work, sizes);
      }
      WorkSize held = sizes.get(Integer.valueOf(row.size));
      if (held != null) {
        problems.add(where(row) + ": " + row.work + " already has a size " + row.size + " row ("
            + held.rowId + owner(held) + "), so this one is ignored. To change that size, reuse"
            + " its row id");
        continue;
      }
      sizes.put(Integer.valueOf(row.size), row);
    }

    List<WorkSize> out = new ArrayList<WorkSize>();
    for (Map.Entry<String, Map<Integer, WorkSize>> entry : byWork.entrySet()) {
      Map<Integer, WorkSize> sizes = entry.getValue();
      int top = 0;
      while (sizes.containsKey(Integer.valueOf(top + 1))) {
        top++;
      }
      for (WorkSize row : sizes.values()) {
        if (row.size > top) {
          problems.add(where(row) + ": " + row.work + " has no usable size " + (top + 1)
              + " row, so " + (top == 0 ? "the Work is left out" : "its size " + row.size
              + " is ignored"));
        }
      }
      for (int n = 1; n <= top; n++) {
        out.add(sizes.get(Integer.valueOf(n)));
      }
    }
    return new Result(out, named);
  }

  private static boolean usable(WorkSize row, List<String> problems) {
    if (!ordinary(row.space) || !ordinary(row.staff) || !ordinary(row.cost)) {
      problems.add(where(row) + ": space, staff and cost must be ordinary numbers, so the row is"
          + " ignored");
      return false;
    }
    for (String good : row.makes.keySet()) {
      if (!Commod.STORED.contains(good) && !Works.POINTS.equals(good)
          && !Works.CREDITS.equals(good) && !Works.RESIDENTS.equals(good)
          && !Works.MARINES.equals(good)) {
        problems.add(where(row) + ": makes names '" + good + "', which the Flotilla does not"
            + " store, so the row is ignored. It stores " + Commod.STORED + ", and a makes cell"
            + " may also name " + Works.CREDITS + ", " + Works.RESIDENTS + " or " + Works.MARINES);
        return false;
      }
    }
    for (String good : row.uses.keySet()) {
      if (!Commod.STORED.contains(good) && !Works.RESIDENTS.equals(good)) {
        problems.add(where(row) + ": uses names '" + good + "', which the Flotilla does not"
            + " store, so the row is ignored. It stores " + Commod.STORED + ", and a uses cell"
            + " may also name " + Works.RESIDENTS);
        return false;
      }
    }
    if (row.size == 1) {
      if (!WorkDef.CATEGORIES.contains(row.category)) {
        problems.add(where(row) + ": category '" + row.category + "' is not one of "
            + WorkDef.CATEGORIES + ", so the row is ignored");
        return false;
      }
      if (!row.scope.isEmpty() && !WorkDef.SCOPE_FLEET.equals(row.scope)
          && !WorkDef.SCOPE_FLOTILLA.equals(row.scope)) {
        problems.add(where(row) + ": scope '" + row.scope + "' is neither " + WorkDef.SCOPE_FLEET
            + " nor " + WorkDef.SCOPE_FLOTILLA + ", so the row is ignored");
        return false;
      }
    }
    return true;
  }

  private static boolean ordinary(float value) {
    return !Float.isNaN(value) && !Float.isInfinite(value);
  }

  private static String where(WorkSize row) {
    return row.rowId + (row.sourceMod.isEmpty() ? "" : " from " + row.sourceMod);
  }

  private static String owner(WorkSize row) {
    if (row.ownRow) {
      return ", one of Nomad Flotilla's own rows";
    }
    return row.sourceMod.isEmpty() ? "" : " from " + row.sourceMod;
  }
}
