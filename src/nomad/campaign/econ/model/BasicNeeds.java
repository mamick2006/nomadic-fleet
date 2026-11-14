package nomad.campaign.econ.model;

import java.util.List;
import java.util.Map;

import nomad.campaign.econ.EconConstants;

public final class BasicNeeds {

  private BasicNeeds() {
  }

  public static final String SOURCE_NORMAL_DIFFICULTY = "normal_difficulty";
  public static final String SOURCE_STORY_DIFFICULTY = "story_difficulty";

  static final float EDGE = 1e-4f;

  public static final class Reading {

    public NeedsLevel level = NeedsLevel.NORMAL;
    public float provision = 1f;
    public float provisionSpace = 1f;
    public float provisionResidents = 1f;
    public float haveSpace;
    public float haveResidents;
    public float factor = 1f;
    public float wantSpace;
    public float wantResidents;
    public NeedsLevel next;
    public float nextSpace;
    public float nextResidents;
  }

  public static Reading read(float haveSpace, float haveResidents, float residents,
      boolean normalOrStory, boolean storyDifficulty, float factor, Rules k) {
    Reading r = new Reading();
    r.factor = factor;
    r.haveSpace = Math.max(0f, haveSpace);
    r.haveResidents = Math.max(0f, haveResidents);
    r.wantSpace = spaceWanted(NeedsLevel.NORMAL, residents, normalOrStory, storyDifficulty,
        factor, k);
    r.wantResidents = residentsWanted(NeedsLevel.NORMAL, residents, normalOrStory,
        storyDifficulty, factor, k);
    r.provisionSpace = figure(r.haveSpace, r.wantSpace, k);
    r.provisionResidents = figure(r.haveResidents, r.wantResidents, k);
    r.provision = Math.min(r.provisionSpace, r.provisionResidents);
    r.level = k.levelFor(r.provision + EDGE);
    r.next = r.level.above();
    if (r.next != null) {
      r.nextSpace = spaceWanted(r.next, residents, normalOrStory, storyDifficulty, factor, k);
      r.nextResidents = residentsWanted(r.next, residents, normalOrStory, storyDifficulty,
          factor, k);
    }
    return r;
  }

  private static float figure(float have, float want, Rules k) {
    return want > 0f ? have / want : k.provision(NeedsLevel.OPULENT);
  }

  public static Reading read(FlotillaState s, WorkCatalog catalog, Rules k, EconConstants shared) {
    return FlotillaTick.preview(s, catalog, k, shared).needs;
  }

  public static float spaceWanted(NeedsLevel level, float residents, boolean normalOrStory,
      boolean storyDifficulty, float factor, Rules k) {
    return k.provision(level) * Math.max(0f, residents) * k.needsSpacePerResident
        * k.wantMult(normalOrStory, storyDifficulty) * Math.max(0f, factor);
  }

  public static float residentsWanted(NeedsLevel level, float residents, boolean normalOrStory,
      boolean storyDifficulty, float factor, Rules k) {
    return k.provision(level) * Math.max(0f, residents) * k.needsStaffPerResident
        * k.wantMult(normalOrStory, storyDifficulty) * Math.max(0f, factor);
  }

  public static float globalEfficiency(NeedsLevel level, List<String> items, float guildHallBonus,
      boolean normalOrStory, boolean storyDifficulty, Rules k, EconConstants shared) {
    return efficiency(level, items, guildHallBonus, 0f, normalOrStory, storyDifficulty, k, shared,
        null);
  }

  private static float efficiency(NeedsLevel level, List<String> items, float guildHallBonus,
      float panopticonBonus, boolean normalOrStory, boolean storyDifficulty, Rules k,
      EconConstants shared, Map<String, Float> sources) {
    float base = k.efficiency(level);
    note(sources, level.key, base);
    float added = 0f;
    for (Map.Entry<String, Float> item : shared.itemEfficiency.entrySet()) {
      if (items != null && items.contains(item.getKey())) {
        added += item.getValue().floatValue();
        note(sources, item.getKey(), item.getValue().floatValue());
      }
    }
    if (guildHallBonus != 0f) {
      added += guildHallBonus;
      note(sources, Works.GUILD_HALL, guildHallBonus);
    }
    if (panopticonBonus != 0f) {
      added += panopticonBonus;
      note(sources, Works.PANOPTICON, panopticonBonus);
    }
    if (normalOrStory) {
      note(sources, SOURCE_NORMAL_DIFFICULTY, shared.normalDifficultyEfficiencyMult);
    }
    if (storyDifficulty) {
      note(sources, SOURCE_STORY_DIFFICULTY, shared.storyDifficultyEfficiencyMult);
    }
    return base * Math.max(0f, 1f + added)
        * shared.difficultyProductionMult(normalOrStory, storyDifficulty);
  }

