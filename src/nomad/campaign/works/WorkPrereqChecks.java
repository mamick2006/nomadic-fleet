package nomad.campaign.works;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.intel.contacts.ContactIntel;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.util.Misc;

import nomad.campaign.NomadManager;
import nomad.campaign.econ.model.Prereq;
import nomad.campaign.econ.model.Prereqs;
import nomad.data.NomadFleetData;
import nomad.ui.NomadItems;
import nomad.ui.NomadPrereqText;

public final class WorkPrereqChecks {

  private WorkPrereqChecks() {
  }

  public static List<Prereqs.Reading> readings(final List<String> items,
      final CampaignFleetAPI carrier) {
    List<Prereq> rows = WorkPrereqTable.read();
    if (rows.isEmpty()) {
      return Collections.emptyList();
    }
    final Set<String>[] hulls = new Set[1];
    NomadFleetData data = NomadFleetData.getOrNull();
    final List<nomad.campaign.econ.model.WorkState> works = data == null
        ? Collections.<nomad.campaign.econ.model.WorkState>emptyList() : data.getWorkStates();
    final float residents = data == null ? 0f : data.getResidents();
    final float slack = nomad.campaign.NomadModel.rules().prereqResidentsSlack;
    return Prereqs.readings(rows, new Prereqs.Check() {
      @Override
      public boolean met(Prereq row) {
        switch (row.kind) {
          case ITEM:
            return anyIn(row.values, items);
          case SHIP:
            if (hulls[0] == null) {
              hulls[0] = hulls(carrier);
            }
            return anyIn(row.values, hulls[0]);
          case LEVEL:
            return level() >= row.number;
          case OFFICERS:
            return officers() >= row.number;
          case STANDING:
            return standing(row);
          case MISSION:
            return Prereqs.missionMet(row.values, FLAGS, academy(), volturn());
          case COMMISSION:
            return commission(row);
          case COLONIES:
            return colonies() >= row.number;
          case CONTACTS:
            return contacts() >= row.number;
          case WORK:
            return Prereqs.workMet(row, works);
          case RESIDENTS:
            return Prereqs.residentsMet(row, works, residents, slack);
          case NONE:
          default:
            return true;
        }
      }
    });
  }

  private static boolean anyIn(List<String> values, java.util.Collection<String> have) {
    if (have == null) {
      return false;
    }
    for (String v : values) {
      if (have.contains(v)) {
        return true;
      }
    }
    return false;
  }

  static Set<String> hulls(CampaignFleetAPI carrier) {
    Set<String> out = new HashSet<String>();
    if (carrier == null || carrier.getFleetData() == null) {
      return out;
    }
    addHulls(out, carrier);
    CampaignFleetAPI player = Global.getSector() == null ? null : Global.getSector().getPlayerFleet();
    if (player != null && player != carrier && player.getFleetData() != null) {
      addHulls(out, player);
    }
    return out;
  }

  private static void addHulls(Set<String> out, CampaignFleetAPI fleet) {
    for (FleetMemberAPI m : fleet.getFleetData().getMembersListCopy()) {
      if (m.isMothballed() || !nomad(m.getVariant())) {
        continue;
      }
      ShipHullSpecAPI spec = m.getHullSpec();
      if (spec == null) {
        continue;
      }
      add(out, spec.getHullId());
      add(out, spec.getBaseHullId());
      if (spec.isDHull()) {
        add(out, spec.getDParentHullId());
      }
    }
  }

  private static boolean nomad(ShipVariantAPI v) {
    if (v == null) {
      return false;
    }
    for (String id : NomadManager.ALL_HULLMOD_IDS) {
      if (v.hasHullMod(id)) {
        return true;
      }
    }
    return false;
  }

  private static void add(Set<String> out, String id) {
    if (id != null && !id.isEmpty()) {
      out.add(id);
    }
  }

  private static final Prereqs.Flags FLAGS = new Prereqs.Flags() {
    @Override
    public boolean set(String key) {
      return Global.getSector().getMemoryWithoutUpdate().getBoolean(key);
    }
  };

  private static final String ACADEMY_ID = "station_galatia_academy";

