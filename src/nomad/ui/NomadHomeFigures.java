package nomad.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import nomad.campaign.econ.model.BasicNeeds;
import nomad.campaign.econ.model.NeedsLevel;
import nomad.campaign.econ.model.Requirement;
import nomad.campaign.econ.model.Residents;
import nomad.campaign.econ.model.Rules;

public final class NomadHomeFigures {

  private NomadHomeFigures() {
  }

  public static String placeInSentence(String typed, String plain) {
    if (typed != null && !typed.trim().isEmpty()) {
      return "the " + typed.trim();
    }
    return plain == null || plain.trim().isEmpty() ? "unknown space" : plain.trim();
  }

  public static final float BAND_UP = 52f;
  public static final float BAND_H = 51f;
  public static final float HERO_INSET = 10f;
  public static final float CAPTION_UP = 47f;
  public static final float VALUE_UP = 31f;
  public static final float LINE_H = 15f;
  public static final float VALUE_H = 24f;
  public static final float HERO_GAP = 10f;

  public static final int PIPS = 6;
  public static final float PIP_W = 10f;
  public static final float PIP_H = 6f;
  public static final float PIP_GAP = 2f;
  public static final float PIP_UP = 43f;
  public static final float PIP_X = 94f;
  public static final float PIP_AFTER = 6f;

  public static final float LINE1_DOWN = 6f;
  public static final float LINE2_DOWN = 23f;
  public static final float CREW_DOWN = 42f;
  public static final float CREW_H = 109f;
  public static final float BLOCK_GAP = 6f;
  public static final float BAR_DOWN = 18f;
  public static final float BAR_H = 8f;
  public static final float PARTS_DOWN = 29f;
  public static final float SPACE_H = 44f;
  public static final float STORES_GAP = 7f;
  public static final float MARK = 16f;
  public static final float MARK_STEP = 20f;
  public static final float PARTS_GAP = 10f;

  public static final class Layout {

    public final float w;
    public final float h;
    public final NomadGrid.Box hero;
    public final NomadGrid.Box band;
    public final NomadGrid.Box line1;
    public final NomadGrid.Box line2;
    public final NomadGrid.Box heroHover;
    public final NomadGrid.Box crew;
    public final NomadGrid.Box spaceCaption;
    public final NomadGrid.Box spaceBar;
    public final NomadGrid.Box spaceParts;
    public final NomadGrid.Box space;
    public final NomadGrid.Box stores;

    Layout(float w, float h, float heroH, float crewUsed) {
      this.w = w;
      this.h = h;
      hero = new NomadGrid.Box(0f, 0f, w, heroH);
      band = new NomadGrid.Box(1f, heroH - BAND_UP, w - 2f, BAND_H);
      line1 = new NomadGrid.Box(0f, heroH + LINE1_DOWN, w, LINE_H);
      line2 = new NomadGrid.Box(0f, heroH + LINE2_DOWN, w, LINE_H);
      heroHover = new NomadGrid.Box(0f, 0f, w, line2.bottom());
      crew = new NomadGrid.Box(0f, heroH + CREW_DOWN, w, CREW_H);
      float top = crew.y + Math.max(0f, crewUsed) + BLOCK_GAP;
      spaceCaption = new NomadGrid.Box(0f, top, w, LINE_H);
      spaceBar = new NomadGrid.Box(0f, top + BAR_DOWN, w, BAR_H);
      spaceParts = new NomadGrid.Box(0f, top + PARTS_DOWN, w, LINE_H);
      space = new NomadGrid.Box(0f, top, w, SPACE_H);
      float storesY = space.bottom() + STORES_GAP;
      stores = new NomadGrid.Box(0f, storesY, w, Math.max(0f, h - storesY));
    }

    public float captionY() {
      return hero.h - CAPTION_UP;
    }

    public float valueY() {
      return hero.h - VALUE_UP;
    }

    public float textRight() {
      return w - HERO_INSET;
    }

