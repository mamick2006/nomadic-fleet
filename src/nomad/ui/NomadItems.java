package nomad.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.SpecialItemSpecAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;

import nomad.NomadStrings;
import nomad.campaign.NomadModel;
import nomad.campaign.NomadSettings;
import nomad.campaign.econ.Commod;
import nomad.campaign.econ.EconConstants;
import nomad.campaign.econ.ItemRow;
import nomad.campaign.econ.Work;
import nomad.campaign.econ.model.Capacity;
import nomad.campaign.econ.model.Foundry;
import nomad.campaign.econ.model.Production;
import nomad.campaign.econ.model.Residents;
import nomad.campaign.econ.model.Rules;
import nomad.campaign.econ.model.Staffing;
import nomad.campaign.econ.model.Works;
import nomad.campaign.tables.ItemTable;
import nomad.data.NomadFleetData;

public final class NomadItems {

  private NomadItems() {
  }

  public static List<String> effective() {
    List<String> ids = ItemTable.ids();
    return ids.isEmpty() ? Work.ITEMS : ids;
  }

  public static final List<String> CORES = Arrays.asList(Work.ALPHA_CORE, Work.BETA_CORE, Work.GAMMA_CORE);

  public static boolean isCore(String id) {
    return CORES.contains(id);
  }

  public static boolean isEffective(String id) {
    if (ItemTable.row(id) != null) {
      return true;
    }
    return ItemTable.isEmpty() && Work.ITEMS.contains(id);
  }

  public static boolean isInstalled(NomadFleetData data, String id) {
    return data.getInstalledItems().contains(id);
  }

  public static List<String> installable(NomadFleetData data) {
    List<String> out = new ArrayList<String>();
    for (String id : effective()) {
      if (!isInstalled(data, id)) {
        out.add(id);
      }
    }
    return out;
  }

  public enum Where {
    FLEET,
    DETACHED,
    FLOTILLA,
    STORAGE
  }

  public static final class Owned {
    public final String id;
    public final Where where;
    public final String marketName;
    public final boolean effective;

    Owned(String id, Where where, String marketName, boolean effective) {
      this.id = id;
      this.where = where;
      this.marketName = marketName;
      this.effective = effective;
    }
  }

  public static List<Owned> ownedIn(NomadFleetData data, CargoAPI fleetCargo, CargoAPI detachedCargo,
      CargoAPI flotillaHold, CargoAPI storage, String storageMarketName) {
    Map<String, Owned> found = new LinkedHashMap<String, Owned>();
    scan(fleetCargo, Where.FLEET, null, data, found);
    scan(detachedCargo, Where.DETACHED, null, data, found);
    scan(flotillaHold, Where.FLOTILLA, null, data, found);
    scan(storage, Where.STORAGE, storageMarketName, data, found);
    return new ArrayList<Owned>(found.values());
  }

  public static List<Owned> owned(NomadFleetData data) {
    CampaignFleetAPI player = Global.getSector() == null ? null : Global.getSector().getPlayerFleet();
    CargoAPI playerCargo = player == null ? null : player.getCargo();
    CampaignFleetAPI carrier = NomadUi.flotillaFleet();
    CargoAPI detachedCargo = carrier == null || carrier == player ? null : carrier.getCargo();
    MarketAPI market = NomadUi.dockedMarket();
    SubmarketAPI sm = market == null ? null : market.getSubmarket(Submarkets.SUBMARKET_STORAGE);
    CargoAPI storage = null;
    try {
      storage = sm == null ? null : sm.getCargo();
    } catch (Exception e) {
      storage = null;
    }
    return ownedIn(data, playerCargo, detachedCargo, data.getFlotillaCargo(), storage,
        market == null ? null : market.getName());
  }

  private static void scan(CargoAPI cargo, Where where, String marketName, NomadFleetData data,
      Map<String, Owned> out) {
    if (cargo == null) {
      return;
    }
    for (CargoStackAPI s : cargo.getStacksCopy()) {
      String id = idOf(s);
      if (id == null || isInstalled(data, id) || out.containsKey(id)) {
        continue;
      }
      out.put(id, new Owned(id, where, marketName, isEffective(id)));
    }
  }

  private static String idOf(CargoStackAPI s) {
    if (s.isCommodityStack()) {
      String cid = s.getCommodityId();
      return cid != null && isCore(cid) ? cid : null;
    }
    if (!s.isSpecialStack() || s.getSpecialItemSpecIfSpecial() == null) {
      return null;
    }
    SpecialItemSpecAPI spec = s.getSpecialItemSpecIfSpecial();
    String id = spec.getId();
    if (isEffective(id)) {
      noteUntagged(spec, id);
      return id;
    }
    return spec.hasTag(Items.TAG_COLONY_ITEM) || spec.hasTag(Work.TAG_ITEM) ? id : null;
  }

  private static volatile boolean untaggedNoted;

