package nomad.campaign.econ.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import nomad.campaign.econ.ModTables;

public final class Prereqs {

  private Prereqs() {
  }

  public static final String PATH = "data/config/nomad/nomad_work_prereqs.csv";
  public static final List<String> COLUMNS = Collections.unmodifiableList(
      Arrays.asList("id", "work", "from_size", "kind", "value", "at_least"));
  public static final List<String> STANDINGS = Collections.unmodifiableList(Arrays.asList(
      "VENGEFUL", "HOSTILE", "INHOSPITABLE", "SUSPICIOUS", "NEUTRAL", "FAVORABLE", "WELCOMING",
      "FRIENDLY", "COOPERATIVE"));

  public static final class Reading {

    public final Prereq row;
    public final boolean met;

    public Reading(Prereq row, boolean met) {
      this.row = row;
      this.met = met;
    }
  }

  public interface Known {
    boolean item(String id);

    boolean hull(String id);

    boolean faction(String id);

    int levelCap();
  }

  public interface Check {
    boolean met(Prereq row);
  }

  public static List<Prereq> fromRows(List<ModTables.Row> rows, WorkCatalog catalog, Known known,
      List<String> problems) {
    List<Prereq> out = new ArrayList<Prereq>();
    if (rows == null || catalog == null) {
      return out;
    }
    List<ModTables.Row> defined = new ArrayList<ModTables.Row>();
    for (ModTables.Row raw : rows) {
      if (catalog.work(cell(raw, "work")) != null) {
        defined.add(raw);
      }
    }
    for (ModTables.Row raw : ModTables.latestById(defined)) {
      Prereq row = fromCells(raw, catalog, known, problems);
      if (row != null && !closesCircle(row, out, problems)) {
        out.add(row);
      }
    }
    return out;
  }

  private static boolean closesCircle(Prereq row, List<Prereq> kept, List<String> problems) {
    if (row.kind != Prereq.Kind.WORK) {
      return false;
    }
    List<String> reached = new ArrayList<String>(row.values);
    for (int i = 0; i < reached.size(); i++) {
      String at = reached.get(i);
      if (at.equals(row.work)) {
        problems.add(where(row) + ": " + row.work + " would need itself through "
            + String.join("|", row.values) + ", so the row is ignored");
        return true;
      }
      for (Prereq p : kept) {
        if (p.kind == Prereq.Kind.WORK && p.work.equals(at)) {
          for (String v : p.values) {
            if (!reached.contains(v)) {
              reached.add(v);
            }
          }
        }
      }
    }
    return false;
  }

  private static String where(Prereq row) {
    return row.id + (row.sourceMod.isEmpty() ? "" : " from " + row.sourceMod);
  }