    public float firstPipX(float captionW) {
      return Math.max(PIP_X, HERO_INSET + captionW + PIP_AFTER);
    }

    public NomadGrid.Box pip(int i, float captionW) {
      return new NomadGrid.Box(firstPipX(captionW) + i * (PIP_W + PIP_GAP), hero.h - PIP_UP,
          PIP_W, PIP_H);
    }

    public float leftBox(float rightW) {
      return Math.max(0f, w - 2f * HERO_INSET - rightW - (rightW > 0f ? HERO_GAP : 0f));
    }
  }

  public static Layout layout(float w, float h, float heroH, float crewUsed) {
    return new Layout(w, h, heroH, crewUsed);
  }

  public static int pipsLit(NeedsLevel level, boolean paused) {
    return paused || level == null ? 0 : Math.min(PIPS, level.ordinal());
  }

  public static int pipsDrawn(boolean paused) {
    return paused ? 0 : PIPS;
  }

  public static String[] detachedLines(String detached, String system, int ships, float box,
      NomadText.Measure m, NomadWorkCardModel.Strings splitStrings) {
    String where = holding(system, ships, splitStrings);
    if (NomadText.fits(where, box, m)) {
      return new String[] { detached, where };
    }
    String[] two = twoLines(detached + " " + where, box, m);
    for (int n = system.length() - 1; two == null && n >= 1; n--) {
      two = twoLines(detached + " "
          + holding(system.substring(0, n).trim() + "...", ships, splitStrings), box, m);
    }
    return two != null ? two : new String[] { detached, NomadText.fit(where, box, m) };
  }

  private static String holding(String system, int ships,
      NomadWorkCardModel.Strings splitStrings) {
    String count = Integer.toString(ships);
    return ships == 1 ? splitStrings.get("detach_summary_one", system, count)
        : splitStrings.get("detach_summary_many", system, count);
  }

  static String[] twoLines(String text, float box, NomadText.Measure m) {
    String[] words = text.trim().split(" +");
    String first = "";
    int i = 0;
    for (; i < words.length; i++) {
      String tried = first.isEmpty() ? words[i] : first + " " + words[i];
      if (!NomadText.fits(tried, box, m)) {
        break;
      }
      first = tried;
    }
    StringBuilder rest = new StringBuilder();
    for (; i < words.length; i++) {
      rest.append(rest.length() == 0 ? "" : " ").append(words[i]);
    }
    return !first.isEmpty() && NomadText.fits(rest.toString(), box, m)
        ? new String[] { first, rest.toString() } : null;
  }

  public static final class SpaceParts {

    public final int total;
    public final int used;
    public final int needs;
    public final int works;
    public final int stores;
    public final float usedShare;
    public final boolean over;

    SpaceParts(float space, float worksF, float storesF) {
      float usedF = Math.max(0f, worksF) + Math.max(0f, storesF);
      total = Math.round(Math.max(0f, space));
      over = usedF > space + 0.5f;
      used = over ? Math.round(usedF) : Math.min(total, Math.round(usedF));
      needs = Math.max(0, total - used);
      works = Math.min(used, Math.round(Math.max(0f, worksF)));
      stores = used - works;
      usedShare = space <= 0f ? 0f : Math.max(0f, Math.min(1f, usedF / space));
    }
  }

  public static SpaceParts spaceParts(float space, float works, float stores) {
    return new SpaceParts(space, works, stores);
  }

  public static float[] barParts(float w, float firstShare, float secondShare) {
    float a = Math.max(0f, Math.min(1f, firstShare));
    float b = Math.max(0f, Math.min(1f - a, secondShare));
    float first = a <= 0f ? 0f : Math.max(2f, Math.round(w * a));
    float second = b <= 0f ? 0f : Math.max(2f, Math.round(w * b));
    float gap = first > 0f && second > 0f ? 1f : 0f;
    if (first + gap + second > w) {
      if (first >= second) {
        first = w - gap - second;
      } else {
        second = w - gap - first;
      }
    }
    return new float[] { first, first + gap, second };
  }

