package nomad.api;

public interface NomadTableTick {

  float getProductionEfficiency();

  float getMonthFraction();

  float getResidents();

  boolean isDetached();

  boolean areNeedsMet();

  void addCredits(float credits);

  void addCommodity(String commodityId, float units);

  void addFleetStat(NomadFleetStat stat, float amount);
}
