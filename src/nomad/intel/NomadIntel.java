package nomad.intel;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.campaign.comm.IntelManagerAPI;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import nomad.NomadStrings;
import nomad.data.NomadFleetData;
import nomad.ui.NomadAutoforgeTab;
import nomad.ui.NomadConfirm;
import nomad.ui.NomadEconView;
import nomad.ui.NomadFlotillaTab;
import nomad.ui.NomadFlotillaView;
import nomad.ui.NomadHistoryTab;
import nomad.ui.NomadHomeColumn;
import nomad.ui.NomadItemsTab;
import nomad.ui.NomadManualTab;
import nomad.ui.NomadPanelFigures;
import nomad.ui.NomadTabbedPanels;
import nomad.ui.NomadUi;

public class NomadIntel extends BaseIntelPlugin {

  public static final String TAG = "Flotilla";

  public static final String TAB_OVERVIEW = "overview";
  public static final String TAB_ITEMS = "items";
  public static final String TAB_AUTOFORGE = "autoforge";
  public static final String TAB_HISTORY = "history";
  public static final String TAB_MANUAL = "manual";

  private static final String SEEN_MANUAL_KEY = "nomad_seen_manual";

  private static final char PAGE_SEP = '/';

  private String currentTab;
  private transient String manualSection;
  private transient String autoforgeBrowse;
  private transient String autoforgeSize;
  private transient String autoforgeType;
  private transient String autoforgeClass;
  private transient String autoforgeRole;
  private transient String autoforgeDesign;
  private transient String autoforgeSearch;
  private transient int autoforgePage;
  private transient TooltipMakerAPI activeBody;
  private transient Map<String, Float> scrollByTab;

  public static NomadIntel get() {
    if (Global.getSector() == null) {
      return null;
    }
    return (NomadIntel) Global.getSector().getIntelManager().getFirstIntel(NomadIntel.class);
  }

  public static void ensureExists(NomadFleetData data) {
    if (Global.getSector() == null || data == null || !data.isFounded()) {
      return;
    }
    IntelManagerAPI im = Global.getSector().getIntelManager();
    NomadIntel intel = get();
    if (intel == null) {
      intel = new NomadIntel();
      im.addIntel(intel, true);
    }
    if (data.isNeedsFoundingDialog()) {
      CampaignUIAPI ui = Global.getSector().getCampaignUI();
      boolean safe = ui != null && !ui.isShowingDialog() && !ui.isShowingMenu()
          && ui.getCurrentCoreTab() == null;
      if (safe) {
        intel.currentTab = TAB_MANUAL;
        if (nomad.topbar.NomadMgmtInjector.isAlive()) {
          nomad.topbar.NomadMgmtInjector.requestSelect();
          nomad.topbar.NomadMgmtInjector.openManagementScreen();
        } else {
          ui.showCoreUITab(CoreUITabId.INTEL, intel);
        }
        data.setNeedsFoundingDialog(false);
      }
    }
  }

  private static final List<String> TAB_IDS = java.util.Collections.unmodifiableList(
      java.util.Arrays.asList(TAB_OVERVIEW, TAB_ITEMS, TAB_AUTOFORGE, TAB_HISTORY, TAB_MANUAL));

  private List<String> tabIds(NomadFleetData data) {
    return TAB_IDS;
  }

  private List<String> tabLabels(List<String> ids) {
    List<String> labels = new ArrayList<String>();
    for (String id : ids) {
      if (TAB_OVERVIEW.equals(id)) {
        labels.add("Overview");
      } else if (TAB_ITEMS.equals(id)) {
        labels.add("Items");
      } else if (TAB_AUTOFORGE.equals(id)) {
        labels.add("Foundry");
      } else if (TAB_HISTORY.equals(id)) {
        labels.add("History");
      } else {
        labels.add("Manual");
      }
    }
    return labels;
  }

