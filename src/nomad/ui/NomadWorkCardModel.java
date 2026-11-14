package nomad.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import nomad.campaign.econ.model.Foundry;
import nomad.campaign.econ.model.Mining;
import nomad.campaign.econ.model.Prereq;
import nomad.campaign.econ.model.Prereqs;
import nomad.campaign.econ.model.Production;
import nomad.campaign.econ.model.StoreFlags;
import nomad.campaign.econ.model.TickSheet;
import nomad.campaign.econ.model.WorkDef;
import nomad.campaign.econ.model.WorkSize;
import nomad.campaign.econ.model.WorkState;
import nomad.campaign.econ.model.WorkStatus;
import nomad.campaign.econ.model.Works;

public final class NomadWorkCardModel {

  private NomadWorkCardModel() {
  }

  public static final float CARD_H = NomadGrid.CARD_H;
  public static final float NAME_H = NomadGrid.NAME_H;
  public static final float IMAGE_H = 95f;
  public static final float PAD = 3f;
  public static final float LINE_H = 15f;
  public static final float LINE_Y = NAME_H + IMAGE_H + PAD;
  public static final float BAR_H = 12f;
  public static final float BAR_GAP = 2f;
  public static final float ICON_PX = 15f;
  public static final int MAX_FLOWS = 4;
  public static final float WORD_Y = 50f;
  public static final float WORD_H = 24f;
  public static final float MARK_PX = 18f;
  public static final float MARK_GAP = 4f;
  public static final float ADD_H = NomadGrid.ADD_H;

  public static NomadGrid.Box nameBar(float w) {
    return new NomadGrid.Box(0f, 0f, w, NAME_H);
  }

  public static NomadGrid.Box picture(float w) {
    return new NomadGrid.Box(0f, NAME_H, w, IMAGE_H);
  }

  public static NomadGrid.Box line(float w) {
    return new NomadGrid.Box(TEXT_X, LINE_Y, lineBox(w), LINE_H);
  }

  public static NomadGrid.Box word(float w) {
    return new NomadGrid.Box(0f, WORD_Y, w, WORD_H);
  }

  public static NomadGrid.Box bar(float w, boolean firstBuild) {
    return new NomadGrid.Box(0f, firstBuild ? LINE_Y + 1f : CARD_H + BAR_GAP, w, BAR_H);
  }

  public static final float TEXT_X = 4f;
  public static final float FLOW_NUM_PAD = 1f;
  public static final float FLOW_GAP = 5f;
  public static final float MORE_W = 55f;
  public static final float REASON_ICON_GAP = 3f;

  public static float lineBox(float w) {
    return w - 2f * TEXT_X;
  }

  public static final class NameLine {

    public final float nameX;
    public final float nameW;
    public final float tagX;
    public final float tagW;

    NameLine(float nameX, float nameW, float tagX, float tagW) {
      this.nameX = nameX;
      this.nameW = nameW;
      this.tagX = tagX;
      this.tagW = tagW;
    }
  }

  public static final float NAME_GAP = 4f;
  public static final float NAME_MARGIN_MIN = 2f;

  public static NameLine nameLine(float w, float nameNeed, float tagNeed) {
    if (tagNeed <= 0f) {
      return new NameLine(TEXT_X, lineBox(w), w - TEXT_X, 0f);
    }
    float tagW = Math.min(lineBox(w), tagNeed);
    float room = lineBox(w) - tagW - NAME_GAP;
    float margin = TEXT_X;
    if (nameNeed > room) {
      margin = Math.max(NAME_MARGIN_MIN, TEXT_X - (float) Math.ceil((nameNeed - room) / 2f));
    }
    float tagX = w - margin - tagW;
    return new NameLine(margin, Math.max(0f, tagX - NAME_GAP - margin), tagX, tagW);
  }

  public static String sizeTag(WorkState w, WorkDef def, Strings flotillaStrings) {
    if (def == null || !def.isSized()) {
      return "";
    }
    return flotillaStrings.get("cards_size", Integer.valueOf(shownSize(w)));
  }

  public static final class WordAt {

    public final float markX;
    public final boolean mark;
    public final float wordX;
    public final float wordW;
    public final float width;

    WordAt(float markX, boolean mark, float wordX, float wordW, float width) {
      this.markX = markX;
      this.mark = mark;
      this.wordX = wordX;
      this.wordW = wordW;
      this.width = width;
    }
  }

  public static WordAt wordAt(float w, float wordNeed, boolean mark) {
    float lead = mark ? MARK_PX + MARK_GAP : 0f;
    float width = lead + wordNeed;
    float left = (w - width) / 2f;
    return new WordAt(left, mark, left + lead, wordNeed, width);
  }

