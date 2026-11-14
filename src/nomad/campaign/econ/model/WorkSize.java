package nomad.campaign.econ.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class WorkSize {

  public static final List<String> COLUMNS = Collections.unmodifiableList(Arrays.asList(
      "id", "work", "size", "name", "category", "scope", "space", "staff", "cost", "makes", "uses",
      "params", "illustration", "effect", "plugin"));

  public static final class Line {

    public final Map<String, Float> makes;
    public final Map<String, Float> uses;

    Line(Map<String, Float> makes, Map<String, Float> uses) {
      this.makes = Collections.unmodifiableMap(makes);
      this.uses = Collections.unmodifiableMap(uses);
    }
  }

  public final String rowId;
  public final String work;
  public final int size;
  public final float space;
  public final float staff;
  public final float cost;
  public final Map<String, Float> makes;
  public final Map<String, Float> uses;
  public final List<Line> lines;
  public final Map<String, Float> params;

  public final String name;
  public final String category;
  public final String scope;
  public final String illustration;
  public final String effect;
  public final String plugin;
  public final String sourceMod;
  public final boolean ownRow;

  private WorkSize(String rowId, String work, int size, float space, float staff, float cost,
      Map<String, Float> makes, Map<String, Float> uses, List<Line> lines,
      Map<String, Float> params, String name, String category, String scope, String illustration,
      String effect, String plugin, String sourceMod, boolean ownRow) {
    this.rowId = rowId;
    this.work = work;
    this.size = size;
    this.space = space;
    this.staff = staff;
    this.cost = cost;
    this.makes = Collections.unmodifiableMap(makes);
    this.uses = Collections.unmodifiableMap(uses);
    this.lines = Collections.unmodifiableList(lines);
    this.params = Collections.unmodifiableMap(params);
    this.name = name;
    this.category = category;
    this.scope = scope;
    this.illustration = illustration;
    this.effect = effect;
    this.plugin = plugin;
    this.sourceMod = sourceMod;
    this.ownRow = ownRow;
  }

  public float makes(String good) {
    Float v = makes.get(good);
    return v == null ? 0f : v.floatValue();
  }

  public float uses(String good) {
    Float v = uses.get(good);
    return v == null ? 0f : v.floatValue();
  }

  public float param(String name, float fallback) {
    Float v = params.get(name);
    return v == null ? fallback : v.floatValue();
  }

  public static WorkSize fromColumns(Map<String, String> cells, String sourceMod, boolean ownRow,
      List<String> problems) {
    String rowId = str(cells, "id");
    if (rowId.isEmpty() || rowId.startsWith("#")) {
      return null;
    }
    String where = rowId + (sourceMod == null || sourceMod.isEmpty() ? "" : " from " + sourceMod);
    String work = str(cells, "work");
    if (work.isEmpty()) {
      problems.add(where + ": the work column is empty");
      return null;
    }
    int before = problems.size();
    float sizeValue = num(cells, "size", where, problems);
    int size = Math.round(sizeValue);
    if (size < 1 || Math.abs(sizeValue - size) > 1e-4f) {
      problems.add(where + ": size '" + str(cells, "size") + "' is not a whole number of 1 or more");
      return null;
    }
    float space = num(cells, "space", where, problems);
    float staff = num(cells, "staff", where, problems);
    float cost = num(cells, "cost", where, problems);
    if (space < 0f || staff < 0f || cost < 0f) {
      problems.add(where + ": space, staff and cost must all be zero or more");
      return null;
    }
    Map<String, Float> makes = new LinkedHashMap<String, Float>();
    Map<String, Float> uses = new LinkedHashMap<String, Float>();
    Map<String, String> feeds = new LinkedHashMap<String, String>();
    Map<String, Float> params = new LinkedHashMap<String, Float>();
    pairs(str(cells, "makes"), "makes", where, makes, null, problems);
    pairs(str(cells, "uses"), "uses", where, uses, feeds, problems);
    pairs(str(cells, "params"), "params", where, params, null, problems);
    for (Map.Entry<String, String> feed : feeds.entrySet()) {
      if (!makes.containsKey(feed.getValue())) {
        problems.add(where + ": input " + feed.getKey() + " feeds '" + feed.getValue()
            + "', which the row does not make");
      }
    }
    if (problems.size() > before) {
      return null;
    }
    String scope = str(cells, "scope").toLowerCase(Locale.ROOT);
    return new WorkSize(rowId, work, size, space, staff, cost, makes, uses,
        lines(makes, uses, feeds), params, str(cells, "name"), str(cells, "category"), scope,
        str(cells, "illustration"), str(cells, "effect"), str(cells, "plugin"),
        sourceMod == null ? "" : sourceMod, ownRow);
  }

  private static List<Line> lines(Map<String, Float> makes, Map<String, Float> uses,
      Map<String, String> feeds) {
    List<Line> out = new ArrayList<Line>();
    Map<String, Float> sharedMakes = new LinkedHashMap<String, Float>();
    Map<String, Float> sharedUses = new LinkedHashMap<String, Float>();
    for (Map.Entry<String, Float> made : makes.entrySet()) {
      if (!feeds.containsValue(made.getKey())) {
        sharedMakes.put(made.getKey(), made.getValue());
        continue;
      }
      Map<String, Float> own = new LinkedHashMap<String, Float>();
      for (Map.Entry<String, String> feed : feeds.entrySet()) {
        if (feed.getValue().equals(made.getKey())) {
          own.put(feed.getKey(), uses.get(feed.getKey()));
        }
      }
      Map<String, Float> one = new LinkedHashMap<String, Float>();
      one.put(made.getKey(), made.getValue());
      out.add(new Line(one, own));
    }
    for (Map.Entry<String, Float> used : uses.entrySet()) {
      if (!feeds.containsKey(used.getKey())) {
        sharedUses.put(used.getKey(), used.getValue());
      }
    }
    if (!sharedMakes.isEmpty() || !sharedUses.isEmpty()) {
      out.add(new Line(sharedMakes, sharedUses));
    }
    return out;
  }

  private static void pairs(String cell, String column, String where, Map<String, Float> into,
      Map<String, String> feeds, List<String> problems) {
    if (cell.isEmpty()) {
      return;
    }
    for (String part : cell.split(";")) {
      String pair = part.trim();
      if (pair.isEmpty()) {
        continue;
      }
      String product = null;
      int arrow = pair.indexOf('>');
      if (arrow >= 0) {
        product = pair.substring(arrow + 1).trim();
        pair = pair.substring(0, arrow).trim();
        if (feeds == null || product.isEmpty()) {
          problems.add(where + ": " + column + " pair '" + part.trim() + "' is not name:amount");
          continue;
        }
      }
      int colon = pair.indexOf(':');
      String name = colon < 0 ? "" : pair.substring(0, colon).trim();
      String amount = colon < 0 ? "" : pair.substring(colon + 1).trim();
      float value;
      try {
        value = Float.parseFloat(amount);
      } catch (NumberFormatException e) {
        value = Float.NaN;
      }
      if (name.isEmpty() || Float.isNaN(value) || Float.isInfinite(value)) {
        problems.add(where + ": " + column + " pair '" + part.trim() + "' is not name:amount");
        continue;
      }
      if (value < 0f && !"params".equals(column)) {
        problems.add(where + ": " + column + " amount for " + name + " is below zero");
        continue;
      }
      if (into.containsKey(name)) {
        problems.add(where + ": " + column + " names " + name + " twice");
        continue;
      }
      into.put(name, Float.valueOf(value));
      if (product != null) {
        feeds.put(name, product);
      }
    }
  }

  private static String str(Map<String, String> cells, String column) {
    String value = cells.get(column);
    return value == null ? "" : value.trim();
  }

  private static float num(Map<String, String> cells, String column, String where,
      List<String> problems) {
    String value = str(cells, column);
    if (value.isEmpty()) {
      return 0f;
    }
    try {
      return Float.parseFloat(value);
    } catch (NumberFormatException e) {
      problems.add(where + ": " + column + " '" + value + "' is not a number");
      return 0f;
    }
  }
}
