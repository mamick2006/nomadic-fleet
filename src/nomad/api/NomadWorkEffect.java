package nomad.api;

public interface NomadWorkEffect {

  void applyTick(NomadWorkTick tick);

  void onApplied(String workId);

  void onCleared(String workId);
}
