package nomad.api;

import java.util.List;

import nomad.campaign.econ.model.WorkState;

public final class NomadWorks {

  public interface Source {
    List<WorkState> works();
  }

  private static volatile Source source;

  private NomadWorks() {
  }

  public static void install(Source reader) {
    source = reader;
  }

  public static int getEffectSize(String workId) {
    Source reader = source;
    return reader == null ? 0 : effectSize(reader.works(), workId);
  }

  public static boolean isEffectOn(String workId) {
    return getEffectSize(workId) > 0;
  }

  public static int effectSize(List<WorkState> works, String workId) {
    if (works == null || workId == null) {
      return 0;
    }
    for (WorkState w : works) {
      if (w != null && workId.equals(w.id)) {
        boolean on = w.effectOn && w.isBuilt() && !w.mothballed && !w.suspended
            && !w.standingDown && !w.prereqUnmet;
        return on ? w.size : 0;
      }
    }
    return 0;
  }
}