  public static boolean partsBothFit(float leftW, float rightW, float w) {
    return leftW + PARTS_GAP + rightW <= w;
  }

  static final float LEVEL_EDGE = 1e-4f;

  public static boolean spaceShort(BasicNeeds.Reading r) {
    return belowNormal(r) && under(r.haveSpace, r.nextSpace, r.wantSpace);
  }

  public static boolean residentsShort(BasicNeeds.Reading r) {
    return belowNormal(r) && under(r.haveResidents, r.nextResidents, r.wantResidents);
  }

  private static boolean belowNormal(BasicNeeds.Reading r) {
    return r != null && r.level != null && r.next != null
        && r.level.ordinal() < NeedsLevel.NORMAL.ordinal();
  }

  private static boolean under(float has, float requires, float want) {
    return has + LEVEL_EDGE * Math.max(0f, want) < requires;
  }

  public static int[] hasAndRequires(float has, float requires, float want) {
    if (under(has, requires, want)) {
      return new int[] { (int) Math.floor(has), (int) Math.ceil(requires) };
    }
    return new int[] { Math.round(has), Math.round(requires) };
  }

  public static int moreRequired(float has, float requires, float want) {
    if (!under(has, requires, want)) {
      return 0;
    }
    int[] shown = hasAndRequires(has, requires, want);
    return Math.max(1, shown[1] - shown[0]);
  }

  public static final class LevelRow {

    public final float has;
    public final float requires;
    public final float ratio;
    public final int hasShown;
    public final int requiresShown;
    public final int percent;

    LevelRow(float has, float requires, Rules k) {
      this.has = has;
      this.requires = requires;
      this.ratio = requires > 0f ? has / requires : k.provision(NeedsLevel.OPULENT);
      int[] shown = hasAndRequires(has, requires, requires);
      this.hasShown = shown[0];
      this.requiresShown = shown[1];
      this.percent = (int) Math.floor((ratio + LEVEL_EDGE) * 100f);
    }
  }

  public static LevelRow[] levelRows(BasicNeeds.Reading r, Rules k) {
    return new LevelRow[] { new LevelRow(r.haveSpace, r.wantSpace, k),
        new LevelRow(r.haveResidents, r.wantResidents, k) };
  }

  public static boolean spaceDecides(LevelRow[] rows) {
    return rows[0].ratio <= rows[1].ratio;
  }

  public static NeedsLevel levelOf(LevelRow[] rows, Rules k) {
    return k.levelFor(Math.min(rows[0].ratio, rows[1].ratio) + LEVEL_EDGE);
  }

  public static NeedsLevel levelOfShown(LevelRow[] rows, Rules k) {
    return k.levelFor(Math.min(rows[0].percent, rows[1].percent) / 100f + 1e-6f);
  }

  public static final class LevelLine {

    public final NeedsLevel level;
    public final float space;
    public final float residents;
    public final float efficiency;
    public final float grow;
    public final float leave;
    public final boolean current;

    LevelLine(NeedsLevel level, BasicNeeds.Reading r, Rules k, float schoolGrowth) {
      this.level = level;
      float p = k.provision(level);
      this.space = p * r.wantSpace;
      this.residents = p * r.wantResidents;
      this.efficiency = k.efficiency(level);
      this.grow = Residents.growthPerYear(k, level, schoolGrowth);
      this.leave = k.leavePerMonth(level);
      this.current = level == r.level;
    }
  }

  public static List<LevelLine> everyLevel(BasicNeeds.Reading r, Rules k) {
    return everyLevel(r, k, 0f);
  }

  public static List<LevelLine> everyLevel(BasicNeeds.Reading r, Rules k, float schoolGrowth) {
    List<LevelLine> out = new ArrayList<LevelLine>();
    NeedsLevel[] levels = NeedsLevel.values();
    for (int i = levels.length - 1; i >= 0; i--) {
      out.add(new LevelLine(levels[i], r, k, schoolGrowth));
    }
    return out;
  }

