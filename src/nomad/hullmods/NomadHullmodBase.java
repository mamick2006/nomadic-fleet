package nomad.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import nomad.NomadStrings;
import nomad.campaign.NomadCapacityUtil;
import nomad.campaign.NomadDedication;
import nomad.campaign.NomadManager;
import nomad.campaign.NomadSettings;
import nomad.campaign.econ.NomadCapacity;

public abstract class NomadHullmodBase extends com.fs.starfarer.api.combat.BaseHullMod {

  public static final String NERF_SPEED_ID = "nomad_nerf_speed";
  public static final String NERF_ROF_ID = "nomad_nerf_rof";
  public static final String NERF_CR_ID = "nomad_nerf_cr";

  private static final String HANDICAP_LABEL = "Nomad Flotilla";

  @Override
  public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
    nomad.campaign.works.WorksEffects.addCombatListeners(ship);
  }

  public static void applyHandicaps(MutableShipStatsAPI stats) {
    if (stats == null) {
      return;
    }
    if (NomadSettings.nerfSpeed()) {
      stats.getMaxSpeed().modifyMult(NERF_SPEED_ID, NomadSettings.nerfSpeedMult(), HANDICAP_LABEL);
    } else {
      stats.getMaxSpeed().unmodifyMult(NERF_SPEED_ID);
    }
    if (NomadSettings.nerfRoF()) {
      float rof = NomadSettings.nerfRoFMult();
      stats.getBallisticRoFMult().modifyMult(NERF_ROF_ID, rof, HANDICAP_LABEL);
      stats.getEnergyRoFMult().modifyMult(NERF_ROF_ID, rof, HANDICAP_LABEL);
      stats.getMissileRoFMult().modifyMult(NERF_ROF_ID, rof, HANDICAP_LABEL);
    } else {
      stats.getBallisticRoFMult().unmodifyMult(NERF_ROF_ID);
      stats.getEnergyRoFMult().unmodifyMult(NERF_ROF_ID);
      stats.getMissileRoFMult().unmodifyMult(NERF_ROF_ID);
    }
    if (NomadSettings.nerfCr()) {
      stats.getMaxCombatReadiness().modifyFlat(NERF_CR_ID, -NomadSettings.nerfCrPenalty(), HANDICAP_LABEL);
    } else {
      stats.getMaxCombatReadiness().unmodifyFlat(NERF_CR_ID);
    }
  }

  protected static boolean carriesOtherNomadMod(ShipAPI ship, String selfId) {
    if (ship == null || ship.getVariant() == null) {
      return false;
    }
    for (String id : NomadManager.ALL_HULLMOD_IDS) {
      if (!id.equals(selfId) && ship.getVariant().hasHullMod(id)) {
        return true;
      }
    }
    return false;
  }

  protected static String pct(String key, float fallback) {
    return "" + (int) NomadSettings.getFloat(key, fallback) + "%";
  }

  private static String num(float v) {
    return "" + Math.round(v);
  }

  protected float[] dedicationFor(MutableShipStatsAPI stats, ShipVariantAPI variant) {
    return NomadDedication.forMemberMod(stats, variant);
  }

  @Override
  public void addPostDescriptionSection(TooltipMakerAPI tooltip, HullSize hullSize, ShipAPI ship, float width,
      boolean isForModSpec) {
    if (!isForModSpec) {
      renderContribution(tooltip, ship);
    }
    renderHandicaps(tooltip);
  }

  protected void renderHandicaps(TooltipMakerAPI tooltip) {
    boolean speed = NomadSettings.nerfSpeed();
    boolean rof = NomadSettings.nerfRoF();
    boolean cr = NomadSettings.nerfCr();
    if (!speed && !rof && !cr) {
      return;
    }
    tooltip.addSectionHeading("Handicaps", Alignment.MID, 6f);
    if (speed) {
      tooltip.addPara(NomadStrings.get("nomad_hullmods", "hm_handicap_speed"), 4f,
          Misc.getNegativeHighlightColor(), drop(NomadSettings.nerfSpeedMult()));
    }
    if (rof) {
      tooltip.addPara(NomadStrings.get("nomad_hullmods", "hm_handicap_rof"), speed ? 2f : 4f,
          Misc.getNegativeHighlightColor(), drop(NomadSettings.nerfRoFMult()));
    }
    if (cr) {
      tooltip.addPara(NomadStrings.get("nomad_hullmods", "hm_handicap_cr"), speed || rof ? 2f : 4f,
          Misc.getNegativeHighlightColor(), pctOf(NomadSettings.nerfCrPenalty()));
    }
  }

  private static String drop(float mult) {
    return pctOf(1f - mult);
  }

  private static String pctOf(float fraction) {
    return Math.round(fraction * 100f) + "%";
  }

  protected void renderContribution(TooltipMakerAPI tooltip, ShipAPI ship) {
    if (Global.getSector() == null || ship == null || ship.getVariant() == null) {
      return;
    }
    float[] pair = dedicationFor(ship.getMutableStats(), ship.getVariant());
    if (pair == null) {
      return;
    }
    NomadCapacity.Capacity contribution =
        NomadCapacityUtil.forShip(ship.getMutableStats(), ship.getVariant(), pair[0], pair[1]);

    tooltip.addSectionHeading("Flotilla", Alignment.MID, 6f);
    tooltip.addPara(NomadStrings.get("nomad_hullmods", "hm_dedication"), 4f,
        Misc.getHighlightColor(), (int) pair[0] + "%", (int) pair[1] + "%");
    tooltip.addPara(NomadStrings.get("nomad_hullmods", "hm_contribution"), 2f,
        Misc.getHighlightColor(), num(contribution.space), num(contribution.berths));
  }
}