  private static Prereq fromCells(ModTables.Row raw, WorkCatalog catalog, Known known,
      List<String> problems) {
    String id = raw.id();
    String where = id + (raw.sourceMod.isEmpty() || raw.own ? "" : " from " + raw.sourceMod);
    Prereq.Kind kind = Prereq.Kind.of(cell(raw, "kind"));
    if (kind == null) {
      problems.add(where + ": kind '" + cell(raw, "kind") + "' is not one of item, ship, level,"
          + " officers, standing, mission, commission, colonies, contacts, work, residents or"
          + " none, so the row is ignored");
      return null;
    }
    if (kind == Prereq.Kind.NONE) {
      return null;
    }
    String work = cell(raw, "work");
    WorkDef def = catalog == null ? null : catalog.work(work);
    if (def == null) {
      return null;
    }
    int from = whole(cell(raw, "from_size"));
    if (from < 1 || def.size(from) == null) {
      problems.add(where + ": " + work + " has no size '" + cell(raw, "from_size")
          + "', so the row is ignored");
      return null;
    }
    String atLeast = cell(raw, "at_least");
    int number = 0;
    List<String> values = new ArrayList<String>();
    switch (kind) {
      case LEVEL:
      case OFFICERS:
      case COLONIES:
      case CONTACTS:
      case RESIDENTS: {
        number = whole(atLeast);
        if (number < 1) {
          problems.add(where + ": at_least '" + atLeast + "' is not a whole number above 0, so"
              + " the row is ignored");
          return null;
        }
        int cap = known == null ? Integer.MAX_VALUE : known.levelCap();
        if (kind == Prereq.Kind.LEVEL && number > cap) {
          problems.add(where + ": level " + number + " is above the level cap of " + cap
              + ", so the row is ignored");
          return null;
        }
        break;
      }
      case STANDING:
        atLeast = atLeast.toUpperCase(Locale.ROOT);
        if (!STANDINGS.contains(atLeast)) {
          problems.add(where + ": at_least '" + cell(raw, "at_least") + "' is not one of "
              + STANDINGS + ", so the row is ignored");
          return null;
        }
        values = alternatives(raw, kind, known, where, problems);
        break;
      case MISSION:
        values = missions(raw, where, problems);
        break;
      case COMMISSION:
        values = alternatives(raw, kind, known, where, problems);
        if (values.isEmpty() && !cell(raw, "value").replace("|", "").trim().isEmpty()) {
          problems.add(where + ": value names no faction the sector has, so the row is ignored");
          return null;
        }
        break;
      case WORK: {
        number = atLeast.isEmpty() ? 1 : whole(atLeast);
        if (number < 1) {
          problems.add(where + ": at_least '" + atLeast + "' is not a size, so the row is"
              + " ignored");
          return null;
        }
        values = works(raw, catalog, number, where, problems);
        if (values.contains(work)) {
          problems.add(where + ": " + work + " cannot need itself, so the row is ignored");
          return null;
        }
        break;
      }
      case ITEM:
      case SHIP:
      default:
        values = alternatives(raw, kind, known, where, problems);
        break;
    }
    if (needsValues(kind) && values.isEmpty()) {
      problems.add(where + ": value names nothing the game has, so the row is ignored");
      return null;
    }
    return new Prereq(id, work, from, kind, values, atLeast, number,
        raw.own ? "" : raw.sourceMod);
  }

  private static List<String> alternatives(ModTables.Row raw, Prereq.Kind kind, Known known,
      String where, List<String> problems) {
    List<String> out = new ArrayList<String>();
    for (String part : cell(raw, "value").split("\\|")) {
      String v = part.trim();
      if (v.isEmpty() || out.contains(v)) {
        continue;
      }
      boolean ok = known == null
          || kind == Prereq.Kind.ITEM && known.item(v)
          || kind == Prereq.Kind.SHIP && known.hull(v)
          || (kind == Prereq.Kind.STANDING || kind == Prereq.Kind.COMMISSION) && known.faction(v);
      if (ok) {
        out.add(v);
      } else {
        String what = kind == Prereq.Kind.ITEM ? "no item in the items table"
            : kind == Prereq.Kind.SHIP ? "no hull the game knows" : "no faction";
        problems.add(where + ": '" + v + "' is " + what + ", so that alternative is ignored");
      }
    }
    return out;
  }

  private static boolean needsValues(Prereq.Kind kind) {
    switch (kind) {
      case ITEM:
      case SHIP:
      case STANDING:
      case MISSION:
      case WORK:
        return true;
      default:
        return false;
    }
  }

  private static List<String> works(ModTables.Row raw, WorkCatalog catalog, int size,
      String where, List<String> problems) {
    List<String> out = new ArrayList<String>();
    for (String part : cell(raw, "value").split("\\|")) {
      String v = part.trim();
      if (v.isEmpty() || out.contains(v)) {
        continue;
      }
      WorkDef def = catalog.work(v);
      if (def == null) {
        problems.add(where + ": '" + v + "' is no Work an installed mod defines, so that"
            + " alternative is ignored");
      } else if (def.size(size) == null) {
        problems.add(where + ": " + v + " has no size " + size + ", so that alternative is"
            + " ignored");
      } else {
        out.add(v);
      }
    }
    return out;
  }

