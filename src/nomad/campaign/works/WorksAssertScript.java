package nomad.campaign.works;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.util.IntervalUtil;

import nomad.campaign.NomadManager;

public class WorksAssertScript implements EveryFrameScript {

  private final IntervalUtil interval = new IntervalUtil(0.9f, 1.1f);
  private boolean warnedOnce;

  @Override
  public boolean isDone() {
    return false;
  }

  @Override
  public boolean runWhilePaused() {
    return true;
  }

  @Override
  public void advance(float amount) {
    interval.advance(amount);
    if (!interval.intervalElapsed()) {
      return;
    }
    try {
      NomadManager mgr = NomadManager.getInstance();
      CampaignFleetAPI fleet = mgr == null ? null : mgr.getFlagshipFleet();
      WorksEffects.assertBetweenTicks(fleet);
    } catch (Throwable t) {
      if (!warnedOnce) {
        warnedOnce = true;
        Global.getLogger(WorksAssertScript.class).warn("Nomad Flotilla: keeping the Works' effects"
            + " in step between economy ticks failed, so a mothballed Work may stay on until the"
            + " next tick and the Repair Yard bonus may not apply until the next game"
            + " load. The rest of the Flotilla is intact.", t);
      }
    }
  }
}
