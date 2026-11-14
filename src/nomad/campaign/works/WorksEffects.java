package nomad.campaign.works;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.BeamAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.listeners.DamageDealtModifier;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.MutableFleetStatsAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;

import nomad.api.NomadFleetStat;
import nomad.campaign.NomadSettings;
import nomad.campaign.econ.EconConstants;
import nomad.campaign.econ.SupportEffects;
import nomad.campaign.econ.model.WorkCatalog;
import nomad.campaign.econ.model.Works;
import nomad.data.NomadFleetData;

public final class WorksEffects {

  private WorksEffects() {
  }

  private static boolean active() {
    return Global.getSector() != null && NomadFleetData.exists() && !NomadFleetData.getInstance().isPaused();
  }

  private static final EconConstants.FloatSource SETTINGS = new EconConstants.FloatSource() {
    @Override
    public float get(String key, float fallback) {
      return NomadSettings.getFloat(key, fallback);
    }
  };

  private static final SupportEffects.Levels OFF = new SupportEffects.Levels();

  private static volatile Reading reading;

  private static final class Reading {
    final WeakReference<NomadFleetData> data;
    final long serial;
    final WorkCatalog catalog;
    final SupportEffects.Levels levels;

    Reading(NomadFleetData data, long serial, WorkCatalog catalog, SupportEffects.Levels levels) {
      this.data = new WeakReference<NomadFleetData>(data);
      this.serial = serial;
      this.catalog = catalog;
      this.levels = levels;
    }
  }

  public static SupportEffects.Levels levels() {
    if (Global.getSector() == null) {
      return OFF;
    }
    NomadFleetData data = NomadFleetData.getOrNull();
    if (data == null || !data.isFounded() || data.isPaused()) {
      return OFF;
    }
    WorkCatalog catalog = WorkCatalogTable.read();
    long serial = data.getAllocSerial();
    Reading held = reading;
    if (held != null && held.serial == serial && held.catalog == catalog && held.data.get() == data) {
      return held.levels;
    }
    SupportEffects.Levels fresh = SupportEffects.resolve(data.getWorkStates(), catalog, SETTINGS);
    reading = new Reading(data, serial, catalog, fresh);
    return fresh;
  }

  private static volatile int lastShipDigest = SupportEffects.OFF_SHIP_DIGEST;

  private static volatile SupportEffects.Levels applied;

  private static volatile boolean carrierMoved;

  private static final int UNKNOWN_SHIP_DIGEST = SupportEffects.OFF_SHIP_DIGEST ^ 0x5a5a5a5a;

  public static void carrierChanged() {
    carrierMoved = true;
  }

  private static volatile boolean yardsWritten;

  private static void resyncShips(CampaignFleetAPI fleet, int digest) {
    if (digest == lastShipDigest) {
      return;
    }
    lastShipDigest = digest;
    if (fleet != null && fleet.getFleetData() != null) {
      fleet.getFleetData().setSyncNeeded();
      fleet.getFleetData().syncIfNeeded();
    }
  }

  public static void applyShipStats(MutableShipStatsAPI stats) {
    if (stats == null || !active()) {
      return;
    }
    SupportEffects.Levels l = levels();

    if (l.resistMult != 1f) {
      stats.getDynamic().getStat(Stats.CORONA_EFFECT_MULT)
          .modifyMult(Works.STORM_SHIELDING_WORKSHOP, l.resistMult);
    }
    if (l.pdRange != 0f) {
      stats.getBeamPDWeaponRangeBonus().modifyFlat(Works.GUNNERY_RANGE, l.pdRange);
      stats.getNonBeamPDWeaponRangeBonus().modifyFlat(Works.GUNNERY_RANGE, l.pdRange);
    }
    if (l.repairTimeMult != 1f) {
      stats.getCombatWeaponRepairTimeMult().modifyMult(Works.DAMAGE_CONTROL_ROOM, l.repairTimeMult);
      stats.getCombatEngineRepairTimeMult().modifyMult(Works.DAMAGE_CONTROL_ROOM, l.repairTimeMult);
    }
    if (l.prideCr != 0f && isCrewed(stats)) {
      stats.getMaxCombatReadiness().modifyFlat(Works.FAMILY_CABINS, l.prideCr);
    }
    if (l.pptPct != 0f) {
      stats.getPeakCRDuration().modifyPercent(Works.WATCH_STATIONS, l.pptPct);
    }
    if (l.statPct != 0f) {
      stats.getMaxTurnRate().modifyPercent(Works.TINKERING_FACILITIES, l.statPct);
      stats.getMaxSpeed().modifyPercent(Works.TINKERING_FACILITIES, l.statPct);
      stats.getFluxDissipation().modifyPercent(Works.TINKERING_FACILITIES, l.statPct);
      stats.getFluxCapacity().modifyPercent(Works.TINKERING_FACILITIES, l.statPct);
    }
    if (l.recoverMult != 1f) {
      stats.getSuppliesToRecover().modifyMult(Works.WELDING_SHOP, l.recoverMult);
    }
  }

