package nomad.ui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import org.lwjgl.input.Keyboard;

import nomad.NomadStrings;
import nomad.campaign.NomadCapacityUtil;
import nomad.campaign.NomadDedication;
import nomad.campaign.NomadEcon;
import nomad.campaign.NomadModel;
import nomad.campaign.econ.NomadCapacity;
import nomad.campaign.econ.ShareOutcome;
import nomad.campaign.econ.ShellRules;
import nomad.campaign.econ.SplitShortfall;
import nomad.campaign.econ.model.FlotillaState;
import nomad.campaign.econ.model.NeedsLevel;
import nomad.campaign.econ.model.WorkCatalog;
import nomad.campaign.econ.model.WorkDef;
import nomad.data.NomadFleetData;

public class NomadDedicationPanel implements CustomUIPanelPlugin {

  public static final String P = "nomad_ded*";
  public static final String CARGO = "cargo";
  public static final String QUARTERS = "quarters";

  public static final float WIDTH = NomadDedicationLayout.WIDTH;
  public static final float HEIGHT = NomadDedicationLayout.HEIGHT;

  private final FleetMemberAPI member;
  private final float width;
  private final float height;
  private final NomadSlider cargo = new NomadSlider(CARGO);
  private final NomadSlider quarters = new NomadSlider(QUARTERS);
  private final NomadColumns.Layout cols = NomadDedicationLayout.row();

  private static final java.awt.Color GROUND = new java.awt.Color(3, 5, 8);

  private static final float CLOSE_REST_ALPHA = 0.7f;

  private boolean hasAxes;
  private CustomPanelAPI root;
  private CustomPanelAPI content;
  private boolean closeRequested;
  private boolean closeHover;
  private SpriteAPI closeGlyph;
  private boolean closeGlyphMissing;
  private int framesLive;
  private static final int ARM_FRAMES = 2;
  private CustomPanelAPI cargoRow;
  private CustomPanelAPI quartersRow;
  private PositionAPI pos;
  private LabelAPI cargoPct;
  private LabelAPI cargoUsed;
  private LabelAPI quartersPct;
  private LabelAPI quartersUsed;

  private CustomPanelAPI outcomeSeat;
  private TooltipMakerAPI outcomeLines;
  private NomadNeedsPicture picture;
  private NeedsLevel pictureLevel;
  private boolean pictureMade;
  private boolean flotillaRead;
  private FlotillaState flotillaAtOpen;
  private NomadCapacity.Capacity givenAtOpen;
  private ShellRules.ShareWrite writeAtOpen = ShellRules.ShareWrite.SAVED;
  private int cargoPctAtOpen;
  private int quartersPctAtOpen;
  private ShareOutcome outcome;
  private int outcomeCargoPct = -1;
  private int outcomeQuartersPct = -1;

  private final ShipVariantAPI handed;

  private NomadDedicationPanel(FleetMemberAPI member, ShipVariantAPI handed, float width,
      float height) {
    this.member = member;
    this.handed = handed;
    this.width = width;
    this.height = height;
  }

  public static CustomPanelAPI create(FleetMemberAPI member, ShipVariantAPI variant) {
    NomadDedicationPanel p = new NomadDedicationPanel(member, variant, WIDTH, HEIGHT);
    p.root = Global.getSettings().createCustom(WIDTH, HEIGHT, p);
    p.build();
    return p.root;
  }

  private ShipVariantAPI editing;

  private ShipVariantAPI variant() {
    if (editing == null) {
      editing = nomad.topbar.NomadRefitInjector.editedVariant(member, handed);
    }
    return editing;
  }

  public static float paintedHeightOf(CustomPanelAPI panel) {
    if (panel == null) {
      return -1f;
    }
    Object plugin = panel.getPlugin();
    return plugin instanceof NomadDedicationPanel ? ((NomadDedicationPanel) plugin).height : -1f;
  }

