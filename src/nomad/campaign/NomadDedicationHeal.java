package nomad.campaign;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.listeners.CoreUITabListener;
import com.fs.starfarer.api.campaign.listeners.RefitScreenListener;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.loading.VariantSource;

import nomad.data.NomadFleetData;

public class NomadDedicationHeal implements CoreUITabListener, RefitScreenListener {

  @Override
  public void reportAboutToOpenCoreTab(CoreUITabId tab, Object param) {
    heal();
  }

  @Override
  public void reportFleetMemberVariantSaved(FleetMemberAPI member, MarketAPI dockedAt) {
    heal();
  }

  public static void heal() {
    SectorAPI sector = Global.getSector();
    if (sector == null || NomadFleetData.getOrNull() == null) {
      return;
    }
    healFleet(sector.getPlayerFleet());
    CampaignFleetAPI flotilla = nomad.ui.NomadUi.flotillaFleet();
    if (flotilla != null && flotilla != sector.getPlayerFleet()) {
      healFleet(flotilla);
    }
    healMarket(nomad.ui.NomadUi.dockedMarket());
  }

  public static void reconcileOnLoad() {
    SectorAPI sector = Global.getSector();
    NomadFleetData data = NomadFleetData.getOrNull();
    if (sector == null || data == null) {
      return;
    }
    Set<String> live = new LinkedHashSet<String>();
    collect(live, sector.getPlayerFleet());
    for (com.fs.starfarer.api.campaign.StarSystemAPI sys : sector.getStarSystems()) {
      collectFleets(live, sys.getFleets());
    }
    collectFleets(live, sector.getHyperspace().getFleets());
    if (sector.getEconomy() != null) {
      for (MarketAPI market : sector.getEconomy().getMarketsCopy()) {
        collectMarket(live, market);
      }
    }
    heal();
    int dropped = data.reconcileDedication(live);
    if (dropped > 0) {
      Global.getLogger(NomadDedicationHeal.class).warn("Nomad Flotilla: dropped " + dropped
          + " stored ship dedication(s) for ships that no longer exist (most likely lost in battle);"
          + " every ship still in the fleet keeps its own percentages.");
    }
  }

  private static void healFleet(CampaignFleetAPI fleet) {
    if (fleet == null || fleet.getFleetData() == null) {
      return;
    }
    for (FleetMemberAPI m : fleet.getFleetData().getMembersListCopy()) {
      healMember(m);
    }
  }

  private static void healMarket(MarketAPI market) {
    if (market == null) {
      return;
    }
    for (SubmarketAPI sub : market.getSubmarketsCopy()) {
      CargoAPI cargo = sub.getCargo();
      if (cargo == null || cargo.getMothballedShips() == null) {
        continue;
      }
      for (FleetMemberAPI m : cargo.getMothballedShips().getMembersListCopy()) {
        healMember(m);
      }
    }
  }

  public static void healMember(FleetMemberAPI member) {
    if (member == null || member.getId() == null || member.getVariant() == null) {
      return;
    }
    ShipVariantAPI variant = member.getVariant();
    if (!variant.hasHullMod(NomadManager.FLAGSHIP_HULLMOD_ID)
        && !variant.hasHullMod(NomadManager.MEMBER_HULLMOD_ID)) {
      return;
    }
    String stored = NomadDedication.stored(member);
    float[] tagged = NomadDedication.parse(NomadDedication.tagOf(variant));
    if (stored == null) {
      if (tagged != null) {
        NomadFleetData.getInstance().getDedication()
            .put(member.getId(), (int) tagged[0] + ":" + (int) tagged[1]);
      }
      return;
    }
    float[] want = NomadDedication.parseValue(stored);
    if (want == null) {
      return;
    }
    if (tagged != null && (int) tagged[0] == (int) want[0] && (int) tagged[1] == (int) want[1]) {
      return;
    }
    NomadDedication.write(member, ownedVariant(member), want[0], want[1]);
    member.updateStats();
  }

  private static ShipVariantAPI ownedVariant(FleetMemberAPI member) {
    ShipVariantAPI variant = member.getVariant();
    if (variant.isStockVariant() || variant.getSource() != VariantSource.REFIT) {
      variant = variant.clone();
      variant.setSource(VariantSource.REFIT);
      member.setVariant(variant, false, false);
    }
    return variant;
  }

  private static void collectFleets(Set<String> out, List<CampaignFleetAPI> fleets) {
    if (fleets == null) {
      return;
    }
    for (CampaignFleetAPI f : fleets) {
      collect(out, f);
    }
  }

  private static void collect(Set<String> out, CampaignFleetAPI fleet) {
    if (fleet == null || fleet.getFleetData() == null) {
      return;
    }
    for (FleetMemberAPI m : fleet.getFleetData().getMembersListCopy()) {
      collectMember(out, m);
    }
  }

  private static void collectMarket(Set<String> out, MarketAPI market) {
    if (market == null) {
      return;
    }
    for (SubmarketAPI sub : market.getSubmarketsCopy()) {
      if (!playerHeld(market, sub)) {
        continue;
      }
      CargoAPI cargo = sub.getCargo();
      if (cargo == null || cargo.getMothballedShips() == null) {
        continue;
      }
      for (FleetMemberAPI m : cargo.getMothballedShips().getMembersListCopy()) {
        collectMember(out, m);
      }
    }
  }

  private static boolean playerHeld(MarketAPI market, SubmarketAPI sub) {
    if (sub == null || sub.getSpecId() == null) {
      return false;
    }
    return Submarkets.SUBMARKET_STORAGE.equals(sub.getSpecId()) || market.isPlayerOwned();
  }

  private static void collectMember(Set<String> out, FleetMemberAPI m) {
    if (m == null || m.getId() == null) {
      return;
    }
    out.add(m.getId());
  }
}