  private static boolean isCrewed(MutableShipStatsAPI stats) {
    ShipVariantAPI v = stats.getVariant();
    if (v == null || v.getHullSpec() == null) {
      return false;
    }
    return v.getHullSpec().getMaxCrew() > 0f;
  }

  public static void addCombatListeners(ShipAPI ship) {
    if (ship == null || !active()) {
      return;
    }
    float pct = levels().pdDamagePct;
    if (pct != 0f) {
      ship.addListener(new PDDamageMod(pct));
    }
  }

  public static void applyFleetEffects(CampaignFleetAPI fleet, boolean needsMet) {
    applyFleetEffects(fleet, needsMet, null, null);
  }

  public static void clearAllFleetEffects() {
    clearAllFleetEffects(null);
  }

  public static void applyFleetEffects(CampaignFleetAPI fleet, boolean needsMet,
      Map<String, Map<NomadFleetStat, Float>> pluginStats, EconConstants k) {
    if (fleet == null || !active()) {
      return;
    }
    SupportEffects.Levels l = levels();
    boolean tick = k != null;
    MutableFleetStatsAPI fs = fleet.getStats();
    clearFleetStats(fs);
    if (tick) {
      clearPluginStats(fleet, k);
    }
    CampaignFleetAPI player = Global.getSector().getPlayerFleet();
    if (player != null && player != fleet) {
      clearFleetStats(player.getStats());
      if (tick) {
        clearPluginStats(player, k);
      }
    }
    if (tick) {
      applyPluginStats(fleet, pluginStats);
    }

    if (l.surveyMult != 1f) {
      fs.getDynamic().getStat(Stats.SURVEY_COST_MULT).modifyMult(Works.SURVEY_OFFICE, l.surveyMult);
    }
    if (l.detectedMult != 1f) {
      fs.getDetectedRangeMod().modifyMult(Works.STEALTH_CONTROL_ROOM, l.detectedMult);
    }
    if (l.sensorPct != 0f) {
      fs.getSensorRangeMod().modifyPercent(Works.SENSOR_STATION, l.sensorPct);
    }
    if (l.groundRaidPct != 0f) {
      fs.getDynamic().getMod(Stats.PLANETARY_OPERATIONS_MOD)
          .modifyPercent(Works.RAID_TRAINING_GROUND, l.groundRaidPct);
    }
    if (l.burn != 0f && needsMet) {
      fs.getFleetwideMaxBurnMod().modifyFlat(Works.TUG_BAY, l.burn);
    }

    resyncShips(fleet, l.shipDigest());

    assertYardsMembers(fleet);
    if (player != null && player != fleet) {
      clearMemberBuffs(player);
    }

    MutableCharacterStatsAPI ps = Global.getSector().getPlayerStats();
    if (ps != null) {
      ps.getOfficerNumber().unmodify(Works.OFFICER_ACADEMY);
      if (l.officers != 0f) {
        ps.getOfficerNumber().modifyFlat(Works.OFFICER_ACADEMY, l.officers);
      }
      if (tick) {
        clearPluginOfficers(ps, k);
        applyPluginOfficers(ps, pluginStats);
      }
      ps.getDynamic().getMod(Stats.ALLOW_RESTORE_ANYWHERE).unmodify(Works.RESTORATION_BAY);
      if (l.restoreAnywhere && fleet == player) {
        ps.getDynamic().getMod(Stats.ALLOW_RESTORE_ANYWHERE).modifyFlat(Works.RESTORATION_BAY, 1f);
      }
    }

    applied = l;
  }

