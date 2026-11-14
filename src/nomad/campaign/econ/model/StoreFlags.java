package nomad.campaign.econ.model;

public final class StoreFlags {

  private StoreFlags() {
  }

  public static final int IN = 1;
  public static final int OUT = 2;
  public static final int SELL = 4;
  public static final int SELL_CARGO = 8;

  public static final int DEFAULT = IN | OUT;

  public static boolean has(int flags, int flag) {
    return (flags & flag) != 0;
  }

  public static int with(int flags, int flag, boolean on) {
    return on ? flags | flag : flags & ~flag;
  }

  public static int of(Integer saved) {
    return saved == null ? DEFAULT : saved.intValue();
  }
}