  public static float flowWidth(String figure, NomadText.Measure m) {
    return NomadText.need(figure, m) + FLOW_NUM_PAD + ICON_PX;
  }

  public static int flowsShown(float box, List<String> figures, int more, NomadText.Measure m) {
    for (int k = figures.size(); k > 0; k--) {
      float end = flowsEnd(figures, k, m);
      if (more + figures.size() - k > 0) {
        end += FLOW_GAP + MORE_W;
      }
      if (end <= box) {
        return k;
      }
    }
    return 0;
  }

  public static float flowsEnd(List<String> figures, int k, NomadText.Measure m) {
    float end = 0f;
    for (int i = 0; i < k; i++) {
      end += flowWidth(figures.get(i), m) + (i > 0 ? FLOW_GAP : 0f);
    }
    return end;
  }

  public static String moreText(int hidden, Strings flotillaStrings) {
    return flotillaStrings.get("cards_flow_more", Integer.valueOf(hidden));
  }

  public enum LineKind {
    FLOWS,
    EFFECT,
    REASON,
    KEEPS,
    BAR
  }

  public static final class Face {

    public final boolean dim;
    public final String word;
    public final Tone tone;
    public final boolean mark;
    public final LineKind line;

    Face(boolean dim, String word, Tone tone, boolean mark, LineKind line) {
      this.dim = dim;
      this.word = word;
      this.tone = tone;
      this.mark = mark;
      this.line = line;
    }
  }

  public static Face face(Shown shown, boolean production, boolean counted,
      Strings flotillaStrings) {
    if (shown == null || works(shown)) {
      return new Face(false, "", tone(shown), counted,
          production ? LineKind.FLOWS : LineKind.EFFECT);
    }
    LineKind line = shown == Shown.BUILDING ? LineKind.BAR
        : shown == Shown.MOTHBALLED ? LineKind.KEEPS : LineKind.REASON;
    return new Face(true, shownName(shown, flotillaStrings), tone(shown), counted, line);
  }

  public static String capFirst(String s) {
    if (s == null || s.isEmpty()) {
      return "";
    }
    return Character.toUpperCase(s.charAt(0)) + s.substring(1);
  }

  public static String lineReason(Why why, String goodName, String levelName,
      Strings flotillaStrings) {
    if (why == Why.RETURNING) {
      return capFirst(flotillaStrings.get("ui2_reason_returning"));
    }
    if (why == Why.NEEDS_SHORT) {
      return capFirst(flotillaStrings.get("ui2_reason_needs_short"));
    }
    if (why == Why.SURPLUS_NO_ROOM) {
      return capFirst(flotillaStrings.get("koout_reason_hold_full"));
    }
    if (why == Why.QUEUE_WAITING) {
      return capFirst(flotillaStrings.get("card_reason_domain_parts_workshop"));
    }
    if (why == Why.QUEUE_STOPS_BENCH) {
      return capFirst(flotillaStrings.get("card_reason_stops_domain_parts_workshop"));
    }
    if (why == Why.MARINES_NO_ROOM) {
      return capFirst(flotillaStrings.get("card_reason_marines_no_room"));
    }
    return capFirst(whyText(why, goodName, levelName, flotillaStrings));
  }

  public static String keepsText(String space, Strings flotillaStrings) {
    return flotillaStrings.get("ui2_card_holds", space);
  }

  public static String effectText(String label, String value, Strings flotillaStrings) {
    return flotillaStrings.get("ui2_card_effect", label, value);
  }

  public static String keylessText(String sentence, float box, NomadText.Measure m,
      Strings flotillaStrings) {
    return sentence != null && !sentence.isEmpty() && NomadText.fits(sentence, box, m)
        ? sentence : flotillaStrings.get("cards_effect_on");
  }

  public static String firstParam(WorkSize row) {
    if (row == null || row.params.isEmpty()) {
      return null;
    }
    return row.params.keySet().iterator().next();
  }

  public static final class ReasonLine {

    public final String before;
    public final boolean icon;
    public final String after;
    public final float iconX;
    public final float afterX;
    public final float width;

    ReasonLine(String before, boolean icon, String after, float iconX, float afterX,
        float width) {
      this.before = before;
      this.icon = icon;
      this.after = after;
      this.iconX = iconX;
      this.afterX = afterX;
      this.width = width;
    }
  }

