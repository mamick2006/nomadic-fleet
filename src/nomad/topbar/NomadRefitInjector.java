package nomad.topbar;

import java.util.List;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import com.fs.starfarer.api.util.IntervalUtil;

import nomad.NomadStrings;
import nomad.campaign.NomadCapacityUtil;
import nomad.campaign.NomadDedication;
import nomad.campaign.NomadManager;
import nomad.campaign.econ.NomadCapacity;
import nomad.data.NomadFleetData;
import nomad.ui.NomadDedicationPanel;
import nomad.ui.NomadTip;
import nomad.ui.NomadUi;

public class NomadRefitInjector implements EveryFrameScript {

  private static final float MIN_INTERVAL = 0.15f;
  private static final float MAX_INTERVAL = 0.25f;
  private static final int GIVE_UP = 40;
  private static final int HOIST_GIVE_UP = 180;

  private static final String FACE = "+";
  private static final float BTN_MIN_W = 22f;
  private static final float BTN_GAP = 8f;
  private static final float BTN_Y_INSET = 4f;
  private static final float ROW_MIN_CLEAR = 2f;
  private static final float NO_LABEL = -1f;
  private static final float NOT_LAID_OUT = 0f;

  private static final float SCREEN_GAP = 14f;
  private static final float MAX_WRAPPER_INSET = 20f;

  private final IntervalUtil interval = new IntervalUtil(MIN_INTERVAL, MAX_INTERVAL);
  private final Plugin plugin = new Plugin();

  private boolean disabled;
  private int failures;
  private boolean hoistOff;
  private int hoistMisses;

  private CustomPanelAPI seated;
  private UIPanelAPI seatedIn;
  private ButtonAPI button;
  private UIPanelAPI hullmodList;
  private float seatWantOff;
  private float seatOffset;

  private FleetMemberAPI member;
  private UIComponentAPI refitTab;
  private UIComponentAPI hullmodsPanel;
  private Object refitPanel;
  private static volatile Object livePanel;
  private UIPanelAPI descentCore;
  private CustomPanelAPI open;
  private UIPanelAPI openIn;

  public static void resetStaticsForLoad() {
    livePanel = null;
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
    if (disabled) {
      return;
    }
    boolean refit = inRefit();
    resyncIfRequested(refit);
    hoist(refit);
    if (refit) {
      reseatIfRowRebuilt();
    }
    if (open != null && NomadDedicationPanel.consumeCloseRequest(open)) {
      closeScreen();
      return;
    }
    interval.advance(amount);
    if (!interval.intervalElapsed()) {
      return;
    }
    if (!refit) {
      closeScreen();
      seated = null;
      seatedIn = null;
      button = null;
      hullmodList = null;
      return;
    }
    try {
      String missing = inject();
      if (missing == null) {
        failures = 0;
      } else if (++failures >= GIVE_UP) {
        die(missing, null);
      }
    } catch (Throwable t) {
      die("a reflection failure", t);
    }
  }

  private boolean inRefit() {
    if (Global.getSector() == null) {
      return false;
    }
    CampaignUIAPI ui = Global.getSector().getCampaignUI();
    return ui != null && ui.getCurrentCoreTab() == CoreUITabId.REFIT;
  }

