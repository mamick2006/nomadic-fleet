package nomad.campaign.econ;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import nomad.campaign.econ.model.Prereq;
import nomad.campaign.econ.model.Prereqs;
import nomad.campaign.econ.model.WorkCatalog;

public final class PrereqTableText {

  private PrereqTableText() {
  }

  public static List<ModTables.Row> rawRows(String path, List<String> problems) {
    List<ModTables.Row> rows = new ArrayList<ModTables.Row>();
    for (Map<String, String> cells : CsvText.rows(path, problems)) {
      ModTables.Row row = new ModTables.Row(cells, "", true);
      if (!row.id().isEmpty() && !row.id().startsWith("#")) {
        rows.add(row);
      }
    }
    return rows;
  }

  public static List<Prereq> read(String path, WorkCatalog catalog, Prereqs.Known known,
      List<String> problems) {
    return Prereqs.fromRows(rawRows(path, problems), catalog, known, problems);
  }
}
