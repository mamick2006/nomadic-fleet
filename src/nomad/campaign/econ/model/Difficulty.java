package nomad.campaign.econ.model;

public final class Difficulty {

  private Difficulty() {
  }

  public static final String AUTO = "Auto";
  public static final String SURVIVAL = "Survival";
  public static final String NORMAL_DIFFICULTY = "Normal";
  public static final String STORY_DIFFICULTY = "Story";

  public static final String DEFAULT = AUTO;

  public static String resolve(String stored, String fallback) {
    String name = known(stored);
    if (name == null) {
      name = known(fallback);
    }
    return name == null ? DEFAULT : name;
  }

  public static String effective(String setting, boolean survivalMode) {
    if (AUTO.equals(setting)) {
      return survivalMode ? SURVIVAL : NORMAL_DIFFICULTY;
    }
    return setting;
  }

  public static String known(String name) {
    if (name == null) {
      return null;
    }
    String trimmed = name.trim();
    if (AUTO.equalsIgnoreCase(trimmed)) {
      return AUTO;
    }
    if (SURVIVAL.equalsIgnoreCase(trimmed)) {
      return SURVIVAL;
    }
    if (NORMAL_DIFFICULTY.equalsIgnoreCase(trimmed)) {
      return NORMAL_DIFFICULTY;
    }
    if (STORY_DIFFICULTY.equalsIgnoreCase(trimmed)) {
      return STORY_DIFFICULTY;
    }
    return null;
  }
}