  private String inject() throws Exception {
    Object hostCore = Global.getSector() == null || Global.getSector().getCampaignUI() == null ? null
        : Global.getSector().getCampaignUI().getCore2();
    UIPanelAPI core = hostCore instanceof UIPanelAPI ? (UIPanelAPI) hostCore : null;
    if (core == null) {
      return null;
    }
    descentCore = core;
    UIComponentAPI border = NomadCoreReflect.findChildWithMethod(core, "setBorderInsetLeft");
    if (!(border instanceof UIPanelAPI)) {
      return "setBorderInsetLeft";
    }
    UIComponentAPI tab = NomadCoreReflect.findChildWithMethod((UIPanelAPI) border, "goBackToParentIfNeeded");
    if (!(tab instanceof UIPanelAPI)) {
      return "goBackToParentIfNeeded";
    }
    UIComponentAPI panel = NomadCoreReflect.findChildWithMethod((UIPanelAPI) tab, "syncWithCurrentVariant");
    if (!(panel instanceof UIPanelAPI)) {
      return "syncWithCurrentVariant";
    }
    UIComponentAPI stats = NomadCoreReflect.findChildWithMethod((UIPanelAPI) panel, "getColorFor");
    if (!(stats instanceof UIPanelAPI)) {
      return "getColorFor";
    }
    UIComponentAPI mods = NomadCoreReflect.findChildWithMethod((UIPanelAPI) stats, "removeNotApplicableMods");
    if (!(mods instanceof UIPanelAPI)) {
      return "removeNotApplicableMods";
    }
    refitTab = tab;
    refitPanel = panel;
    livePanel = panel;
    hullmodsPanel = mods;
    Object m = NomadCoreReflect.invokeNoArg("getMember", panel);
    member = m instanceof FleetMemberAPI ? (FleetMemberAPI) m : null;
    rehomeScreenIfStranded();

    UIComponentAPI list = NomadCoreReflect.findChildWithMethod((UIPanelAPI) mods, "getFirstItemYPad");
    if (!(list instanceof UIPanelAPI)) {
      return "getFirstItemYPad";
    }
    hullmodList = (UIPanelAPI) list;
    Object items = NomadCoreReflect.invokeNoArg("getItems", list);
    if (!(items instanceof List)) {
      return "getItems";
    }

    UIPanelAPI row = nomadRow((List<?>) items);
    if (row == null) {
      if (seated != null) {
        clearShareOnUnconverted();
      }
      seated = null;
      seatedIn = null;
      button = null;
      return null;
    }
    if (seated == null || seatedIn != row || seated.getParent() != row) {
      String missing = seat(row);
      if (missing != null) {
        return missing;
      }
    } else {
      alignSeat();
    }
    return null;
  }

  private UIPanelAPI nomadRow(List<?> items) throws Exception {
    boolean founded = NomadFleetData.exists() && NomadFleetData.getInstance().isFounded();
    for (String id : NomadManager.ALL_HULLMOD_IDS) {
      if (!founded && !NomadManager.FLAGSHIP_HULLMOD_ID.equals(id)) {
        continue;
      }
      for (Object item : items) {
        if (!(item instanceof UIPanelAPI)) {
          continue;
        }
        Object spec = NomadCoreReflect.firstFieldValueOfType(item, HullModSpecAPI.class);
        if (spec instanceof HullModSpecAPI && id.equals(((HullModSpecAPI) spec).getId())) {
          return (UIPanelAPI) item;
        }
      }
    }
    return null;
  }

  private String seat(UIPanelAPI row) throws Exception {
    PositionAPI rowPos = ((UIComponentAPI) row).getPosition();
    float rowX = rowPos.getX();
    float rowW = rowPos.getWidth();
    float rowH = rowPos.getHeight();
    if (rowW <= 0f || rowH <= 0f) {
      return null;
    }
    Object spec = NomadCoreReflect.firstFieldValueOfType(row, HullModSpecAPI.class);
    float ink = nameInkLeft(row,
        spec instanceof HullModSpecAPI ? ((HullModSpecAPI) spec).getDisplayName() : null);
    if (ink == NO_LABEL) {
      seatDiag = "row " + rect(rowPos) + ": no LabelAPI child at all";
      return "the hullmod row's name label";
    }
    if (ink == NOT_LAID_OUT) {
      seatDiag = "row " + rect(rowPos) + ": " + labelDiag + " (not measurable this frame)";
      return null;
    }
    float h = Math.max(16f, rowH - 2f * BTN_Y_INSET);
    float w = Math.max(BTN_MIN_W, h);
    float wantX = Math.max(rowX, ink - BTN_GAP - w);
    boolean clamped = ink - BTN_GAP - w < rowX;
    if (wantX + w + ROW_MIN_CLEAR > ink) {
      seatDiag = "row " + rect(rowPos) + ": " + labelDiag + "; ink=" + (int) ink
          + " leaves no room, REFUSED";
      return null;
    }
    seatDiag = "row " + rect(rowPos) + ": " + labelDiag + "; ink=" + (int) ink
        + "; button " + (int) w + "x" + (int) h + " wants x=" + (int) wantX
        + (clamped ? " (CLAMPED to the row's left edge)" : "")
        + "; gap asked for " + (int) (ink - wantX - w);

    CustomPanelAPI holder = Global.getSettings().createCustom(w, h, plugin);
    TooltipMakerAPI el = holder.createUIElement(w, h, false);
    el.setButtonFontVictor14();
    button = el.addButton(FACE, Plugin.OPEN,
        NomadUi.text(), NomadUi.dark(), Alignment.MID, CutStyle.ALL, w, h, 0f);
    NomadUi.setEnabledSayingWhy(button, true);
    el.addTooltipToPrevious(NomadTip.guard(new Hover()), TooltipMakerAPI.TooltipLocation.BELOW);
    holder.addUIElement(el).inTL(0f, 0f);

    row.addComponent(holder).inLMid(0f);
    seated = holder;
    seatedIn = row;
    seatWantOff = wantX - rowX;
    seatOffset = 0f;
    alignSeat();
    return null;
  }

