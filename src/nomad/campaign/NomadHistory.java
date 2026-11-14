package nomad.campaign;

import java.util.List;

import nomad.data.NomadFleetData;

public final class NomadHistory {

  private NomadHistory() {
  }

  public static final int MAX_ENTRIES = 150;

  public static void addPressEvent(NomadFleetData data, nomad.campaign.econ.model.FlotillaEvent e) {
    add(data, NomadTickLog.pressLine(e));
  }

  public static void add(NomadFleetData data, String line) {
    if (line == null || line.isEmpty()) {
      return;
    }
    List<String> h = data.getHistory();
    h.add(0, line);
    while (h.size() > MAX_ENTRIES) {
      h.remove(h.size() - 1);
    }
  }

  public static void addDigest(NomadFleetData data, String line) {
    if (line == null || line.isEmpty()) {
      return;
    }
    add(data, NomadHistoryLines.digest(line));
  }
}
