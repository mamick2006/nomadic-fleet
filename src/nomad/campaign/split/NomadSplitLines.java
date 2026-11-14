package nomad.campaign.split;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import nomad.NomadStrings;
import nomad.campaign.NomadModel;
import nomad.campaign.econ.SplitShortfall;
import nomad.campaign.econ.model.WorkCatalog;
import nomad.campaign.econ.model.WorkDef;
import nomad.ui.NomadConfirm;
import nomad.ui.NomadUi;
import nomad.ui.NomadWorkActions;

public final class NomadSplitLines {

  private NomadSplitLines() {
  }

  private static final float PAD = 3f;
  private static final float OPAD = 10f;
  private static final float HOVER_GAP = 8f;
  public static final float HOVER_W = 380f;
  public static final float LONG_ENOUGH = 60f;

  public static String units(float v) {
    return Misc.getWithDGS(Math.round(v));
  }

  public static void hover(TooltipMakerAPI t) {
    t.addTitle(NomadWorkActions.label(NomadWorkActions.Verb.SPLIT), NomadUi.base());
    t.addPara(NomadStrings.get("nomad_flotilla", "ui4_split_tip"), OPAD);
    String over = firstOver(NomadSplitter.preview());
    if (over != null) {
      NomadConfirm.marked(t, over, NomadUi.bad(), HOVER_GAP);
    }
    t.addPara(NomadStrings.get("nomad_flotilla", "ui4_split_click"), NomadUi.gray(), HOVER_GAP);
  }

  private static String firstOver(NomadSplitter.SplitPreview p) {
    if (p == null) {
      return null;
    }
    if (p.cargoOver() > 0.5f) {
      return NomadStrings.get("nomad_overview", "ov_split_overcap_cargo", units(p.cargoOver()));
    }
    if (p.fuelOver() > 0.5f) {
      return NomadStrings.get("nomad_overview", "ov_split_overcap_fuel", units(p.fuelOver()));
    }
    if (p.crewOver() > 0.5f) {
      return NomadStrings.get("nomad_overview", "ov_split_overcap_crew", units(p.crewOver()));
    }
    return null;
  }

  public static void rows(TooltipMakerAPI t, SplitShortfall s, float pad) {
    WorkCatalog catalog = NomadModel.catalog();
    float next = pad;
    next = berths(t, s, next) ? PAD : next;
    next = space(t, s, next) ? PAD : next;
    next = targets(t, s, next) ? PAD : next;
    next = suspended(t, s, catalog, next) ? PAD : next;
    lost(t, s, next);
  }

  private static boolean space(TooltipMakerAPI t, SplitShortfall s, float pad) {
    if (!s.spaceDrops()) {
      return false;
    }
    String before = NomadUi.i(s.spaceBefore);
    String after = NomadUi.i(s.spaceAfter);
    String used = NomadUi.i(s.spaceUsed);
    say(t, NomadUi.hl(), pad, NomadStrings.get("nomad_overview", "ov_split_flotilla_space",
        before, after, used), before, after, used);
    return true;
  }

  private static boolean targets(TooltipMakerAPI t, SplitShortfall s, float pad) {
    if (s.targets.isEmpty()) {
      return false;
    }
    List<String> items = new ArrayList<String>();
    for (SplitShortfall.Target cut : s.targets) {
      items.add(NomadStrings.get("nomad_overview", "detach_split_target_item",
          NomadUi.commodityName(cut.good), NomadUi.i(cut.from), NomadUi.i(cut.to)));
    }
    String list = join(items);
    say(t, NomadUi.warn(), pad, NomadStrings.get("nomad_overview", "detach_split_targets", list),
        list);
    return true;
  }

  private static boolean suspended(TooltipMakerAPI t, SplitShortfall s, WorkCatalog catalog,
      float pad) {
    if (s.suspended.isEmpty()) {
      return false;
    }
    if (s.suspended.size() == 1) {
      String name = workName(s.suspended.get(0), catalog);
      say(t, NomadUi.warn(), pad,
          NomadStrings.get("nomad_overview", "detach_split_suspended_one", name), name);
      return true;
    }
    List<String> names = new ArrayList<String>();
    for (String id : s.suspended) {
      names.add(workName(id, catalog));
    }
    String list = join(names);
    say(t, NomadUi.warn(), pad,
        NomadStrings.get("nomad_overview", "detach_split_suspended_many", list), list);
    return true;
  }

  private static boolean lost(TooltipMakerAPI t, SplitShortfall s, float pad) {
    if (s.lost.isEmpty()) {
      return false;
    }
    List<String> items = new ArrayList<String>();
    for (Map.Entry<String, Float> e : s.lost.entrySet()) {
      items.add(NomadUi.i(e.getValue().floatValue()) + " " + NomadUi.commodityName(e.getKey()));
    }
    String list = join(items);
    say(t, NomadUi.warn(), pad, NomadStrings.get("nomad_overview", "detach_split_lost", list),
        list);
    return true;
  }

  private static boolean berths(TooltipMakerAPI t, SplitShortfall s, float pad) {
    if (!s.berthsShort()) {
      return false;
    }
    String before = NomadUi.i(s.berthsBefore);
    String after = NomadUi.i(s.berthsAfter);
    String lost = NomadUi.i(s.residentsLost);
    say(t, NomadUi.warn(), pad, NomadStrings.get("nomad_overview", "ov_split_flotilla_berths",
        before, after, lost), before, after, lost);
    return true;
  }

  private static void say(TooltipMakerAPI t, Color hl, float pad, String text,
      String... values) {
    LabelAPI label = t.addPara(text, pad);
    label.setHighlight(values);
    label.setHighlightColor(hl);
  }

  private static String join(List<String> items) {
    StringBuilder sb = new StringBuilder();
    for (String item : items) {
      if (sb.length() > 0) {
        sb.append(", ");
      }
      sb.append(item);
    }
    return sb.toString();
  }

  private static String workName(String workId, WorkCatalog catalog) {
    WorkDef def = workId == null ? null : catalog.work(workId);
    return def == null || def.name == null || def.name.isEmpty() ? String.valueOf(workId)
        : def.name;
  }
}
