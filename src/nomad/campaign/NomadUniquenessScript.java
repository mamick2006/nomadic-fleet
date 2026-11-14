package nomad.campaign;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.util.IntervalUtil;

public class NomadUniquenessScript implements EveryFrameScript {

  private final IntervalUtil interval = new IntervalUtil(9f, 11f);

  @Override
  public boolean isDone() {
    return false;
  }

  @Override
  public boolean runWhilePaused() {
    return false;
  }

  @Override
  public void advance(float amount) {
    interval.advance(amount);
    if (!interval.intervalElapsed()) {
      return;
    }
    NomadManager m = NomadManager.getInstance();
    if (m != null) {
      m.rescanForFlagship();
    }
  }
}