  private void alignSeat() {
    if (seated == null || seatedIn == null || button == null) {
      return;
    }
    PositionAPI rowPos = ((UIComponentAPI) seatedIn).getPosition();
    PositionAPI btn = button.getPosition();
    if (rowPos == null || btn == null || rowPos.getWidth() <= 0f) {
      return;
    }
    float rowX = rowPos.getX();
    float bx = btn.getX();
    if (bx < rowX - MAX_WRAPPER_INSET || bx > rowX + rowPos.getWidth()) {
      return;
    }
    float want = rowX + seatWantOff;
    float delta = want - bx;
    if (Math.abs(delta) < 1f) {
      return;
    }
    seatOffset += delta;
    seated.getPosition().setXAlignOffset(seatOffset);
  }

  private static float nameInkLeft(UIPanelAPI row, String displayName) throws Exception {
    LabelAPI best = null;
    float bestW = -1f;
    for (Object child : NomadCoreReflect.childrenList(row)) {
      if (!(child instanceof LabelAPI)) {
        continue;
      }
      LabelAPI l = (LabelAPI) child;
      PositionAPI p = l.getPosition();
      float pw = p == null ? 0f : p.getWidth();
      if (displayName != null && displayName.equals(l.getText())) {
        return inkLeftOf(l, true);
      }
      if (pw > bestW) {
        best = l;
        bestW = pw;
      }
    }
    return best == null ? NO_LABEL : inkLeftOf(best, false);
  }

  private static float inkLeftOf(LabelAPI l, boolean matchedByText) {
    PositionAPI p = l.getPosition();
    String text = l.getText();
    if (p == null || p.getWidth() <= 1f || text == null || text.length() == 0) {
      labelDiag = "label " + (matchedByText ? "by text" : "by width") + " rect " + rect(p)
          + " text=" + (text == null ? "null" : "\"" + text + "\"");
      return NOT_LAID_OUT;
    }
    float textW = l.computeTextWidth(text);
    labelDiag = "label " + (matchedByText ? "by text" : "by width") + " rect " + rect(p)
        + " text=\"" + text + "\" computeTextWidth=" + (int) textW;
    if (textW <= 0f || textW > p.getWidth()) {
      return NOT_LAID_OUT;
    }
    return p.getX() + p.getWidth() - textW;
  }

  private static String rect(PositionAPI p) {
    return p == null ? "(none)"
        : "x=" + (int) p.getX() + " y=" + (int) p.getY()
            + " " + (int) p.getWidth() + "x" + (int) p.getHeight();
  }

  private static volatile String strandDiag = "none";

  private static volatile String seatDiag = "never seated";
  private static volatile String labelDiag = "no label read";

  public static String seatDiag() {
    return seatDiag;
  }

  public static ShipVariantAPI liveRefitVariant() {
    try {
      Object panel = livePanel;
      if (panel == null) {
        return null;
      }
      Object display = NomadCoreReflect.invokeNoArg("getShipDisplay", panel);
      Object variant = display == null ? null
          : NomadCoreReflect.invokeNoArg("getCurrentVariant", display);
      return variant instanceof ShipVariantAPI ? (ShipVariantAPI) variant : null;
    } catch (Throwable t) {
      return null;
    }
  }

  public static ShipVariantAPI editedVariant(FleetMemberAPI member, ShipVariantAPI fallback) {
    if (member == null) {
      return fallback;
    }
    try {
      Object panel = livePanel;
      CampaignUIAPI ui = Global.getSector() == null ? null : Global.getSector().getCampaignUI();
      if (panel != null && ui != null && ui.getCurrentCoreTab() == CoreUITabId.REFIT
          && NomadCoreReflect.invokeNoArg("getMember", panel) == member) {
        ShipVariantAPI live = liveRefitVariant();
        if (live != null) {
          return live;
        }
      }
    } catch (Throwable t) {
    }
    return fallback != null ? fallback : member.getVariant();
  }

