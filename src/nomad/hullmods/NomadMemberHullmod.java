package nomad.hullmods;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;

import nomad.NomadStrings;
import nomad.campaign.NomadDedication;
import nomad.campaign.NomadManager;
import nomad.campaign.NomadSettings;
import nomad.campaign.econ.NomadCapacity;

public class NomadMemberHullmod extends NomadHullmodBase {

  @Override
  public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
    applyDedication(stats, id, "nomad_member_supplies_penalty");
    nomad.campaign.works.WorksEffects.applyShipStats(stats);
  }

  static void applyDedication(MutableShipStatsAPI stats, String id, String suppliesKey) {
    float[] pair = NomadDedication.forMemberMod(stats, stats.getVariant());
    stats.getCargoMod().modifyMult(id, NomadCapacity.cargoMult(pair[0]));
    float minCrew = 0f;
    float maxCrew = 0f;
    if (stats.getVariant() != null && stats.getVariant().getHullSpec() != null) {
      minCrew = stats.getVariant().getHullSpec().getMinCrew();
      maxCrew = stats.getVariant().getHullSpec().getMaxCrew();
    }
    stats.getMaxCrewMod().modifyMult(id, NomadCapacity.crewMult(pair[1], minCrew, maxCrew));
    stats.getSuppliesPerMonth().modifyPercent(id, NomadSettings.getFloat(suppliesKey, 10f));
    applyHandicaps(stats);
  }

  @Override
  public boolean isApplicableToShip(ShipAPI ship) {
    return !carriesOtherNomadMod(ship, NomadManager.MEMBER_HULLMOD_ID);
  }

  @Override
  public String getUnapplicableReason(ShipAPI ship) {
    return NomadStrings.get("nomad_hullmods", "hm_one_per_hull");
  }

  @Override
  public String getDescriptionParam(int index, HullSize hullSize) {
    if (index == 0) {
      return pct("nomad_dedication_default_cargo", NomadCapacity.DEFAULT_PCT);
    }
    if (index == 1) {
      return pct("nomad_member_supplies_penalty", 10f);
    }
    return null;
  }
}
