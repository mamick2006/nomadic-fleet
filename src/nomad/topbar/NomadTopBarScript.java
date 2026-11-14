package nomad.topbar;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import com.fs.starfarer.api.util.IntervalUtil;

import nomad.NomadStrings;
import nomad.campaign.NomadSettings;
import nomad.campaign.econ.model.MiningSystem;
import nomad.campaign.econ.model.NeedsLevel;
import nomad.data.NomadFleetData;
import nomad.intel.NomadIntel;
import nomad.ui.NomadEconView;
import nomad.ui.NomadFlotillaView;
import nomad.ui.NomadMining;
import nomad.ui.NomadPanelFigures;
import nomad.ui.NomadStoresPanel;
import nomad.ui.NomadTable;
import nomad.ui.NomadTip;
import nomad.ui.NomadTopBarTip;
import nomad.ui.NomadUi;

public class NomadTopBarScript implements EveryFrameScript {

  private static final float H = NomadTopBarLayout.H;
  private static final float PAD = NomadTopBarLayout.PAD;
  private static final float PAD_V = 5f;
  private static final float GLYPH = NomadTopBarLayout.GLYPH;
  private static final float GAP = NomadTopBarLayout.GAP;
  private static final float BTN_H = 20f;
  private static final float BTN_OPEN_W = NomadTopBarLayout.BTN_OPEN_W;
  private static final float BTN_LIM_W = NomadTopBarLayout.BTN_LIM_W;
  static final float SCREEN_PAD = 10f;
  static final float TOP_OFFSET = 34f;

  private static final float MAX_W = NomadTopBarLayout.widest();

  private static final Object B_OPEN = "nomad_topbar_open";
  private static final Object B_LIM = "nomad_topbar_lim";
  private static final String LIMINAL_RESEARCH_KEY = "$liminality_research";
  private static final String LIMINAL_SELECT_TAB_KEY = "$liminality_select_tab";

  private static NomadTopBarScript live;
  private static volatile boolean forceFailForTest;
  private static volatile Throwable lastErrorForTest;

  private final IntervalUtil interval = new IntervalUtil(0.15f, 0.25f);
  private static final float MINING_PROX_PERIOD = 1f;

  private boolean dead;
  private boolean shownOnScreen;
  private boolean wasBare;

  private transient UIPanelAPI lastCore;
  private transient CustomPanelAPI panel;
  private transient NomadTopBarBackground background;
  private transient CustomPanelAPI content;
  private transient ButtonAPI openButton;
  private transient ButtonAPI limButton;
  private transient String lastSig;
  private transient float usedWidth;
  private transient long lastViewTick = Long.MIN_VALUE;
  private transient long lastViewAllocSerial = Long.MIN_VALUE;
  private transient NomadEconView cachedView;
  private transient boolean viewStale;

  private transient PlanetAPI miningBody;
  private transient String miningResolvedLocId;
  private transient boolean miningInRange;
  private transient NomadMining.State liveMining;
  private transient float miningProxElapsed;

  public NomadTopBarScript() {
    live = this;
  }

  public static boolean isAlive() {
    return live != null && !live.dead;
  }

  public static boolean isAttached() {
    return live != null && !live.dead && live.panel != null && live.shownOnScreen;
  }

  public static void injectReflectionFailureForTest() {
    forceFailForTest = true;
  }

