package nomad.ui;

import nomad.data.NomadFleetData;

public final class NomadProgress {

  private NomadProgress() {
  }

  public static void advance(NomadFleetData data, float days, boolean paused) {
    if (data == null || paused) {
      return;
    }
    NomadAutoforge.advance(data);
  }
}