  public static ReasonLine reasonLine(String text, String goodName, boolean hasIcon, float box,
      NomadText.Measure m) {
    String all = text == null ? "" : text;
    int at = goodName == null || goodName.isEmpty() || !hasIcon ? -1 : all.indexOf(goodName);
    if (at < 0) {
      String cut = NomadText.fit(all, box - NomadText.MARGIN, m);
      return new ReasonLine("", false, cut, 0f, 0f, NomadText.need(cut, m));
    }
    String before = all.substring(0, at).trim();
    ReasonLine named = withIcon(before, all.substring(at).trim(), m);
    if (named.width <= box) {
      return named;
    }
    String rest = all.substring(at + goodName.length()).trim();
    ReasonLine alone = withIcon(before, before.isEmpty() ? capFirst(rest) : rest, m);
    if (alone.width <= box) {
      return alone;
    }
    return withIcon(before, NomadText.fit(alone.after,
        Math.max(0f, box - alone.afterX - NomadText.MARGIN), m), m);
  }

  private static ReasonLine withIcon(String before, String after, NomadText.Measure m) {
    float iconX = before.isEmpty() ? 0f : NomadText.need(before, m) + REASON_ICON_GAP;
    float afterX = iconX + ICON_PX + (after.isEmpty() ? 0f : REASON_ICON_GAP);
    return new ReasonLine(before, true, after, iconX, afterX,
        afterX + NomadText.need(after, m));
  }

  public enum Shown {
    RUNNING, IDLE, UNSTAFFED, MOTHBALLED, SUSPENDED, STANDING_DOWN, BUILDING, STOPPED, PAUSED,
    LACKING
  }

  public static Shown shown(WorkState w, WorkStatus status, boolean paused,
      boolean production, boolean needsAtLeastSpartan, float efficiency) {
    if (paused || status == null) {
      if (w != null && w.suspended) {
        return Shown.SUSPENDED;
      }
      if (w != null && w.prereqUnmet && !w.mothballed) {
        return Shown.LACKING;
      }
      if (w != null && w.standingDown && !w.mothballed) {
        return Shown.STANDING_DOWN;
      }
      return w != null && w.mothballed ? Shown.MOTHBALLED : Shown.PAUSED;
    }
    switch (status) {
      case SUSPENDED:
        return Shown.SUSPENDED;
      case STANDING_DOWN:
        return Shown.STANDING_DOWN;
      case LACKING:
        return Shown.LACKING;
      case BUILDING:
        return Shown.BUILDING;
      case MOTHBALLED:
        return Shown.MOTHBALLED;
      case UNSTAFFED:
        return Shown.UNSTAFFED;
      case IDLE:
        return Shown.IDLE;
      default:
        break;
    }
    if (efficiency <= 0f || !production && !needsAtLeastSpartan) {
      return Shown.STOPPED;
    }
    return Shown.RUNNING;
  }

  public static boolean works(Shown shown) {
    return shown == Shown.RUNNING;
  }

  public enum Tone {
    GOOD, WARN, BAD, MUTED, BUILD
  }

  public static Tone tone(Shown shown) {
    if (shown == null) {
      return Tone.MUTED;
    }
    switch (shown) {
      case RUNNING:
        return Tone.GOOD;
      case IDLE:
      case STANDING_DOWN:
      case LACKING:
        return Tone.WARN;
      case UNSTAFFED:
      case SUSPENDED:
      case STOPPED:
      case PAUSED:
        return Tone.BAD;
      case BUILDING:
        return Tone.BUILD;
      case MOTHBALLED:
      default:
        return Tone.MUTED;
    }
  }

  public static boolean dimmed(Shown shown) {
    return !works(shown);
  }

  public enum Why {
    NONE,
    NO_DEPOSIT, EMPTY_QUEUE, QUEUE_PAUSED, QUEUE_WAITING, INPUT_EMPTY, OUTPUT_FULL, HOLD_FULL,
    NO_RESIDENTS_TO_SPARE,
    MARINES_DETACHED, MARINES_AT_TARGET, MARINES_NO_ROOM,
    NO_BERTHS_FOR_RESIDENTS,
    QUEUE_STOPS_BENCH,
    NO_RESIDENTS,
    NEEDS_LEVEL,
    NO_FLAGSHIP,
    RETURNING, NO_SPACE, NEEDS_SHORT, SURPLUS_NO_ROOM,
    OVER_LIMIT,
    NEEDS_PREREQ
  }

