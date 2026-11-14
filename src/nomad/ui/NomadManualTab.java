package nomad.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import nomad.NomadStrings;
import nomad.campaign.NomadEcon;
import nomad.campaign.NomadModel;
import nomad.campaign.NomadSettings;
import nomad.campaign.econ.EconConstants;
import nomad.campaign.econ.model.Brokerage;
import nomad.data.NomadFleetData;
import nomad.intel.NomadIntel;

public final class NomadManualTab {

  private NomadManualTab() {
  }

  private static final float OPAD = 10f;
  private static final float PARA_GAP = 8f;

  private static final String PICTURE = "nomad_illus_manual";
  private static final float INDEX_W = 200f;
  private static final float INDEX_ROW_H = 22f;
  private static final float INDEX_ROW_GAP = 2f;
  private static final boolean INDEX_LEFT_ALIGNED = true;
  private static final float INDEX_TEXT_INSET = 10f;
  private static final float INDEX_MAX_W = 320f;
  private static final float HEAD_TEXT_X = 6f;

  private static final String CAT = "nomad_manual";
  private static final String ORDER = "man_blocks";
  private static final String HEAD_PREFIX = "man_head_";

  private static final class Section {
    final String id;
    final List<String> blocks = new ArrayList<String>();

    Section(String id) {
      this.id = id;
    }
  }

  public static TooltipMakerAPI build(CustomPanelAPI panel, float width, float bodyHeight,
      NomadIntel intel, NomadFleetData data) {
    List<Section> sections = sections(NomadStrings.get(CAT, ORDER));
    if (sections.isEmpty()) {
      return null;
    }
    int open = openIndex(sections, intel == null ? null : intel.getManualSection());
    float viewH = bodyHeight;
    float top = NomadTabbedPanels.BODY_TOP;

    boolean indexed = sections.get(0).id != null;
    String[] names = indexed ? names(sections) : null;
    NomadReadingLayout.Manual at = NomadReadingLayout.manual(width, viewH,
        indexed ? indexW(names) : 0f);
    if (indexed) {
      index(panel, names, sections, open, at.indexX, at.indexW, viewH, top);
    }

    float sectionW = at.section.w;
    Section section = sections.get(open);
    NomadReadingColumn column = new NomadReadingColumn(panel, at.section, PICTURE, viewH);
    TooltipMakerAPI m = column.text;
    Map<String, String> fills = fills();
    boolean first = true;
    if (section.id != null) {
      m.addCustom(NomadCell.headBar(panel, sectionW, NomadStrings.get(CAT, section.id),
          HEAD_TEXT_X), 0f);
    }
    for (String id : section.blocks) {
      para(m, fill(NomadStrings.get(CAT, id), fills),
          first ? (section.id == null ? 0f : OPAD) : PARA_GAP);
      first = false;
    }
    return column.place(panel, top, viewH);
  }

  private static String[] names(List<Section> sections) {
    String[] names = new String[sections.size()];
    for (int i = 0; i < names.length; i++) {
      names[i] = NomadStrings.get(CAT, sections.get(i).id);
    }
    return names;
  }

  private static float indexW(String[] names) {
    return Math.min(INDEX_MAX_W, Math.max(INDEX_W,
        NomadText.need(NomadUi.BUTTON, names) + 2f * INDEX_TEXT_INSET));
  }

  private static List<Section> sections(String order) {
    List<Section> out = new ArrayList<Section>();
    List<String> lead = new ArrayList<String>();
    Section current = null;
    for (String raw : order.split(",")) {
      String id = raw.trim();
      if (id.isEmpty()) {
        continue;
      }
      if (id.startsWith(HEAD_PREFIX)) {
        current = new Section(id);
        if (out.isEmpty()) {
          current.blocks.addAll(lead);
        }
        out.add(current);
      } else if (current == null) {
        lead.add(id);
      } else {
        current.blocks.add(id);
      }
    }
    if (out.isEmpty() && !lead.isEmpty()) {
      Section only = new Section(null);
      only.blocks.addAll(lead);
      out.add(only);
    }
    return out;
  }

  private static int openIndex(List<Section> sections, String id) {
    for (int i = 0; i < sections.size(); i++) {
      if (id != null && id.equals(sections.get(i).id)) {
        return i;
      }
    }
    return 0;
  }

  private static void index(CustomPanelAPI panel, String[] names, List<Section> sections,
      int open, float x, float w, float viewH, float top) {
    int n = sections.size();
    float pitch = INDEX_ROW_H + INDEX_ROW_GAP;
    boolean scrolls = n * pitch - INDEX_ROW_GAP > viewH;

    TooltipMakerAPI index = panel.createUIElement(
        NomadText.element(w) + (scrolls ? NomadReadingLayout.SCROLLBAR : 0f), viewH, scrolls);
    for (int i = 0; i < n; i++) {
      ButtonAPI name = index.addAreaCheckbox(
          NomadText.fit(names[i], w - 2f * INDEX_TEXT_INSET, NomadUi.BUTTON),
          NomadTabbedPanels.TAB_PREFIX + NomadIntel.manualPage(sections.get(i).id),
          NomadUi.base(), NomadUi.dark(), NomadUi.bright(), w, INDEX_ROW_H,
          i == 0 ? 0f : INDEX_ROW_GAP, INDEX_LEFT_ALIGNED);
      name.setChecked(i == open);
    }
    panel.addUIElement(index).inTL(NomadText.elementX(x), top);
    float openBottom = (open + 1) * pitch - INDEX_ROW_GAP;
    if (scrolls && openBottom > viewH && index.getExternalScroller() != null) {
      index.getExternalScroller().setYOffset(openBottom - viewH);
    }
  }

  private static void para(TooltipMakerAPI m, String text, float pad) {
    m.addParaWithMarkup(text, NomadUi.text(), pad);
  }

  private static Map<String, String> fills() {
    EconConstants k = NomadEcon.constants();
    Map<String, String> f = new HashMap<String, String>();
    f.put("dedication_default", (int) nomad.campaign.NomadDedication.defaults()[0] + "%");
    f.put("credits_price", NomadUi.pct(k.creditsSalePriceFrac));
    f.put("support_limit_base", NomadUi.i(NomadModel.rules().supportLimitBase));
    f.put("support_limit_first",
        NomadPanelFigures.heads(NomadModel.rules().supportLimitFirstAt));
    f.put("brokerage_bonus_share", NomadUi.pct(NomadModel.rules().brokerageBonusShare));
    f.put("brokerage_paid_at_130",
        NomadUi.pct(Brokerage.feeEfficiency(1.3f, NomadModel.rules())));
    f.put("needs_tier_relief", NomadUi.pct(NomadModel.rules().needsTierRelief));
    f.put("needs_relief_cap", NomadUi.pct(NomadModel.rules().needsReliefCap));
    f.put("reserve_days_supplies",
        NomadUi.i(NomadSettings.getFloat("nomad_autotransfer_reserve_days_supplies", 30f)));
    f.put("reserve_ly_fuel",
        NomadUi.i(NomadSettings.getFloat("nomad_autotransfer_reserve_ly_fuel", 10f)));
    f.put("founding_store_target", NomadUi.i(k.foundingStoreTarget));
    f.put("founding_space",
        NomadUi.i(nomad.campaign.econ.model.Capacity.foundingSpace(k)));
    f.put("build_days", NomadUi.i(k.workBuildDays));
    f.put("remote_on_days", NomadUi.i(NomadModel.rules().remoteOnDays));
    return f;
  }

  private static String fill(String text, Map<String, String> fills) {
    return NomadManualText.fill(text, fills);
  }
}
