package nomad.campaign.econ;

public final class Wreckage {

  private Wreckage() {
  }

  public enum Spill {
    COMMODITY,
    SPECIAL,
    NOTHING
  }

  public static Spill spillAs(boolean defined, boolean core) {
    if (!defined) {
      return Spill.NOTHING;
    }
    return core ? Spill.COMMODITY : Spill.SPECIAL;
  }

  public enum Left {
    STORES_AND_ITEMS,
    STORES,
    ITEMS,
    NOTHING
  }

  public static Left left(boolean stores, int items) {
    if (stores) {
      return items > 0 ? Left.STORES_AND_ITEMS : Left.STORES;
    }
    return items > 0 ? Left.ITEMS : Left.NOTHING;
  }
}