  public static float[] screenRectOf(CustomPanelAPI panel) {
    if (panel == null) {
      return null;
    }
    Object plugin = panel.getPlugin();
    if (!(plugin instanceof NomadDedicationPanel)) {
      return null;
    }
    NomadDedicationPanel p = (NomadDedicationPanel) plugin;
    return p.trueRect();
  }

  private float[] trueRect() {
    if (content != null && content.getPosition() != null
        && content.getPosition().getWidth() > 1f) {
      PositionAPI c = content.getPosition();
      return new float[] { c.getX(), c.getY(), c.getWidth(), c.getHeight() };
    }
    return pos == null ? null
        : new float[] { pos.getX(), pos.getY(), pos.getWidth(), pos.getHeight() };
  }

  public static float[] knobRectOf(CustomPanelAPI panel, String axis) {
    float[] track = trackRectOf(panel, axis);
    if (track == null) {
      return null;
    }
    Object plugin = panel.getPlugin();
    NomadDedicationPanel p = (NomadDedicationPanel) plugin;
    NomadSlider s = CARGO.equals(axis) ? p.cargo : p.quarters;
    float kx = NomadDedicationLayout.knobX(s.pct(), track[0], track[2], NomadDedicationLayout.KNOB_W);
    return new float[] { kx, track[1], NomadDedicationLayout.KNOB_W, track[3] };
  }

  public static float[] trackRectOf(CustomPanelAPI panel, String axis) {
    if (panel == null) {
      return null;
    }
    Object plugin = panel.getPlugin();
    if (!(plugin instanceof NomadDedicationPanel)) {
      return null;
    }
    NomadDedicationPanel p = (NomadDedicationPanel) plugin;
    if (!p.hasAxes) {
      return null;
    }
    NomadSlider s = CARGO.equals(axis) ? p.cargo : QUARTERS.equals(axis) ? p.quarters : null;
    return s == null ? null : s.rect();
  }

  public static float[] closeRectOf(CustomPanelAPI panel) {
    if (panel == null) {
      return null;
    }
    Object plugin = panel.getPlugin();
    return plugin instanceof NomadDedicationPanel ? ((NomadDedicationPanel) plugin).closeRect() : null;
  }

  private void build() {
    content = root.createCustomPanel(width, height, NomadUi.consoleRow());
    root.addComponent(content).inTL(0f, 0f);

    float inset = NomadDedicationLayout.INSET;
    float bodyW = NomadDedicationLayout.contentW();
    TooltipMakerAPI body = content.createUIElement(bodyW, height - 2f * inset, false);
    body.addTitle(NomadStrings.get("nomad_dedication", "ded_button"), NomadUi.bright());

    editing = null;
    ShipVariantAPI variant = variant();
    float[] pair = NomadDedication.of(member, variant);
    if (pair == null) {
      body.addPara(NomadStrings.get("nomad_dedication", "ded_needs_mod"), NomadUi.gray(),
          NomadDedicationLayout.TITLE_GAP);
      content.addUIElement(body).inTL(inset, inset);
      hasAxes = false;
      cargo.place(0f, 0f, 0f, 0f);
      quarters.place(0f, 0f, 0f, 0f);
      return;
    }
    hasAxes = true;

    boolean docked = NomadUi.playerIsDocked();
    cargoRow = axisRow(body, cargo, pair[0], docked);
    quartersRow = axisRow(body, quarters, pair[1], docked);

    body.addPara(NomadStrings.get("nomad_dedication", "ded_needs_dock"),
        docked ? NomadUi.gray() : NomadUi.warn(), NomadDedicationLayout.NOTE_GAP);

    outcomeSeat = null;
    outcomeLines = null;
    if (docked) {
      readFlotilla(pair);
      if (flotillaAtOpen != null) {
        outcomeSeat = content.createCustomPanel(bodyW, NomadDedicationLayout.OUTCOME_H,
            NomadRowChrome.NONE);
        showOutcome();
        body.addCustom(outcomeSeat, NomadDedicationLayout.OUTCOME_GAP);
        body.addTooltipTo(outcomeTip(), outcomeSeat, TooltipMakerAPI.TooltipLocation.BELOW, false);
      }
    }

    content.addUIElement(body).inTL(inset, inset);

    seatSliders();
  }