  public static Why idleWhy(WorkStatus.Reason reason, boolean queuePaused) {
    if (reason == null) {
      return Why.NONE;
    }
    switch (reason) {
      case NO_DEPOSIT:
        return Why.NO_DEPOSIT;
      case EMPTY_QUEUE:
        return queuePaused ? Why.QUEUE_PAUSED : Why.EMPTY_QUEUE;
      case QUEUE_WAITING:
        return queuePaused ? Why.QUEUE_PAUSED : Why.QUEUE_WAITING;
      case INPUT_EMPTY:
        return Why.INPUT_EMPTY;
      case NO_RESIDENTS_TO_SPARE:
        return Why.NO_RESIDENTS_TO_SPARE;
      case NO_BERTHS_FOR_RESIDENTS:
        return Why.NO_BERTHS_FOR_RESIDENTS;
      case OUTPUT_FULL:
        return Why.OUTPUT_FULL;
      case HOLD_FULL:
        return Why.HOLD_FULL;
      case MARINES_DETACHED:
        return Why.MARINES_DETACHED;
      case MARINES_AT_TARGET:
        return Why.MARINES_AT_TARGET;
      case MARINES_NO_ROOM:
        return Why.MARINES_NO_ROOM;
      case NONE:
      default:
        return Why.NONE;
    }
  }

  public static Why suspendedWhy(boolean returns, float held, float free) {
    return suspendedWhy(returns, false, held, free);
  }

  public static Why suspendedWhy(boolean returns, boolean holdFull, float held, float free) {
    if (returns) {
      return Why.RETURNING;
    }
    if (holdFull) {
      return Why.SURPLUS_NO_ROOM;
    }
    if (held > free + 0.01f) {
      return Why.NO_SPACE;
    }
    return Why.NEEDS_SHORT;
  }

  public static Why why(Shown shown, WorkStatus.Reason reason, boolean queuePaused,
      Why suspended) {
    if (shown == null) {
      return Why.NONE;
    }
    switch (shown) {
      case IDLE:
        return idleWhy(reason, queuePaused);
      case UNSTAFFED:
        return Why.NO_RESIDENTS;
      case STOPPED:
        return Why.NEEDS_LEVEL;
      case PAUSED:
        return Why.NO_FLAGSHIP;
      case SUSPENDED:
        return suspended == null ? Why.NONE : suspended;
      case STANDING_DOWN:
        return Why.OVER_LIMIT;
      case LACKING:
        return Why.NEEDS_PREREQ;
      default:
        return Why.NONE;
    }
  }

  public static String blockingGood(TickSheet sheet, TickSheet.WorkRun run) {
    if (sheet == null || run == null || run.def == null || run.status != WorkStatus.IDLE) {
      return null;
    }
    WorkStatus.Reason reason = run.reason;
    boolean input = reason == WorkStatus.Reason.INPUT_EMPTY;
    if (!input && reason != WorkStatus.Reason.OUTPUT_FULL || Works.MINING_RIG.equals(run.id)) {
      return null;
    }
    if (Works.FOUNDRY.equals(run.id)) {
      if (!input) {
        return null;
      }
      for (Map.Entry<String, Float> need : Foundry.materials(run.def, 1f, sheet.s.items,
          sheet.s.normalOrStory, sheet.shared).entrySet()) {
        if (need.getValue().floatValue() > 0f && sheet.stock(need.getKey()) <= 1e-4f) {
          return need.getKey();
        }
      }
      return null;
    }
    Map<String, WorkStatus.Reason> stalled = Production.stalled(sheet, run.id);
    String best = null;
    float bestScore = 0f;
    for (WorkSize.Line line : run.def.lines) {
      boolean stopped = false;
      for (String made : line.makes.keySet()) {
        stopped |= stalled.get(made) == reason;
      }
      if (!stopped) {
        continue;
      }
      if (input) {
        String least = leastStocked(sheet, line);
        float months = least == null ? 0f : monthsOf(sheet, line, least);
        if (least != null && (best == null || months < bestScore)) {
          best = least;
          bestScore = months;
        }
      } else {
        for (String made : line.makes.keySet()) {
          TickSheet.Store store = sheet.stores.get(made);
          boolean leaves = sheet.s.flagsAct()
              && StoreFlags.has(sheet.s.flags(made), StoreFlags.OUT);
          if (store == null || leaves) {
            continue;
          }
          float over = sheet.stock(made) - store.target;
          if (best == null || over > bestScore) {
            best = made;
            bestScore = over;
          }
        }
      }
    }
    if (best == null && input) {
      for (WorkSize.Line line : run.def.lines) {
        String least = leastStocked(sheet, line);
        float months = least == null ? 0f : monthsOf(sheet, line, least);
        if (least != null && (best == null || months < bestScore)) {
          best = least;
          bestScore = months;
        }
      }
    }
    return best;
  }

  private static String leastStocked(TickSheet sheet, WorkSize.Line line) {
    String best = null;
    float bestMonths = 0f;
    for (Map.Entry<String, Float> used : line.uses.entrySet()) {
      if (used.getValue().floatValue() <= 0f || Works.RESIDENTS.equals(used.getKey())) {
        continue;
      }
      float months = monthsOf(sheet, line, used.getKey());
      if (best == null || months < bestMonths) {
        best = used.getKey();
        bestMonths = months;
      }
    }
    return best;
  }

