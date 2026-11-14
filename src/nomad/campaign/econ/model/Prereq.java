package nomad.campaign.econ.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Prereq {

  public enum Kind {
    ITEM,
    SHIP,
    LEVEL,
    OFFICERS,
    STANDING,
    MISSION,
    COMMISSION,
    COLONIES,
    CONTACTS,
    WORK,
    RESIDENTS,
    NONE;

    public static Kind of(String cell) {
      String c = cell == null ? "" : cell.trim().toLowerCase(java.util.Locale.ROOT);
      for (Kind k : values()) {
        if (k.name().toLowerCase(java.util.Locale.ROOT).equals(c)) {
          return k;
        }
      }
      return null;
    }
  }

  public final String id;
  public final String work;
  public final int fromSize;
  public final Kind kind;
  public final List<String> values;
  public final String atLeast;
  public final int number;
  public final String sourceMod;

  public Prereq(String id, String work, int fromSize, Kind kind, List<String> values,
      String atLeast, int number, String sourceMod) {
    this.id = id;
    this.work = work;
    this.fromSize = Math.max(1, fromSize);
    this.kind = kind;
    this.values = Collections.unmodifiableList(new ArrayList<String>(values));
    this.atLeast = atLeast == null ? "" : atLeast;
    this.number = number;
    this.sourceMod = sourceMod == null ? "" : sourceMod;
  }

  public boolean covers(int size) {
    return size >= fromSize;
  }

  @Override
  public String toString() {
    return id;
  }
}
