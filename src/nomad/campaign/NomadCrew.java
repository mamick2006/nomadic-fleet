package nomad.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;

import nomad.NomadStrings;
import nomad.campaign.econ.model.Residents;
import nomad.data.NomadFleetData;

public final class NomadCrew {

  private NomadCrew() {
  }

  public static float crewBasePrice() {
    return Global.getSettings().getCommoditySpec(Commodities.CREW).getBasePrice();
  }

  public static int priceToTake(int count) {
    return Math.round(crewBasePrice() * Math.max(0, count));
  }

  private static float reserve() {
    return NomadSettings.getFloat("nomad_crew_extract_reserve", 10f);
  }

  public static boolean canMoveCrew(NomadFleetData data) {
    return data != null && data.isFounded() && !data.isPaused() && !data.isDetached();
  }

  public static boolean canTakeCrew(int count) {
    if (count <= 0) {
      return false;
    }
    NomadFleetData data = NomadFleetData.getInstance();
    if (!canMoveCrew(data)) {
      return false;
    }
    if (Math.round(data.getResidents()) < count + reserve()) {
      return false;
    }
    return Global.getSector().getPlayerFleet().getCargo().getCredits().get() >= priceToTake(count);
  }

  public static int takeCrewFromFlotilla(int count) {
    if (!canTakeCrew(count)) {
      return 0;
    }
    NomadFleetData data = NomadFleetData.getInstance();
    CargoAPI player = Global.getSector().getPlayerFleet().getCargo();
    player.getCredits().subtract(priceToTake(count));
    data.setResidents(data.getResidents() - count);
    player.addCrew(count);
    data.markChanged();
    NomadHistory.add(data, NomadStrings.get("nomad_history", "hist_crew_out", count));
    return count;
  }

  public enum AddLimit {
    NONE,
    BERTHS,
    SKELETON
  }

  public static int addable(int playerCrew, float fleetMinCrew, float berths, float residents) {
    int aboveSkeleton = playerCrew - (int) Math.ceil(fleetMinCrew);
    int freeBerths = (int) Math.floor(berths - residents);
    return Math.max(0, Math.min(aboveSkeleton, freeBerths));
  }

  public static AddLimit addLimit(int playerCrew, float fleetMinCrew, float berths, float residents) {
    if (addable(playerCrew, fleetMinCrew, berths, residents) > 0) {
      return AddLimit.NONE;
    }
    return Math.floor(berths - residents) < 1 ? AddLimit.BERTHS : AddLimit.SKELETON;
  }

  public static int addableNow(NomadFleetData data) {
    CampaignFleetAPI fleet = Global.getSector() == null ? null : Global.getSector().getPlayerFleet();
    if (fleet == null || fleet.getCargo() == null || !canMoveCrew(data)) {
      return 0;
    }
    return (int) Residents.maxCrewIn(data.getResidents(), consoleBerths(data),
        fleet.getCargo().getCrew(), fleet.getFleetData().getMinCrew());
  }

  private static float consoleBerths(NomadFleetData data) {
    return NomadModel.preview(data, null).berths;
  }

  public static int freeBerths(NomadFleetData data) {
    if (data == null) {
      return 0;
    }
    return Math.max(0, (int) Math.floor(consoleBerths(data) - data.getResidents()));
  }

  public static int addResidents(NomadFleetData data, int amount) {
    int add = Math.min(Math.max(0, amount), freeBerths(data));
    if (add > 0) {
      data.setResidents(data.getResidents() + add);
      data.markChanged();
    }
    return add;
  }

  public static int addCrewToFlotilla(int count) {
    if (count <= 0) {
      return 0;
    }
    NomadFleetData data = NomadFleetData.getInstance();
    int take = Math.min(count, addableNow(data));
    if (take <= 0) {
      return 0;
    }
    CargoAPI player = Global.getSector().getPlayerFleet().getCargo();
    player.removeCrew(take);
    data.setResidents(data.getResidents() + take);
    data.markChanged();
    NomadHistory.add(data, NomadStrings.get("nomad_history", "hist_crew_in", take));
    return take;
  }
}
