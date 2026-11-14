package nomad.ui;

import java.util.HashMap;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import nomad.topbar.NomadCoreReflect;

public final class NomadShipHover {

  private NomadShipHover() {
  }

  private static final String FACTORY_CLASS = "com.fs.starfarer.ui.impl.StandardTooltipV2";
  private static final String FACTORY_METHOD = "createFleetMemberTooltipPreDeploy";
  private static final String WEAPON_METHOD = "createWeaponTooltip";

  private static boolean dead = false;
  private static boolean wingsDead = false;
  private static boolean weaponsDead = false;
  private static Object factoryMethod;
  private static Object weaponFactoryMethod;

  private static final Map<String, FleetMemberAPI> DISPLAY_MEMBERS = new HashMap<String, FleetMemberAPI>();
  private static MutableCharacterStatsAPI displayStats;

  public static void resetStaticsForLoad() {
    DISPLAY_MEMBERS.clear();
    displayStats = null;
  }

  static boolean attach(TooltipMakerAPI cell, String hullId) {
    return attach(cell, FleetMemberType.SHIP, hullId);
  }

  static boolean attachWing(TooltipMakerAPI cell, String wingId) {
    return attach(cell, FleetMemberType.FIGHTER_WING, wingId);
  }

  static boolean attachWeapon(TooltipMakerAPI cell, String weaponId) {
    if (weaponsDead || cell == null || weaponId == null) {
      return false;
    }
    Object spec;
    try {
      spec = Global.getSettings().getWeaponSpec(weaponId);
    } catch (Throwable notRegistered) {
      return false;
    }
    if (spec == null) {
      return false;
    }
    Object factory;
    Object setTooltip;
    try {
      factory = weaponFactoryMethod();
      setTooltip = NomadCoreReflect.methodNamed(cell.getClass(), "setTooltip", 2);
      if (setTooltip == null) {
        throw new NoSuchMethodException("no setTooltip(float, card) on " + cell.getClass().getName());
      }
    } catch (Throwable structural) {
      dieWeapons(structural);
      return false;
    }
    try {
      Class<?>[] pt = NomadCoreReflect.paramTypes(factory);
      MutableCharacterStatsAPI stats = displayStats();
      if (pt.length != 3 || !pt[0].isInstance(spec) || !pt[1].isInstance(stats)) {
        throw new NoSuchMethodException(FACTORY_CLASS + "." + WEAPON_METHOD + " does not take ("
            + spec.getClass().getName() + ", " + stats.getClass().getName() + ", null)");
      }
      Object card = NomadCoreReflect.invokeMethod(factory, null, new Object[] { spec, stats, null });
      if (!accepts(setTooltip, card)) {
        throw new NoSuchMethodException("setTooltip(float, card) does not take " + cardName(card));
      }
      NomadCoreReflect.invokeMethod(setTooltip, cell, new Object[] { Float.valueOf(0f), card });
      return true;
    } catch (Throwable t) {
      dieWeapons(t);
      return false;
    }
  }

  private static boolean attach(TooltipMakerAPI cell, FleetMemberType type, String id) {
    boolean wing = type == FleetMemberType.FIGHTER_WING;
    if (dead || (wing && wingsDead) || cell == null || id == null) {
      return false;
    }
    FleetMemberAPI member = displayMember(type, id);
    if (member == null) {
      return false;
    }
    return attachCard(cell, member, wing);
  }

  private static boolean attachCard(TooltipMakerAPI cell, FleetMemberAPI member, boolean wing) {
    Object factory;
    Object setTooltip;
    try {
      factory = factoryMethod();
      setTooltip = NomadCoreReflect.methodNamed(cell.getClass(), "setTooltip", 2);
      if (setTooltip == null) {
        throw new NoSuchMethodException("no setTooltip(float, card) on " + cell.getClass().getName());
      }
    } catch (Throwable structural) {
      die(structural);
      return false;
    }
    try {
      Object card = NomadCoreReflect.invokeMethod(factory, null, new Object[] { member, stats(member) });
      if (!accepts(setTooltip, card)) {
        throw new NoSuchMethodException("setTooltip(float, card) does not take " + cardName(card));
      }
      NomadCoreReflect.invokeMethod(setTooltip, cell, new Object[] { Float.valueOf(0f), card });
      return true;
    } catch (Throwable t) {
      if (wing) {
        dieWings(t);
      } else {
        die(t);
      }
      return false;
    }
  }