  private static void noteUntagged(SpecialItemSpecAPI spec, String id) {
    if (untaggedNoted || spec.hasTag(Items.TAG_COLONY_ITEM) || spec.hasTag(Work.TAG_ITEM)) {
      return;
    }
    untaggedNoted = true;
    Global.getLogger(NomadItems.class).info("Nomad Flotilla: another mod has altered the item data for"
        + " '" + id + "', which no longer carries the colony-item tag; the Flotilla recognised it from"
        + " its own items table and it works normally.");
  }

  public static boolean install(NomadFleetData data, String id) {
    if (id == null || !isEffective(id) || isInstalled(data, id)) {
      return false;
    }
    for (Owned o : owned(data)) {
      if (!id.equals(o.id)) {
        continue;
      }
      if (takeOne(cargoFor(data, o), id)) {
        data.getInstalledItems().add(id);
        return true;
      }
      return false;
    }
    return false;
  }

  private static CargoAPI cargoFor(NomadFleetData data, Owned o) {
    if (o.where == Where.FLOTILLA) {
      return data.getFlotillaCargo();
    }
    if (o.where == Where.STORAGE) {
      MarketAPI market = NomadUi.dockedMarket();
      SubmarketAPI sm = market == null ? null : market.getSubmarket(Submarkets.SUBMARKET_STORAGE);
      try {
        return sm == null ? null : sm.getCargo();
      } catch (Exception e) {
        return null;
      }
    }
    if (o.where == Where.DETACHED) {
      CampaignFleetAPI carrier = NomadUi.flotillaFleet();
      return carrier == null ? null : carrier.getCargo();
    }
    return Global.getSector() == null || Global.getSector().getPlayerFleet() == null
        ? null : Global.getSector().getPlayerFleet().getCargo();
  }

  private static boolean takeOne(CargoAPI cargo, String id) {
    if (cargo == null) {
      return false;
    }
    for (CargoStackAPI s : cargo.getStacksCopy()) {
      if (!id.equals(idOf(s))) {
        continue;
      }
      if (s.isCommodityStack()) {
        cargo.removeCommodity(id, 1f);
      } else {
        cargo.removeItems(s.getType(), s.getData(), 1f);
      }
      return true;
    }
    return false;
  }

  public static String displayName(String id) {
    try {
      if (isCore(id)) {
        CommoditySpecAPI c = Global.getSettings().getCommoditySpec(id);
        return c != null ? c.getName() : id;
      }
      SpecialItemSpecAPI spec = Global.getSettings().getSpecialItemSpec(id);
      return spec != null ? spec.getName() : id;
    } catch (Exception e) {
      return id;
    }
  }

  public static String iconName(String id) {
    try {
      String path;
      if (isCore(id)) {
        CommoditySpecAPI c = Global.getSettings().getCommoditySpec(id);
        path = c == null ? null : c.getIconName();
      } else {
        SpecialItemSpecAPI spec = Global.getSettings().getSpecialItemSpec(id);
        path = spec == null ? null : spec.getIconName();
      }
      if (path == null || path.isEmpty()) {
        return null;
      }
      SpriteAPI s = Global.getSettings().getSprite(path);
      return s == null || s.getWidth() <= 0f || s.getHeight() <= 0f ? null : path;
    } catch (Exception e) {
      return null;
    }
  }

  public static boolean gameDefines(String id) {
    try {
      return isCore(id)
          ? Global.getSettings().getCommoditySpec(id) != null
          : Global.getSettings().getSpecialItemSpec(id) != null;
    } catch (Exception e) {
      return false;
    }
  }

  public static String effectLine(String id, EconConstants k) {
    boolean normalOrStory = NomadSettings.isNormalOrStory();
    List<String> one = Collections.singletonList(id);
    String line = workLine(id, one, normalOrStory, k);
    float efficiency = k.itemEfficiencyBonus(id);
    if (Work.FULLERENE_SPOOL.equals(id) || Work.CRYOARITHMETIC_ENGINE.equals(id)) {
      return line;
    }
    ItemRow row = ItemTable.row(id);
    if (line == null && row != null && !row.ownRow && !row.effect.isEmpty()) {
      return row.effect;
    }
    if (efficiency > 0f) {
      String eff = text("shell_item_efficiency", NomadUi.pct(efficiency));
      return line == null ? eff : line + " " + eff;
    }
    if (line != null) {
      return line;
    }
    if (row != null && !row.effect.isEmpty()) {
      return row.effect;
    }
    return text("it_effect_none");
  }

  public static final class EffectRow {
    public final String value;
    public final String label;
    public final String sentence;

    EffectRow(String value, String label, String sentence) {
      this.value = value;
      this.label = label;
      this.sentence = sentence;
    }
  }

  private static final Pattern FIGURE_LAST = Pattern.compile("^(.*\\S) ([+-]\\d+(?:[.,]\\d+)*%?)$");

