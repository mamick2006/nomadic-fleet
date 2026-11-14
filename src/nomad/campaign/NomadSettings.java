package nomad.campaign;

import com.fs.starfarer.api.Global;

import lunalib.lunaSettings.LunaSettings;

import nomad.campaign.econ.model.Difficulty;

public class NomadSettings {

  public static final String MOD_ID = "nomadic_fleet";

  public static float getFloat(String key, float fallback) {
    return Global.getSettings() == null ? fallback : Global.getSettings().optFloat(key, fallback);
  }

  public static int getInt(String key, int fallback) {
    return Global.getSettings() == null ? fallback : (int) Global.getSettings().optFloat(key, fallback);
  }

  private static boolean lunaEnabled() {
    return Global.getSettings() != null
        && Global.getSettings().getModManager() != null
        && Global.getSettings().getModManager().isModEnabled("lunalib");
  }

  private static String difficulty() {
    String stored = lunaEnabled() ? LunaSettings.getString(MOD_ID, "nomad_difficulty") : null;
    String fallback = Difficulty.known(stored) != null ? null : settingsDefault();
    return Difficulty.effective(Difficulty.resolve(stored, fallback),
        NomadSurvival.isSurvivalCampaign());
  }

  private static String settingsDefault() {
    try {
      return Global.getSettings().getString("nomad_difficulty_default");
    } catch (Exception e) {
      return null;
    }
  }

  public static boolean isNormalOrStory() {
    return !Difficulty.SURVIVAL.equals(difficulty());
  }

  public static boolean isStoryDifficulty() {
    return Difficulty.STORY_DIFFICULTY.equals(difficulty());
  }

  public static float tunable(String lunaField, String settingsKey, float fallback) {
    if (lunaEnabled()) {
      Double v = LunaSettings.getDouble(MOD_ID, lunaField);
      if (v != null) {
        return v.floatValue();
      }
    }
    return getFloat(settingsKey, fallback);
  }

  public static int tunableInt(String lunaField, String settingsKey, int fallback) {
    if (lunaEnabled()) {
      Integer v = LunaSettings.getInt(MOD_ID, lunaField);
      if (v != null) {
        return v;
      }
    }
    return getInt(settingsKey, fallback);
  }

  public static boolean tunableBool(String lunaField, boolean fallback) {
    if (lunaEnabled()) {
      Boolean v = LunaSettings.getBoolean(MOD_ID, lunaField);
      if (v != null) {
        return v;
      }
    }
    return Global.getSettings() == null ? fallback : Global.getSettings().optBoolean(lunaField, fallback);
  }

  public static float opCostMult() {
    return Math.max(0f, tunable("nomad_op_cost_mult", "nomad_op_cost_mult", 1f));
  }

  public static boolean nerfSpeed() {
    return tunableBool("nomad_nerf_speed", false);
  }

  public static boolean nerfRoF() {
    return tunableBool("nomad_nerf_rof", false);
  }

  public static boolean nerfCr() {
    return tunableBool("nomad_nerf_cr", false);
  }

  public static float nerfSpeedMult() {
    return getFloat("nomad_nerf_speed_mult", 0.85f);
  }

  public static float nerfRoFMult() {
    return getFloat("nomad_nerf_rof_mult", 0.85f);
  }

  public static float nerfCrPenalty() {
    return getFloat("nomad_nerf_cr_penalty", 0.15f);
  }
}
