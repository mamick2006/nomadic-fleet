package nomad.campaign.econ;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ItemTableText {

  public static final String PATH = "data/config/nomad/nomad_items.csv";

  private ItemTableText() {
  }

  public static List<ItemRow> read(String path, List<String> problems) {
    List<ItemRow> rows = new ArrayList<ItemRow>();
    for (Map<String, String> cells : CsvText.rows(path, problems)) {
      ItemRow row = ItemRow.fromColumns(cells, "", true, problems);
      if (row != null) {
        rows.add(row);
      }
    }
    return rows;
  }
}