  public static List<EffectRow> effectRows(String line) {
    List<EffectRow> out = new ArrayList<EffectRow>();
    if (line == null) {
      return out;
    }
    for (String sentence : line.trim().split("(?<=\\.)\\s+")) {
      String s = sentence.trim();
      if (s.isEmpty()) {
        continue;
      }
      String body = s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
      List<EffectRow> rows = new ArrayList<EffectRow>();
      for (String part : body.split(", ")) {
        Matcher m = FIGURE_LAST.matcher(part.trim());
        if (!m.matches()) {
          rows = null;
          break;
        }
        rows.add(new EffectRow(m.group(2), capital(m.group(1)), capital(part.trim()) + "."));
      }
      if (rows == null) {
        out.add(new EffectRow(null, null, s));
      } else {
        out.addAll(rows);
      }
    }
    return out;
  }

  private static String capital(String s) {
    return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
  }

  private static String workLine(String id, List<String> one, boolean normalOrStory, EconConstants k) {
    if (Work.SOIL_NANITES.equals(id)) {
      return text("shell_item_food", pctOf(Production.itemYield(Commod.FOOD, one, normalOrStory, k) - 1f));
    }
    if (Work.BIOFACTORY_EMBRYO.equals(id)) {
      return text("it_effect_biofactory",
          pctOf(Production.itemYield(Commod.FOOD, one, normalOrStory, k) - 1f),
          pctOf(Production.itemYield(Commod.LUXURY_GOODS, one, normalOrStory, k) - 1f),
          pctOf(Production.itemYield(Works.RESIDENTS, one, normalOrStory, k) - 1f));
    }
    if (Work.SYNCHROTRON.equals(id)) {
      return text("shell_item_synchrotron", pctOf(1f - Production.inputFactor(null,
          Collections.singletonList(Commod.FUEL), Commod.VOLATILES, one, normalOrStory, rules(), k)));
    }
    if (Work.CATALYTIC_CORE.equals(id)) {
      return text("shell_item_catalytic", pctOf(1f - Production.inputFactor(null,
          Collections.singletonList(Commod.METALS), Commod.ORE, one, normalOrStory, rules(), k)));
    }
    if (Work.CORRUPTED_NANOFORGE.equals(id) || Work.PRISTINE_NANOFORGE.equals(id)) {
      float dmods = Work.PRISTINE_NANOFORGE.equals(id) ? k.autoforgeDmodPristine
          : k.autoforgeDmodCorrupted;
      int n = Math.max(0, Math.round(dmods));
      return text(n == 1 ? "shell_item_nanoforge_one" : "shell_item_nanoforge_many",
          pctOf(Production.itemYield(Commod.SUPPLIES, one, normalOrStory, k) - 1f),
          pctOf(Production.itemYield(Works.POINTS, one, normalOrStory, k) - 1f), "" + n);
    }
    if (Work.FULLERENE_SPOOL.equals(id)) {
      return text("it_effect_fullerene",
          pctOf(Capacity.flotillaSpace(1f, one, rules()) - 1f), pctOf(k.itemEfficiencyBonus(id)));
    }
    if (Work.ORBITAL_FUSION_LAMP.equals(id)) {
      return text("shell_item_lamp",
          pctOf(Residents.effectiveBerths(1f, one, null, normalOrStory, k) - 1f));
    }
    if (Work.MANTLE_BORE.equals(id)) {
      return text("shell_item_mantle_bore",
          pctOf(Production.itemYield(Works.MINING_RIG, Commod.ORE, one, normalOrStory, k) - 1f));
    }
    if (Work.PLASMA_DYNAMO.equals(id)) {
      return text("shell_item_plasma_dynamo",
          pctOf(Production.itemYield(Works.MINING_RIG, Commod.VOLATILES, one, normalOrStory, k) - 1f));
    }
    if (Work.CRYOARITHMETIC_ENGINE.equals(id)) {
      return text("shell_item_cryoarithmetic",
          pctOf(Production.itemYield(Works.POINTS, one, normalOrStory, k) - 1f),
          pctOf(k.itemEfficiencyBonus(id)));
    }
    if (Work.DEALMAKER_HOLOSUITE.equals(id)) {
      return text("it_effect_dealmaker",
          pctOf(Production.itemYield(Works.CREDITS, one, normalOrStory, k) - 1f),
          pctOf(1f - Foundry.materialsMult(one, normalOrStory, k)));
    }
    if (Work.DRONE_REPLICATOR.equals(id)) {
      return text("it_effect_drone_replicator",
          pctOf(Production.itemYield(Commod.HAND_WEAPONS, one, normalOrStory, k) - 1f),
          pctOf(Math.min(1f, Math.max(0f, k.itemDroneReplicatorStaff))));
    }
    if (isCore(id)) {
      return text("shell_item_core", pctOf(1f - Staffing.coreStaffMult(one, k)));
    }
    return null;
  }

  private static String text(String key, String... args) {
    return args.length == 0 ? NomadStrings.get("nomad_items", key)
        : NomadStrings.get("nomad_items", key, (Object[]) args);
  }

  private static String pctOf(float fraction) {
    return NomadUi.pct(Math.max(0f, fraction));
  }

  private static Rules rules() {
    return NomadModel.rules();
  }
}