  private static float monthsOf(TickSheet sheet, WorkSize.Line line, String good) {
    return sheet.stock(good) / line.uses.get(good).floatValue();
  }

  public static final class State {

    public final Shown shown;
    public final Why why;
    public final String good;
    public final Prereq prereq;

    State(Shown shown, Why why, String good, Prereq prereq) {
      this.shown = shown;
      this.why = why;
      this.good = good;
      this.prereq = prereq;
    }
  }

  public static State state(TickSheet sheet, WorkState w, WorkDef def, boolean queuePaused,
      Why suspended) {
    TickSheet.WorkRun run = w == null ? null : sheet.run(w.id);
    Shown shown = shown(w, run == null ? null : run.status, sheet.s.paused,
        def == null || def.isProduction(), sheet.needs.level.atLeastSpartan(), sheet.efficiency);
    if (shown == Shown.RUNNING && def != null && !def.isProduction() && run != null
        && !run.effectOn) {
      shown = Shown.STOPPED;
    }
    Why why = why(shown, run == null ? null : run.reason, queuePaused, suspended);
    if (why == Why.QUEUE_WAITING && Foundry.benchOn(sheet)) {
      why = Why.QUEUE_STOPS_BENCH;
    }
    String good = shown == Shown.IDLE ? blockingGood(sheet, run) : null;
    Prereq prereq = shown == Shown.LACKING && w != null
        ? Prereqs.firstUnmet(sheet.s.prereqs, w.id, w.size) : null;
    return new State(shown, why, good, prereq);
  }

  public interface Strings {
    String get(String key, Object... args);
  }

  public static String shownName(Shown shown, Strings flotillaStrings) {
    if (shown == null) {
      return "";
    }
    switch (shown) {
      case RUNNING:
        return flotillaStrings.get("screen_status_running");
      case IDLE:
        return flotillaStrings.get("screen_status_idle");
      case UNSTAFFED:
        return flotillaStrings.get("screen_status_unstaffed");
      case MOTHBALLED:
        return flotillaStrings.get("screen_status_mothballed");
      case SUSPENDED:
        return flotillaStrings.get("screen_status_suspended");
      case STANDING_DOWN:
      case LACKING:
        return flotillaStrings.get("roster_status_standing_down");
      case BUILDING:
        return flotillaStrings.get("screen_status_building");
      case STOPPED:
        return flotillaStrings.get("fixui_status_stopped");
      case PAUSED:
        return flotillaStrings.get("fixui_status_paused");
      default:
        return "";
    }
  }

  public static String whyText(Why why, String goodName, String levelName,
      Strings flotillaStrings) {
    if (why == null) {
      return "";
    }
    boolean named = goodName != null && !goodName.isEmpty();
    switch (why) {
      case NO_DEPOSIT:
        return flotillaStrings.get("screen_reason_no_deposit");
      case EMPTY_QUEUE:
        return flotillaStrings.get("screen_reason_empty_queue");
      case QUEUE_PAUSED:
        return flotillaStrings.get("fixui_reason_queue_paused");
      case QUEUE_WAITING:
        return flotillaStrings.get("sw_reason_domain_parts_workshop");
      case QUEUE_STOPS_BENCH:
        return flotillaStrings.get("sw_reason_stops_domain_parts_workshop");
      case INPUT_EMPTY:
        return named ? flotillaStrings.get("fixui_reason_input", goodName)
            : flotillaStrings.get("screen_reason_input_empty");
      case NO_RESIDENTS_TO_SPARE:
        return flotillaStrings.get("goods_reason_no_residents_to_spare");
      case NO_BERTHS_FOR_RESIDENTS:
        return flotillaStrings.get("vats_reason_no_room");
      case OUTPUT_FULL:
        return named ? flotillaStrings.get("fixui_reason_output", goodName)
            : flotillaStrings.get("screen_reason_output_full");
      case HOLD_FULL:
        return flotillaStrings.get("koout_reason_hold_full");
      case MARINES_DETACHED:
        return flotillaStrings.get("marines_reason_detached");
      case MARINES_AT_TARGET:
        return flotillaStrings.get("marines_reason_at_target");
      case MARINES_NO_ROOM:
        return flotillaStrings.get("marines_reason_no_room");
      case NO_RESIDENTS:
        return flotillaStrings.get("fixui_reason_no_residents");
      case NEEDS_LEVEL:
        return flotillaStrings.get("fixui_reason_needs", levelName == null ? "" : levelName);
      case NO_FLAGSHIP:
        return flotillaStrings.get("fixui_reason_no_flagship");
      case RETURNING:
        return flotillaStrings.get("fixui_reason_returning");
      case NO_SPACE:
        return flotillaStrings.get("fixui_reason_no_space");
      case NEEDS_SHORT:
        return flotillaStrings.get("fixui_reason_needs_short");
      case SURPLUS_NO_ROOM:
        return flotillaStrings.get("fixmodel3_reason_surplus_no_room");
      case OVER_LIMIT:
        return flotillaStrings.get("roster_reason_over_limit");
      case NEEDS_PREREQ:
        return named ? goodName : flotillaStrings.get("prereq_card_unmet");
      case NONE:
      default:
        return "";
    }
  }