  public static void assertBetweenTicks(CampaignFleetAPI fleet) {
    boolean moved = carrierMoved;
    if (moved) {
      carrierMoved = false;
      lastShipDigest = UNKNOWN_SHIP_DIGEST;
    }
    if (fleet == null || fleet.getFleetData() == null || !active()) {
      if (moved || (applied != null && applied != OFF)) {
        clearAllFleetEffects();
        applied = OFF;
      }
      return;
    }
    SupportEffects.Levels l = levels();
    if (moved || l != applied) {
      applyFleetEffects(fleet, true);
      return;
    }
    if (l.yardsCrPct != 0f || l.yardsRepairPct != 0f || yardsWritten) {
      assertYardsMembers(fleet);
    }
  }

  public static void assertYardsMembers(CampaignFleetAPI fleet) {
    if (fleet == null || fleet.getFleetData() == null || !active()) {
      return;
    }
    List<FleetMemberAPI> members = fleet.getFleetData().getMembersListCopy();
    SupportEffects.Levels l = levels();
    boolean yards = l.yardsCrPct != 0f || l.yardsRepairPct != 0f;
    for (FleetMemberAPI m : members) {
      if (yards) {
        m.getStats().getBaseCRRecoveryRatePercentPerDay()
            .modifyPercent(Works.REPAIR_YARD, l.yardsCrPct);
        m.getStats().getRepairRatePercentPerDay()
            .modifyPercent(Works.REPAIR_YARD, l.yardsRepairPct);
      } else {
        clearYardsStats(m);
      }
    }
    yardsWritten = yards;
  }

  public static void clearAllFleetEffects(EconConstants k) {
    if (Global.getSector() == null) {
      return;
    }
    boolean tick = k != null;
    CampaignFleetAPI player = Global.getSector().getPlayerFleet();
    if (player != null) {
      clearFleetStats(player.getStats());
      if (tick) {
        clearPluginStats(player, k);
      }
      clearMemberBuffs(player);
    }
    MutableCharacterStatsAPI ps = Global.getSector().getPlayerStats();
    if (ps != null) {
      ps.getOfficerNumber().unmodify(Works.OFFICER_ACADEMY);
      ps.getDynamic().getMod(Stats.ALLOW_RESTORE_ANYWHERE).unmodify(Works.RESTORATION_BAY);
      if (tick) {
        clearPluginOfficers(ps, k);
      }
    }
    resyncShips(player, SupportEffects.OFF_SHIP_DIGEST);
  }

  private static void clearFleetStats(MutableFleetStatsAPI fs) {
    fs.getDynamic().getStat(Stats.SURVEY_COST_MULT).unmodify(Works.SURVEY_OFFICE);
    fs.getDetectedRangeMod().unmodify(Works.STEALTH_CONTROL_ROOM);
    fs.getSensorRangeMod().unmodify(Works.SENSOR_STATION);
    fs.getDynamic().getMod(Stats.PLANETARY_OPERATIONS_MOD).unmodify(Works.RAID_TRAINING_GROUND);
    fs.getFleetwideMaxBurnMod().unmodify(Works.TUG_BAY);
  }

  private static final String ROW_STATS_KEY = "$nomad_rowStatIds";
  private static final String ROW_OFFICERS_KEY = "$nomad_rowOfficerIds";

  private static List<String> recorded(MemoryAPI memory, String key) {
    List<String> out = new ArrayList<String>();
    Object held = memory == null ? null : memory.get(key);
    if (held instanceof List) {
      for (Object id : (List<?>) held) {
        if (id instanceof String) {
          out.add((String) id);
        }
      }
    }
    return out;
  }

  private static void record(MemoryAPI memory, String key, List<String> ids) {
    if (memory == null) {
      return;
    }
    if (ids.isEmpty()) {
      memory.unset(key);
    } else {
      memory.set(key, new ArrayList<String>(ids));
    }
  }

  private static void clearPluginStats(CampaignFleetAPI fleet, EconConstants k) {
    MutableFleetStatsAPI fs = fleet.getStats();
    MemoryAPI memory = fleet.getMemoryWithoutUpdate();
    for (String id : SupportEffects.rowsToClear(recorded(memory, ROW_STATS_KEY),
        nomad.campaign.tables.TableEffects.pluginRowIds(k))) {
      fs.getSensorRangeMod().unmodify(id);
      fs.getDetectedRangeMod().unmodify(id);
      fs.getFleetwideMaxBurnMod().unmodify(id);
      fs.getDynamic().getStat(Stats.SURVEY_COST_MULT).unmodify(id);
      fs.getDynamic().getMod(Stats.PLANETARY_OPERATIONS_MOD).unmodify(id);
    }
    record(memory, ROW_STATS_KEY, new ArrayList<String>());
  }

