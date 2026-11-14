package nomad.campaign;

import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;

import nomad.campaign.econ.NomadCapacity;
import nomad.campaign.econ.ShellRules;
import nomad.campaign.works.WorksEffects;
import nomad.data.NomadFleetData;

public class NomadManager {

  public static final String KEY = "$nomad_manager";
  public static final String FLAGSHIP_HULLMOD_ID = "nomad_flagship";
  public static final String MEMBER_HULLMOD_ID = "nomad_member";
  public static final String[] ALL_HULLMOD_IDS = { FLAGSHIP_HULLMOD_ID, MEMBER_HULLMOD_ID };
  public static final String NOMAD_FLEET_FLAG = "$nomad_isNomadFleet";

  public static NomadManager getInstance() {
    SectorAPI sector = Global.getSector();
    if (sector == null) {
      return null;
    }
    Object o = sector.getMemoryWithoutUpdate().get(KEY);
    if (o instanceof NomadManager) {
      return (NomadManager) o;
    }
    NomadManager m = new NomadManager();
    sector.getMemoryWithoutUpdate().set(KEY, m);
    return m;
  }

  private transient FleetMemberAPI flagship;
  private transient CampaignFleetAPI flagshipFleet;

  public boolean flagshipExists() {
    validateFlagship();
    return flagship != null;
  }

  public FleetMemberAPI getFlagshipMember() {
    validateFlagship();
    return flagship;
  }

  public CampaignFleetAPI getFlagshipFleet() {
    validateFlagship();
    return flagshipFleet;
  }

  private void validateFlagship() {
    if (flagship != null && !carriesMod(flagship)) {
      flagship = null;
      flagshipFleet = null;
      announce();
    }
  }

  private static boolean carriesMod(FleetMemberAPI member) {
    return member.getVariant() != null && member.getVariant().hasHullMod(FLAGSHIP_HULLMOD_ID);
  }

  private void announce() {
    try {
      tell();
    } catch (RuntimeException e) {
      if (!warnedTell) {
        warnedTell = true;
        Global.getLogger(NomadManager.class).warn("Nomad Flotilla: reading the fleet that carries"
            + " the flagship failed (" + e + "). The top bar and the Flotilla screen may show the"
            + " old state until the next economy tick.");
      }
    }
  }

  private static boolean warnedTell;

  private void tell() {
    NomadFleetData data = NomadFleetData.getOrNull();
    if (data == null || !data.isFounded()) {
      return;
    }
    ShellRules.Carrier now = read(data);
    ShellRules.Carrier before = data.getCarrierReading();
    if (!ShellRules.moved(before, now)) {
      return;
    }
    data.setCarrierReading(now);
    boolean pausedFlipped = data.isPaused() != now.paused();
    if (pausedFlipped) {
      data.setPaused(now.paused());
    }
    data.markChanged();
    boolean carrierChanged = before != null && (before.where != now.where
        || (before.flagshipId == null ? now.flagshipId != null
            : !before.flagshipId.equals(now.flagshipId)));
    if (pausedFlipped || carrierChanged) {
      WorksEffects.carrierChanged();
    }
  }

  private ShellRules.Carrier read(NomadFleetData data) {
    CampaignFleetAPI fleet = flagshipFleet;
    if (fleet == null || fleet.getFleetData() == null) {
      return new ShellRules.Carrier(ShellRules.Carrier.NONE, null, 0, 0f, 0f);
    }
    NomadCapacity.Capacity cap = NomadCapacityUtil.forFlotilla(data, fleet);
    return new ShellRules.Carrier(
        fleet.isPlayerFleet() ? ShellRules.Carrier.WITH_FLEET : ShellRules.Carrier.DETACHED,
        flagship == null ? null : flagship.getId(), fleet.getFleetData().getNumMembers(),
        cap.space, cap.berths);
  }

  public void fleetSynced(CampaignFleetAPI fleet) {
    if (fleet == null || fleet.getFleetData() == null) {
      return;
    }
    if (fleet == flagshipFleet) {
      FleetMemberAPI found = findFlagshipIn(fleet.getFleetData());
      if (found == null) {
        flagshipFleet = null;
        if (flagship != null && !carriesMod(flagship)) {
          flagship = null;
        }
      } else {
        flagship = found;
      }
    } else if (flagshipFleet == null && fleet.isPlayerFleet()) {
      FleetMemberAPI found = findFlagshipIn(fleet.getFleetData());
      if (found == null) {
        return;
      }
      flagship = found;
      flagshipFleet = fleet;
    } else {
      return;
    }
    announce();
  }