  public static String lastErrorForTest() {
    Throwable t = lastErrorForTest;
    if (t == null) {
      return "none";
    }
    StackTraceElement[] st = t.getStackTrace();
    return t + (st.length > 0 ? " @ " + st[0] : "");
  }

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
    if (dead) {
      return;
    }
    miningProxElapsed += amount;
    interval.advance(amount);
    if (!interval.intervalElapsed()) {
      return;
    }
    try {
      tick();
    } catch (Throwable t) {
      lastErrorForTest = t;
      Global.getLogger(NomadTopBarScript.class).warn("Nomad Flotilla: the campaign top bar hit a"
          + " reflection error (" + t + ") and disabled itself for the session. Likely cause: a game"
          + " version change. Flotilla management remains available through the Management screen.");
      disableForSession();
    }
  }

  private void tick() throws Exception {
    if (forceFailForTest) {
      forceFailForTest = false;
      throw new RuntimeException("nomad: injected top-bar reflection failure (test hook)");
    }

    pollButtons();

    NomadFleetData data = NomadFleetData.exists() ? NomadFleetData.getInstance() : null;
    boolean bare = data != null && data.isFounded() && onBareMap();
    if (bare && !wasBare) {
      NomadStoresPanel.releaseTargetField();
    }
    wasBare = bare;
    boolean show = bare && NomadSettings.tunableBool("nomad_topbar_enabled", true);

    UIPanelAPI core = NomadCoreReflect.getCore();
    if (core == null) {
      return;
    }

    boolean attached = panel != null && core == lastCore && panel.getParent() == core;
    if (!attached) {
      forgetPanel();
      lastCore = core;
      if (!show) {
        return;
      }
      attach(core);
    }

    if (!show) {
      hideStrip(data);
      return;
    }
    drainDrag(data);
    refreshContent(data);
    reposition(data);
  }

  private void drainDrag(NomadFleetData data) {
    if (background == null) {
      return;
    }
    boolean reset = background.consumeReset();
    boolean commit = background.consumeCommit();
    if (data == null) {
      return;
    }
    if (reset) {
      data.clearTopBarPos();
    }
    if (commit) {
      data.setTopBarPos(background.committedLeft(), background.committedTop());
    }
  }

  private void pollButtons() {
    if (openButton != null && openButton.isChecked()) {
      openButton.setChecked(false);
      openIntel(NomadIntel.get());
    }
    if (limButton != null && limButton.isChecked()) {
      limButton.setChecked(false);
      openLiminal();
    }
  }

  private boolean onBareMap() {
    CampaignUIAPI ui = Global.getSector() == null ? null : Global.getSector().getCampaignUI();
    return ui != null
        && ui.getCurrentCoreTab() == null
        && !ui.isShowingDialog()
        && !ui.isShowingMenu();
  }

  private void attach(UIPanelAPI core) {
    background = new NomadTopBarBackground();
    panel = Global.getSettings().createCustom(MAX_W, H, background);
    core.addComponent(panel);
    lastCore = core;
    content = null;
    lastSig = null;
    openButton = null;
    limButton = null;
  }

  private void forgetPanel() {
    if (background != null) {
      background.dropDrag();
    }
    panel = null;
    background = null;
    content = null;
    lastSig = null;
    openButton = null;
    limButton = null;
    shownOnScreen = false;
  }

  private void hideStrip(NomadFleetData data) {
    if (background != null) {
      background.settleDrag();
      drainDrag(data);
    }
    try {
      if (panel != null && lastCore != null) {
        lastCore.removeComponent(panel);
      }
    } catch (Throwable ignore) {
    }
    forgetPanel();
  }

  private void reposition(NomadFleetData data) {
    float screenW = Global.getSettings().getScreenWidth();
    float screenH = Global.getSettings().getScreenHeight();
    if (background != null) {
      background.visible = true;
      background.usedWidth = usedWidth;
    }
    if (panel != null && (background == null || !background.isDragging())) {
      Float px = data == null ? null : data.getTopBarX();
      Float py = data == null ? null : data.getTopBarY();
      float left = px == null ? NomadTopBarLayout.defaultLeft(screenW, usedWidth, SCREEN_PAD) : px;
      float top = py == null ? TOP_OFFSET : py;
      left = NomadTopBarLayout.clampLeft(left, usedWidth, screenW, SCREEN_PAD);
      top = NomadTopBarLayout.clampTop(top, H, screenH, SCREEN_PAD);
      panel.getPosition().inTL(left, top);
    }
    shownOnScreen = true;
  }

  private void refreshContent(NomadFleetData data) {
    long tick = data.getLastTickTimestamp();
    long allocSerial = data.getAllocSerial();
    if (cachedView == null || viewStale || tick != lastViewTick || allocSerial != lastViewAllocSerial) {
      if (viewStale) {
        NomadEconView.invalidate();
        viewStale = false;
      }
      cachedView = NomadEconView.compute(data);
      lastViewTick = tick;
      lastViewAllocSerial = allocSerial;
      miningResolvedLocId = null;
      miningProxElapsed = MINING_PROX_PERIOD;
    }
    NomadEconView v = cachedView;

    if (v.miningSpace > 0f && miningProxElapsed >= MINING_PROX_PERIOD) {
      miningProxElapsed = 0f;
      updateMiningProximity(v);
    }

    boolean lim = liminalPresent();
    NomadTopBarLayout.State state = stateOf(v);
    NomadTopBarLayout.Mining mining = miningKind(v);
    List<String> mined = minedGoods(v, mining);
    NomadTopBarLayout.Cells cells = NomadTopBarLayout.fit(
        NomadTopBarLayout.cells(state, v.attention, v.suppliesOwed, v.suppliesCover >= 0f, mining,
            mined.size(), lim),
        Global.getSettings().getScreenWidth());
    usedWidth = NomadTopBarLayout.stripWidth(cells);
    if (background != null) {
      background.dragWidth = NomadTopBarLayout.dragWidth(cells);
    }

    String sig = state + "|" + v.status + "|" + v.level
        + "|" + NomadUi.pct(v.efficiency) + "|" + (cells.attention ? v.attention : 0)
        + "|" + (cells.owed ? NomadTopBarLayout.owedText(v.suppliesOwed) : "")
        + "|" + (cells.cover ? NomadPanelFigures.coverPercent(v.suppliesCover) : -1)
        + "|" + cells.mining + (cells.miningIcons > 0 ? mined.toString() : "") + "|" + lim;
    if (sig.equals(lastSig) && content != null) {
      return;
    }
    lastSig = sig;

    if (content != null) {
      panel.removeComponent(content);
    }
    openButton = null;
    limButton = null;
    content = buildContent(v, cells, mined);
    panel.addComponent(content);
    content.getPosition().inTL(0f, 0f);
  }

  private static NomadTopBarLayout.State stateOf(NomadEconView v) {
    switch (v.status) {
      case PAUSED:
        return NomadTopBarLayout.State.PAUSED;
      case DETACHED:
        return NomadTopBarLayout.State.DETACHED;
      default:
        return NomadTopBarLayout.State.RUNNING;
    }
  }

  private static NomadTopBarLayout.Mining miningKind(NomadEconView v) {
    if (v.miningSpace <= 0f) {
      return NomadTopBarLayout.Mining.NONE;
    }
    if (v.mining != null && v.mining.active) {
      return NomadTopBarLayout.Mining.IN_RANGE;
    }
    MiningSystem.Readout r = v.miningReadout;
    boolean remote = r != null && !r.idle() && (r.kind == MiningSystem.Kind.REMOTE
        || r.kind == MiningSystem.Kind.REMOTE_ASTEROIDS);
    return remote ? NomadTopBarLayout.Mining.REMOTE : NomadTopBarLayout.Mining.NONE;
  }

  private static List<String> minedGoods(NomadEconView v, NomadTopBarLayout.Mining mining) {
    List<String> out = new ArrayList<String>();
    if (mining == NomadTopBarLayout.Mining.IN_RANGE) {
      out.addAll(v.mining.tiers.keySet());
    } else if (mining == NomadTopBarLayout.Mining.REMOTE) {
      out.addAll(v.miningReadout.shares.keySet());
    }
    while (out.size() > NomadTopBarLayout.MINING_MAX_ICONS) {
      out.remove(out.size() - 1);
    }
    return out;
  }

  private void updateMiningProximity(NomadEconView v) {
    CampaignFleetAPI fleet = NomadUi.flotillaFleet();
    if (fleet == null || fleet.getContainingLocation() == null) {
      miningBody = null;
      miningResolvedLocId = null;
      miningInRange = false;
      liveMining = new NomadMining.State();
      v.mining = liveMining;
      return;
    }
    String locId = fleet.getContainingLocation().getId();
    if (!eq(locId, v.locationId)) {
      viewStale = true;
    }
    float radius = NomadSettings.getFloat("nomad_mining_radius", 333f);
    boolean stale = miningBody != null
        && (miningBody.getMarket() == null || miningBody.getContainingLocation() != fleet.getContainingLocation());
    if (!eq(locId, miningResolvedLocId) || stale) {
      miningBody = NomadMining.nearestBody(fleet);
      miningResolvedLocId = locId;
      recomputeLiveMining(fleet, radius);
    } else if (miningBody != null) {
      boolean now = NomadMining.inRange(fleet, miningBody, radius);
      if (now != miningInRange) {
        miningBody = NomadMining.nearestBody(fleet);
        recomputeLiveMining(fleet, radius);
        viewStale = true;
      }
    }
    if (liveMining == null) {
      liveMining = new NomadMining.State();
    }
    v.mining = liveMining;
  }

  private void recomputeLiveMining(CampaignFleetAPI fleet, float radius) {
    miningInRange = miningBody != null && NomadMining.inRange(fleet, miningBody, radius);
    liveMining = miningInRange ? NomadMining.stateFor(miningBody) : new NomadMining.State();
  }

  private static boolean eq(String a, String b) {
    return a == null ? b == null : a.equals(b);
  }

  private CustomPanelAPI buildContent(NomadEconView v, NomadTopBarLayout.Cells cells, List<String> mined) {
    CustomPanelAPI c = panel.createCustomPanel(MAX_W, H, new BaseCustomUIPanelPlugin());
    TooltipMakerAPI.TooltipCreator tip = breakdownTip();

    TooltipMakerAPI grip = c.createUIElement(NomadTopBarLayout.GRIP_W, H, false);
    grip.addCustom(c.createCustomPanel(NomadTopBarLayout.GRIP_W, H, null), 0f);
    grip.addTooltipToPrevious(NomadTable.tip(NomadStrings.get("nomad_topbar", "bar_grip_tip"), 0f),
        TooltipMakerAPI.TooltipLocation.BELOW, false);
    c.addUIElement(grip).inTL(0f, 0f);

    String sprite = Global.getSettings().getSpriteName("icons", spriteVariant(v.status));
    TooltipMakerAPI glyph = c.createUIElement(GLYPH, GLYPH, false);
    glyph.addImage(sprite, GLYPH, GLYPH, 0f);
    c.addUIElement(glyph).inTL(NomadTopBarLayout.GRIP_W + PAD, PAD_V);
    TooltipMakerAPI prev = glyph;

    NeedsLevel level = v.level == null ? NeedsLevel.NORMAL : v.level;
    String levelName = NomadFlotillaView.levelName(level);
    if (cells.state == NomadTopBarLayout.State.PAUSED) {
      prev = textCell(c, prev, NomadTopBarLayout.STATE_PAUSED_W,
          NomadStrings.get("nomad_topbar", "topbar_paused"), NomadUi.bad(), tip);
    } else if (cells.state == NomadTopBarLayout.State.DETACHED) {
      prev = textCell(c, prev, NomadTopBarLayout.STATE_DETACHED_W,
          NomadStrings.get("nomad_topbar", "topbar_detached"), NomadUi.warn(), tip);
    }
    if (cells.needs) {
      String eff = NomadUi.pct(v.efficiency);
      prev = textCell(c, prev, NomadTopBarLayout.NEEDS_W,
          NomadStrings.get("nomad_topbar", "topbar_needs", levelName, eff), NomadUi.text(), tip,
          new String[] { levelName, eff }, new Color[] { NomadTopBarTip.levelColor(level), NomadUi.hl() });
    }
    if (cells.needsName) {
      prev = textCell(c, prev, NomadTopBarLayout.NEEDS_NAME_W,
          NomadStrings.get("nomad_topbar", "bar_needs", levelName), NomadUi.text(), tip,
          new String[] { levelName }, new Color[] { NomadTopBarTip.levelColor(level) });
    }

    if (cells.attention) {
      prev = textCell(c, prev, NomadTopBarLayout.ATTENTION_W,
          NomadStrings.get("nomad_topbar", "topbar_attention", "" + v.attention), NomadUi.warn(), tip);
    }
    if (cells.owed) {
      prev = textCell(c, prev, NomadTopBarLayout.OWED_W,
          NomadStrings.get("nomad_topbar", "topbar_owed", NomadTopBarLayout.owedText(v.suppliesOwed)),
          NomadUi.warn(), tip);
    }

    if (cells.cover) {
      String pct = NomadPanelFigures.coverPercent(v.suppliesCover) + "%";
      prev = textCell(c, prev, NomadTopBarLayout.COVER_W,
          NomadStrings.get("nomad_topbar", "topbar_cover", pct), NomadUi.text(), tip,
          new String[] { pct }, new Color[] { NomadUi.hl() });
    }
    if (cells.mining != NomadTopBarLayout.Mining.NONE) {
      boolean remote = cells.mining == NomadTopBarLayout.Mining.REMOTE;
      prev = textCell(c, prev,
          remote ? NomadTopBarLayout.REMOTE_LABEL_W : NomadTopBarLayout.MINING_LABEL_W,
          remote ? NomadStrings.get("nomad_topbar", "topbar_remote")
              : NomadStrings.get("nomad_topbar", "topbar_mining"),
          NomadUi.good(), tip);
      for (int i = 0; i < cells.miningIcons && i < mined.size(); i++) {
        TooltipMakerAPI ic = c.createUIElement(NomadTopBarLayout.MINING_ICON_W, BTN_H, false);
        NomadUi.iconInline(ic, mined.get(i), 18f, 1f);
        c.addUIElement(ic).rightOfMid(prev, NomadTopBarLayout.MINING_ICON_GAP);
        prev = ic;
      }
    }

    TooltipMakerAPI ob = c.createUIElement(BTN_OPEN_W, BTN_H, false);
    openButton = ob.addButton("Flotilla", B_OPEN, NomadUi.bright(), NomadUi.dark(),
        Alignment.MID, CutStyle.ALL, BTN_OPEN_W, BTN_H, 0f);
    c.addUIElement(ob).rightOfMid(prev, GAP);
    prev = ob;

    if (cells.liminal) {
      TooltipMakerAPI lb = c.createUIElement(BTN_LIM_W, BTN_H, false);
      limButton = lb.addButton("Liminality", B_LIM, NomadUi.bright(), NomadUi.dark(),
          Alignment.MID, CutStyle.ALL, BTN_LIM_W, BTN_H, 0f);
      c.addUIElement(lb).rightOfMid(prev, GAP);
    }

    return c;
  }

  private static TooltipMakerAPI textCell(CustomPanelAPI c, TooltipMakerAPI prev, float w, String text,
      Color color, TooltipMakerAPI.TooltipCreator tip) {
    return textCell(c, prev, w, text, color, tip, null, null);
  }

  private static TooltipMakerAPI textCell(CustomPanelAPI c, TooltipMakerAPI prev, float w, String text,
      Color color, TooltipMakerAPI.TooltipCreator tip, String[] highlights, Color[] colors) {
    TooltipMakerAPI el = c.createUIElement(w, BTN_H, false);
    LabelAPI l = el.addPara(text, color, 2f);
    if (highlights != null) {
      l.setHighlight(highlights);
      l.setHighlightColors(colors);
    }
    el.addTooltipToPrevious(tip, TooltipMakerAPI.TooltipLocation.BELOW, false);
    c.addUIElement(el).rightOfMid(prev, GAP);
    return el;
  }

  private TooltipMakerAPI.TooltipCreator breakdownTip() {
    return NomadTip.guard(new NomadTip(NomadTopBarTip.WIDTH) {
      @Override
      public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object param) {
        NomadTopBarTip.build(tooltip, cachedView);
      }
    });
  }

  private void openIntel(NomadIntel intel) {
    try {
      if (Global.getSector() == null) {
        return;
      }
      CampaignUIAPI ui = Global.getSector().getCampaignUI();
      if (NomadMgmtInjector.isAlive()) {
        NomadMgmtInjector.requestSelect();
        NomadMgmtInjector.openManagementScreen();
      } else if (intel != null) {
        ui.showCoreUITab(CoreUITabId.INTEL, intel);
      } else {
        ui.showCoreUITab(CoreUITabId.INTEL);
      }
    } catch (Throwable ignore) {
    }
  }

  private void openLiminal() {
    try {
      if (Global.getSector() == null) {
        return;
      }
      Global.getSector().getMemoryWithoutUpdate().set(LIMINAL_SELECT_TAB_KEY, true);
      NomadMgmtInjector.openManagementScreen();
    } catch (Throwable ignore) {
    }
  }

  private boolean liminalPresent() {
    try {
      if (Global.getSettings() == null
          || Global.getSettings().getModManager() == null
          || !Global.getSettings().getModManager().isModEnabled("liminal")) {
        return false;
      }
      return Global.getSector().getMemoryWithoutUpdate().get(LIMINAL_RESEARCH_KEY) != null;
    } catch (Throwable ignore) {
      return false;
    }
  }

  private void disableForSession() {
    dead = true;
    shownOnScreen = false;
    if (background != null) {
      background.dropDrag();
    }
    try {
      if (panel != null && lastCore != null) {
        lastCore.removeComponent(panel);
      }
    } catch (Throwable ignore) {
    }
    panel = null;
    background = null;
    content = null;
    openButton = null;
    limButton = null;
    lastCore = null;
    lastSig = null;
  }

  private static String spriteVariant(NomadEconView.Status status) {
    switch (status) {
      case PAUSED:
        return "nomad_intel_paused";
      case DETACHED:
        return "nomad_intel_detached";
      case SHORT:
        return "nomad_intel_short";
      default:
        return "nomad_intel_ok";
    }
  }
}