  private static Object factoryMethod() throws Exception {
    if (factoryMethod == null) {
      Object m = NomadCoreReflect.methodNamed(Class.forName(FACTORY_CLASS), FACTORY_METHOD, 2);
      if (m == null) {
        throw new NoSuchMethodException(FACTORY_CLASS + "." + FACTORY_METHOD + "(member, stats) not found");
      }
      factoryMethod = m;
    }
    return factoryMethod;
  }

  private static Object weaponFactoryMethod() throws Exception {
    if (weaponFactoryMethod == null) {
      Object m = NomadCoreReflect.methodNamed(Class.forName(FACTORY_CLASS), WEAPON_METHOD, 3);
      if (m == null) {
        throw new NoSuchMethodException(FACTORY_CLASS + "." + WEAPON_METHOD
            + "(spec, stats, mounted) not found");
      }
      weaponFactoryMethod = m;
    }
    return weaponFactoryMethod;
  }

  private static boolean accepts(Object method, Object card) throws Exception {
    Class<?>[] pt = NomadCoreReflect.paramTypes(method);
    return pt.length == 2 && pt[0] == float.class && pt[1].isInstance(card);
  }

  public static FleetMemberAPI displayMember(FleetMemberType type, String id) {
    String key = type.name() + "|" + id;
    FleetMemberAPI cached = DISPLAY_MEMBERS.get(key);
    if (cached != null) {
      return cached;
    }
    try {
      FleetMemberAPI m;
      if (type == FleetMemberType.FIGHTER_WING) {
        m = Global.getFactory().createFleetMember(FleetMemberType.FIGHTER_WING, id);
      } else {
        ShipHullSpecAPI spec = Global.getSettings().getHullSpec(id);
        if (spec == null) {
          return null;
        }
        ShipVariantAPI variant = NomadAutoforge.stockOrEmpty(id, spec, "nomad_af_display_" + id);
        m = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variant);
      }
      if (m == null) {
        return null;
      }
      DISPLAY_MEMBERS.put(key, m);
      return m;
    } catch (Throwable notRegistered) {
      return null;
    }
  }

  private static MutableCharacterStatsAPI stats(FleetMemberAPI member) {
    if (member.getCaptain() != null) {
      return member.getCaptain().getStats();
    }
    return displayStats();
  }

  private static MutableCharacterStatsAPI displayStats() {
    if (displayStats == null) {
      displayStats = Global.getFactory().createPerson().getStats();
    }
    return displayStats;
  }

  private static String cardName(Object card) {
    return card == null ? "null" : card.getClass().getName();
  }

  private static void die(Throwable t) {
    if (dead) {
      return;
    }
    dead = true;
    Global.getLogger(NomadShipHover.class).warn("Nomad Flotilla: the native ship hover could not attach ("
        + t.getClass().getSimpleName() + ": " + t.getMessage()
        + "); autoforge ship and wing rows fall back to the mod's own stat block, weapon rows are"
        + " unaffected.");
  }

  private static void dieWeapons(Throwable t) {
    if (weaponsDead) {
      return;
    }
    weaponsDead = true;
    Global.getLogger(NomadShipHover.class).warn("Nomad Flotilla: the native weapon hover could not attach ("
        + t.getClass().getSimpleName() + ": " + t.getMessage()
        + "); autoforge weapon rows fall back to the mod's own stat block, ship and wing rows are unaffected.");
  }

  private static void dieWings(Throwable t) {
    if (wingsDead) {
      return;
    }
    wingsDead = true;
    Global.getLogger(NomadShipHover.class).warn("Nomad Flotilla: the native hover would not take a fighter"
        + " wing (" + t.getClass().getSimpleName() + ": " + t.getMessage()
        + "); autoforge wing rows fall back to the mod's own stat block, ship rows are unaffected.");
  }
}