  private static void clearShareOnUnconverted() {
    ShipVariantAPI live = liveRefitVariant();
    if (live != null && !NomadDedication.isEditable(live)) {
      NomadDedication.clearTags(live);
    }
  }

  public static void resyncRefit() {
    resyncRequested = true;
  }

  private static volatile boolean resyncRequested;

  private void resyncIfRequested(boolean refit) {
    if (!resyncRequested) {
      return;
    }
    resyncRequested = false;
    if (!refit) {
      return;
    }
    try {
      Object panel = livePanel;
      if (panel == null) {
        return;
      }
      Object display = NomadCoreReflect.invokeNoArg("getShipDisplay", panel);
      Object variant = display == null ? null
          : NomadCoreReflect.invokeNoArg("getCurrentVariant", display);
      Object opDisplay = NomadCoreReflect.invokeNoArg("getOpDisplay", panel);
      if (opDisplay != null && variant != null) {
        NomadCoreReflect.invokeOneArg("syncWithVariant", opDisplay, variant);
      }
    } catch (Throwable t) {
    }
  }

  private void reseatIfRowRebuilt() {
    if (disabled || seated == null || seatedIn == null || hullmodList == null) {
      return;
    }
    try {
      Object items = NomadCoreReflect.invokeNoArg("getItems", hullmodList);
      if (!(items instanceof List)) {
        return;
      }
      List<?> rows = (List<?>) items;
      for (int i = 0, n = rows.size(); i < n; i++) {
        if (rows.get(i) == seatedIn) {
          return;
        }
      }
      UIPanelAPI row = nomadRow(rows);
      if (row == null) {
        clearShareOnUnconverted();
        seated = null;
        seatedIn = null;
        button = null;
        return;
      }
      seat(row);
    } catch (Throwable ignore) {
    }
  }

  private void toggleScreen() {
    NomadDedicationPanel.consumeCloseRequest(open);
    if (open != null) {
      closeScreen();
      return;
    }
    openScreen();
  }

  private void openScreen() {
    if (member == null || refitTab == null || refitPanel == null || hullmodsPanel == null) {
      return;
    }
    try {
      Object host = NomadCoreReflect.invokeNoArg("getCoreUI", refitPanel);
      if (!(host instanceof UIPanelAPI)) {
        return;
      }
      CustomPanelAPI built = NomadDedicationPanel.create(member, editedVariant(member, null));
      UIPanelAPI hostPanel = (UIPanelAPI) host;
      setHostMatch(hostPanel == descentCore ? MATCH_SAME : MATCH_DIFFERENT);
      hostPanel.addComponent(built).inTL(0f, 0f);
      PositionAPI tab = refitTab.getPosition();
      float[] band = shipDisplayRect();
      float x;
      float bottom;
      if (band != null) {
        x = band[0] + (band[2] - NomadDedicationPanel.WIDTH) / 2f;
        bottom = band[1] + (band[3] - NomadDedicationPanel.HEIGHT) / 2f;
      } else {
        x = tab.getX() + (tab.getWidth() - NomadDedicationPanel.WIDTH) / 2f;
        bottom = tab.getY() + (tab.getHeight() - NomadDedicationPanel.HEIGHT) / 2f;
      }
      x = Math.max(tab.getX() + SCREEN_GAP,
          Math.min(x, tab.getX() + tab.getWidth() - NomadDedicationPanel.WIDTH - SCREEN_GAP));
      bottom = Math.max(tab.getY() + SCREEN_GAP,
          Math.min(bottom, tab.getY() + tab.getHeight() - NomadDedicationPanel.HEIGHT - SCREEN_GAP));
      built.getPosition().setXAlignOffset(x - built.getPosition().getX());
      built.getPosition().setYAlignOffset(bottom + NomadDedicationPanel.HEIGHT - top(built));
      open = built;
      openIn = hostPanel;
    } catch (Throwable t) {
      die("opening the Flotilla screen", t);
    }
  }

  private float[] shipDisplayRect() {
    try {
      Object display = NomadCoreReflect.invokeNoArg("getShipDisplay", refitPanel);
      if (!(display instanceof UIComponentAPI)) {
        return null;
      }
      PositionAPI p = ((UIComponentAPI) display).getPosition();
      if (p == null || p.getWidth() <= 1f || p.getHeight() <= 1f) {
        return null;
      }
      return new float[] { p.getX(), p.getY(), p.getWidth(), p.getHeight() };
    } catch (Throwable t) {
      return null;
    }
  }