  public static String stateText(Shown shown, Why why, String goodName, String levelName,
      int daysLeft, Strings flotillaStrings) {
    if (shown == Shown.BUILDING) {
      return flotillaStrings.get("cards_status_building", Integer.valueOf(daysLeft));
    }
    String name = shownName(shown, flotillaStrings);
    String reason = whyText(why, goodName, levelName, flotillaStrings);
    if (why == Why.NEEDS_PREREQ && !reason.isEmpty()) {
      reason = Character.toLowerCase(reason.charAt(0)) + reason.substring(1);
    }
    return reason.isEmpty() ? name : flotillaStrings.get("cards_status_reason", name, reason);
  }

  public static int shownSize(WorkState w) {
    if (w == null) {
      return 0;
    }
    return w.size > 0 ? w.size : w.buildTo;
  }

  public static int upgradingTo(WorkState w) {
    return w != null && w.size > 0 && w.buildTo > w.size ? w.buildTo : 0;
  }

  public static float progress(WorkState w, float buildDays) {
    if (w == null || !w.isBuilding()) {
      return -1f;
    }
    if (buildDays <= 0f) {
      return 1f;
    }
    return Math.max(0f, Math.min(1f, w.buildDays / buildDays));
  }

  public static int daysLeft(float buildDaysLeft) {
    return Math.max(1, (int) Math.ceil(buildDaysLeft - 1e-4f));
  }

  public static final class Flow {

    public final String good;
    public final float amount;

    Flow(String good, float amount) {
      this.good = good;
      this.amount = amount;
    }
  }

  public static final class Strip {

    public final List<Flow> flows;
    public final int more;

    Strip(List<Flow> flows, int more) {
      this.flows = Collections.unmodifiableList(flows);
      this.more = more;
    }
  }

  public static Strip strip(TickSheet.WorkRun run) {
    return run == null ? strip(null, null) : strip(run.makesPerMonth, run.usesPerMonth);
  }

  public static Strip strip(Map<String, Float> makes, Map<String, Float> uses) {
    List<Flow> all = new ArrayList<Flow>();
    if (makes != null) {
      add(all, makes, 1f);
    }
    if (uses != null) {
      add(all, uses, -1f);
    }
    if (all.size() <= MAX_FLOWS) {
      return new Strip(all, 0);
    }
    return new Strip(new ArrayList<Flow>(all.subList(0, MAX_FLOWS)), all.size() - MAX_FLOWS);
  }

  private static void add(List<Flow> out, Map<String, Float> rates, float sign) {
    for (Map.Entry<String, Float> e : rates.entrySet()) {
      float v = e.getValue() == null ? 0f : e.getValue().floatValue();
      if (Math.abs(v) < 0.05f) {
        continue;
      }
      out.add(new Flow(e.getKey(), sign * Math.abs(v)));
    }
  }

  public static String compact(float v) {
    String sign = v < 0f ? "-" : "+";
    float a = Math.abs(v);
    if (a < 0.05f) {
      return "0";
    }
    String body;
    if (a < 9.95f) {
      body = String.format(Locale.ROOT, "%.1f", a);
    } else if (a < 999.5f) {
      body = Integer.toString(Math.round(a));
    } else if (a < 9950f) {
      body = String.format(Locale.ROOT, "%.1fk", a / 1000f);
    } else {
      body = Math.round(a / 1000f) + "k";
    }
    return sign + body;
  }

  public static final float NOW_SIZE_W = 60f;
  public static final float NOW_DEPOSIT_W = 60f;
  public static final float NOW_EFFICIENCY_W = 120f;
  public static final float NOW_OTHER_W = 124f;
  public static final float NOW_RESULT_W = 50f;
  public static final float NOW_LABEL_PAD = 38f;

  public static final class Now {

    public final String good;
    public final float base;
    public final float deposit;
    public final float efficiency;
    public final float other;
    public final float now;

    Now(String good, float base, float deposit, float efficiency, float other, float now) {
      this.good = good;
      this.base = base;
      this.deposit = deposit;
      this.efficiency = efficiency;
      this.other = other;
      this.now = now;
    }

    public boolean mined() {
      return !Float.isNaN(deposit);
    }

