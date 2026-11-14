package nomad.campaign.works;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.PlayerFleetPersonnelTracker;
import com.fs.starfarer.api.impl.campaign.velfield.SlipstreamVisibilityManager;
import com.fs.starfarer.api.util.Misc;

import nomad.NomadStrings;
import nomad.campaign.NomadModel;
import nomad.campaign.econ.Arrivals;
import nomad.campaign.econ.SupportEffects;
import nomad.campaign.econ.SupportPay;
import nomad.data.NomadFleetData;

public final class WorksCampaign {

  private WorksCampaign() {
  }

  private static final String HARBOR_MOD_ID = "nomad_passenger_terminal";
  public static final String SHUTTLES_MOD_ID = "nomad_cargo_shuttle_bay";
  private static final String GATE_NODE = "node_id_nomad_work_gate_freight_office";
  private static final String PATROL_NODE = "node_id_nomad_work_patrol_supply_depot";

  public static void onTick(CampaignFleetAPI fleet, NomadFleetData data, float monthFrac) {
    if (fleet == null || data == null || Global.getSector() == null) {
      return;
    }
    SupportEffects.Levels l = WorksEffects.levels();
    MarketAPI harbor = l.harbor ? orbitedColony(fleet, l.harborRadius) : null;
    MarketAPI shuttles = !l.shuttles ? null
        : l.harbor && l.shuttlesRadius == l.harborRadius ? harbor
            : orbitedColony(fleet, l.shuttlesRadius);
    passengerTerminal(fleet, data, monthFrac, l, harbor);
    cargoShuttleBay(data, l, shuttles);
    wayfinding(fleet, l.revealLy);
    gateFreightOffice(data, monthFrac, l.gateCredits);
    patrolSupplyDepot(monthFrac, l.patrolCredits);
    if (data.hasInsuranceClaims()) {
      WorksCampaignEventListener.settleInsurance(data);
    }
  }

  public static void onMonthEnd(NomadFleetData data) {
    if (data == null || Global.getSector() == null) {
      return;
    }
    float xp = WorksEffects.levels().marineXp;
    if (xp > 0f) {
      levyMarineXp(xp);
    }
  }

  private static void gateFreightOffice(NomadFleetData data, float monthFrac, float perMonth) {
    SupportPay.GateMonth m = gateMonth(data);
    float due = m.tick(perMonth, monthFrac);
    if (due > 0f) {
      NomadModel.payIncome(GATE_NODE,
          NomadStrings.get("nomad_works", "wk_name_gate_freight_office"), due);
    }
    data.setGateFreightOfficeAccrued(m.accrued);
  }

  private static SupportPay.GateMonth gateMonth(NomadFleetData data) {
    return new SupportPay.GateMonth(data.isGateTransitThisMonth(), data.getGateFreightOfficeAccrued());
  }

  public static void closeGateMonth(NomadFleetData data) {
    if (data == null) {
      return;
    }
    SupportPay.GateMonth m = gateMonth(data);
    float due = m.close();
    if (due > 0f && Global.getSector() != null) {
      NomadModel.payIncome(GATE_NODE,
          NomadStrings.get("nomad_works", "wk_name_gate_freight_office"), due);
    }
    data.setGateFreightOfficeAccrued(m.accrued);
    data.setGateTransitThisMonth(m.transit);
  }

  private static void patrolSupplyDepot(float monthFrac, float perMonth) {
    float pay = SupportPay.patrolPay(perMonth, monthFrac);
    if (pay > 0f) {
      NomadModel.payIncome(PATROL_NODE,
          NomadStrings.get("nomad_works", "wk_name_patrol_supply_depot"), pay);
    }
  }

