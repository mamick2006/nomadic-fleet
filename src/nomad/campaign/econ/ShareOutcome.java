package nomad.campaign.econ;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import nomad.campaign.econ.model.FlotillaState;
import nomad.campaign.econ.model.FlotillaTick;
import nomad.campaign.econ.model.Rules;
import nomad.campaign.econ.model.TickSheet;
import nomad.campaign.econ.model.WorkCatalog;
import nomad.campaign.econ.model.WorkState;
import nomad.campaign.econ.model.WorkStatus;

public final class ShareOutcome {

  public enum Line {
    LEVEL_LEAVE,
    LEVEL_DOWN,
    LEVEL_UP,
    LEVEL_SAME,
    SUSPENDED,
    RETURNING,
    BERTHS,
    UNSTAFFED
  }

  public final SplitShortfall shortfall;
  public final List<String> unstaffed;
  public final List<String> returning;
  public final float residentsLeaving;

  private ShareOutcome(SplitShortfall shortfall, List<String> unstaffed, List<String> returning,
      float residentsLeaving) {
    this.shortfall = shortfall;
    this.unstaffed = Collections.unmodifiableList(unstaffed);
    this.returning = Collections.unmodifiableList(returning);
    this.residentsLeaving = residentsLeaving;
  }

  private static final float SHOWN = 0.5f;

  public static ShareOutcome predict(FlotillaState now, float space, float berths,
      WorkCatalog catalog, Rules k, EconConstants shared) {
    SplitShortfall shortfall = SplitShortfall.predict(now, space, berths, 0f, now.site, catalog, k,
        shared);
    TickSheet next = FlotillaTick.tick(SplitShortfall.afterSplit(now, space, berths, 0f, now.site),
        catalog, k, shared);
    List<String> unstaffed = new ArrayList<String>();
    List<String> returning = new ArrayList<String>();
    for (TickSheet.WorkRun run : next.runs.values()) {
      WorkState was = now.work(run.id);
      WorkState after = next.work(run.id);
      if (was == null || after == null) {
        continue;
      }
      if (was.staffed && run.status == WorkStatus.UNSTAFFED) {
        unstaffed.add(run.id);
      }
      if (was.suspended && !after.suspended) {
        returning.add(run.id);
      }
    }
    float anyway = Math.max(0f, now.residents - shortfall.berthsBefore);
    return new ShareOutcome(shortfall, unstaffed, returning, shortfall.residentsLost - anyway);
  }

  public boolean berthsRise() {
    return shortfall.berthsAfter > shortfall.berthsBefore;
  }

  public boolean berthsMatter() {
    return Math.abs(residentsLeaving) >= SHOWN;
  }

  public boolean levelRises() {
    return shortfall.levelAfter.ordinal() > shortfall.levelBefore.ordinal();
  }

  public List<Line> lines() {
    List<Line> out = new ArrayList<Line>();
    if (shortfall.residentsWouldLeave()) {
      out.add(Line.LEVEL_LEAVE);
    } else if (shortfall.levelFalls()) {
      out.add(Line.LEVEL_DOWN);
    } else if (levelRises()) {
      out.add(Line.LEVEL_UP);
    } else {
      out.add(Line.LEVEL_SAME);
    }
    if (!shortfall.suspended.isEmpty()) {
      out.add(Line.SUSPENDED);
    } else if (!returning.isEmpty()) {
      out.add(Line.RETURNING);
    }
    if (berthsMatter()) {
      out.add(Line.BERTHS);
    }
    if (!unstaffed.isEmpty()) {
      out.add(Line.UNSTAFFED);
    }
    return out;
  }

  public static final int BAND_LINES = 2;

  public List<Line> band() {
    List<Line> all = lines();
    return all.size() <= BAND_LINES ? all : new ArrayList<Line>(all.subList(0, BAND_LINES));
  }

  private static final String SLOT = "%s";

  public static String listFor(List<String> names, String sentence, String rest, int lineChars) {
    int room = lineChars - (sentence.length() - SLOT.length());
    int restRoom = rest.length() - 2 * SLOT.length() + String.valueOf(names.size()).length();
    int shown = namesThatFit(names, room, restRoom);
    StringBuilder list = new StringBuilder();
    for (int i = 0; i < shown; i++) {
      list.append(i > 0 ? ", " : "").append(names.get(i));
    }
    if (shown == names.size()) {
      return list.toString();
    }
    return fill(fill(rest, list.toString()), String.valueOf(names.size() - shown));
  }

  private static int namesThatFit(List<String> names, int room, int restRoom) {
    if (joinedLength(names, names.size()) <= room) {
      return names.size();
    }
    for (int n = names.size() - 1; n > 1; n--) {
      if (joinedLength(names, n) + restRoom <= room) {
        return n;
      }
    }
    return Math.min(1, names.size());
  }

  private static int joinedLength(List<String> names, int n) {
    int length = 0;
    for (int i = 0; i < n; i++) {
      length += names.get(i).length() + (i > 0 ? 2 : 0);
    }
    return length;
  }

  private static String fill(String template, String value) {
    int at = template.indexOf(SLOT);
    return at < 0 ? template
        : template.substring(0, at) + value + template.substring(at + SLOT.length());
  }
}