  private void closeScreen() {
    if (open == null) {
      return;
    }
    try {
      if (openIn != null) {
        openIn.removeComponent(open);
      }
    } catch (Throwable ignore) {
    }
    open = null;
    openIn = null;
    hoistMisses = 0;
  }

  private void rehomeScreenIfStranded() throws Exception {
    if (open == null || openIn == null || refitPanel == null) {
      return;
    }
    Object live = NomadCoreReflect.invokeNoArg("getCoreUI", refitPanel);
    boolean sameHost = live == openIn;
    if (sameHost && open.getParent() == openIn) {
      return;
    }
    strandDiag = sameHost ? "the host swept the screen out of its own tree"
        : "the host core was replaced under the open screen";
    rehomes[hostSlot()]++;
    closeScreen();
    openScreen();
  }

  private void hoist(boolean refit) {
    int host = hostSlot();
    if (hoistOff || open == null || openIn == null || !refit) {
      hoistCode[host] = hoistOff ? HOIST_GAVE_UP : HOIST_SHUT;
      return;
    }
    hoistEverOpen[host] = true;
    try {
      if (NomadCoreReflect.hoistToTop(openIn, open)) {
        hoistMisses = 0;
        hoistMissCount[host] = 0;
        hoistCode[host] = HOIST_HOLDING;
        hoistEverHeld[host] = true;
        return;
      }
      hoistMissCount[host] = ++hoistMisses;
      if (hoistMisses > hoistWorstMisses[host]) {
        hoistWorstMisses[host] = hoistMisses;
      }
      hoistCode[host] = HOIST_STRANDED;
      if (hoistMisses >= HOIST_GIVE_UP) {
        closeScreen();
      }
    } catch (Throwable t) {
      hoistOff = true;
      hoistCode[host] = HOIST_GAVE_UP;
      Global.getLogger(NomadRefitInjector.class).warn(hoistWarning(), t);
    }
  }

  private static final int HOIST_SHUT = 0;
  private static final int HOIST_HOLDING = 1;
  private static final int HOIST_STRANDED = 2;
  private static final int HOIST_GAVE_UP = 3;

  private static final int HOST_CAMPAIGN = 0;
  private static final int HOST_DOCKED = 1;
  private static final String[] HOST_NAMES = { "campaign core", "docked dialog core" };

  private static final int[] hoistCode = { HOIST_SHUT, HOIST_SHUT };
  private static final int[] hoistMissCount = new int[2];
  private static final boolean[] hoistEverOpen = new boolean[2];
  private static final boolean[] hoistEverHeld = new boolean[2];
  private static final int[] hoistWorstMisses = new int[2];
  private static final int[] rehomes = new int[2];

  private static int hostSlot() {
    try {
      if (Global.getSector() != null && Global.getSector().getCampaignUI() != null
          && Global.getSector().getCampaignUI().getCurrentInteractionDialog() != null) {
        return HOST_DOCKED;
      }
    } catch (Throwable ignore) {
    }
    return HOST_CAMPAIGN;
  }

  public static int rehomeCount() {
    return rehomes[HOST_CAMPAIGN] + rehomes[HOST_DOCKED];
  }

  public static String hoistDiag() {
    StringBuilder sb = new StringBuilder();
    sb.append("last stranding: ").append(strandDiag).append("; ");
    for (int i = 0; i < hoistCode.length; i++) {
      if (i > 0) {
        sb.append("; ");
      }
      sb.append(HOST_NAMES[i]).append(": ").append(hoistDiagOf(i));
    }
    return sb.toString();
  }

  private static String hoistDiagOf(int host) {
    if (!hoistEverOpen[host]) {
      return "no screen has ever been open here";
    }
    String sticky = hoistEverHeld[host]
        ? "the order WAS held here at least once"
        : "the order was NEVER held here, on any frame a screen was open";
    if (hoistWorstMisses[host] > 0) {
      sticky += "; worst run of frames the screen spent outside its host's tree: "
          + hoistWorstMisses[host] + " (takes it down at " + HOIST_GIVE_UP + ")";
    }
    if (rehomes[host] > 0) {
      sticky += "; re-homed into the live core " + rehomes[host] + " time(s)";
    }
    switch (hoistCode[host]) {
      case HOIST_HOLDING:
        return sticky + "; right now: holding";
      case HOIST_STRANDED:
        return sticky + "; right now: the screen is not in its host's tree ("
            + hoistMissCount[host] + ")";
      case HOIST_GAVE_UP:
        return sticky + "; right now: gave up, the screen renders under the tab's own black";
      default:
        return sticky + "; right now: no screen open";
    }
  }