  public enum EffKind {
    LEVEL,
    SHARE,
    DIFFICULTY
  }

  public static final class EffRow {

    public final String id;
    public final EffKind kind;
    public final float value;

    EffRow(String id, EffKind kind, float value) {
      this.id = id;
      this.kind = kind;
      this.value = value;
    }
  }

  public static List<EffRow> efficiencyRows(Map<String, Float> sources, NeedsLevel level,
      Rules k) {
    List<EffRow> out = new ArrayList<EffRow>();
    if (sources == null || sources.isEmpty()) {
      NeedsLevel l = level == null ? NeedsLevel.NORMAL : level;
      out.add(new EffRow(l.key, EffKind.LEVEL, k.efficiency(l)));
      return out;
    }
    for (Map.Entry<String, Float> e : sources.entrySet()) {
      float v = e.getValue() == null ? 0f : e.getValue().floatValue();
      out.add(new EffRow(e.getKey(), kindOf(e.getKey()), v));
    }
    return out;
  }

  private static EffKind kindOf(String id) {
    if (BasicNeeds.SOURCE_NORMAL_DIFFICULTY.equals(id)
        || BasicNeeds.SOURCE_STORY_DIFFICULTY.equals(id)) {
      return EffKind.DIFFICULTY;
    }
    return BasicNeeds.isFactor(id) ? EffKind.LEVEL : EffKind.SHARE;
  }

  public static float total(List<EffRow> rows) {
    float factors = 1f;
    float shares = 0f;
    for (EffRow row : rows) {
      if (row.kind == EffKind.SHARE) {
        shares += row.value;
      } else {
        factors *= row.value;
      }
    }
    return factors * Math.max(0f, 1f + shares);
  }

  public static String value(EffRow row) {
    switch (row.kind) {
      case SHARE:
        return (row.value < 0f ? "-" : "+") + percent(Math.abs(row.value));
      case DIFFICULTY:
        return "x" + String.format(Locale.ROOT, "%.2f", row.value);
      case LEVEL:
      default:
        return percent(row.value);
    }
  }

  public enum ReqKind {
    WORKS,
    ITEMS,
    LIMIT,
    AMENITY,
    RAISE
  }

  public static final class ReqRow {

    public final ReqKind kind;
    public final String workId;
    public final int count;
    public final float value;

    ReqRow(ReqKind kind, String workId, int count, float value) {
      this.kind = kind;
      this.workId = workId;
      this.count = count;
      this.value = value;
    }
  }

  public static List<ReqRow> requirementRows(Requirement.Reading r) {
    List<ReqRow> out = new ArrayList<ReqRow>();
    if (r == null) {
      return out;
    }
    if (r.worksShare > 0f) {
      out.add(new ReqRow(ReqKind.WORKS, null, r.workTiers, -r.worksShare));
    }
    if (r.itemsShare > 0f) {
      out.add(new ReqRow(ReqKind.ITEMS, null, r.items, -r.itemsShare));
    }
    if (r.overLimit > 0f) {
      out.add(new ReqRow(ReqKind.LIMIT, null, 0, r.overLimit));
    }
    for (Requirement.Part p : r.amenities) {
      out.add(new ReqRow(ReqKind.AMENITY, p.workId, p.size, p.share));
    }
    for (Requirement.Part p : r.raises) {
      out.add(new ReqRow(ReqKind.RAISE, p.workId, p.size, p.share));
    }
    return out;
  }

  public static float requirementTotal(List<ReqRow> rows) {
    float sum = 0f;
    for (ReqRow row : rows) {
      sum += row.value;
    }
    return sum;
  }

  public static String signedPercent(float change) {
    int p = Math.round(change * 100f);
    return p > 0 ? "+" + p + "%" : p + "%";
  }

