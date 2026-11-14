package nomad.campaign.works;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fs.starfarer.api.Global;

import nomad.campaign.econ.ModTables;
import nomad.campaign.econ.model.Prereq;
import nomad.campaign.econ.model.Prereqs;
import nomad.campaign.econ.model.Works;
import nomad.campaign.tables.ItemTable;
import nomad.campaign.tables.MergedTable;

public final class WorkPrereqTable {

  private WorkPrereqTable() {
  }

  private static volatile boolean warned;
  private static volatile boolean columnsWarned;

  private static volatile List<Prereq> cached;

  public static void onGameLoad() {
    cached = null;
  }

  public static List<Prereq> read() {
    List<Prereq> held = cached;
    if (held != null) {
      return held;
    }
    List<Prereq> built = build();
    cached = built;
    return built;
  }

  public static Prereq row(String id) {
    if (id == null) {
      return null;
    }
    for (Prereq p : read()) {
      if (id.equals(p.id)) {
        return p;
      }
    }
    return null;
  }

  private static List<Prereq> build() {
    List<String> problems = new ArrayList<String>();
    try {
      List<ModTables.Row> rows = MergedTable.read(Prereqs.PATH, Works.MOD_ID, problems);
      List<Prereq> out = Collections.unmodifiableList(
          Prereqs.fromRows(rows, WorkCatalogTable.read(), KNOWN, problems));
      warnUnreadColumns(rows);
      if (!problems.isEmpty()) {
        warnOnce(problems.size() + " Work prerequisite row problem(s); those rows ask for nothing,"
            + " and every other row is in use", problems);
      }
      return out;
    } catch (RuntimeException e) {
      problems.add(String.valueOf(e));
      warnOnce("could not read the Work prerequisites table, so no Work asks for anything this"
          + " session", problems);
      return Collections.<Prereq>emptyList();
    }
  }

  private static final Prereqs.Known KNOWN = new Prereqs.Known() {
    @Override
    public boolean item(String id) {
      return ItemTable.row(id) != null;
    }

    @Override
    public boolean hull(String id) {
      try {
        return Global.getSettings().getHullSpec(id) != null;
      } catch (RuntimeException e) {
        return false;
      }
    }

    @Override
    public boolean faction(String id) {
      return Global.getSector() == null || Global.getSector().getFaction(id) != null;
    }

    @Override
    public int levelCap() {
      try {
        return Global.getSettings().getLevelupPlugin().getMaxLevel();
      } catch (RuntimeException e) {
        return Integer.MAX_VALUE;
      }
    }
  };

  private static void warnUnreadColumns(List<ModTables.Row> rows) {
    if (columnsWarned) {
      return;
    }
    List<String> notes = MergedTable.unreadColumnNotes(rows, Prereqs.COLUMNS, Prereqs.PATH);
    if (notes.isEmpty()) {
      return;
    }
    columnsWarned = true;
    MergedTable.warn(WorkPrereqTable.class, notes.size()
        + " Work prerequisite column(s) had no effect", notes);
  }

  private static void warnOnce(String what, List<String> problems) {
    if (warned) {
      return;
    }
    warned = true;
    MergedTable.warn(WorkPrereqTable.class, what, problems);
  }
}