  public void rescanForFlagship() {
    FleetMemberAPI wasFlagship = flagship;
    CampaignFleetAPI wasFleet = flagshipFleet;
    sweep();
    NomadFleetData data = NomadFleetData.getOrNull();
    if (flagship != wasFlagship || flagshipFleet != wasFleet
        || (data != null && data.getCarrierReading() == null)) {
      announce();
    }
  }

  private void sweep() {
    SectorAPI sector = Global.getSector();
    if (sector == null) {
      flagship = null;
      flagshipFleet = null;
      return;
    }
    if (flagshipFleet != null && flagshipFleet != sector.getPlayerFleet()
        && flagshipFleet.getContainingLocation() != null
        && flagshipFleet.getMemoryWithoutUpdate().getBoolean(NOMAD_FLEET_FLAG)) {
      FleetMemberAPI cached = findFlagshipIn(flagshipFleet.getFleetData());
      if (cached != null) {
        flagship = cached;
        return;
      }
    }
    flagship = null;
    flagshipFleet = null;
    CampaignFleetAPI pf = sector.getPlayerFleet();
    if (pf != null) {
      FleetMemberAPI m = findFlagshipIn(pf.getFleetData());
      if (m != null) {
        flagship = m;
        flagshipFleet = pf;
        return;
      }
    }
    for (StarSystemAPI sys : sector.getStarSystems()) {
      if (scanFleets(sys.getFleets())) {
        return;
      }
    }
    if (scanFleets(sector.getHyperspace().getFleets())) {
      return;
    }
    if (sector.getEconomy() != null) {
      for (MarketAPI market : sector.getEconomy().getMarketsCopy()) {
        for (SubmarketAPI sub : market.getSubmarketsCopy()) {
          CargoAPI cargo = sub.getCargo();
          if (cargo == null) {
            continue;
          }
          FleetMemberAPI m = findFlagshipIn(cargo.getMothballedShips());
          if (m != null) {
            flagship = m;
            flagshipFleet = null;
            return;
          }
        }
      }
    }
  }

  private boolean scanFleets(List<CampaignFleetAPI> fleets) {
    if (fleets == null) {
      return false;
    }
    SectorAPI sector = Global.getSector();
    for (CampaignFleetAPI f : fleets) {
      if (f == null) {
        continue;
      }
      boolean ours = f.isPlayerFleet()
          || f.getFaction() == sector.getPlayerFaction()
          || f.getMemoryWithoutUpdate().getBoolean(NOMAD_FLEET_FLAG);
      if (!ours) {
        continue;
      }
      FleetMemberAPI m = findFlagshipIn(f.getFleetData());
      if (m != null) {
        flagship = m;
        flagshipFleet = f;
        return true;
      }
    }
    return false;
  }

  public void eagerUpdate(FleetMemberAPI member, boolean added) {
    if (member == null) {
      return;
    }
    if (added) {
      if (!carriesMod(member)) {
        return;
      }
      FleetDataAPI fd = member.getFleetData();
      CampaignFleetAPI fleet = fd != null ? fd.getFleet() : null;
      if (member == flagship && (fleet == null || fleet == flagshipFleet)) {
        return;
      }
      if (flagship != null && flagship != member && flagshipFleet != null && carriesMod(flagship)
          && flagship.getFleetData() == flagshipFleet.getFleetData()) {
        return;
      }
      flagship = member;
      if (fleet != null) {
        flagshipFleet = fleet;
      }
      announce();
    } else if (member == flagship) {
      flagship = null;
      flagshipFleet = null;
      announce();
    }
  }

  private static FleetMemberAPI findFlagshipIn(FleetDataAPI data) {
    if (data == null) {
      return null;
    }
    for (FleetMemberAPI m : data.getMembersListCopy()) {
      if (m == null || m.getVariant() == null) {
        continue;
      }
      if (m.getVariant().hasHullMod(FLAGSHIP_HULLMOD_ID)) {
        return m;
      }
    }
    return null;
  }
}
