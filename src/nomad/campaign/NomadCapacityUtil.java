package nomad.campaign;

import java.util.List;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.combat.StatBonus;
import com.fs.starfarer.api.fleet.FleetMemberAPI;

import nomad.campaign.econ.NomadCapacity;

public class NomadCapacityUtil {

  private static final float EPS = 1e-4f;

  private static String nomadModId(ShipVariantAPI variant) {
    for (String id : NomadManager.ALL_HULLMOD_IDS) {
      if (variant.hasHullMod(id)) {
        return id;
      }
    }
    return null;
  }

  public static NomadCapacity.Capacity forShip(MutableShipStatsAPI stats, ShipVariantAPI variant,
      float cargoPct, float quartersPct) {
    if (variant == null || variant.getHullSpec() == null) {
      return new NomadCapacity.Capacity(0f, 0f);
    }
    float[] fitted = NomadDedication.of(stats, variant);
    return delta(stats, variant.getHullSpec(), cargoPct, quartersPct, fitted,
        fitted == null ? null : nomadModId(variant));
  }

  public static NomadCapacity.Capacity fittedCapacity(MutableShipStatsAPI stats, ShipVariantAPI variant) {
    if (variant == null || variant.getHullSpec() == null) {
      return new NomadCapacity.Capacity(0f, 0f);
    }
    ShipHullSpecAPI spec = variant.getHullSpec();
    if (stats == null) {
      return new NomadCapacity.Capacity(spec.getCargo(), spec.getMaxCrew());
    }
    float[] fitted = NomadDedication.of(stats, variant);
    String ourId = fitted == null ? null : nomadModId(variant);
    float cargo = asOtherwiseFitted(stats.getCargoMod(), spec.getCargo(), ourId,
        fitted == null ? 1f : NomadCapacity.cargoMult(fitted[0]));
    float crew = asOtherwiseFitted(stats.getMaxCrewMod(), spec.getMaxCrew(), ourId,
        fitted == null ? 1f : NomadCapacity.crewMult(fitted[1], spec.getMinCrew(), spec.getMaxCrew()));
    return new NomadCapacity.Capacity(cargo, crew);
  }

  public static float skeletonCrew(MutableShipStatsAPI stats, ShipVariantAPI variant) {
    if (variant == null || variant.getHullSpec() == null || variant.getHullSpec().getMaxCrew() <= 0f) {
      return 0f;
    }
    ShipHullSpecAPI spec = variant.getHullSpec();
    float ratio = Math.max(0f, Math.min(1f, spec.getMinCrew() / spec.getMaxCrew()));
    return fittedCapacity(stats, variant).berths * ratio;
  }

  public static NomadCapacity.Capacity forFleetData(FleetDataAPI data) {
    return forMembers(data == null ? null : data.getMembersListCopy());
  }

  public static NomadCapacity.Capacity forMembers(List<FleetMemberAPI> members) {
    float space = 0f;
    float berths = 0f;
    if (members != null) {
      for (FleetMemberAPI m : members) {
        if (m == null || m.getVariant() == null) {
          continue;
        }
        ShipVariantAPI variant = m.getVariant();
        float[] pair = NomadDedication.of(m, variant);
        String ourId = pair == null ? null : nomadModId(variant);
        ShipHullSpecAPI spec = variant.getHullSpec();
        if (pair == null || ourId == null || spec == null) {
          continue;
        }
        NomadCapacity.Capacity c = delta(m.getStats(), spec, pair[0], pair[1], pair, ourId);
        space += c.space;
        berths += c.berths;
      }
    }
    return new NomadCapacity.Capacity(space, berths);
  }

  public static NomadCapacity.Capacity forFlotilla(nomad.data.NomadFleetData data,
      CampaignFleetAPI fleet) {
    if (data != null && data.isCapacityFrozen()) {
      return new NomadCapacity.Capacity(data.getFrozenSpace(), data.getFrozenBerths());
    }
    return fleet == null ? new NomadCapacity.Capacity(0f, 0f) : forFleetData(fleet.getFleetData());
  }

  public static float suppliesPerMonth(FleetDataAPI data) {
    return data == null ? 0f : suppliesPerMonth(data.getMembersListCopy());
  }

  public static float suppliesPerMonth(java.util.List<FleetMemberAPI> members) {
    float sup = 0f;
    if (members != null) {
      for (FleetMemberAPI m : members) {
        if (m != null && m.getStats() != null && m.getStats().getSuppliesPerMonth() != null) {
          sup += m.getStats().getSuppliesPerMonth().getModifiedValue();
        }
      }
    }
    return sup;
  }

  private static NomadCapacity.Capacity delta(MutableShipStatsAPI stats, ShipHullSpecAPI spec,
      float cargoPct, float quartersPct, float[] fittedPair, String fittedId) {
    if (stats == null) {
      return NomadCapacity.shipDelta(new NomadCapacity.ShipStats(
          spec.getCargo(), spec.getMinCrew(), spec.getMaxCrew(), cargoPct, quartersPct));
    }
    String outId = fittedPair == null ? null : fittedId;
    float cargoOthers = asOtherwiseFitted(stats.getCargoMod(), spec.getCargo(), outId,
        fittedPair == null ? 1f : NomadCapacity.cargoMult(fittedPair[0]));
    float removedCargo = cargoOthers * (1f - NomadCapacity.cargoMult(cargoPct));

    float crewOthers = asOtherwiseFitted(stats.getMaxCrewMod(), spec.getMaxCrew(), outId,
        fittedPair == null ? 1f
            : NomadCapacity.crewMult(fittedPair[1], spec.getMinCrew(), spec.getMaxCrew()));
    float removedBerths = crewOthers
        * (1f - NomadCapacity.crewMult(quartersPct, spec.getMinCrew(), spec.getMaxCrew()));
    return new NomadCapacity.Capacity(Math.max(0f, removedCargo), Math.max(0f, removedBerths));
  }

  private static float asOtherwiseFitted(StatBonus b, float base, String ourId, float ourMult) {
    if (b == null) {
      return 0f;
    }
    String carriedId = null;
    MutableStat.StatMod carried = null;
    for (String id : NomadManager.ALL_HULLMOD_IDS) {
      MutableStat.StatMod mod = b.getMultBonus(id);
      if (mod != null) {
        carriedId = id;
        carried = mod;
        break;
      }
    }
    if (carried == null) {
      return b.computeEffective(base);
    }
    float mult = carried.getValue();
    if (mult > EPS) {
      return b.computeEffective(base) / mult;
    }
    b.unmodifyMult(carriedId);
    try {
      return b.computeEffective(base);
    } finally {
      b.modifyMult(carriedId, mult);
    }
  }
}