  private float[] closeRect() {
    float[] r = trueRect();
    if (r == null) {
      return null;
    }
    float size = NomadDedicationLayout.CLOSE_SIZE;
    float gap = NomadDedicationLayout.CLOSE_INSET;
    return new float[] { r[0] + r[2] - size - gap, r[1] + r[3] - size - gap, size, size };
  }

  private static boolean inRect(float[] r, float px, float py) {
    return r != null && px >= r[0] && px <= r[0] + r[2] && py >= r[1] && py <= r[1] + r[3];
  }

  private void renderClose() {
    float[] c = closeRect();
    if (c == null) {
      return;
    }
    SpriteAPI glyph = closeGlyph();
    if (glyph == null) {
      NomadGl.opaqueBox(c[0], c[1], c[2], c[3], GROUND, closeHover ? NomadUi.bright() : NomadUi.dark());
      return;
    }
    glyph.setNormalBlend();
    glyph.setSize(NomadDedicationLayout.CLOSE_GLYPH, NomadDedicationLayout.CLOSE_GLYPH);
    glyph.setColor(closeHover ? NomadUi.bright() : NomadUi.base());
    glyph.setAlphaMult(closeHover ? 1f : CLOSE_REST_ALPHA);
    glyph.renderAtCenter(c[0] + c[2] / 2f, c[1] + c[3] / 2f);
  }

  private SpriteAPI closeGlyph() {
    if (closeGlyph != null || closeGlyphMissing) {
      return closeGlyph;
    }
    try {
      closeGlyph = Global.getSettings().getSprite("codex", "close");
    } catch (Throwable t) {
      closeGlyph = null;
    }
    closeGlyphMissing = closeGlyph == null;
    return closeGlyph;
  }

  public static boolean consumeCloseRequest(CustomPanelAPI panel) {
    if (panel == null) {
      return false;
    }
    Object plugin = panel.getPlugin();
    if (!(plugin instanceof NomadDedicationPanel)) {
      return false;
    }
    NomadDedicationPanel p = (NomadDedicationPanel) plugin;
    boolean asked = p.closeRequested;
    p.closeRequested = false;
    return asked;
  }

  private CustomPanelAPI axisRow(TooltipMakerAPI body, NomadSlider slider, float pct, boolean docked) {
    boolean isCargo = CARGO.equals(slider.axis());
    float rowH = NomadDedicationLayout.ROW_H;
    String locked = lockedReason(slider.axis(), docked);

    slider.setMin(NomadDedication.minimums()[isCargo ? 0 : 1]);
    slider.setPct(pct);
    slider.setInteractive(locked == null);

    CustomPanelAPI row = content.createCustomPanel(NomadDedicationLayout.contentW(), rowH,
        NomadRowChrome.NONE);

    TooltipMakerAPI lab = row.createUIElement(cols.w(NomadDedicationLayout.COL_LABEL), rowH, false);
    lab.addPara(NomadStrings.get("nomad_dedication", isCargo ? "ded_cargo" : "ded_quarters"),
        locked == null ? NomadUi.text() : NomadUi.gray(), 0f);
    lab.addTooltipToPrevious(NomadTable.tip(
        locked != null ? locked : NomadStrings.get("nomad_dedication", "ded_slider_tip"), 0f),
        TooltipMakerAPI.TooltipLocation.BELOW);
    row.addUIElement(lab).inTL(cols.x(NomadDedicationLayout.COL_LABEL), 0f);

    TooltipMakerAPI pctCell = row.createUIElement(cols.w(NomadDedicationLayout.COL_PCT), rowH, false);
    LabelAPI pctLabel = pctCell.addPara(pctText(pct), NomadUi.hl(), 0f);
    pctLabel.setAlignment(Alignment.RMID);
    row.addUIElement(pctCell).inTL(cols.x(NomadDedicationLayout.COL_PCT), 0f);

    TooltipMakerAPI usedCell = row.createUIElement(cols.w(NomadDedicationLayout.COL_USED), rowH, false);
    LabelAPI usedLabel = usedCell.addPara(usedText(slider.axis(), pct), NomadUi.text(), 0f);
    highlightGiven(usedLabel, slider.axis(), pct);
    row.addUIElement(usedCell).inTL(cols.x(NomadDedicationLayout.COL_USED), 0f);

    body.addCustom(row, isCargo ? NomadDedicationLayout.TITLE_GAP : NomadDedicationLayout.ROW_GAP);

    if (isCargo) {
      cargoPct = pctLabel;
      cargoUsed = usedLabel;
    } else {
      quartersPct = pctLabel;
      quartersUsed = usedLabel;
    }
    return row;
  }

