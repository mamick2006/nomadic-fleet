package nomad.api;

public interface NomadItemEffect {

  void applyTick(NomadItemTick tick);

  void onApplied(String itemId);

  void onCleared(String itemId);
}
