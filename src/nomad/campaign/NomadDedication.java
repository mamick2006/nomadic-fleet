package nomad.campaign;

import java.util.ArrayList;
import java.util.List;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;

import nomad.campaign.econ.NomadCapacity;
import nomad.data.NomadFleetData;

public final class NomadDedication {

  public static final String TAG_PREFIX = "nomad_dedication:";

  private NomadDedication() {
  }

  public static float[] of(FleetMemberAPI member, ShipVariantAPI variant) {
    return isEditable(variant) ? forMemberMod(member, variant) : null;
  }

  public static float[] of(FleetMemberAPI member) {
    return member == null ? null : of(member, member.getVariant());
  }

  public static float[] of(MutableShipStatsAPI stats, ShipVariantAPI variant) {
    return of(memberOf(stats), variant);
  }

  public static FleetMemberAPI memberOf(MutableShipStatsAPI stats) {
    return stats == null ? null : stats.getFleetMember();
  }

  static String stored(FleetMemberAPI member) {
    if (member == null || member.getId() == null) {
      return null;
    }
    NomadFleetData data = NomadFleetData.getOrNull();
    return data == null ? null : data.getDedication().get(member.getId());
  }

  public static float[] forMemberMod(FleetMemberAPI member, ShipVariantAPI variant) {
    float[] mapped = parseValue(stored(member));
    if (mapped != null) {
      return mapped;
    }
    float[] tagged = variant == null ? null : parse(tagOf(variant));
    return tagged != null ? tagged : defaults();
  }

  public static float[] forMemberMod(MutableShipStatsAPI stats, ShipVariantAPI variant) {
    return forMemberMod(memberOf(stats), variant);
  }

  public static float[] defaults() {
    return new float[] {
        NomadSettings.getFloat("nomad_dedication_default_cargo", NomadCapacity.DEFAULT_PCT),
        NomadSettings.getFloat("nomad_dedication_default_quarters", NomadCapacity.DEFAULT_PCT) };
  }

  public static float[] minimums() {
    return defaults();
  }

  public static boolean isEditable(ShipVariantAPI variant) {
    return variant != null
        && (variant.hasHullMod(NomadManager.FLAGSHIP_HULLMOD_ID)
            || variant.hasHullMod(NomadManager.MEMBER_HULLMOD_ID));
  }

  public static void write(FleetMemberAPI member, ShipVariantAPI variant, float cargoPct,
      float quartersPct) {
    float[] min = minimums();
    String value = (int) NomadCapacity.clampPct(cargoPct, min[0]) + ":"
        + (int) NomadCapacity.clampPct(quartersPct, min[1]);
    if (member != null && member.getId() != null) {
      NomadFleetData.getInstance().getDedication().put(member.getId(), value);
      NomadFleetData.getInstance().markChanged();
    }
    if (variant != null) {
      clearTags(variant);
      variant.addTag(TAG_PREFIX + value);
    }
  }

  public static void write(FleetMemberAPI member, float cargoPct, float quartersPct) {
    if (member != null) {
      write(member, member.getVariant(), cargoPct, quartersPct);
    }
  }

  public static void writeTagOnly(ShipVariantAPI variant, float cargoPct, float quartersPct) {
    write(null, variant, cargoPct, quartersPct);
  }

  public static void clearTags(ShipVariantAPI variant) {
    if (variant == null || variant.getTags() == null) {
      return;
    }
    List<String> ours = new ArrayList<String>(2);
    for (String tag : variant.getTags()) {
      if (tag != null && tag.startsWith(TAG_PREFIX)) {
        ours.add(tag);
      }
    }
    for (String tag : ours) {
      variant.removeTag(tag);
    }
  }

  static String tagOf(ShipVariantAPI variant) {
    if (variant == null || variant.getTags() == null) {
      return null;
    }
    for (String tag : variant.getTags()) {
      if (tag != null && tag.startsWith(TAG_PREFIX)) {
        return tag;
      }
    }
    return null;
  }

  static float[] parse(String tag) {
    return tag == null ? null : parseValue(tag.substring(TAG_PREFIX.length()));
  }

  static float[] parseValue(String value) {
    if (value == null) {
      return null;
    }
    String[] parts = value.split(":");
    if (parts.length != 2) {
      return null;
    }
    try {
      float[] min = minimums();
      return new float[] {
          NomadCapacity.clampPct(Float.parseFloat(parts[0].trim()), min[0]),
          NomadCapacity.clampPct(Float.parseFloat(parts[1].trim()), min[1]) };
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
