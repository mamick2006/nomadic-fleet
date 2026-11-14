package nomad.campaign.econ;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import nomad.campaign.econ.model.WorkCatalog;
import nomad.campaign.econ.model.WorkState;

public final class ShellRules {

  private ShellRules() {
  }

  public static final class Carrier {

    public static final int NONE = 0;
    public static final int WITH_FLEET = 1;
    public static final int DETACHED = 2;

    public final int where;
    public final String flagshipId;
    public final int ships;
    public final float space;
    public final float berths;

    public Carrier(int where, String flagshipId, int ships, float space, float berths) {
      this.where = where;
      this.flagshipId = flagshipId;
      this.ships = ships;
      this.space = space;
      this.berths = berths;
    }

    public boolean paused() {
      return where == NONE;
    }
  }

  public static boolean moved(Carrier before, Carrier now) {
    if (now == null) {
      return false;
    }
    if (before == null) {
      return true;
    }
    boolean sameFlagship = before.flagshipId == null ? now.flagshipId == null
        : before.flagshipId.equals(now.flagshipId);
    return before.where != now.where || !sameFlagship || before.ships != now.ships
        || before.space != now.space || before.berths != now.berths;
  }

  public static List<WorkState> dormant(List<WorkState> saved, WorkCatalog catalog,
      Set<String> named) {
    List<WorkState> out = new ArrayList<WorkState>();
    if (saved == null || named == null || catalog == null || catalog.isEmpty()) {
      return out;
    }
    for (WorkState w : saved) {
      if (w != null && w.id != null && catalog.work(w.id) == null && named.contains(w.id)) {
        WorkState kept = w.copy();
        kept.effectOn = false;
        out.add(kept);
      }
    }
    return out;
  }

  public static List<WorkState> withDormant(List<WorkState> ticked, List<WorkState> dormant) {
    if (dormant == null || dormant.isEmpty()) {
      return ticked;
    }
    List<WorkState> out = new ArrayList<WorkState>(ticked);
    out.addAll(dormant);
    return out;
  }

  public static float[] place(float units, float stock, float target, boolean outActs,
      float cargoRoom) {
    float add = units > 0f ? units : 0f;
    float over = stock + add - Math.max(0f, target);
    if (over < 1f) {
      return new float[] { add, 0f };
    }
    float surplus = Math.min(add, over);
    float out = outActs ? Math.min(surplus, Math.max(0f, cargoRoom)) : 0f;
    return new float[] { add - surplus, out };
  }

  public static String holdsAt(String parked, String nearest) {
    if (parked == null) {
      return nearest;
    }
    return parked.isEmpty() ? null : parked;
  }

  public static boolean remnantHoldsFlotilla(boolean shipHasMod, boolean flagshipExists,
      boolean detachmentOut) {
    return detachmentOut && !flagshipExists && !shipHasMod;
  }

  public enum ShareWrite {
    REFUSE,
    SAVED,
    EDITED_ONLY
  }

  public static ShareWrite shareWrite(boolean savedHasMod, boolean editedHasMod) {
    if (!editedHasMod) {
      return ShareWrite.REFUSE;
    }
    return savedHasMod ? ShareWrite.SAVED : ShareWrite.EDITED_ONLY;
  }

  public static float sharePredicted(ShareWrite where, float atOpen, float givenAtOpen,
      float givesNow) {
    float gave = where == ShareWrite.EDITED_ONLY ? 0f : givenAtOpen;
    return Math.max(0f, atOpen - gave + givesNow);
  }

  public static boolean sharePredictsChange(ShareWrite where, boolean sharesMoved) {
    return sharesMoved || where == ShareWrite.EDITED_ONLY;
  }

  public static boolean endsFlotilla(boolean rejoining, boolean flagshipLives,
      boolean flagshipAboard) {
    return !rejoining && (flagshipAboard || !flagshipLives);
  }
}
