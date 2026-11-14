package nomad.campaign.econ;

public final class NomadPickup {

  private NomadPickup() {
  }

  public static float compute(int marketSize, float residents, float freeBerths, EconConstants k) {
    float bySize = Math.max(k.pickupBase, k.pickupSizeCoef * (float) Math.pow(10.0, marketSize));
    float byPop = k.pickupPopFrac * residents;
    float offered = Math.max(k.pickupMin, Math.min(bySize, byPop));
    return Math.max(0f, Math.min(offered, Math.max(0f, freeBerths)));
  }
}