  private static void cargoShuttleBay(NomadFleetData data, SupportEffects.Levels l,
      MarketAPI colony) {
    String cur = colony != null ? colony.getId() : null;
    String last = data.getShuttlesMarketId();
    if (last != null && !last.equals(cur)) {
      MarketAPI old = market(last);
      if (old != null) {
        old.getAccessibilityMod().unmodify(SHUTTLES_MOD_ID);
      }
    }
    if (colony != null) {
      colony.getAccessibilityMod().modifyFlat(SHUTTLES_MOD_ID, l.shuttlesAccess,
          NomadStrings.get("nomad_works", "wk_name_cargo_shuttle_bay"));
    }
    data.setShuttlesMarketId(cur);
  }

  private static void passengerTerminal(CampaignFleetAPI fleet, NomadFleetData data, float monthFrac,
      SupportEffects.Levels l, MarketAPI colony) {
    String cur = colony != null ? colony.getId() : null;
    String last = data.getHarborMarketId();

    if (last != null && !last.equals(cur)) {
      MarketAPI old = market(last);
      if (old != null) {
        old.getStability().unmodify(HARBOR_MOD_ID);
      }
    }
    if (colony != null) {
      colony.getStability().modifyFlat(HARBOR_MOD_ID, l.harborStability,
          NomadStrings.get("nomad_works", "wk_name_passenger_terminal"));
      intake(data, fleet, monthFrac, l.harborIntake);
    }
    data.setHarborMarketId(cur);
  }

  private static MarketAPI orbitedColony(CampaignFleetAPI fleet, float radius) {
    if (fleet.getContainingLocation() == null) {
      return null;
    }
    for (PlanetAPI planet : fleet.getContainingLocation().getPlanets()) {
      if (planet == null || planet.getMarket() == null) {
        continue;
      }
      MarketAPI m = planet.getMarket();
      if (!m.isPlayerOwned() || m.getSize() <= 0) {
        continue;
      }
      if (Misc.getDistance(fleet.getLocation(), planet.getLocation()) <= radius + planet.getRadius()) {
        return m;
      }
    }
    return null;
  }

  private static void intake(NomadFleetData data, CampaignFleetAPI fleet, float monthFrac,
      float perMonth) {
    float offered = perMonth * monthFrac;
    if (!(offered > 0f) || !fleet.isPlayerFleet()) {
      return;
    }
    float room = Arrivals.room(NomadModel.preview(data, fleet));
    float add = Arrivals.take(room, offered);
    if (add > 0f) {
      data.setResidents(data.getResidents() + add);
      data.markChanged();
    }
  }

  public static void clearColonyEffects(NomadFleetData data) {
    if (data == null || Global.getSector() == null) {
      return;
    }
    String last = data.getHarborMarketId();
    if (last != null) {
      MarketAPI old = market(last);
      if (old != null) {
        old.getStability().unmodify(HARBOR_MOD_ID);
      }
      data.setHarborMarketId(null);
    }
    String shuttles = data.getShuttlesMarketId();
    if (shuttles != null) {
      MarketAPI old = market(shuttles);
      if (old != null) {
        old.getAccessibilityMod().unmodify(SHUTTLES_MOD_ID);
      }
      data.setShuttlesMarketId(null);
    }
  }

  private static MarketAPI market(String id) {
    if (id == null || Global.getSector().getEconomy() == null) {
      return null;
    }
    for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
      if (id.equals(m.getId())) {
        return m;
      }
    }
    return null;
  }

  public static float wayfindingRevealLy(NomadFleetData data) {
    return data == null ? 0f : WorksEffects.levels().revealLy;
  }

  private static void wayfinding(CampaignFleetAPI fleet, float ly) {
    if (ly <= 0f) {
      return;
    }
    try {
      SlipstreamVisibilityManager.updateSlipstreamVisibility(fleet.getLocationInHyperspace(), ly);
    } catch (Throwable ignore) {
    }
  }

  private static void levyMarineXp(float xp) {
    try {
      PlayerFleetPersonnelTracker.getInstance().getMarineData().addXP(xp);
    } catch (Throwable ignore) {
    }
  }
}