  private static void applyPluginStats(CampaignFleetAPI fleet,
      Map<String, Map<NomadFleetStat, Float>> pluginStats) {
    if (pluginStats == null || pluginStats.isEmpty()) {
      return;
    }
    MutableFleetStatsAPI fs = fleet.getStats();
    List<String> written = new ArrayList<String>();
    for (Map.Entry<String, Map<NomadFleetStat, Float>> row : pluginStats.entrySet()) {
      String id = row.getKey();
      written.add(id);
      for (Map.Entry<NomadFleetStat, Float> ask : row.getValue().entrySet()) {
        float amount = ask.getValue() == null ? 0f : ask.getValue();
        if (amount == 0f) {
          continue;
        }
        switch (ask.getKey()) {
          case SENSOR_RANGE_PERCENT:
            fs.getSensorRangeMod().modifyPercent(id, amount);
            break;
          case SENSOR_PROFILE_PERCENT:
            fs.getDetectedRangeMod().modifyPercent(id, amount);
            break;
          case MAX_BURN:
            fs.getFleetwideMaxBurnMod().modifyFlat(id, amount);
            break;
          case SURVEY_COST_PERCENT:
            fs.getDynamic().getStat(Stats.SURVEY_COST_MULT).modifyPercent(id, amount);
            break;
          case GROUND_RAID_PERCENT:
            fs.getDynamic().getMod(Stats.PLANETARY_OPERATIONS_MOD).modifyPercent(id, amount);
            break;
          default:
            break;
        }
      }
    }
    record(fleet.getMemoryWithoutUpdate(), ROW_STATS_KEY, written);
  }

  private static MemoryAPI playerMemory() {
    PersonAPI player = Global.getSector().getPlayerPerson();
    return player == null ? null : player.getMemoryWithoutUpdate();
  }

  private static void clearPluginOfficers(MutableCharacterStatsAPI ps, EconConstants k) {
    MemoryAPI memory = playerMemory();
    for (String id : SupportEffects.rowsToClear(recorded(memory, ROW_OFFICERS_KEY),
        nomad.campaign.tables.TableEffects.pluginRowIds(k))) {
      ps.getOfficerNumber().unmodify(id);
    }
    record(memory, ROW_OFFICERS_KEY, new ArrayList<String>());
  }

  private static void applyPluginOfficers(MutableCharacterStatsAPI ps,
      Map<String, Map<NomadFleetStat, Float>> pluginStats) {
    if (pluginStats == null || pluginStats.isEmpty()) {
      return;
    }
    List<String> written = new ArrayList<String>();
    for (Map.Entry<String, Map<NomadFleetStat, Float>> row : pluginStats.entrySet()) {
      Float slots = row.getValue().get(NomadFleetStat.OFFICER_SLOTS);
      if (slots != null && slots != 0f) {
        ps.getOfficerNumber().modifyFlat(row.getKey(), slots);
        written.add(row.getKey());
      }
    }
    record(playerMemory(), ROW_OFFICERS_KEY, written);
  }

  private static void clearMemberBuffs(CampaignFleetAPI fleet) {
    for (FleetMemberAPI m : fleet.getFleetData().getMembersListCopy()) {
      clearYardsStats(m);
    }
  }

  private static void clearYardsStats(FleetMemberAPI m) {
    m.getStats().getBaseCRRecoveryRatePercentPerDay().unmodify(Works.REPAIR_YARD);
    m.getStats().getRepairRatePercentPerDay().unmodify(Works.REPAIR_YARD);
  }

  public static class PDDamageMod implements DamageDealtModifier {
    private final float pct;

    public PDDamageMod(float pct) {
      this.pct = pct;
    }

    @Override
    public String modifyDamageDealt(Object param, CombatEntityAPI target, DamageAPI damage, Vector2f point,
        boolean shieldHit) {
      WeaponAPI weapon = null;
      if (param instanceof DamagingProjectileAPI) {
        weapon = ((DamagingProjectileAPI) param).getWeapon();
      } else if (param instanceof BeamAPI) {
        weapon = ((BeamAPI) param).getWeapon();
      } else if (param instanceof MissileAPI) {
        weapon = ((MissileAPI) param).getWeapon();
      }
      if (weapon == null || !weapon.hasAIHint(WeaponAPI.AIHints.PD)) {
        return null;
      }
      damage.getModifier().modifyPercent(Works.GUNNERY_RANGE, pct);
      return Works.GUNNERY_RANGE;
    }
  }
}
