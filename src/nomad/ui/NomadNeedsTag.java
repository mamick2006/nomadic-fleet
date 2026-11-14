package nomad.ui;

import nomad.campaign.econ.model.NeedsLevel;

public final class NomadNeedsTag {

  public enum Direction {
    SAME, UP, DOWN;

    public static Direction of(NeedsLevel now, NeedsLevel after) {
      if (now == null || after == null || now == after) {
        return SAME;
      }
      return after.ordinal() > now.ordinal() ? UP : DOWN;
    }
  }

  public enum Tone {
    GOOD, PLAIN, WARN, BAD
  }

  public final String top;
  public final String bottom;
  public final String efficiency;
  public final Tone tone;

  private NomadNeedsTag(String top, String bottom, String efficiency, Tone tone) {
    this.top = top;
    this.bottom = bottom;
    this.efficiency = efficiency;
    this.tone = tone;
  }

  public int lines() {
    return efficiency == null ? 2 : 3;
  }

  public static String efficiencyPct(float efficiency) {
    return Math.round(efficiency * 100f) + "%";
  }

  public static String levelName(NeedsLevel level, NomadWorkCardModel.Strings flotillaStrings) {
    switch (level) {
      case NON_EXISTENT:
        return flotillaStrings.get("screen_level_nonexistent");
      case DECREPIT:
        return flotillaStrings.get("screen_level_decrepit");
      case SPARTAN:
        return flotillaStrings.get("screen_level_spartan");
      case NORMAL:
        return flotillaStrings.get("screen_level_normal");
      case NICE:
        return flotillaStrings.get("screen_level_nice");
      case LAVISH:
        return flotillaStrings.get("screen_level_lavish");
      default:
        return flotillaStrings.get("screen_level_opulent");
    }
  }

  public static boolean stopsEveryWork(NeedsLevel now, NeedsLevel after) {
    return after == NeedsLevel.NON_EXISTENT && now != null && now != after;
  }

  public static Tone tone(NeedsLevel now, NeedsLevel after, boolean residentsWouldLeave) {
    Direction d = Direction.of(now, after);
    if (d == Direction.UP) {
      return Tone.GOOD;
    }
    if (stopsEveryWork(now, after) || residentsWouldLeave) {
      return Tone.BAD;
    }
    return after != null && after.atLeastNormal() ? Tone.PLAIN : Tone.WARN;
  }

  public static NomadNeedsTag of(boolean paused, NeedsLevel now, NeedsLevel after,
      boolean residentsWouldLeave, float effNow, float effAfter,
      NomadWorkCardModel.Strings flotillaStrings, NomadText.Measure m, float box) {
    Direction d = paused ? Direction.SAME : Direction.of(now, after);
    if (d == Direction.SAME) {
      return null;
    }
    String efficiency = after == NeedsLevel.NON_EXISTENT ? null
        : NomadText.fit(flotillaStrings.get("ui2_picker_needs_efficiency",
            efficiencyPct(effNow), efficiencyPct(effAfter)), box, m);
    String name = levelName(after, flotillaStrings);
    String top = d == Direction.UP ? flotillaStrings.get("ui2_picker_needs_rise")
        : flotillaStrings.get("ui2_picker_needs_fall");
    String bottom = name;
    if (d == Direction.DOWN && stopsEveryWork(now, after)) {
      bottom = flotillaStrings.get("ui2_picker_needs_level_none_run", name);
    } else if (d == Direction.DOWN && residentsWouldLeave) {
      bottom = flotillaStrings.get("ui2_picker_needs_level_leave", name);
    }
    if (!fits(bottom, box, m)) {
      bottom = name;
    }
    if (!fits(top, box, m)) {
      top = d == Direction.UP ? flotillaStrings.get("ui2_picker_needs_rise_short")
          : flotillaStrings.get("ui2_picker_needs_fall_short");
    }
    return new NomadNeedsTag(NomadText.fit(top, box, m), NomadText.fit(bottom, box, m),
        efficiency, tone(now, after, residentsWouldLeave));
  }

  public static final class Common {
    private NeedsLevel after;
    private boolean leave;
    private String effNow;
    private String effAfter;
    private int rows;
    private boolean differ;

    public void add(boolean paused, NeedsLevel now, NeedsLevel rowAfter, boolean rowLeave,
        float rowEffNow, float rowEffAfter) {
      NeedsLevel a = paused || Direction.of(now, rowAfter) == Direction.SAME ? null : rowAfter;
      boolean l = a != null && rowLeave;
      String en = a == null ? null : efficiencyPct(rowEffNow);
      String ea = a == null ? null : efficiencyPct(rowEffAfter);
      if (rows > 0 && (a != after || l != leave || !same(en, effNow) || !same(ea, effAfter))) {
        differ = true;
      }
      after = a;
      leave = l;
      effNow = en;
      effAfter = ea;
      rows++;
    }

    private static boolean same(String a, String b) {
      return a == null ? b == null : a.equals(b);
    }

    public String effNow() {
      return effNow;
    }

    public String effAfter() {
      return effAfter;
    }

    public boolean sayOnce() {
      return rows >= 2 && !differ && after != null;
    }

    public NeedsLevel after() {
      return after;
    }

    public boolean leave() {
      return leave;
    }
  }

  public static String once(NeedsLevel now, NeedsLevel after, boolean residentsWouldLeave,
      float effNow, float effAfter, NomadWorkCardModel.Strings flotillaStrings) {
    return once(now, after, residentsWouldLeave, efficiencyPct(effNow), efficiencyPct(effAfter),
        flotillaStrings);
  }

  public static String once(NeedsLevel now, NeedsLevel after, boolean residentsWouldLeave,
      String from, String to, NomadWorkCardModel.Strings flotillaStrings) {
    String name = levelName(after, flotillaStrings);
    if (stopsEveryWork(now, after)) {
      return flotillaStrings.get("ui2_picker_needs_all_none", name);
    }
    if (Direction.of(now, after) == Direction.UP) {
      return flotillaStrings.get("ui2_picker_needs_all_rise", name, from, to);
    }
    return residentsWouldLeave ? flotillaStrings.get("ui2_picker_needs_all_leave", name, from, to)
        : flotillaStrings.get("ui2_picker_needs_all_fall", name, from, to);
  }

  public static boolean fits(String s, float box, NomadText.Measure m) {
    return NomadText.need(s, m) <= box;
  }

  public static final class Taken {

    public final float space;
    public final float work;
    public final float stores;
    public final float residents;

    private Taken(float space, float work, float stores, float residents) {
      this.space = space;
      this.work = work;
      this.stores = stores;
      this.residents = residents;
    }

    public boolean split() {
      return stores >= 0.5f;
    }
  }

  public static Taken taken(NomadBuildPickerLayout.Sum sum, float workSpace) {
    float space = sum.spaceNow - sum.spaceAfter;
    float residents = sum.residentsNow - sum.residentsAfter;
    if (space < 0f || residents < 0f || (space < 0.5f && residents < 0.5f)) {
      return null;
    }
    float work = Math.max(0f, Math.min(space, workSpace));
    return new Taken(space, work, space - work, residents);
  }
}
