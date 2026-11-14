package nomad.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import nomad.campaign.econ.EconConstants;
import nomad.campaign.econ.model.Brokerage;
import nomad.campaign.econ.model.Production;
import nomad.campaign.econ.model.Requirement;
import nomad.campaign.econ.model.Rules;
import nomad.campaign.econ.model.WorkDef;
import nomad.campaign.econ.model.WorkSize;
import nomad.campaign.econ.model.WorkState;
import nomad.campaign.econ.model.Works;

public final class NomadWorkHoverRows {

  private NomadWorkHoverRows() {
  }

  public static final float LABEL_W = 170f;
  public static final float GOOD_INDENT = 8f;
  public static final float GOOD_ICON = 16f;
  public static final float GOOD_ICON_GAP = 4f;

  public static float goodNameW() {
    return LABEL_W - GOOD_INDENT - GOOD_ICON - GOOD_ICON_GAP;
  }

  public static float labelW(float widestGood, float widestText) {
    return Math.max(LABEL_W, Math.max(widestGood + GOOD_INDENT + GOOD_ICON + GOOD_ICON_GAP,
        widestText));
  }

  public static final float SIZE_W = 64f;

  public static boolean headRow(WorkDef def) {
    return def != null && def.isSized();
  }

  public static String headCell(int s, int built, int building,
      NomadWorkCardModel.Strings flotillaStrings) {
    String size = Integer.toString(s);
    return s == built ? flotillaStrings.get("hover_col_built", size)
        : s == building ? flotillaStrings.get("hover_col_building", size) : size;
  }

  public static float sizeW(WorkDef def, int built, int building, float relief,
      NomadWorkCardModel.Strings flotillaStrings, NomadText.Measure m) {
    float widest = 0f;
    if (def == null) {
      return SIZE_W;
    }
    int n = def.maxSize();
    if (headRow(def)) {
      for (int s = 1; s <= n; s++) {
        widest = Math.max(widest, NomadText.need(headCell(s, built, building, flotillaStrings), m));
      }
    }
    for (Row row : rows(def)) {
      for (int s = 1; s <= n; s++) {
        widest = Math.max(widest, NomadText.need(cell(row, s, relief), m));
      }
    }
    return columnW(widest, SIZE_W);
  }

  public static float columnW(float widestNeed, float least) {
    return Math.max(least, NomadText.element(widestNeed));
  }

  public static final float TIP_SIZE_W = 62f;
  public static final float TIP_VALUE_W = 64f;

  public static String tipFigure(Row row, float v, float relief) {
    if (Float.isNaN(v)) {
      return "-";
    }
    switch (row.kind) {
      case MAKES:
        return "+" + (Works.CREDITS.equals(row.key) ? credits(v) : amount(v));
      case USES:
        return "-" + amount(v);
      case PARAM:
        return param(row.key, v, relief);
      default:
        return amount(v);
    }
  }

  public static String tipHead(int size, NomadWorkCardModel.Strings flotillaStrings) {
    return size < 1 ? "" : flotillaStrings.get("cards_size", Integer.toString(size));
  }

  public static float tipSizeW(WorkDef def, float relief,
      NomadWorkCardModel.Strings flotillaStrings, NomadText.Measure m) {
    float widest = 0f;
    if (def == null) {
      return TIP_SIZE_W;
    }
    for (int s = 1; s <= def.maxSize(); s++) {
      widest = Math.max(widest, NomadText.need(tipHead(s, flotillaStrings), m));
      for (Row row : rows(def)) {
        if (row.kind != Kind.COST && row.kind != Kind.NEEDS) {
          widest = Math.max(widest, NomadText.need(tipFigure(row, row.at(s), relief), m));
        }
      }
    }
    return Math.max(TIP_SIZE_W, widest);
  }

  public static float tipValueW(WorkDef def, String days, NomadText.Measure m) {
    float widest = NomadText.need(days, m);
    if (def != null) {
      for (int s = 1; s <= def.maxSize(); s++) {
        widest = Math.max(widest, NomadText.need(credits(def.size(s).cost), m));
      }
    }
    return columnW(widest, TIP_VALUE_W);
  }