  public static String changePercent(float change) {
    return Math.abs(Math.round(change * 100f)) + "%";
  }

  public static boolean noChange(float change) {
    return Math.round(change * 100f) == 0;
  }

  public static final class OrderNeeds {

    public final boolean shows;
    public final boolean less;
    public final String percent;
    public final String space;
    public final String residents;

    OrderNeeds(boolean shows, boolean less, String percent, String space, String residents) {
      this.shows = shows;
      this.less = less;
      this.percent = percent;
      this.space = space;
      this.residents = residents;
    }
  }

  public static OrderNeeds orderNeeds(float before, float after, BasicNeeds.Reading now,
      BasicNeeds.Reading then) {
    float change = after - before;
    if (noChange(change) || now == null || then == null) {
      return new OrderNeeds(false, false, "", "", "");
    }
    return new OrderNeeds(true, change < 0f, changePercent(change),
        whole(Math.abs(now.wantSpace - then.wantSpace)),
        whole(Math.abs(now.wantResidents - then.wantResidents)));
  }

  public static final float TIP_W = 450f;
  public static final float SIDE_TIP_W = 360f;
  public static final float[] LEVEL_COLS = { 80f, 70f, 150f, 90f };
  public static final float[] EVERY_COLS = { 94f, 60f, 74f, 120f, 92f };
  public static final float GRID_VALUE_W = 60f;
  public static final float GRID_VALUE_PAD = 10f;

  public static final float TIP_PAD = 3f;
  public static final float TIP_OPAD = 10f;
  public static final float TIP_ROW_H = 18f;
  public static final float TIP_LINE_H = 15f;
  public static final float TIP_HEADING_H = 18f;
  public static final float TIP_TITLE_H = 15f;
  public static final float TIP_MARGIN = 14f;

  public static final float TIP_COL_GAP = 20f;
  public static final float TIP_RIGHT_W = 380f;
  public static final float TIP_EXPANDED_W = TIP_W + TIP_COL_GAP + TIP_RIGHT_W;
  public static final float TIP_FRAME_W = 24f;
  public static final float TIP_COL_INSET = 10f;
  public static final float TIP_HINT_H = 23f;
  public static final float SCREEN_H = 768f;
  public static final float TIP_SPARE = 40f;

  public static final class TipHeight {

    private final float width;
    private final NomadText.Measure font;
    private float h = TIP_MARGIN;

    public TipHeight(float width, NomadText.Measure font) {
      this.width = width;
      this.font = font;
    }

    public TipHeight title() {
      h += TIP_TITLE_H;
      return this;
    }

    public TipHeight para(String text, float pad) {
      h += pad + NomadText.lines(text, width, font) * TIP_LINE_H;
      return this;
    }

    public TipHeight heading() {
      return heading(TIP_OPAD);
    }

    public TipHeight heading(float pad) {
      h += pad + TIP_HEADING_H;
      return this;
    }

    public TipHeight rows(int n, float pad) {
      h += pad + n * TIP_ROW_H;
      return this;
    }

    public TipHeight grid(int n) {
      if (n > 0) {
        h += TIP_PAD + (n - 1) * TIP_ROW_H + TIP_LINE_H;
      }
      return this;
    }

    public float height() {
      return h;
    }
  }

  public static float columns(TipHeight... cols) {
    float most = 0f;
    for (TipHeight c : cols) {
      most = Math.max(most, c.height());
    }
    return most;
  }

  public static boolean fitsScreen(float h) {
    return h + TIP_HINT_H + TIP_SPARE <= SCREEN_H;
  }

  public static float colX(float[] cols, int i) {
    float x = 0f;
    for (int c = 0; c < i; c++) {
      x += cols[c];
    }
    return x;
  }

  public static String whole(float v) {
    return String.format(Locale.ROOT, "%,d", Long.valueOf(Math.round((double) v)));
  }

  public static String percent(float fraction) {
    return Math.round(fraction * 100f) + "%";
  }
}