  private void seatSliders() {
    if (!hasAxes) {
      return;
    }
    seat(cargo, cargoRow);
    seat(quarters, quartersRow);
  }

  private void seat(NomadSlider slider, CustomPanelAPI row) {
    if (row == null) {
      return;
    }
    PositionAPI rp = row.getPosition();
    if (rp == null || rp.getWidth() <= 0f || rp.getHeight() <= 0f) {
      return;
    }
    float h = Math.max(8f, rp.getHeight() - 2f);
    slider.place(rp.getX() + cols.x(NomadDedicationLayout.COL_SLIDER),
        rp.getY() + (rp.getHeight() - h) / 2f, cols.w(NomadDedicationLayout.COL_SLIDER), h);
  }

  private static String pctText(float pct) {
    return (int) pct + "%";
  }

  private String usedText(String axis, float pct) {
    boolean isCargo = CARGO.equals(axis);
    return NomadStrings.get("nomad_dedication", isCargo ? "ded_space_used" : "ded_berths_used",
        NomadUi.i(given(axis, pct)), NomadUi.i(fittedTotal(isCargo)));
  }

  private void highlightGiven(LabelAPI label, String axis, float pct) {
    label.setHighlightColor(NomadUi.hl());
    label.highlightFirst(NomadUi.i(given(axis, pct)));
  }

  private void refreshReadouts(String axis, float pct) {
    boolean isCargo = CARGO.equals(axis);
    LabelAPI pctLabel = isCargo ? cargoPct : quartersPct;
    LabelAPI usedLabel = isCargo ? cargoUsed : quartersUsed;
    if (pctLabel != null) {
      pctLabel.setText(pctText(pct));
    }
    if (usedLabel != null) {
      usedLabel.setText(usedText(axis, pct));
      highlightGiven(usedLabel, axis, pct);
    }
  }

  private void readFlotilla(float[] pair) {
    if (flotillaRead) {
      return;
    }
    flotillaRead = true;
    try {
      NomadFleetData data = NomadFleetData.getOrNull();
      CampaignFleetAPI player = Global.getSector() == null ? null : Global.getSector().getPlayerFleet();
      if (data == null || player == null || !data.isFounded()
          || member.getFleetData() != player.getFleetData()) {
        return;
      }
      FlotillaState now = NomadModel.snapshot(data, player, 0f);
      if (now.paused || now.detached) {
        return;
      }
      givenAtOpen = NomadCapacityUtil.forShip(member.getStats(), variant(), pair[0], pair[1]);
      writeAtOpen = ShellRules.shareWrite(NomadDedication.isEditable(member.getVariant()),
          NomadDedication.isEditable(variant()));
      cargoPctAtOpen = (int) pair[0];
      quartersPctAtOpen = (int) pair[1];
      flotillaAtOpen = now;
    } catch (RuntimeException e) {
      outcomeFailed(e);
    }
  }

