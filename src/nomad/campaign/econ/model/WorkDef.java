package nomad.campaign.econ.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class WorkDef {

  public static final String CAT_ECONOMY = "Economy";
  public static final String CAT_CAPACITY = "Capacity";
  public static final String CAT_FLEET = "Fleet";
  public static final String CAT_COMBAT = "Combat";
  public static final String CAT_EVENT = "Fortune";

  public static final List<String> CATEGORIES = Collections.unmodifiableList(Arrays.asList(
      CAT_ECONOMY, CAT_CAPACITY, CAT_FLEET, CAT_COMBAT, CAT_EVENT));

  public static final String SCOPE_FLEET = "fleet";
  public static final String SCOPE_FLOTILLA = "flotilla";

  public final String id;
  public final String name;
  public final String category;
  public final String scope;
  public final String illustration;
  public final String effect;
  public final String plugin;
  public final String sourceMod;
  public final boolean ownRow;

  private final WorkSize[] sizes;

  WorkDef(WorkSize first, String category, String scope, WorkSize[] sizes) {
    this.id = first.work;
    this.name = first.name;
    this.category = category;
    this.scope = scope;
    this.illustration = first.illustration;
    this.effect = first.effect;
    this.plugin = first.plugin;
    this.sourceMod = first.sourceMod;
    this.ownRow = first.ownRow;
    this.sizes = sizes;
  }

  public int maxSize() {
    return sizes.length;
  }

  public WorkSize size(int size) {
    return size < 1 || size > sizes.length ? null : sizes[size - 1];
  }

  public boolean isSized() {
    return sizes.length > 1;
  }

  public boolean isFleetScoped() {
    return SCOPE_FLEET.equals(scope);
  }

  public boolean isProduction() {
    for (WorkSize s : sizes) {
      if (!s.makes.isEmpty() || !s.uses.isEmpty()) {
        return true;
      }
    }
    return false;
  }
}