  private static void note(Map<String, Float> sources, String id, float value) {
    if (sources != null) {
      sources.put(id, Float.valueOf(value));
    }
  }

  public static float fold(Map<String, Float> sources) {
    float factors = 1f;
    float shares = 0f;
    for (Map.Entry<String, Float> source : sources.entrySet()) {
      if (isFactor(source.getKey())) {
        factors *= source.getValue().floatValue();
      } else {
        shares += source.getValue().floatValue();
      }
    }
    return factors * Math.max(0f, 1f + shares);
  }

  public static boolean isFactor(String sourceId) {
    if (SOURCE_NORMAL_DIFFICULTY.equals(sourceId) || SOURCE_STORY_DIFFICULTY.equals(sourceId)) {
      return true;
    }
    for (NeedsLevel level : NeedsLevel.values()) {
      if (level.key.equals(sourceId)) {
        return true;
      }
    }
    return false;
  }

  static float room(NeedsLevel level, float haveSpace, float residents, boolean normalOrStory,
      boolean storyDifficulty, float factor, Rules k) {
    float spacePerResident = spaceWanted(level, 1f, normalOrStory, storyDifficulty, factor, k);
    if (spacePerResident <= 0f) {
      return Float.MAX_VALUE;
    }
    return Math.max(0f, Math.max(0f, haveSpace) / spacePerResident - Math.max(0f, residents));
  }

  static void settle(TickSheet t) {
    NeedsLevel prior = t.s.priorLevel;
    if (prior != null && prior != t.needs.level) {
      t.event(FlotillaEvent.Type.LEVEL_CHANGED, t.needs.level.key, prior.ordinal());
    }
  }

  private static Reading read(TickSheet t) {
    return read(t.spaceNeeds(), t.residentsInNeeds(), t.residents, t.s.normalOrStory,
        t.s.storyDifficulty, t.requirementFactor(), t.k);
  }

  static void level(TickSheet t) {
    t.needs = read(t);
    t.detachedRoomAtFit = Float.NaN;
    effects(t);
  }

  private static void effects(TickSheet t) {
    NeedsLevel level = t.needs.level;
    for (TickSheet.WorkRun run : t.runs.values()) {
      run.effectOn = effectOn(t, run, level);
    }
    t.efficiencySources.clear();
    t.efficiency = efficiencyAt(t, level, t.efficiencySources);
  }

  private static boolean effectOn(TickSheet t, TickSheet.WorkRun run, NeedsLevel level) {
    WorkDef def = t.catalog.work(run.id);
    boolean on = def != null && !def.isProduction() && run.status == WorkStatus.RUNNING
        && level.atLeastSpartan();
    return Works.PANOPTICON.equals(run.id) ? on && level == NeedsLevel.SPARTAN : on;
  }

  public static float efficiencyAt(TickSheet t, NeedsLevel level) {
    return efficiencyAt(t, level, null);
  }

  private static float efficiencyAt(TickSheet t, NeedsLevel level, Map<String, Float> sources) {
    float guildHall = 0f;
    float panopticon = 0f;
    for (TickSheet.WorkRun run : t.runs.values()) {
      if (run.def == null || !effectOn(t, run, level)) {
        continue;
      }
      if (Works.GUILD_HALL.equals(run.id)) {
        guildHall = run.def.param(Works.PARAM_EFFICIENCY, 0f);
      }
      if (Works.PANOPTICON.equals(run.id)) {
        panopticon = run.def.param(Works.PARAM_SPARTAN_EFFICIENCY, 0f);
      }
    }
    return efficiency(level, t.s.items, guildHall, panopticon, t.s.normalOrStory,
        t.s.storyDifficulty, t.k, t.shared, sources);
  }
}
