package nomad.campaign.econ.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import nomad.campaign.econ.CsvText;

public final class WorkCatalogText {

  private WorkCatalogText() {
  }

  public static List<WorkSize> rows(String path, List<String> problems) {
    List<WorkSize> rows = new ArrayList<WorkSize>();
    for (Map<String, String> cells : CsvText.rows(path, problems)) {
      WorkSize row = WorkSize.fromColumns(cells, "", true, problems);
      if (row != null) {
        rows.add(row);
      }
    }
    return rows;
  }

  public static WorkCatalog read(String path, List<String> problems) {
    return WorkCatalog.build(rows(path, problems), problems);
  }
}