  private ShareOutcome outcome() {
    if (flotillaAtOpen == null) {
      return null;
    }
    int cargoPct = (int) cargo.pct();
    int quartersPct = (int) quarters.pct();
    if (outcome != null && cargoPct == outcomeCargoPct && quartersPct == outcomeQuartersPct) {
      return outcome;
    }
    try {
      float space = flotillaAtOpen.space;
      float berths = flotillaAtOpen.berths;
      if (ShellRules.sharePredictsChange(writeAtOpen,
          cargoPct != cargoPctAtOpen || quartersPct != quartersPctAtOpen)) {
        NomadCapacity.Capacity gives = NomadCapacityUtil.forShip(member.getStats(), variant(),
            cargoPct, quartersPct);
        space = ShellRules.sharePredicted(writeAtOpen, space, givenAtOpen.space, gives.space);
        berths = ShellRules.sharePredicted(writeAtOpen, berths, givenAtOpen.berths, gives.berths);
      }
      outcome = ShareOutcome.predict(flotillaAtOpen, space, berths, NomadModel.catalog(),
          NomadModel.rules(), NomadEcon.constants());
      outcomeCargoPct = cargoPct;
      outcomeQuartersPct = quartersPct;
    } catch (RuntimeException e) {
      outcomeFailed(e);
    }
    return outcome;
  }

  private void outcomeFailed(RuntimeException e) {
    outcome = null;
    flotillaAtOpen = null;
    Global.getLogger(NomadDedicationPanel.class).warn("Nomad Flotilla: the refit screen's Flotilla panel"
        + " could not work out what the ship's shares would do, so it shows the sliders without it.", e);
  }

  private void showOutcome() {
    if (outcomeSeat == null) {
      return;
    }
    if (outcomeLines != null) {
      outcomeSeat.removeComponent(outcomeLines);
    }
    ShareOutcome o = outcome();
    List<ShareOutcome.Line> band = o == null ? null : o.band();
    float textY = NomadDedicationLayout.outcomeTextY(band == null ? 1 : band.size());
    outcomeLines = outcomeSeat.createUIElement(NomadDedicationLayout.outcomeTextW(),
        NomadDedicationLayout.OUTCOME_H - textY, false);
    if (o != null) {
      addOutcomeLines(outcomeLines, o, band, 0f, NomadDedicationLayout.OUTCOME_LINE_PAD,
          NomadDedicationLayout.OUTCOME_LINE_CHARS);
    }
    outcomeSeat.addUIElement(outcomeLines).inTL(NomadDedicationLayout.OUTCOME_TEXT_X, textY);
    showPicture(o == null ? null : o.shortfall.levelAfter, o != null);
  }

  private void showPicture(NeedsLevel level, boolean predicted) {
    if (!predicted) {
      picture = null;
      pictureMade = false;
      return;
    }
    if (pictureMade && level == pictureLevel) {
      return;
    }
    picture = NomadNeedsPicture.of(level,
        NomadDedicationLayout.PICTURE_W / NomadDedicationLayout.PICTURE_H);
    pictureLevel = level;
    pictureMade = true;
  }

  private void renderPicture() {
    if (picture == null || outcomeSeat == null) {
      return;
    }
    PositionAPI sp = outcomeSeat.getPosition();
    if (sp == null || sp.getWidth() <= 0f || sp.getHeight() <= 0f) {
      return;
    }
    picture.draw(sp.getX() + NomadDedicationLayout.PICTURE_X,
        sp.getY() + sp.getHeight() - NomadDedicationLayout.PICTURE_H,
        NomadDedicationLayout.PICTURE_W, NomadDedicationLayout.PICTURE_H, 1f);
  }