  @Override
  public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
    info.addPara(getName(), getTitleColor(mode), 0f);
    if (!NomadFleetData.exists()) {
      return;
    }
    NomadFleetData data = NomadFleetData.getInstance();
    NomadEconView view = NomadEconView.compute(data);
    boolean live = view.status != NomadEconView.Status.PAUSED
        && view.status != NomadEconView.Status.DETACHED;
    if (live) {
      info.addPara(NomadStrings.get("nomad_flotilla", "ui4_intel_needs"), 3f,
          new Color[] {NomadHomeColumn.levelColor(view.level),
              NomadHomeColumn.efficiencyColor(view.efficiency)},
          NomadFlotillaView.levelName(view.level), NomadUi.pct(view.efficiency));
    } else {
      info.addPara(statusLine(view), statusColor(view), 3f);
    }
    info.addPara(NomadStrings.get("nomad_flotilla", "ui2_residents_value"), 3f, NomadUi.hl(),
        NomadPanelFigures.heads(view.residents), NomadPanelFigures.heads(view.berths));
    if (live && view.attention > 0) {
      info.addPara(NomadStrings.get("nomad_flotilla", "ui2_works_to_check",
          Integer.toString(view.attention)), NomadUi.warn(), 3f);
    }
    if (view.status == NomadEconView.Status.DETACHED) {
      String where = nomad.campaign.split.NomadSplitter.detachedLocation();
      if (!where.isEmpty()) {
        info.addPara(NomadStrings.get("nomad_intel", "intel_holding_station", where),
            NomadUi.gray(), 3f);
      }
    }
  }

  @Override
  public boolean hasLargeDescription() {
    return true;
  }

  @Override
  public boolean hasSmallDescription() {
    return false;
  }

  @Override
  public boolean isHidden() {
    return nomad.topbar.NomadMgmtInjector.isAlive();
  }

  @Override
  public void createLargeDescription(CustomPanelAPI panel, float width, float height) {
    if (!NomadFleetData.exists()) {
      TooltipMakerAPI e = panel.createUIElement(width, height, false);
      e.addPara(NomadStrings.get("nomad_intel", "intel_not_founded"), NomadUi.gray(), 0f);
      panel.addUIElement(e).inTL(0f, 0f);
      return;
    }
    NomadFleetData data = NomadFleetData.getInstance();

    List<String> ids = tabIds(data);
    String active = activeTab(data);
    NomadTabbedPanels.buildTabBar(panel, width, ids, tabLabels(ids), active);
    float bodyH = height - NomadTabbedPanels.BODY_TOP;

    activeBody = null;
    try {
      if (TAB_ITEMS.equals(active)) {
        activeBody = NomadItemsTab.build(panel, width, bodyH, this, data);
      } else if (TAB_AUTOFORGE.equals(active)) {
        activeBody = NomadAutoforgeTab.build(panel, width, bodyH, this, data);
      } else if (TAB_HISTORY.equals(active)) {
        activeBody = NomadHistoryTab.build(panel, width, bodyH, this, data);
      } else if (TAB_MANUAL.equals(active)) {
        activeBody = NomadManualTab.build(panel, width, bodyH, this, data);
        markManualSeen();
      } else {
        activeBody = NomadFlotillaTab.build(panel, width, bodyH, this, data);
      }
    } catch (RuntimeException e) {
      Global.getLogger(NomadIntel.class).error(
          "Nomad Flotilla: the " + active + " tab could not be drawn", e);
      TooltipMakerAPI failed = panel.createUIElement(width, 18f, false);
      failed.addPara("Display error. See starsector.log.", NomadUi.bad(), 0f);
      panel.addUIElement(failed).inTL(0f, height - 18f);
    }

    Float saved = scrollByTab().get(active);
    if (saved != null) {
      NomadTabbedPanels.applyScroll(activeBody, saved);
    }
  }

  @Override
  public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
    if (!NomadFleetData.exists()) {
      return;
    }
    NomadFleetData data = NomadFleetData.getInstance();
    saveScroll();

    if (NomadTabbedPanels.isTabButton(buttonId)) {
      setCurrentTab(NomadTabbedPanels.tabIdOf(buttonId));
      ui.updateUIForItem(this);
      return;
    }

    boolean handled = NomadFlotillaTab.handle(buttonId, this, data, ui)
        || NomadItemsTab.handle(buttonId, this, data, ui)
        || NomadAutoforgeTab.handle(buttonId, this, data, ui);
    if (handled) {
      ui.updateUIForItem(this);
    }
  }

  @Override
  public boolean doesButtonHaveConfirmDialog(Object buttonId) {
    return NomadConfirm.needed(buttonId);
  }

  @Override
  public void createConfirmationPrompt(Object buttonId, TooltipMakerAPI prompt) {
    NomadConfirm.prompt(buttonId, prompt, NomadFleetData.getInstance());
  }

  @Override
  public String getConfirmText(Object buttonId) {
    return NomadConfirm.acceptLabel(buttonId);
  }

  @Override
  public String getCancelText(Object buttonId) {
    return NomadConfirm.cancelLabel(buttonId);
  }

  private String activeTab(NomadFleetData data) {
    if (currentTab == null) {
      currentTab = seenManual() ? TAB_OVERVIEW : TAB_MANUAL;
    }
    if (!tabIds(data).contains(currentTab)) {
      currentTab = TAB_OVERVIEW;
    }
    return currentTab;
  }

  public void setCurrentTab(String tab) {
    String page = null;
    int cut = tab == null ? -1 : tab.indexOf(PAGE_SEP);
    if (cut >= 0) {
      page = tab.substring(cut + 1);
      tab = tab.substring(0, cut);
    }
    if (tab == null || !TAB_IDS.contains(tab)) {
      return;
    }
    if (!tab.equals(currentTab) && TAB_OVERVIEW.equals(currentTab)) {
      NomadFlotillaTab.resetUi();
    }
    this.currentTab = tab;
    if (page != null && TAB_MANUAL.equals(tab)) {
      manualSection = page;
      scrollByTab().remove(TAB_MANUAL);
    }
  }

  public String getCurrentTab() {
    return currentTab;
  }

  public static String manualPage(String sectionId) {
    return TAB_MANUAL + PAGE_SEP + sectionId;
  }

  public String getManualSection() {
    return manualSection;
  }

  public String getAutoforgeBrowse() {
    return autoforgeBrowse;
  }

  public void setAutoforgeBrowse(String mode) {
    this.autoforgeBrowse = mode;
  }

  public String getAutoforgeSize() {
    return autoforgeSize;
  }

  public void setAutoforgeSize(String size) {
    this.autoforgeSize = size;
  }

  public String getAutoforgeType() {
    return autoforgeType;
  }

  public void setAutoforgeType(String type) {
    this.autoforgeType = type;
  }

  public String getAutoforgeClass() {
    return autoforgeClass;
  }

  public void setAutoforgeClass(String cls) {
    this.autoforgeClass = cls;
  }

  public String getAutoforgeRole() {
    return autoforgeRole;
  }

  public void setAutoforgeRole(String role) {
    this.autoforgeRole = role;
  }

  public String getAutoforgeDesign() {
    return autoforgeDesign;
  }

  public void setAutoforgeDesign(String design) {
    this.autoforgeDesign = design;
  }

  public String getAutoforgeSearch() {
    return autoforgeSearch;
  }

  public void setAutoforgeSearch(String search) {
    this.autoforgeSearch = search;
  }

  public int getAutoforgePage() {
    return autoforgePage;
  }

  public void setAutoforgePage(int page) {
    this.autoforgePage = page;
  }

  private Map<String, Float> scrollByTab() {
    if (scrollByTab == null) {
      scrollByTab = new LinkedHashMap<String, Float>();
    }
    return scrollByTab;
  }

  private void saveScroll() {
    if (activeBody != null && currentTab != null) {
      scrollByTab().put(currentTab, NomadTabbedPanels.scrollOf(activeBody));
    }
    if (TAB_OVERVIEW.equals(currentTab)) {
      NomadFlotillaTab.saveScroll();
    }
  }

  public void saveConsoleScroll() {
    saveScroll();
  }

  private static boolean seenManual() {
    Object o = Global.getSector().getPersistentData().get(SEEN_MANUAL_KEY);
    return Boolean.TRUE.equals(o);
  }

  private static void markManualSeen() {
    Global.getSector().getPersistentData().put(SEEN_MANUAL_KEY, Boolean.TRUE);
  }

  private static NomadEconView.Status statusFor(NomadFleetData data) {
    return NomadEconView.compute(data).status;
  }

  private static String statusLine(NomadEconView view) {
    switch (view.status) {
      case PAUSED:
        return NomadStrings.get("nomad_intel", "intel_status_paused");
      case DETACHED:
        return NomadStrings.get("nomad_intel", "intel_status_detached");
      default:
        return "";
    }
  }

  private static Color statusColor(NomadEconView view) {
    switch (view.status) {
      case PAUSED:
        return NomadUi.bad();
      case DETACHED:
        return NomadUi.warn();
      default:
        return NomadUi.text();
    }
  }

  @Override
  public String getName() {
    return "The Flotilla";
  }

  @Override
  public String getSmallDescriptionTitle() {
    return getName();
  }

  @Override
  public String getIcon() {
    String variant = "nomad_intel_ok";
    if (NomadFleetData.exists()) {
      switch (statusFor(NomadFleetData.getInstance())) {
        case PAUSED:
          variant = "nomad_intel_paused";
          break;
        case DETACHED:
          variant = "nomad_intel_detached";
          break;
        case SHORT:
          variant = "nomad_intel_short";
          break;
        default:
          variant = "nomad_intel_ok";
      }
    }
    return Global.getSettings().getSpriteName("icons", variant);
  }

  @Override
  public Set<String> getIntelTags(SectorMapAPI map) {
    Set<String> tags = super.getIntelTags(map);
    tags.add(TAG);
    return tags;
  }

  @Override
  public String getSortString() {
    return getName();
  }

  @Override
  public boolean shouldRemoveIntel() {
    return false;
  }

  @Override
  public IntelSortTier getSortTier() {
    return IntelSortTier.TIER_2;
  }
}