    public boolean hasOther() {
      return Math.abs(other - 1f) > 0.005f;
    }
  }

  public static List<Now> nowRows(TickSheet sheet, TickSheet.WorkRun run) {
    List<Now> out = new ArrayList<Now>();
    if (sheet == null || run == null || run.def == null) {
      return out;
    }
    Map<String, Float> tiers = null;
    if (Works.MINING_RIG.equals(run.id)) {
      WorkState w = sheet.work(run.id);
      tiers = Mining.tiers(sheet.s.site, w != null && w.remote, sheet.k);
    }
    for (Map.Entry<String, Float> made : run.makesPerMonth.entrySet()) {
      float now = made.getValue() == null ? 0f : made.getValue().floatValue();
      if (Math.abs(now) < 0.05f) {
        continue;
      }
      String good = made.getKey();
      float base = run.def.makes(good);
      Float tier = tiers == null ? null : tiers.get(good);
      float deposit = tiers == null ? Float.NaN : tier == null ? 0f : tier.floatValue();
      float efficiency = Works.BROKERAGE.equals(run.id)
          ? nomad.campaign.econ.model.Brokerage.feeEfficiency(sheet.efficiency, sheet.k)
          : sheet.efficiency;
      float part = base * (tiers == null ? 1f : deposit) * efficiency;
      out.add(new Now(good, base, deposit, efficiency, part > 0f ? now / part : 1f, now));
    }
    return out;
  }

  public static String times(float v) {
    float tenth = Math.round(v * 10f) / 10f;
    return Math.abs(v - tenth) < 0.005f ? String.format(Locale.ROOT, "%.1fx", tenth)
        : String.format(Locale.ROOT, "%.2fx", v);
  }

  static final String POINT_ICON = "nomad_autoforge_production";
  static final String CREDITS_ICON = "nomad_credits";
  static final String CREW_COMMODITY = "crew";

  public static String pseudoIcon(String good) {
    if (Works.POINTS.equals(good)) {
      return POINT_ICON;
    }
    if (Works.CREDITS.equals(good)) {
      return CREDITS_ICON;
    }
    if (Works.RESIDENTS.equals(good)) {
      return CREW_COMMODITY;
    }
    return null;
  }

  public static String illustrationKey(String workId, WorkDef def) {
    if (def != null && def.illustration != null && !def.illustration.isEmpty()) {
      return def.illustration;
    }
    return "nomad_work_illus_" + workId;
  }

  public static String standIn(String workId) {
    if (Works.DOMESTIC_GOODS_PLANT.equals(workId)) {
      return "nomad_work_illus_family_cabins";
    }
    if (Works.LUXURY_GOODS_FACTORY.equals(workId)) {
      return "nomad_work_illus_passenger_terminal";
    }
    if (Works.MACHINERY_FACTORY.equals(workId)) {
      return "nomad_work_illus_repair_yard";
    }
    if (Works.MINING_RIG.equals(workId)) {
      return "nomad_work_illus_refinery";
    }
    if (Works.FOUNDRY.equals(workId)) {
      return "nomad_work_illus_foundry_annex";
    }
    if (Works.BROKERAGE.equals(workId)) {
      return "nomad_work_illus_purchasing_office";
    }
    if (Works.BASIC_NEEDS.equals(workId)) {
      return "nomad_illus_habring";
    }
    return null;
  }

  public enum Row {
    UPGRADE,
    UPGRADE_WHY,
    CANCEL, DOWNGRADE, MOTHBALL, REVIVE, PRIORITY, REMOTE, MARINES, SCRAP, CLOSE
  }

  public static final float REASON_H = 16f;

  public static final float PANEL_PAD = 6f;
  public static final float PANEL_PAD_X = 6f;
  public static final float PRICE_BOX = 66f;
  public static final float PRICE_GAP = 4f;

  public static float orderButtonW(float w) {
    return w - 2f * PANEL_PAD_X - PRICE_BOX - PRICE_GAP;
  }

  public static final float FACE_PAD = 4f;

  public static float orderFaceBox(float w) {
    return orderButtonW(w) - 2f * FACE_PAD;
  }

  public static String orderFaceKey(Row row, boolean remoteStop) {
    switch (row) {
      case UPGRADE:
        return "screen_verb_upgrade";
      case CANCEL:
        return "screen_verb_cancel";
      case DOWNGRADE:
        return "screen_verb_downgrade";
      case MOTHBALL:
        return "screen_verb_mothball";
      case REVIVE:
        return "screen_verb_revive";
      case REMOTE:
        return remoteStop ? "screen_verb_remote_stop" : "screen_verb_remote";
      case SCRAP:
        return "screen_verb_scrap";
      default:
        return null;
    }
  }
  public static final float BUTTON_H = 22f;
  public static final float ROW_GAP = 4f;
  public static final float CAPTION_H = 15f;