  private TooltipMakerAPI.TooltipCreator outcomeTip() {
    return NomadTip.guard(new NomadTip(NomadDedicationLayout.OUTCOME_TIP_W) {
      @Override
      public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object param) {
        tooltip.addTitle(NomadStrings.get("nomad_flotilla", "ui2_share_title"), NomadUi.base());
        ShareOutcome o = outcome;
        if (o != null) {
          addOutcomeLines(tooltip, o, o.lines(), TIP_OPAD, TIP_PAD, Integer.MAX_VALUE);
        }
      }
    });
  }

  private static final float TIP_OPAD = 10f;
  private static final float TIP_PAD = 3f;

  public static List<ShareOutcome.Line> outcomeLinesOf(CustomPanelAPI panel) {
    NomadDedicationPanel p = shownOutcome(panel);
    return p == null ? new ArrayList<ShareOutcome.Line>() : p.outcome.band();
  }

  public static List<ShareOutcome.Line> outcomeHoverLinesOf(CustomPanelAPI panel) {
    NomadDedicationPanel p = shownOutcome(panel);
    return p == null ? new ArrayList<ShareOutcome.Line>() : p.outcome.lines();
  }

  public static boolean outcomePictureDimmedOf(CustomPanelAPI panel) {
    NomadDedicationPanel p = shownOutcome(panel);
    return p != null && p.picture != null && p.picture.dimmed();
  }

  private static NomadDedicationPanel shownOutcome(CustomPanelAPI panel) {
    Object plugin = panel == null ? null : panel.getPlugin();
    if (!(plugin instanceof NomadDedicationPanel)) {
      return null;
    }
    NomadDedicationPanel p = (NomadDedicationPanel) plugin;
    return p.outcomeSeat == null || p.outcome == null ? null : p;
  }

  private static void addOutcomeLines(TooltipMakerAPI t, ShareOutcome o, List<ShareOutcome.Line> lines,
      float firstPad, float linePad, int lineChars) {
    SplitShortfall s = o.shortfall;
    WorkCatalog catalog = NomadModel.catalog();
    String level = NomadFlotillaView.levelName(s.levelAfter);
    float pad = firstPad;
    for (ShareOutcome.Line line : lines) {
      switch (line) {
        case LEVEL_LEAVE: {
          String leaving = NomadUi.pct(s.leavePerMonthAfter);
          say(t, pad, NomadUi.warn(),
              NomadStrings.get("nomad_flotilla", "confirm_leave", level, leaving), level, leaving);
          break;
        }
        case LEVEL_DOWN:
          say(t, pad, NomadUi.warn(), NomadStrings.get("nomad_flotilla", "needs_after_down", level),
              level);
          break;
        case LEVEL_UP:
          say(t, pad, NomadUi.good(), NomadStrings.get("nomad_flotilla", "needs_after_up", level),
              level);
          break;
        case LEVEL_SAME:
          say(t, pad, NomadUi.hl(), NomadStrings.get("nomad_flotilla", "needs_after_same", level),
              level);
          break;
        case SUSPENDED:
          sayWorks(t, pad, NomadUi.warn(), s.suspended, catalog,
              NomadStrings.get("nomad_overview", "detach_split_suspended_one"),
              NomadStrings.get("nomad_overview", "detach_split_suspended_many"), lineChars);
          break;
        case RETURNING:
          sayWorks(t, pad, NomadUi.good(), o.returning, catalog,
              NomadStrings.get("nomad_dedication", "koflow_return_one"),
              NomadStrings.get("nomad_dedication", "koflow_return_many"), lineChars);
          break;
        case BERTHS: {
          String before = NomadUi.i(s.berthsBefore);
          String after = NomadUi.i(s.berthsAfter);
          String count = NomadUi.i(Math.abs(o.residentsLeaving));
          boolean rise = o.berthsRise();
          say(t, pad, rise ? NomadUi.good() : NomadUi.warn(), NomadStrings.get("nomad_dedication",
              rise ? "ded_berths_rise" : "ded_berths_drop", before, after, count), before, after,
              count);
          break;
        }
        case UNSTAFFED:
          sayWorks(t, pad, NomadUi.warn(), o.unstaffed, catalog,
              NomadStrings.get("nomad_dedication", "koflow_unstaffed_one"),
              NomadStrings.get("nomad_dedication", "koflow_unstaffed_many"), lineChars);
          break;
        default:
          break;
      }
      pad = linePad;
    }
  }

  private static void sayWorks(TooltipMakerAPI t, float pad, Color hl, List<String> ids,
      WorkCatalog catalog, String one, String many, int lineChars) {
    if (ids.size() == 1) {
      String name = workName(ids.get(0), catalog);
      say(t, pad, hl, NomadStrings.format(one, name), name);
      return;
    }
    List<String> names = new ArrayList<String>();
    for (String id : ids) {
      names.add(workName(id, catalog));
    }
    String listed = ShareOutcome.listFor(names, many,
        NomadStrings.get("nomad_dedication", "koflow_list_rest"), lineChars);
    say(t, pad, hl, NomadStrings.format(many, listed), listed);
  }

  private static void say(TooltipMakerAPI t, float pad, Color hl, String text, String... values) {
    LabelAPI label = t.addPara(text, pad);
    label.setHighlight(values);
    label.setHighlightColor(hl);
  }

  private static String workName(String workId, WorkCatalog catalog) {
    WorkDef def = workId == null ? null : catalog.work(workId);
    return def == null || def.name == null || def.name.isEmpty() ? String.valueOf(workId) : def.name;
  }

  private float given(String axis, float pct) {
    ShipVariantAPI variant = member == null ? null : variant();
    if (variant == null) {
      return 0f;
    }
    float[] pair = NomadDedication.of(member, variant);
    if (pair == null) {
      return 0f;
    }
    boolean isCargo = CARGO.equals(axis);
    NomadCapacity.Capacity gives = NomadCapacityUtil.forShip(member.getStats(), variant,
        isCargo ? pct : pair[0], isCargo ? pair[1] : pct);
    return isCargo ? gives.space : gives.berths;
  }

  private float fittedTotal(boolean isCargo) {
    if (member == null) {
      return 0f;
    }
    NomadCapacity.Capacity all = NomadCapacityUtil.fittedCapacity(member.getStats(), variant());
    return isCargo ? all.space : all.berths;
  }

  private String lockedReason(String axis, boolean docked) {
    if (!docked) {
      return NomadStrings.get("nomad_dedication", "ded_needs_dock");
    }
    ShipVariantAPI variant = member == null ? null : variant();
    if (variant != null && variant.getHullSpec() != null) {
      com.fs.starfarer.api.combat.ShipHullSpecAPI spec = variant.getHullSpec();
      if (QUARTERS.equals(axis) && spec.getMaxCrew() <= 0f) {
        return NomadStrings.get("nomad_dedication", "ded_no_crew");
      }
      if (CARGO.equals(axis) && spec.getCargo() <= 0f) {
        return NomadStrings.get("nomad_dedication", "ded_no_cargo");
      }
    }
    return null;
  }

  public static boolean setPct(FleetMemberAPI member, String axis, float pct) {
    return setPct(member, null, axis, pct);
  }

  public static boolean setPct(FleetMemberAPI member, ShipVariantAPI edited, String axis, float pct) {
    ShipVariantAPI saved = member == null ? null : member.getVariant();
    ShipVariantAPI variant = edited != null ? edited : saved;
    ShellRules.ShareWrite where = ShellRules.shareWrite(NomadDedication.isEditable(saved),
        NomadDedication.isEditable(variant));
    if (where == ShellRules.ShareWrite.REFUSE || !NomadUi.playerIsDocked()) {
      return false;
    }
    if (where == ShellRules.ShareWrite.EDITED_ONLY) {
      NomadFleetData data = NomadFleetData.getOrNull();
      if (data != null && member.getId() != null && data.getDedication().remove(member.getId()) != null) {
        data.markChanged();
      }
    }
    float[] pair = NomadDedication.of(member, variant);
    if (pair == null) {
      return false;
    }
    float[] min = NomadDedication.minimums();
    float cargoPct = pair[0];
    float quartersPct = pair[1];
    if (CARGO.equals(axis)) {
      cargoPct = NomadCapacity.clampPct(pct, min[0]);
    } else if (QUARTERS.equals(axis)) {
      quartersPct = NomadCapacity.clampPct(pct, min[1]);
    } else {
      return false;
    }
    if ((int) cargoPct == (int) pair[0] && (int) quartersPct == (int) pair[1]) {
      return false;
    }
    if (where == ShellRules.ShareWrite.SAVED) {
      NomadDedication.write(member, saved, cargoPct, quartersPct);
    }
    if (variant != saved) {
      NomadDedication.writeTagOnly(variant, cargoPct, quartersPct);
    }
    syncStats(member);
    nomad.topbar.NomadRefitInjector.resyncRefit();
    return true;
  }

  private static void syncStats(FleetMemberAPI member) {
    try {
      member.updateStats();
      nomad.campaign.NomadManager m = nomad.campaign.NomadManager.getInstance();
      if (m != null && member.getVariant() != null
          && member.getVariant().hasHullMod(nomad.campaign.NomadManager.FLAGSHIP_HULLMOD_ID)) {
        m.eagerUpdate(member, true);
      }
    } catch (Throwable t) {
    }
  }

  private void rebuild() {
    if (root == null || content == null) {
      return;
    }
    root.removeComponent(content);
    content = null;
    cargoRow = null;
    quartersRow = null;
    build();
  }

  @Override
  public void buttonPressed(Object buttonId) {
  }

  @Override
  public void positionChanged(PositionAPI position) {
    this.pos = position;
    seatSliders();
  }

  @Override
  public void renderBelow(float alphaMult) {
    lastAlphaMult = alphaMult;
    float[] r = trueRect();
    if (r == null) {
      return;
    }
    NomadGl.opaqueBox(r[0], r[1], r[2], r[3], GROUND, NomadUi.dark());
  }

  private float lastAlphaMult = -1f;

  public static float lastAlphaOf(CustomPanelAPI panel) {
    if (panel == null) {
      return -1f;
    }
    Object plugin = panel.getPlugin();
    return plugin instanceof NomadDedicationPanel
        ? ((NomadDedicationPanel) plugin).lastAlphaMult : -1f;
  }

  @Override
  public void render(float alphaMult) {
    cargo.render(alphaMult);
    quarters.render(alphaMult);
    renderPicture();
    renderClose();
  }

  @Override
  public void advance(float amount) {
    if (framesLive < ARM_FRAMES) {
      framesLive++;
    }
    seatSliders();
    closeHover = inRect(closeRect(), Global.getSettings().getMouseX(), Global.getSettings().getMouseY());
    drive(cargo, cargo.advance());
    drive(quarters, quarters.advance());
  }

  private void drive(NomadSlider slider, NomadSlider.Change change) {
    if (change == NomadSlider.Change.MOVED) {
      refreshReadouts(slider.axis(), slider.pct());
      showOutcome();
    } else if (change == NomadSlider.Change.SETTLED) {
      setPct(member, variant(), slider.axis(), slider.pct());
      rebuild();
    }
  }

  @Override
  public void processInput(List<InputEventAPI> events) {
    drive(cargo, cargo.handle(events));
    drive(quarters, quarters.handle(events));
    if (events == null) {
      return;
    }
    for (InputEventAPI e : events) {
      if (e.isConsumed() || !(e.isKeyDownEvent() || e.isKeyUpEvent())
          || e.getEventValue() != Keyboard.KEY_ESCAPE) {
        continue;
      }
      if (e.isKeyDownEvent()) {
        closeRequested = true;
      }
      e.consume();
    }
    float[] r = trueRect();
    if (r == null) {
      return;
    }
    float left = r[0];
    float right = left + r[2];
    float bottom = r[1];
    float top = bottom + r[3];
    float[] close = closeRect();
    for (InputEventAPI e : events) {
      if (e.isConsumed() || !e.isMouseEvent() || e.isMouseScrollEvent()) {
        continue;
      }
      float ex = e.getX();
      float ey = e.getY();
      if (e.isLMBUpEvent() && inRect(close, ex, ey)) {
        closeRequested = true;
        e.consume();
        continue;
      }
      if (ex >= left && ex <= right && ey >= bottom && ey <= top) {
        e.consume();
      } else if (framesLive >= ARM_FRAMES && (e.isLMBDownEvent() || e.isRMBDownEvent())) {
        closeRequested = true;
      }
    }
  }
}