  public static String cell(Row row, int size, float relief) {
    float v = row.at(size);
    if (Float.isNaN(v)) {
      return "-";
    }
    switch (row.kind) {
      case COST:
        return credits(v);
      case NEEDS:
        return needs(v, relief);
      case PARAM:
        return param(row.key, v, relief);
      case MAKES:
        return Works.CREDITS.equals(row.key) ? credits(v) : amount(v);
      default:
        return amount(v);
    }
  }

  public static String credits(float v) {
    return String.format(Locale.ROOT, "%,d", (int) v) + "¢";
  }

  public enum Kind {
    SPACE, STAFF, COST, NEEDS, MAKES, USES, PARAM
  }

  public static final class Row {

    public final Kind kind;
    public final String key;
    public final float[] values;
    public final String label;

    Row(Kind kind, String key, float[] values) {
      this(kind, key, values, null);
    }

    Row(Kind kind, String key, float[] values, String label) {
      this.kind = kind;
      this.key = key;
      this.values = values;
      this.label = label;
    }

    public float at(int size) {
      return size < 1 || size > values.length ? Float.NaN : values[size - 1];
    }
  }

  public static final Set<String> NAMED_PARAMS = Collections.unmodifiableSet(
      new LinkedHashSet<String>(Arrays.asList("survey_mult", "detected_mult", "sensor_pct",
          "reveal_ly", "cr_recovery_pct", "repair_pct", "ground_raid_pct", "cr", "efficiency",
          "spartan_efficiency", "repair_mult", "pd_range", "pd_damage_pct", "sale_units", "berths",
          "credits_per_month", "growth_per_year", "accessibility", "insure_frac", "recover_mult",
          "marine_join_xp", "gate_credits", Requirement.PARAM_AMENITY,
          Requirement.PARAM_RAISE)));

  private static final Set<String> NEEDS_PARAMS = Collections.unmodifiableSet(
      new LinkedHashSet<String>(Arrays.asList(Requirement.PARAM_AMENITY,
          Requirement.PARAM_RAISE)));

  public static final String GATE_CREDITS = "gate_credits";

  public static String labelKey(String workId, String param) {
    if (Works.GATE_FREIGHT_OFFICE.equals(workId) && "credits_per_month".equals(param)) {
      return GATE_CREDITS;
    }
    return param;
  }

  private static final Set<String> FRACTION_PARAMS = Collections.unmodifiableSet(
      new LinkedHashSet<String>(Arrays.asList("cr", "efficiency", "spartan_efficiency",
          "growth_per_year", "accessibility")));

  private static final Set<String> SHARE_PARAMS = Collections.unmodifiableSet(
      new LinkedHashSet<String>(Arrays.asList("insure_frac", "marine_join_xp")));

  private static final Set<String> CREDIT_PARAMS = Collections.unmodifiableSet(
      new LinkedHashSet<String>(Arrays.asList("credits_per_month")));

  public static List<Row> rows(WorkDef def) {
    List<Row> out = new ArrayList<Row>();
    if (def == null) {
      return out;
    }
    int n = def.maxSize();
    float[] space = new float[n];
    float[] staff = new float[n];
    float[] cost = new float[n];
    float[] needs = new float[n];
    Set<String> makes = new LinkedHashSet<String>();
    Set<String> uses = new LinkedHashSet<String>();
    Set<String> params = new LinkedHashSet<String>();
    for (int i = 0; i < n; i++) {
      WorkSize s = def.size(i + 1);
      space[i] = s.space;
      staff[i] = s.staff;
      cost[i] = s.cost;
      needs[i] = s.param(Requirement.PARAM_RAISE, 0f) - s.param(Requirement.PARAM_AMENITY, 0f)
          - Requirement.tiers(s);
      makes.addAll(s.makes.keySet());
      uses.addAll(s.uses.keySet());
      params.addAll(s.params.keySet());
    }
    out.add(new Row(Kind.SPACE, null, space));
    out.add(new Row(Kind.STAFF, null, staff));
    out.add(new Row(Kind.COST, null, cost));
    out.add(new Row(Kind.NEEDS, null, needs));
    for (String good : makes) {
      float[] v = new float[n];
      for (int i = 0; i < n; i++) {
        WorkSize s = def.size(i + 1);
        v[i] = s.makes.containsKey(good) ? s.makes(good) : Float.NaN;
      }
      out.add(new Row(Kind.MAKES, good, v));
    }
    for (String good : uses) {
      float[] v = new float[n];
      for (int i = 0; i < n; i++) {
        WorkSize s = def.size(i + 1);
        v[i] = s.uses.containsKey(good) ? s.uses(good) : Float.NaN;
      }
      out.add(new Row(Kind.USES, good, v));
    }
    for (String name : params) {
      if (NEEDS_PARAMS.contains(name)) {
        continue;
      }
      float[] v = new float[n];
      for (int i = 0; i < n; i++) {
        WorkSize s = def.size(i + 1);
        v[i] = s.params.containsKey(name) ? s.param(name, 0f) : Float.NaN;
      }
      out.add(new Row(Kind.PARAM, name, v, labelKey(def.id, name)));
    }
    return out;
  }