  private static final int MATCH_UNKNOWN = 0;
  private static final int MATCH_SAME = 1;
  private static final int MATCH_DIFFERENT = 2;
  private static final int[] hostMatched = new int[2];

  private static void setHostMatch(int value) {
    hostMatched[hostSlot()] = value;
  }

  public static String hostDiag() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < hostMatched.length; i++) {
      if (i > 0) {
        sb.append("; ");
      }
      sb.append(HOST_NAMES[i]).append(": ");
      sb.append(hostMatched[i] == MATCH_SAME ? "added to the descent's own core"
              : hostMatched[i] == MATCH_DIFFERENT
                      ? "added to a DIFFERENT core than the descent walked"
                      : "never opened here");
    }
    return sb.toString();
  }

  private static float top(UIComponentAPI c) {
    return c.getPosition().getY() + c.getPosition().getHeight();
  }

  private void die(String cause, Throwable t) {
    disabled = true;
    closeScreen();
    Global.getLogger(NomadRefitInjector.class).warn(disableWarning(cause), t);
  }

  static String disableWarning(String cause) {
    return "Nomad Flotilla: the refit screen's Flotilla button is off for this session ("
        + cause + " could not be resolved in the refit screen, most likely a game update or a UI mod). "
        + "Allocations cannot be changed until this is fixed; every ship keeps its current percentages "
        + "and the Flotilla keeps the capacity they already give.";
  }

  static String hoistWarning() {
    return "Nomad Flotilla: the refit screen's Flotilla panel cannot be held above the refit tab for this "
        + "session (the core UI's render-order call could not be reached, most likely a game update). "
        + "The screen still opens and allocations still work; it renders dark, because the refit tab "
        + "paints its own background over anything under it.";
  }

  private final class Plugin implements CustomUIPanelPlugin {
    static final String OPEN = "nomad_refit_ded_open";

    @Override
    public void buttonPressed(Object buttonId) {
      if (OPEN.equals(buttonId)) {
        toggleScreen();
      }
    }

    @Override
    public void positionChanged(PositionAPI position) {
    }

    @Override
    public void renderBelow(float alphaMult) {
    }

    @Override
    public void render(float alphaMult) {
    }

    @Override
    public void advance(float amount) {
    }

    @Override
    public void processInput(List<InputEventAPI> events) {
    }
  }

  private final class Hover extends NomadTip {
    @Override
    public void createTooltip(TooltipMakerAPI t, boolean expanded, Object param) {
      t.addPara(NomadStrings.get("nomad_dedication", "ded_button"), NomadUi.base(), 0f);
      ShipVariantAPI variant = editedVariant(member, null);
      float[] pair = NomadDedication.of(member, variant);
      if (pair == null) {
        t.addPara(NomadStrings.get("nomad_dedication", "ded_needs_mod"), NomadUi.gray(), 3f);
        return;
      }
      NomadCapacity.Capacity gives =
          NomadCapacityUtil.forShip(member.getStats(), variant, pair[0], pair[1]);
      NomadCapacity.Capacity fitted = NomadCapacityUtil.fittedCapacity(member.getStats(), variant);
      t.addPara(NomadStrings.get("nomad_hullmods", "hm_dedication"), 3f, NomadUi.hl(),
          (int) pair[0] + "%", (int) pair[1] + "%");
      t.addPara(NomadStrings.get("nomad_hullmods", "hm_contribution"), 3f, NomadUi.hl(),
          NomadUi.i(gives.space), NomadUi.i(gives.berths));
      t.addPara(NomadStrings.get("nomad_dedication", "ded_row_keeps"), 3f, NomadUi.hl(),
          NomadUi.i(Math.max(0f, fitted.space - gives.space)),
          NomadUi.i(Math.max(0f, fitted.berths - gives.berths)));
      if (!NomadUi.playerIsDocked()) {
        t.addPara(NomadStrings.get("nomad_dedication", "ded_needs_dock"), NomadUi.warn(), 3f);
      }
    }
  }
}
