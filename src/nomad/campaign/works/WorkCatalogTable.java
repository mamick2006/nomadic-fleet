package nomad.campaign.works;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import nomad.campaign.econ.ModTables;
import nomad.campaign.econ.model.WorkCatalog;
import nomad.campaign.econ.model.WorkRows;
import nomad.campaign.econ.model.WorkSize;
import nomad.campaign.econ.model.Works;
import nomad.campaign.tables.MergedTable;

public final class WorkCatalogTable {

  private WorkCatalogTable() {
  }

  private static final String[] RETIRED = {
      "data/config/nomad/nomad_works.csv", "data/config/nomad/nomad_allocations.csv"};

  private static volatile boolean warned;
  private static volatile boolean columnsWarned;
  private static volatile boolean retiredChecked;

  private static final class Loaded {
    final WorkCatalog catalog;
    final Set<String> named;

    Loaded(WorkCatalog catalog, Set<String> named) {
      this.catalog = catalog;
      this.named = named;
    }
  }

  private static volatile Loaded cached;

  public static void onGameLoad() {
    cached = null;
  }

  public static WorkCatalog read() {
    return loaded().catalog;
  }

  public static Set<String> named() {
    return loaded().named;
  }

  private static Loaded loaded() {
    Loaded held = cached;
    if (held != null) {
      return held;
    }
    Loaded built = build();
    cached = built;
    return built;
  }

  private static Loaded build() {
    List<String> problems = new ArrayList<String>();
    try {
      List<ModTables.Row> rows = MergedTable.read(WorkCatalog.PATH, Works.MOD_ID, problems);
      WorkRows.Result settled = WorkRows.read(rows, problems);
      warnUnreadColumns(rows);
      warnRetired();
      WorkCatalog catalog = WorkCatalog.build(settled.rows, problems);
      if (catalog.isEmpty()) {
        warnOnce("could not read any Works table, so no Work can be built this session. Works"
            + " already built are kept in the save", problems);
      } else if (!problems.isEmpty()) {
        warnOnce(problems.size() + " Works table problem(s); every other row is in use", problems);
      }
      return new Loaded(catalog, settled.named);
    } catch (RuntimeException e) {
      problems.add(String.valueOf(e));
      warnOnce("could not read the Works table, so no Work can be built this session. Works"
          + " already built are kept in the save", problems);
      return new Loaded(WorkCatalog.EMPTY, Collections.<String>emptySet());
    }
  }

  private static void warnUnreadColumns(List<ModTables.Row> rows) {
    if (columnsWarned) {
      return;
    }
    List<String> notes = MergedTable.unreadColumnNotes(rows, WorkSize.COLUMNS, WorkCatalog.PATH);
    if (notes.isEmpty()) {
      return;
    }
    columnsWarned = true;
    MergedTable.warn(WorkCatalogTable.class, notes.size() + " Works column(s) had no effect", notes);
  }

  private static void warnRetired() {
    if (retiredChecked) {
      return;
    }
    retiredChecked = true;
    List<String> notes = new ArrayList<String>();
    for (String path : RETIRED) {
      for (String mod : MergedTable.shippers(path)) {
        notes.add(mod + " ships " + path + ", which Nomad Flotilla 2.0 does not read, so what it"
            + " adds is missing. That mod needs an update for 2.0");
      }
    }
    if (!notes.isEmpty()) {
      MergedTable.warn(WorkCatalogTable.class, notes.size() + " add-on table(s) from before 2.0"
          + " were not read (see \"Removed in 2.0\" in MODDING.md)", notes);
    }
  }

  private static void warnOnce(String what, List<String> problems) {
    if (warned) {
      return;
    }
    warned = true;
    MergedTable.warn(WorkCatalogTable.class, what, problems);
  }
}