  public static int builtSize(WorkState w) {
    return w == null ? 0 : Math.max(0, w.size);
  }

  public static int buildingSize(WorkState w) {
    return w == null || !w.isBuilding() ? 0 : w.buildTo;
  }

  public static int nextSize(WorkDef def, WorkState w) {
    if (def == null || buildingSize(w) > 0) {
      return 0;
    }
    int next = builtSize(w) + 1;
    return next > def.maxSize() ? 0 : next;
  }

  public static String amount(float v) {
    if (Float.isNaN(v)) {
      return "";
    }
    float a = Math.abs(v);
    if (a < 100f) {
      float r = Math.round(v * 10f) / 10f;
      if (Math.abs(r - Math.round(r)) < 1e-4f) {
        return Integer.toString(Math.round(r));
      }
      return String.format(Locale.ROOT, "%.1f", r);
    }
    return String.format(Locale.ROOT, "%,d", Math.round(v));
  }

  public static String param(String name, float v, float relief) {
    if (Float.isNaN(v)) {
      return "";
    }
    String n = name == null ? "" : name;
    if (FRACTION_PARAMS.contains(n)) {
      return signedPct(v);
    }
    if (SHARE_PARAMS.contains(n)) {
      return Math.round(v * 100f) + "%";
    }
    if (CREDIT_PARAMS.contains(n)) {
      return amount(v) + "¢";
    }
    if (Requirement.PARAM_AMENITY.equals(n)) {
      return signed(-Math.round(v * relief * 100f)) + "%";
    }
    if (Requirement.PARAM_RAISE.equals(n)) {
      return signed(Math.round(v * relief * 100f)) + "%";
    }
    if (n.endsWith("_mult")) {
      return signed(Math.round((v - 1f) * 100f)) + "%";
    }
    if (n.endsWith("_pct")) {
      return signed(Math.round(v)) + "%";
    }
    if (n.endsWith("_range")) {
      return signed(Math.round(v));
    }
    return amount(v);
  }

  public static String param(String name, float v) {
    return param(name, v, 0.01f);
  }

  public static String needs(float points, float relief) {
    if (Float.isNaN(points)) {
      return "";
    }
    return signedPct(points * relief);
  }

  public static final class Fee {
    public final float row;
    public final float paidAt;
    public final float yield;
    public final float credits;
    public final float units;

    Fee(float row, float paidAt, float yield, float credits, float units) {
      this.row = row;
      this.paidAt = paidAt;
      this.yield = yield;
      this.credits = credits;
      this.units = units;
    }

    public boolean hasYield() {
      return Math.abs(yield - 1f) > 1e-4f;
    }
  }

  public static Fee fee(WorkSize def, float efficiency, List<String> items,
      boolean normalOrStory, Rules k, EconConstants shared) {
    return new Fee(def.makes(Works.CREDITS), Brokerage.feeEfficiency(efficiency, k),
        Production.itemYield(Works.CREDITS, items, normalOrStory, shared) * k.productionMult,
        Brokerage.creditsPerMonth(def, efficiency, items, normalOrStory, k, shared),
        Brokerage.unitsPerMonth(def, efficiency));
  }

  public static String factor(float frac) {
    float p = frac * 100f;
    if (Math.abs(p - Math.round(p)) < 0.05f) {
      return Math.round(p) + "%";
    }
    return String.format(Locale.ROOT, "%.1f%%", p);
  }

  static String signedPct(float frac) {
    return signed(Math.round(frac * 100f)) + "%";
  }

  private static String signed(int v) {
    return v > 0 ? "+" + v : Integer.toString(v);
  }
}
