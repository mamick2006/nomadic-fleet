package nomad.api;

public class BaseNomadWorkEffect implements NomadWorkEffect {

  @Override
  public void applyTick(NomadWorkTick tick) {
  }

  @Override
  public void onApplied(String workId) {
  }

  @Override
  public void onCleared(String workId) {
  }
}