  public static List<Row> rows(WorkState w, WorkDef def) {
    return rows(w, def, false);
  }

  public static List<Row> rows(WorkState w, WorkDef def, boolean upgradeWhy) {
    List<Row> out = new ArrayList<Row>();
    if (w == null) {
      out.add(Row.CLOSE);
      return out;
    }
    boolean sized = def != null && def.isSized();
    if (w.isBuilding()) {
      out.add(Row.CANCEL);
    } else if (sized) {
      out.add(Row.UPGRADE);
      if (upgradeWhy) {
        out.add(Row.UPGRADE_WHY);
      }
    }
    if (w.isBuilt()) {
      if (sized) {
        out.add(Row.DOWNGRADE);
      }
      out.add(w.mothballed ? Row.REVIVE : Row.MOTHBALL);
    }
    out.add(Row.PRIORITY);
    if (w.isBuilt() && Works.MINING_RIG.equals(w.id)) {
      out.add(Row.REMOTE);
    }
    if (w.isBuilt() && Works.MARINE_BARRACKS.equals(w.id)) {
      out.add(Row.MARINES);
    }
    if (w.isBuilt()) {
      out.add(Row.SCRAP);
    }
    out.add(Row.CLOSE);
    return out;
  }

  public static float rowHeight(Row row) {
    if (row == Row.MARINES) {
      return MarinesBlock.H;
    }
    if (row == Row.UPGRADE_WHY) {
      return REASON_H;
    }
    return row == Row.PRIORITY ? CAPTION_H + BUTTON_H : BUTTON_H;
  }

  public static final class MarinesBlock {

    public static final float LINE_H = 30f;
    public static final float GAP = 4f;
    public static final float FIELD_W = 50f;
    public static final float SET_W = 40f;
    public static final float STEP_GAP = 3f;
    public static final float WORD_PAD = NomadPanelFigures.STORE_STEP_PAD;
    public static final float H = CAPTION_H + LINE_H + BUTTON_H + GAP + BUTTON_H;

    public final NomadGrid.Box caption;
    public final NomadGrid.Box line;
    public final NomadGrid.Box[] step;
    public final NomadGrid.Box field;
    public final NomadGrid.Box set;

    private MarinesBlock(float x, float inner, float[] stepWordW) {
      this.caption = new NomadGrid.Box(x, 0f, inner, CAPTION_H);
      this.line = new NomadGrid.Box(x, CAPTION_H, inner, LINE_H);
      int n = stepWordW.length;
      this.step = new NomadGrid.Box[n];
      float stepsY = CAPTION_H + LINE_H;
      int steps = n - 1;
      float sum = 0f;
      for (int i = 1; i < n; i++) {
        sum += stepWordW[i];
      }
      float spare = (inner - (steps - 1) * STEP_GAP - sum) / Math.max(1, steps);
      float sx = x;
      for (int i = 1; i < n; i++) {
        float bw = stepWordW[i] + spare;
        step[i] = new NomadGrid.Box(sx, stepsY, bw, BUTTON_H);
        sx += bw + STEP_GAP;
      }
      float lowY = stepsY + BUTTON_H + GAP;
      float minW = inner - FIELD_W - SET_W - 2f * GAP;
      step[0] = new NomadGrid.Box(x, lowY, minW, BUTTON_H);
      this.field = new NomadGrid.Box(x + minW + GAP, lowY, FIELD_W, BUTTON_H);
      this.set = new NomadGrid.Box(x + inner - SET_W, lowY, SET_W, BUTTON_H);
    }

    public static MarinesBlock of(float w, float[] stepWordW) {
      return new MarinesBlock(PANEL_PAD_X, w - 2f * PANEL_PAD_X, stepWordW);
    }

    public boolean wordsFit(float[] stepWordW) {
      for (int i = 0; i < step.length && i < stepWordW.length; i++) {
        if (stepWordW[i] + WORD_PAD > step[i].w + 0.01f) {
          return false;
        }
      }
      return true;
    }
  }

  public static float panelHeight(List<Row> rows) {
    float h = 2f * PANEL_PAD;
    for (int i = 0; i < rows.size(); i++) {
      h += rowHeight(rows.get(i));
      if (i > 0) {
        h += ROW_GAP;
      }
    }
    return Math.min(NomadGrid.PANEL_MAX_H, h);
  }

  public static float rowY(List<Row> rows, int index) {
    float y = PANEL_PAD;
    for (int i = 0; i < index && i < rows.size(); i++) {
      y += rowHeight(rows.get(i)) + ROW_GAP;
    }
    return y;
  }
}