  private static List<String> missions(ModTables.Row raw, String where, List<String> problems) {
    List<String> out = new ArrayList<String>();
    for (String part : cell(raw, "value").split("\\|")) {
      String v = part.trim();
      if (v.isEmpty()) {
        continue;
      }
      if (!missionKey(v).startsWith("$") || missionName(v).isEmpty()) {
        problems.add(where + ": '" + v + "' is not a memory key and a mission's name, as in"
            + " $gaATG_missionCompleted=At The Gates, so that entry is ignored");
        continue;
      }
      out.add(v);
    }
    return out;
  }

  public static String missionKey(String entry) {
    int at = entry.indexOf('=');
    return (at < 0 ? entry : entry.substring(0, at)).trim();
  }

  public static String missionName(String entry) {
    int at = entry.indexOf('=');
    return at < 0 ? "" : entry.substring(at + 1).trim();
  }

  public static final List<String> GALATIA_KEYS = Collections.unmodifiableList(Arrays.asList(
      "$gaKA_missionCompleted", "$gaFC_missionCompleted", "$gaPZ_missionCompleted",
      "$gaATG_missionCompleted", "$gaRH_missionCompleted", "$gaDHO_missionCompleted"));

  public static final List<String> VOLTURN_KEYS = Collections.unmodifiableList(Arrays.asList(
      "$anh_missionCompleted"));

  public interface Flags {
    boolean set(String key);
  }

  public static boolean missionMet(List<String> entries, Flags flags, boolean academy,
      boolean volturn) {
    for (String entry : entries) {
      String key = missionKey(entry);
      if (flags.set(key) || !academy && GALATIA_KEYS.contains(key)
          || !volturn && VOLTURN_KEYS.contains(key)) {
        return true;
      }
    }
    return false;
  }

  public static boolean workMet(Prereq row, List<WorkState> works) {
    if (works == null) {
      return false;
    }
    int size = Math.max(1, row.number);
    for (WorkState w : works) {
      if (w != null && w.isBuilt() && w.size >= size && row.values.contains(w.id)) {
        return true;
      }
    }
    return false;
  }

  public static boolean residentsMet(Prereq row, List<WorkState> works, float residents,
      float slack) {
    WorkState w = null;
    if (works != null) {
      for (WorkState s : works) {
        if (s != null && row.work.equals(s.id)) {
          w = s;
          break;
        }
      }
    }
    boolean holding = w != null && w.isBuilt() && !w.prereqUnmet && row.covers(w.size);
    double need = holding ? row.number * (1.0 - Math.max(0.0, Math.min(1.0, (double) slack)))
        : row.number;
    return residents >= need;
  }

  private static String cell(ModTables.Row raw, String column) {
    String v = raw.cells.get(column);
    return v == null ? "" : v.trim();
  }

  private static int whole(String cell) {
    try {
      return Integer.parseInt(cell.trim());
    } catch (NumberFormatException e) {
      return -1;
    }
  }

  public static List<Reading> readings(List<Prereq> rows, Check check) {
    List<Reading> out = new ArrayList<Reading>(rows.size());
    for (Prereq row : rows) {
      out.add(new Reading(row, check.met(row)));
    }
    return out;
  }

  public static List<Reading> of(List<Reading> readings, String workId) {
    List<Reading> out = new ArrayList<Reading>();
    for (Reading r : readings) {
      if (r.row.work.equals(workId)) {
        out.add(r);
      }
    }
    return out;
  }

  public static List<Prereq> unmetAt(List<Reading> readings, String workId, int size) {
    List<Prereq> out = new ArrayList<Prereq>();
    for (Reading r : readings) {
      if (!r.met && r.row.covers(size) && r.row.work.equals(workId)) {
        out.add(r.row);
      }
    }
    return out;
  }

