package nomad.campaign.econ.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WorkCatalog {

  public static final String PATH = "data/config/nomad/nomad_work_sizes.csv";

  public static final WorkCatalog EMPTY =
      new WorkCatalog(new LinkedHashMap<String, WorkDef>());

  private final Map<String, WorkDef> byId;
  private final List<WorkDef> inOrder;

  private WorkCatalog(Map<String, WorkDef> byId) {
    this.byId = Collections.unmodifiableMap(byId);
    this.inOrder = Collections.unmodifiableList(new ArrayList<WorkDef>(byId.values()));
  }

  public static WorkCatalog build(List<WorkSize> rows, List<String> problems) {
    Map<String, List<WorkSize>> grouped = new LinkedHashMap<String, List<WorkSize>>();
    if (rows != null) {
      for (WorkSize row : rows) {
        List<WorkSize> list = grouped.get(row.work);
        if (list == null) {
          list = new ArrayList<WorkSize>();
          grouped.put(row.work, list);
        }
        list.add(row);
      }
    }
    Map<String, WorkDef> byId = new LinkedHashMap<String, WorkDef>();
    for (Map.Entry<String, List<WorkSize>> entry : grouped.entrySet()) {
      WorkDef def = define(entry.getKey(), entry.getValue(), problems);
      if (def != null) {
        byId.put(def.id, def);
      }
    }
    return new WorkCatalog(byId);
  }

  private static WorkDef define(String work, List<WorkSize> rows, List<String> problems) {
    WorkSize[] sizes = new WorkSize[rows.size()];
    for (WorkSize row : rows) {
      if (row.size > sizes.length) {
        problems.add(work + ": its sizes do not run from 1 with no gap (found size " + row.size
            + " among " + sizes.length + " row(s))");
        return null;
      }
      if (sizes[row.size - 1] != null) {
        problems.add(work + ": two rows give size " + row.size + " (" + sizes[row.size - 1].rowId
            + " and " + row.rowId + ")");
        return null;
      }
      sizes[row.size - 1] = row;
    }
    WorkSize first = sizes[0];
    if (!WorkDef.CATEGORIES.contains(first.category)) {
      problems.add(work + ": category '" + first.category + "' is not one of "
          + WorkDef.CATEGORIES);
      return null;
    }
    String scope = first.scope.isEmpty() ? WorkDef.SCOPE_FLOTILLA : first.scope;
    if (!WorkDef.SCOPE_FLEET.equals(scope) && !WorkDef.SCOPE_FLOTILLA.equals(scope)) {
      problems.add(work + ": scope '" + scope + "' is neither " + WorkDef.SCOPE_FLEET + " nor "
          + WorkDef.SCOPE_FLOTILLA);
      return null;
    }
    return new WorkDef(first, first.category, scope, sizes);
  }

  public WorkDef work(String id) {
    return id == null ? null : byId.get(id);
  }

  public WorkSize size(String id, int size) {
    WorkDef def = work(id);
    return def == null ? null : def.size(size);
  }

  public List<WorkDef> works() {
    return inOrder;
  }

  public List<String> ids() {
    return Collections.unmodifiableList(new ArrayList<String>(byId.keySet()));
  }

  public boolean isEmpty() {
    return byId.isEmpty();
  }
}