  private static boolean academy() {
    return Global.getSector().getEntityById(ACADEMY_ID) != null;
  }

  private static boolean volturn() {
    MarketAPI m = Global.getSector().getEconomy().getMarket("volturn");
    return m != null && "sindrian_diktat".equals(m.getFactionId());
  }

  private static boolean commission(Prereq row) {
    String id = Misc.getCommissionFactionId();
    return id != null && (row.values.isEmpty() || row.values.contains(id));
  }

  private static int colonies() {
    return Misc.getPlayerMarkets(true).size();
  }

  private static int contacts() {
    int n = 0;
    for (IntelInfoPlugin i : Global.getSector().getIntelManager().getIntel(ContactIntel.class)) {
      ContactIntel.ContactState s = ((ContactIntel) i).getState();
      if (s == ContactIntel.ContactState.PRIORITY || s == ContactIntel.ContactState.NON_PRIORITY
          || s == ContactIntel.ContactState.SUSPENDED) {
        n++;
      }
    }
    return n;
  }

  private static int level() {
    try {
      return Global.getSector().getPlayerStats().getLevel();
    } catch (RuntimeException e) {
      return 0;
    }
  }

  private static int officers() {
    CampaignFleetAPI player = Global.getSector().getPlayerFleet();
    if (player == null || player.getFleetData() == null) {
      return 0;
    }
    return player.getFleetData().getOfficersCopy().size();
  }

  private static boolean standing(Prereq row) {
    RepLevel level;
    try {
      level = RepLevel.valueOf(row.atLeast);
    } catch (IllegalArgumentException e) {
      return true;
    }
    FactionAPI player = Global.getSector().getPlayerFaction();
    boolean any = false;
    for (String id : row.values) {
      if (Global.getSector().getFaction(id) == null) {
        continue;
      }
      any = true;
      if (player.isAtWorst(id, level)) {
        return true;
      }
    }
    return !any;
  }

  public static final NomadPrereqText.Names NAMES = new NomadPrereqText.Names() {
    @Override
    public String item(String id) {
      return withArticle(NomadItems.displayName(id));
    }

    @Override
    public String hull(String id) {
      String name = id;
      try {
        ShipHullSpecAPI spec = Global.getSettings().getHullSpec(id);
        name = spec.getHullName();
        if (name == null || name.trim().isEmpty()) {
          name = spec.getDesignation();
        }
      } catch (RuntimeException e) {
      }
      return withArticle(name);
    }

    @Override
    public String faction(String id) {
      FactionAPI f = Global.getSector() == null ? null : Global.getSector().getFaction(id);
      return f == null ? id : f.getDisplayNameWithArticle();
    }

    @Override
    public String standing(String level) {
      try {
        return RepLevel.valueOf(level).getDisplayName();
      } catch (IllegalArgumentException e) {
        return level.toLowerCase(Locale.ROOT);
      }
    }

    @Override
    public String work(String id) {
      return nomad.ui.NomadFlotillaView.workName(WorkCatalogTable.read(), id);
    }
  };

  private static String withArticle(String name) {
    return Misc.getAOrAnFor(name) + " " + name;
  }

  public static String removeWarning(NomadFleetData data, String itemId,
      nomad.ui.NomadWorkCardModel.Strings strings) {
    if (data == null) {
      return null;
    }
    List<String> works = Prereqs.disabledByRemoving(WorkPrereqTable.read(), data.getWorkStates(),
        data.getInstalledItems(), itemId);
    if (works.isEmpty()) {
      return null;
    }
    return NomadPrereqText.removeWarning(
        nomad.ui.NomadFlotillaView.workName(WorkCatalogTable.read(), works.get(0)), strings);
  }

  public static String lowerWarning(List<nomad.campaign.econ.model.WorkState> works,
      String workId, int toSize, nomad.ui.NomadWorkCardModel.Strings strings) {
    List<String> hit = Prereqs.disabledByLowering(WorkPrereqTable.read(), works, workId, toSize);
    if (hit.isEmpty()) {
      return null;
    }
    return NomadPrereqText.lowerWarning(
        nomad.ui.NomadFlotillaView.workName(WorkCatalogTable.read(), hit.get(0)), strings);
  }
}