  public static Prereq firstUnmet(List<Reading> readings, String workId, int size) {
    for (Reading r : readings) {
      if (!r.met && r.row.covers(size) && r.row.work.equals(workId)) {
        return r.row;
      }
    }
    return null;
  }

  static void settle(TickSheet t) {
    List<Reading> readings = t.s.prereqs;
    for (WorkState w : t.works) {
      Prereq first = w.isBuilt() && !readings.isEmpty()
          ? firstUnmet(readings, w.id, w.size) : null;
      WorkState before = t.s.work(w.id);
      boolean was = before != null && before.prereqUnmet;
      boolean running = !w.mothballed && !w.suspended
          && !(before != null && before.standingDown);
      if (running && first != null && !was) {
        t.event(FlotillaEvent.Type.WORK_PREREQ_LOST, w.id, w.size, first.id);
      } else if (running && first == null && was) {
        t.event(FlotillaEvent.Type.WORK_PREREQ_MET, w.id, w.size);
      }
      w.prereqUnmet = first != null;
      TickSheet.WorkRun run = t.run(w.id);
      if (run != null) {
        run.returnsAtResidents = first == null ? Float.POSITIVE_INFINITY
            : returnsAt(readings, w.id, w.size);
      }
    }
  }

  static float returnsAt(List<Reading> readings, String workId, int size) {
    float at = 0f;
    for (Prereq row : unmetAt(readings, workId, size)) {
      if (row.kind != Prereq.Kind.RESIDENTS) {
        return Float.POSITIVE_INFINITY;
      }
      at = Math.max(at, row.number);
    }
    return at > 0f ? at : Float.POSITIVE_INFINITY;
  }

  public static List<String> disabledByLowering(List<Prereq> rows, List<WorkState> works,
      String workId, int toSize) {
    List<String> out = new ArrayList<String>();
    if (rows == null || works == null || workId == null) {
      return out;
    }
    WorkState lowered = null;
    for (WorkState w : works) {
      if (w != null && workId.equals(w.id)) {
        lowered = w;
      }
    }
    if (lowered == null || !lowered.isBuilt()) {
      return out;
    }
    List<WorkState> after = new ArrayList<WorkState>();
    for (WorkState w : works) {
      if (w == null) {
        continue;
      }
      if (w == lowered) {
        if (toSize >= 1) {
          WorkState c = w.copy();
          c.size = toSize;
          after.add(c);
        }
      } else {
        after.add(w);
      }
    }
    for (WorkState w : works) {
      if (w == null || w == lowered || !w.isBuilt() || out.contains(w.id)) {
        continue;
      }
      for (Prereq row : rows) {
        if (row.kind == Prereq.Kind.WORK && row.work.equals(w.id) && row.covers(w.size)
            && row.values.contains(workId) && workMet(row, works) && !workMet(row, after)) {
          out.add(w.id);
          break;
        }
      }
    }
    return out;
  }

  public static List<String> disabledByRemoving(List<Prereq> rows, List<WorkState> works,
      List<String> installed, String itemId) {
    List<String> out = new ArrayList<String>();
    if (rows == null || works == null || itemId == null) {
      return out;
    }
    for (WorkState w : works) {
      if (w == null || !w.isBuilt() || out.contains(w.id)) {
        continue;
      }
      for (Prereq row : rows) {
        if (row.kind != Prereq.Kind.ITEM || !row.work.equals(w.id) || !row.covers(w.size)
            || !row.values.contains(itemId)) {
          continue;
        }
        boolean other = false;
        for (String v : row.values) {
          other |= !v.equals(itemId) && installed != null && installed.contains(v);
        }
        if (!other && installed != null && installed.contains(itemId)) {
          out.add(w.id);
          break;
        }
      }
    }
    return out;
  }
}
