package nomad.ui;

import java.awt.Color;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.combat.ShieldAPI.ShieldType;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.impl.codex.CodexDataV2;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.Fonts;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TextFieldAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import nomad.NomadStrings;
import nomad.data.NomadFleetData;
import nomad.intel.NomadIntel;

public final class NomadAutoforgeTab {

  private NomadAutoforgeTab() {
  }

  static final String P = "af_";

  private static final float PAD = 4f;
  private static final float SHIFT = NomadText.INSET;

  private static String word(String key) {
    return NomadStrings.get("nomad_autoforge", key);
  }

  private static final float CHIP_H = 20f;
  private static final float SHIPS_W = 70f;
  private static final float WEAPONS_X = 74f;
  private static final float WEAPONS_W = 86f;
  private static final float INLINE_CAPTION_X = 182f;
  private static final float INLINE_CHIPS_X = 216f;
  private static final float CHIPS_X = 52f;
  private static final float CAPTION_DOWN = 3f;
  private static final float CHIPROWH = 26f;
  private static final float CHIPGAP = 4f;
  private static final float SEARCH_W = 214f;
  private static final float SEARCH_MIN_W = 120f;
  private static final float SEARCH_H = 22f;
  private static final float SEARCH_UP = 1f;
  private static final float SEARCH_CAPTION_W = 48f;
  private static final float HEAD_GAP = 10f;
  private static final float CAPTIONS_DOWN = 2f;
  private static final float RULE_DOWN = 19f;
  private static final float CELLS_DOWN = 24f;

  private static final float CELL_SPRITE = 44f;
  private static final float CELL_NAME_X = 52f;
  private static final float CELL_NAME_Y = 6f;
  private static final float CELL_SUB_PAD = 3f;
  private static final float CELL_FIGURE_Y = 14f;
  private static final float BTN_W = 64f;
  private static final float BTN_H = 22f;
  private static final float BTN_Y = 11f;
  private static final float DAYS_RIGHT = 68f;
  private static final float DAYS_MIN_W = 32f;
  private static final float COL_GAP = 8f;
  private static final float PAGER_H = 26f;
  private static final float PAGER_BTN_W = 66f;
  private static final float PAGER_GAP = 6f;
  private static final int RULE_ALPHA = 255;

  private static final String[] HULL_SIZE_TOKENS = { "all", "FRIGATE", "DESTROYER", "CRUISER", "CAPITAL_SHIP" };
  private static final String[] HULL_SIZE_LABELS = { "All", "Frigate", "Destroyer", "Cruiser", "Capital" };
  private static final String[] MOUNT_SIZE_TOKENS = { "all", "SMALL", "MEDIUM", "LARGE" };
  private static final String[] MOUNT_SIZE_LABELS = { "All", "Small", "Medium", "Large" };
  private static final String[] MOUNT_TYPE_TOKENS = { "all", "BALLISTIC", "ENERGY", "MISSILE" };
  private static final String[] MOUNT_TYPE_LABELS = { "All", "Ballistic", "Energy", "Missile" };
  private static final String[] ROLE_TOKENS = { "all", "FIGHTER", "INTERCEPTOR", "BOMBER", "ASSAULT", "SUPPORT" };
  private static final String[] ROLE_LABELS = { "All", "Fighter", "Interceptor", "Bomber", "Assault", "Support" };
  private static final String[] CLASS_LABELS = { "All", "Weapons", "Fighters" };
  private static final int PAGE_SIZE = 30;

  static final class Kept {

    private WeakReference<TooltipMakerAPI> live;
    private float offset;
    private String key = "";

    void resume(String nowKey) {
      TooltipMakerAPI el = live == null ? null : live.get();
      if (el != null) {
        offset = NomadTabbedPanels.scrollOf(el);
      }
      live = null;
      if (!key.equals(nowKey)) {
        key = nowKey;
        offset = 0f;
      }
    }

    void place(TooltipMakerAPI el, float contentH, float viewH) {
      offset = NomadGrid.clampScroll(offset, contentH, viewH);
      NomadTabbedPanels.applyScroll(el, offset);
      live = new WeakReference<TooltipMakerAPI>(el);
    }
  }

  static final Kept QUEUE = new Kept();
  private static final Kept WALL = new Kept();

  public static TooltipMakerAPI build(CustomPanelAPI panel, float width, float bodyHeight,
      NomadIntel intel, NomadFleetData data) {
    NomadAutoforge.Line line = NomadAutoforge.line(data);
    NomadAutoforgeQueue.LineState st = NomadAutoforge.state(data, line);
    boolean ships = !NomadAutoforge.WEAPON.equals(intel.getAutoforgeBrowse());

    List<String> preDesign = filteredIgnoringDesign(intel, ships);
    List<String> designs = designsIn(preDesign);
    List<String> ids = withDesign(preDesign, intel.getAutoforgeDesign());
    int totalPages = totalPages(ids.size());
    int page = clampPage(intel.getAutoforgePage(), totalPages);
    if (page != intel.getAutoforgePage()) {
      intel.setAutoforgePage(page);
    }
    List<String> pageIds = pageSlice(ids, page);

    String owner = Integer.toString(System.identityHashCode(data));
    QUEUE.resume(owner);
    WALL.resume(owner + '|' + ships + '|' + page + '|' + intel.getAutoforgeSize() + '|'
        + intel.getAutoforgeType() + '|' + intel.getAutoforgeClass() + '|'
        + intel.getAutoforgeRole() + '|' + intel.getAutoforgeDesign() + '|'
        + nullToEmpty(intel.getAutoforgeSearch()));

    NomadGrid.Screen at = NomadGrid.Screen.of(width, bodyHeight);
    float top = NomadTabbedPanels.BODY_TOP;

    CustomPanelAPI home = panel.createCustomPanel(at.home.w + NomadGrid.GUTTER, at.home.h,
        NomadUi.consoleRow());
    panel.addComponent(home).inTL(at.home.x, top + at.home.y);
    NomadFoundryColumn.build(home, at.home.w, at.home.h, data, line, st,
        NomadAutoforge.stacks(data));

    NomadGrid.Box box = at.picker;
    CustomPanelAPI wall = panel.createCustomPanel(box.w, box.h, NomadUi.consoleRow());
    panel.addComponent(wall).inTL(box.x, top + box.y);
    wall(wall, box.w - NomadGrid.GUTTER, box.h, intel, data, ships, line, pageIds, page,
        totalPages, designs, intelUiOf(panel));
    return null;
  }

  static String haltLine(NomadAutoforgeQueue.LineState st) {
    switch (st) {
      case CONSTRUCTING:
        return NomadStrings.get("nomad_autoforge", "foundry_halt_constructing");
      case MOTHBALLED:
        return NomadStrings.get("nomad_autoforge", "foundry_halt_mothballed");
      case SUSPENDED:
        return NomadStrings.get("nomad_autoforge", "foundry_halt_suspended");
      case UNSTAFFED:
        return NomadStrings.get("nomad_autoforge", "foundry_halt_unstaffed");
      case LACKING:
        return NomadStrings.get("nomad_autoforge", "foundry_halt_lacking");
      case NO_FLAGSHIP:
        return NomadStrings.get("nomad_autoforge", "fixui_foundry_halt_no_flagship");
      case STOPPED:
        return NomadStrings.get("nomad_autoforge", "fixui_foundry_halt_stopped");
      default:
        return null;
    }
  }

  static String haltWord(NomadAutoforgeQueue.LineState st) {
    switch (st) {
      case CONSTRUCTING:
        return NomadStrings.get("nomad_autoforge", "foundry_state_constructing");
      case MOTHBALLED:
        return NomadStrings.get("nomad_autoforge", "foundry_state_mothballed");
      case SUSPENDED:
        return NomadStrings.get("nomad_autoforge", "foundry_state_suspended");
      case UNSTAFFED:
        return NomadStrings.get("nomad_autoforge", "foundry_state_unstaffed");
      case LACKING:
        return NomadStrings.get("nomad_autoforge", "foundry_state_lacking");
      case NO_FLAGSHIP:
        return NomadStrings.get("nomad_autoforge", "fixui_foundry_state_no_flagship");
      case STOPPED:
        return NomadStrings.get("nomad_autoforge", "fixui_foundry_state_stopped");
      default:
        return NomadStrings.get("nomad_autoforge", "foundry_state_none");
    }
  }

  private static void wall(CustomPanelAPI wall, float v, float h, NomadIntel intel,
      NomadFleetData data, boolean ships, NomadAutoforge.Line line, List<String> pageIds,
      int page, int totalPages, List<String> designs, IntelUIAPI hostIntelUi) {
    float fieldW = searchBar(wall, intel, v, hostIntelUi);
    float y = filterArea(wall, intel, ships, v, v - fieldW - SEARCH_CAPTION_W - HEAD_GAP, designs);

    float foot = totalPages > 1 ? PAGER_H : 0f;
    if (totalPages > 1) {
      pager(wall, v, h - PAGER_H, page, totalPages);
    }
    float cellsY = y + CELLS_DOWN;
    float viewH = Math.max(0f, h - foot - cellsY);

    List<NomadAutoforge.Recipe> recipes = new ArrayList<NomadAutoforge.Recipe>();
    List<String> costs = new ArrayList<String>();
    List<String> dayTexts = new ArrayList<String>();
    float costW = NomadText.need(word("fo_col_cost"), NomadUi.PARA);
    float daysW = Math.max(DAYS_MIN_W, NomadText.need(word("fo_col_days"), NomadUi.PARA));
    for (String entry : pageIds) {
      NomadAutoforge.Recipe r = NomadAutoforge.recipeFor(data, typeOf(entry), idOf(entry));
      if (r == null) {
        continue;
      }
      String ds = daysStr(data, r, line);
      recipes.add(r);
      costs.add(pts(r.pointCost));
      dayTexts.add(ds != null ? ds : "-");
      costW = Math.max(costW, NomadText.need(pts(r.pointCost), NomadUi.PARA));
      daysW = Math.max(daysW, NomadText.need(dayTexts.get(dayTexts.size() - 1), NomadUi.PARA));
    }
    if (recipes.isEmpty()) {
      NomadCell.box(wall, 0f, cellsY, v, viewH, emptyListNote(intel, ships), NomadUi.gray(),
          Alignment.TL, 0f);
      return;
    }

    int n = NomadFoundryFigures.wallColumns(v);
    float c = NomadFoundryFigures.cellWidth(v, n);
    float daysEnd = c - DAYS_RIGHT;
    float costEnd = daysEnd - daysW - COL_GAP;
    float nameBox = Math.max(0f, costEnd - costW - COL_GAP - CELL_NAME_X);
    float pitchX = c + NomadFoundryFigures.CELL_GAP_X;
    float pitchY = NomadFoundryFigures.CELL_H + NomadFoundryFigures.CELL_GAP_Y;

    String costCaption = word("fo_col_cost");
    String daysCaption = word("fo_col_days");
    float captionW = NomadText.need(costCaption, NomadUi.PARA);
    float daysCaptionW = NomadText.need(daysCaption, NomadUi.PARA);
    for (int col = 0; col < Math.min(n, recipes.size()); col++) {
      float x = col * pitchX;
      NomadCell.box(wall, x + costEnd - captionW, y + CAPTIONS_DOWN, captionW,
          NomadHomeFigures.LINE_H, costCaption, NomadUi.gray(), Alignment.RMID, 0f);
      NomadCell.box(wall, x + daysEnd - daysCaptionW, y + CAPTIONS_DOWN, daysCaptionW,
          NomadHomeFigures.LINE_H, daysCaption, NomadUi.gray(), Alignment.RMID, 0f);
      CustomPanelAPI rule = wall.createCustomPanel(c, 1f, new Rule());
      wall.addComponent(rule).inTL(x, y + RULE_DOWN);
    }

    int rows = (recipes.size() + n - 1) / n;
    float contentH = rows * pitchY - NomadFoundryFigures.CELL_GAP_Y;
    CustomPanelAPI list = wall.createCustomPanel(v, contentH, NomadUi.consoleRow());
    boolean canQueue = NomadAutoforge.canQueue(data);
    for (int i = 0; i < recipes.size(); i++) {
      CustomPanelAPI cell = list.createCustomPanel(c, NomadFoundryFigures.CELL_H,
          NomadUi.consoleRow());
      cell(cell, c, data, recipes.get(i), line, canQueue, costs.get(i), dayTexts.get(i), nameBox,
          costEnd, costW, daysEnd, daysW);
      list.addComponent(cell).inTL((i % n) * pitchX, (i / n) * pitchY);
    }
    TooltipMakerAPI scroll = wall.createUIElement(v + SHIFT + NomadGrid.GUTTER, viewH, true);
    scroll.addCustom(list, 0f);
    wall.addUIElement(scroll).inTL(-SHIFT, cellsY);
    WALL.place(scroll, contentH, viewH);
  }

  private static final class Rule extends BaseCustomUIPanelPlugin {

    private final Color color = NomadUi.dark();
    private PositionAPI pos;

    @Override
    public void positionChanged(PositionAPI position) {
      this.pos = position;
    }

    @Override
    public void render(float alphaMult) {
      if (pos != null) {
        NomadGl.fillRect(pos.getX(), pos.getY(), pos.getWidth(), 1f, color, RULE_ALPHA, alphaMult);
      }
    }
  }

  private static float searchBar(CustomPanelAPI wall, NomadIntel intel, float v,
      IntelUIAPI hostIntelUi) {
    releaseSearchPoll();
    NomadSearchPoll poll = new NomadSearchPoll(intel, hostIntelUi);
    livePoll = poll;
    float fieldW = Math.max(SEARCH_MIN_W, Math.min(SEARCH_W,
        v - (WEAPONS_X + WEAPONS_W) - HEAD_GAP - SEARCH_CAPTION_W));
    CustomPanelAPI row = wall.createCustomPanel(SEARCH_CAPTION_W + fieldW, SEARCH_H, poll);

    String search = word("fo_search");
    NomadCell.box(row, 0f, SEARCH_UP + CAPTION_DOWN, NomadText.need(search, NomadUi.PARA),
        NomadHomeFigures.LINE_H, search, NomadUi.gray(), Alignment.LMID, 0f);

    TooltipMakerAPI fieldEl = NomadCell.element(row, fieldW, SEARCH_H);
    TextFieldAPI tf = fieldEl.addTextField(fieldW, SEARCH_H, Fonts.DEFAULT_SMALL, 0f);
    tf.setText(nullToEmpty(intel.getAutoforgeSearch()));
    tf.setColor(NomadUi.text());
    if ((NomadConsolePanel.isLiveRebuild() && consoleRefilterRegrab) || intelRefilterRegrab) {
      tf.grabFocus(false);
    }
    fieldEl.addTooltipToPrevious(tip(NomadStrings.get("nomad_autoforge", "af_search_tip")),
        TooltipMakerAPI.TooltipLocation.BELOW);
    NomadCell.place(row, fieldEl, SEARCH_CAPTION_W, 0f);

    poll.attach(tf);
    wall.addComponent(row).inTL(v - fieldW - SEARCH_CAPTION_W, -SEARCH_UP);
    return fieldW;
  }

  private static NomadSearchPoll livePoll;

  public static void releaseSearchPoll() {
    if (livePoll != null) {
      livePoll.release();
      livePoll = null;
    }
  }

  private static void releaseFieldFocus(TextFieldAPI field) {
    if (field != null && field.hasFocus()) {
      Global.getFocusManager().popFocusOwnerIfOwner(field);
    }
  }

  private static boolean intelRefilterRegrab;

  private static boolean consoleRefilterRegrab;

  private static boolean intelRefilterBroken;

  private static IntelUIAPI intelUiOf(CustomPanelAPI panel) {
    return intelRefilterBroken || panel == null ? null : panel.getIntelUI();
  }

  private static void warnIntelRefilterBroken(Throwable t) {
    if (!intelRefilterBroken) {
      intelRefilterBroken = true;
      Global.getLogger(NomadAutoforgeTab.class).warn("Nomad Flotilla: could not drive the intel"
          + " screen's own refresh for the autoforge search (" + t + "). Likely cause: a game"
          + " version change. On the intel entry the search still works, but the list updates on"
          + " the next filter or page click instead of as you type.");
    }
  }

  private static void pager(CustomPanelAPI wall, float v, float y, int page, int totalPages) {
    boolean hasPrev = page > 0;
    boolean hasNext = page < totalPages - 1;
    String words = "Page " + (page + 1) + " of " + totalPages;
    float wordsW = NomadText.need("Page " + totalPages + " of " + totalPages, NomadUi.PARA);
    float btnY = y + PAGER_H - BTN_H;
    float nextX = v - PAGER_BTN_W;
    float wordsX = nextX - PAGER_GAP - wordsW;
    float prevX = wordsX - PAGER_GAP - PAGER_BTN_W;

    TooltipMakerAPI prevEl = NomadCell.element(wall, PAGER_BTN_W, BTN_H);
    ButtonAPI prevBtn = prevEl.addButton("< Prev", P + "prevpage", NomadUi.text(), NomadUi.dark(),
        PAGER_BTN_W, BTN_H, 0f);
    NomadUi.setEnabledSayingWhy(prevBtn, hasPrev);
    prevEl.addTooltipToPrevious(tip(hasPrev
        ? NomadStrings.get("nomad_autoforge", "af_page_prev_tip")
        : NomadStrings.get("nomad_autoforge", "af_page_first_tip")), TooltipMakerAPI.TooltipLocation.ABOVE);
    NomadCell.place(wall, prevEl, prevX, btnY);

    NomadCell.box(wall, wordsX, btnY + CAPTION_DOWN + 1f, wordsW, NomadHomeFigures.LINE_H, words,
        NomadUi.gray(), Alignment.MID, 0f);

    TooltipMakerAPI nextEl = NomadCell.element(wall, PAGER_BTN_W, BTN_H);
    ButtonAPI nextBtn = nextEl.addButton("Next >", P + "nextpage", NomadUi.text(), NomadUi.dark(),
        PAGER_BTN_W, BTN_H, 0f);
    NomadUi.setEnabledSayingWhy(nextBtn, hasNext);
    nextEl.addTooltipToPrevious(tip(hasNext
        ? NomadStrings.get("nomad_autoforge", "af_page_next_tip")
        : NomadStrings.get("nomad_autoforge", "af_page_last_tip")), TooltipMakerAPI.TooltipLocation.ABOVE);
    NomadCell.place(wall, nextEl, nextX, btnY);
  }

  private static final class NomadSearchPoll extends BaseCustomUIPanelPlugin {
    private final NomadIntel intel;
    private String lastSeen;
    private TextFieldAPI field;
    private WeakReference<IntelUIAPI> intelUi;
    private volatile boolean released;

    NomadSearchPoll(NomadIntel intel, IntelUIAPI hostIntelUi) {
      this.intel = intel;
      this.intelUi = hostIntelUi == null ? null : new WeakReference<IntelUIAPI>(hostIntelUi);
      this.lastSeen = nullToEmpty(intel.getAutoforgeSearch());
    }

    void attach(TextFieldAPI field) {
      this.field = field;
    }

    void release() {
      releaseFieldFocus(field);
      released = true;
      field = null;
      intelUi = null;
    }

    @Override
    public void advance(float amount) {
      if (released || field == null) {
        return;
      }
      String now = nullToEmpty(field.getText());
      if (now.equals(lastSeen)) {
        return;
      }
      lastSeen = now;
      intel.setAutoforgeSearch(now);
      intel.setAutoforgePage(0);
      IntelUIAPI ui = intelUi == null ? null : intelUi.get();
      if (ui != null) {
        intelRefilterRegrab = field.hasFocus();
        try {
          ui.updateUIForItem(intel);
        } catch (Throwable t) {
          intelUi = null;
          warnIntelRefilterBroken(t);
        } finally {
          intelRefilterRegrab = false;
        }
      } else {
        consoleRefilterRegrab = field.hasFocus();
        try {
          NomadConsolePanel.routeButton(P + "search*" + now);
        } finally {
          consoleRefilterRegrab = false;
        }
      }
    }
  }

  private static float chipsWidth(String[] labels) {
    float w = 0f;
    for (int i = 0; i < labels.length; i++) {
      w += (i > 0 ? CHIPGAP : 0f) + chipWidth(labels[i]);
    }
    return w;
  }

  private static String emptyListNote(NomadIntel intel, boolean ships) {
    if (ships) {
      return NomadStrings.get("nomad_autoforge", "af_no_ships");
    }
    String cls = intel.getAutoforgeClass();
    if (NomadAutoforge.FIGHTER.equals(cls)) {
      return NomadStrings.get("nomad_autoforge", "af_no_fighters");
    }
    if (NomadAutoforge.WEAPON.equals(cls)) {
      return NomadStrings.get("nomad_autoforge", "af_no_weapons");
    }
    return NomadStrings.get("nomad_autoforge", "af_no_weapons_or_fighters");
  }

  private static float filterArea(CustomPanelAPI wall, NomadIntel intel, boolean ships, float v,
      float inlineEnd, List<String> designs) {
    chip(wall, "Ships", P + "ships", ships, 0f, 0f, SHIPS_W,
        knownTip(NomadStrings.get("nomad_autoforge", "af_filter_ships_tip")));
    chip(wall, "Weapons", P + "weapons", !ships, WEAPONS_X, 0f, WEAPONS_W,
        knownTip(NomadStrings.get("nomad_autoforge", "af_filter_weapons_tip")));

    String firstCaption = ships ? "Size" : "Class";
    String firstActive = ships ? intel.getAutoforgeSize() : intel.getAutoforgeClass();
    String firstPrefix = ships ? P + "size" : P + "class";
    String[] firstTokens = ships ? HULL_SIZE_TOKENS : classTokens();
    String[] firstLabels = ships ? HULL_SIZE_LABELS : CLASS_LABELS;
    float own = v - CHIPS_X;
    float y = CHIPROWH;
    if (INLINE_CHIPS_X + chipsWidth(firstLabels) <= inlineEnd) {
      filterChips(wall, firstCaption, firstActive, firstPrefix, firstTokens, firstLabels,
          INLINE_CAPTION_X, INLINE_CHIPS_X, 0f, inlineEnd - INLINE_CHIPS_X);
    } else {
      y += CHIPROWH * filterChips(wall, firstCaption, firstActive, firstPrefix, firstTokens,
          firstLabels, 0f, CHIPS_X, y, own);
    }
    if (!ships) {
      if (NomadAutoforge.FIGHTER.equals(intel.getAutoforgeClass())) {
        y += CHIPROWH * filterChips(wall, "Role", intel.getAutoforgeRole(), P + "role",
            ROLE_TOKENS, ROLE_LABELS, 0f, CHIPS_X, y, own);
      } else {
        y += CHIPROWH * filterChips(wall, "Size", intel.getAutoforgeSize(), P + "size",
            MOUNT_SIZE_TOKENS, MOUNT_SIZE_LABELS, 0f, CHIPS_X, y, own);
        y += CHIPROWH * filterChips(wall, "Type", intel.getAutoforgeType(), P + "type",
            MOUNT_TYPE_TOKENS, MOUNT_TYPE_LABELS, 0f, CHIPS_X, y, own);
      }
    }
    if (!designs.isEmpty()) {
      String[] tokens = new String[designs.size() + 1];
      tokens[0] = "all";
      for (int i = 0; i < designs.size(); i++) {
        tokens[i + 1] = designs.get(i);
      }
      y += CHIPROWH * filterChips(wall, "Design", intel.getAutoforgeDesign(), P + "design",
          tokens, designLabels(designs), 0f, CHIPS_X, y, own);
    }
    return y;
  }

  private static TooltipMakerAPI.TooltipCreator knownTip(final String what) {
    return NomadTip.guard(new NomadTip() {
      @Override
      public void createTooltip(TooltipMakerAPI t, boolean expanded, Object param) {
        t.addPara(what, PAD);
        t.addPara(NomadStrings.get("nomad_autoforge", "af_known_note"), NomadUi.gray(), PAD);
      }
    });
  }

  private static void chip(CustomPanelAPI wall, String label, String id, boolean checked, float x,
      float y, float w, TooltipMakerAPI.TooltipCreator tip) {
    TooltipMakerAPI el = NomadCell.element(wall, w, CHIP_H);
    ButtonAPI b = el.addAreaCheckbox(label, id, NomadUi.base(), NomadUi.dark(), NomadUi.bright(),
        w, CHIP_H, 0f);
    b.setChecked(checked);
    if (tip != null) {
      el.addTooltipToPrevious(tip, TooltipMakerAPI.TooltipLocation.BELOW);
    }
    NomadCell.place(wall, el, x, y);
  }

  private static String[] classTokens() {
    return new String[] { "all", NomadAutoforge.WEAPON, NomadAutoforge.FIGHTER };
  }

  private static String[] designLabels(List<String> designs) {
    String[] labels = new String[designs.size() + 1];
    labels[0] = "All";
    for (int i = 0; i < designs.size(); i++) {
      labels[i + 1] = designs.get(i);
    }
    return labels;
  }

  private static float chipWidth(String label) {
    return Math.max(40f, 7.5f * label.length() + 12f);
  }

  private static int[] chipRowOf(String[] labels, float room) {
    int[] rowOf = new int[labels.length];
    float avail = Math.max(120f, room);
    float used = 0f;
    int row = 0;
    for (int i = 0; i < labels.length; i++) {
      float w = chipWidth(labels[i]);
      if (used > 0f && used + w > avail) {
        row++;
        used = 0f;
      }
      rowOf[i] = row;
      used += w + CHIPGAP;
    }
    return rowOf;
  }

  private static int filterChips(CustomPanelAPI wall, String caption, String active,
      String idPrefix, String[] tokens, String[] labels, float captionX, float chipsX, float y,
      float room) {
    NomadCell.box(wall, captionX, y + CAPTION_DOWN, NomadText.need(caption, NomadUi.PARA),
        NomadHomeFigures.LINE_H, caption, NomadUi.gray(), Alignment.LMID, 0f);
    int[] rowOf = chipRowOf(labels, room);
    float x = chipsX;
    int row = 0;
    for (int i = 0; i < tokens.length; i++) {
      if (rowOf[i] != row) {
        row = rowOf[i];
        x = chipsX;
      }
      boolean checked = active == null ? "all".equals(tokens[i]) : active.equals(tokens[i]);
      float w = chipWidth(labels[i]);
      chip(wall, labels[i], idPrefix + "*" + tokens[i], checked, x, y + CHIPROWH * row, w, null);
      x += w + CHIPGAP;
    }
    return rowOf[rowOf.length - 1] + 1;
  }

  private static void cell(CustomPanelAPI cell, float c, final NomadFleetData data,
      final NomadAutoforge.Recipe r, final NomadAutoforge.Line line, final boolean canQueue,
      String cost, String days, float nameBox, float costEnd, float costW, float daysEnd,
      float daysW) {
    final boolean enabled = canQueue;

    sprite(cell, spriteName(r), 0f, 0f, CELL_SPRITE);
    TooltipMakerAPI nameEl = NomadCell.element(cell, nameBox,
        NomadFoundryFigures.CELL_H - CELL_NAME_Y);
    nameEl.addPara(NomadText.fit(r.name, nameBox, NomadUi.PARA), r.nameColor, 0f);
    nameEl.addPara(NomadText.fit(subtitle(r), nameBox, NomadUi.PARA), NomadUi.gray(),
        CELL_SUB_PAD);
    NomadCell.place(cell, nameEl, CELL_NAME_X, CELL_NAME_Y);

    NomadCell.box(cell, costEnd - costW, CELL_FIGURE_Y, costW, NomadHomeFigures.LINE_H, cost,
        NomadUi.hl(), Alignment.RMID, 0f);
    NomadCell.box(cell, daysEnd - daysW, CELL_FIGURE_Y, daysW, NomadHomeFigures.LINE_H, days,
        "-".equals(days) ? NomadUi.gray() : NomadUi.text(), Alignment.RMID, 0f);

    hoverArea(cell, 0f, 0f, c - BTN_W - PAD, NomadFoundryFigures.CELL_H, r, statTip(r, data, line),
        TooltipMakerAPI.TooltipLocation.RIGHT);

    TooltipMakerAPI btnEl = NomadCell.element(cell, BTN_W, BTN_H);
    ButtonAPI b = btnEl.addButton(word("fo_queue"), P + "queue*" + r.type + "*" + r.id,
        NomadUi.text(), NomadUi.dark(), BTN_W, BTN_H, 0f);
    NomadUi.setEnabledSayingWhy(b, enabled);
    final NomadAutoforge.Recipe fr = r;
    final NomadFleetData fd = data;
    final NomadAutoforge.Line fline = line;
    btnEl.addTooltipToPrevious(NomadTip.guard(new NomadTip() {
      @Override
      public void createTooltip(TooltipMakerAPI t, boolean expanded, Object param) {
        if (enabled) {
          t.addPara("Queue one " + fr.name + ".", NomadUi.base(), 0f);
          t.addPara(NomadStrings.get("nomad_autoforge", "af_queue_tip_cost"), PAD, NomadUi.hl(),
              pts(fr.pointCost));
          String d = daysStr(fd, fr, fline);
          if (d != null) {
            t.addPara(NomadStrings.get("nomad_autoforge", "af_queue_tip_days"), PAD,
                NomadUi.hl(), d);
          } else if (fline.pointsPerMonth <= 0f) {
            NomadAutoforgeQueue.LineState cause =
                NomadAutoforgeQueue.noRateCause(fline.foundry, fline.noFlagship);
            String why = cause == NomadAutoforgeQueue.LineState.NO_FOUNDRY ? null : haltLine(cause);
            t.addPara(why != null ? why
                : NomadStrings.get("nomad_autoforge", "af_queue_tip_no_space"), NomadUi.warn(), PAD);
          }
          t.addPara(NomadStrings.get("nomad_autoforge", "af_queue_tip_free"), NomadUi.gray(), PAD);
        } else {
          t.addPara(NomadStrings.get("nomad_autoforge", "af_queue_tip_full"), 0f, NomadUi.warn(),
              "" + NomadAutoforge.QUEUE_MAX);
        }
      }
    }), TooltipMakerAPI.TooltipLocation.LEFT);
    NomadCell.place(cell, btnEl, c - BTN_W, BTN_Y);
  }

  public static boolean handle(Object buttonId, NomadIntel intel, NomadFleetData data, IntelUIAPI ui) {
    if (!(buttonId instanceof String) || !((String) buttonId).startsWith(P)) {
      return false;
    }
    String raw = (String) buttonId;
    String designPrefix = P + "design*";
    if (raw.startsWith(designPrefix)) {
      String token = raw.substring(designPrefix.length());
      intel.setAutoforgeDesign("all".equals(token) ? null : token);
      intel.setAutoforgePage(0);
      return true;
    }
    String searchPrefix = P + "search*";
    if (raw.startsWith(searchPrefix)) {
      intel.setAutoforgeSearch(raw.substring(searchPrefix.length()));
      intel.setAutoforgePage(0);
      return true;
    }
    String[] p = raw.split("\\*");
    String kind = p[0];
    if ((P + "queue").equals(kind)) {
      if (p.length >= 3) {
        NomadAutoforge.queue(data, p[1], p[2]);
      }
    } else if ((P + "pause").equals(kind)) {
      NomadAutoforge.togglePaused();
    } else if ((P + "clear").equals(kind)) {
      NomadAutoforge.clear(data);
    } else if ((P + "ships").equals(kind)) {
      intel.setAutoforgeBrowse(NomadAutoforge.SHIP);
      clearAttributeFilters(intel);
      intel.setAutoforgePage(0);
    } else if ((P + "weapons").equals(kind)) {
      intel.setAutoforgeBrowse(NomadAutoforge.WEAPON);
      clearAttributeFilters(intel);
      intel.setAutoforgePage(0);
    } else if ((P + "size").equals(kind)) {
      intel.setAutoforgeSize(p.length >= 2 && !"all".equals(p[1]) ? p[1] : null);
      intel.setAutoforgePage(0);
    } else if ((P + "type").equals(kind)) {
      intel.setAutoforgeType(p.length >= 2 && !"all".equals(p[1]) ? p[1] : null);
      intel.setAutoforgePage(0);
    } else if ((P + "class").equals(kind)) {
      intel.setAutoforgeClass(p.length >= 2 && !"all".equals(p[1]) ? p[1] : null);
      intel.setAutoforgeSize(null);
      intel.setAutoforgeType(null);
      intel.setAutoforgeRole(null);
      intel.setAutoforgeDesign(null);
      intel.setAutoforgePage(0);
    } else if ((P + "role").equals(kind)) {
      intel.setAutoforgeRole(p.length >= 2 && !"all".equals(p[1]) ? p[1] : null);
      intel.setAutoforgePage(0);
    } else if ((P + "prevpage").equals(kind)) {
      intel.setAutoforgePage(intel.getAutoforgePage() - 1);
    } else if ((P + "nextpage").equals(kind)) {
      intel.setAutoforgePage(intel.getAutoforgePage() + 1);
    }
    return true;
  }

  private static void clearAttributeFilters(NomadIntel intel) {
    intel.setAutoforgeSize(null);
    intel.setAutoforgeType(null);
    intel.setAutoforgeClass(null);
    intel.setAutoforgeRole(null);
    intel.setAutoforgeDesign(null);
  }

  public static boolean needsConfirm(Object buttonId) {
    return (P + "clear").equals(buttonId);
  }

  public static void confirmPrompt(Object buttonId, TooltipMakerAPI prompt, NomadFleetData data) {
    prompt.addPara(NomadStrings.get("nomad_autoforge", "af_clear_confirm"), 0f);
  }

  public static List<String> filtered(NomadIntel intel, boolean ships) {
    return withDesign(filteredIgnoringDesign(intel, ships), intel.getAutoforgeDesign());
  }

  static List<String> filteredIgnoringDesign(NomadIntel intel, boolean ships) {
    String sizeF = intel.getAutoforgeSize();
    String searchQ = normalizeSearch(intel.getAutoforgeSearch());
    List<String> out = new ArrayList<String>();
    if (ships) {
      for (String id : NomadAutoforge.knownShipIds()) {
        if (passesFilter(NomadAutoforge.SHIP, id, sizeF, null, null, searchQ)) {
          out.add(NomadAutoforge.SHIP + "|" + id);
        }
      }
      return out;
    }
    String classF = intel.getAutoforgeClass();
    boolean wingsOnly = NomadAutoforge.FIGHTER.equals(classF);
    boolean weaponsOnly = NomadAutoforge.WEAPON.equals(classF);
    String typeF = wingsOnly ? null : intel.getAutoforgeType();
    String roleF = weaponsOnly ? null : intel.getAutoforgeRole();
    if (classF == null && NomadAutoforge.partsOffered(NomadFleetData.getOrNull())
        && matchesSearch(searchQ, nomad.campaign.econ.model.Works.PARTS,
            NomadUi.commodityName(nomad.campaign.econ.model.Works.PARTS))) {
      out.add(NomadAutoforge.PART + "|" + nomad.campaign.econ.model.Works.PARTS);
    }
    if (!wingsOnly) {
      for (String id : NomadAutoforge.knownWeaponIds()) {
        if (passesFilter(NomadAutoforge.WEAPON, id, sizeF, typeF, null, searchQ)) {
          out.add(NomadAutoforge.WEAPON + "|" + id);
        }
      }
    }
    if (!weaponsOnly) {
      for (String id : NomadAutoforge.knownFighterIds()) {
        if (passesFilter(NomadAutoforge.FIGHTER, id, null, null, roleF, searchQ)) {
          out.add(NomadAutoforge.FIGHTER + "|" + id);
        }
      }
    }
    return out;
  }

  private static List<String> withDesign(List<String> entries, String designF) {
    if (designF == null) {
      return entries;
    }
    List<String> out = new ArrayList<String>();
    for (String entry : entries) {
      if (designF.equals(NomadAutoforge.manufacturer(typeOf(entry), idOf(entry)))) {
        out.add(entry);
      }
    }
    return out;
  }

  private static List<String> designsIn(List<String> entries) {
    Set<String> seen = new HashSet<String>();
    for (String entry : entries) {
      String m = NomadAutoforge.manufacturer(typeOf(entry), idOf(entry));
      if (m != null && !m.trim().isEmpty()) {
        seen.add(m);
      }
    }
    List<String> out = new ArrayList<String>(seen);
    Collections.sort(out);
    return out;
  }

  public static List<String> filteredIds(NomadIntel intel, boolean ships) {
    List<String> out = new ArrayList<String>();
    for (String entry : filtered(intel, ships)) {
      out.add(idOf(entry));
    }
    return out;
  }

  public static String typeOf(String entry) {
    int bar = entry == null ? -1 : entry.indexOf('|');
    return bar < 0 ? NomadAutoforge.SHIP : entry.substring(0, bar);
  }

  public static String idOf(String entry) {
    int bar = entry == null ? -1 : entry.indexOf('|');
    return bar < 0 ? entry : entry.substring(bar + 1);
  }

  private static String normalizeSearch(String raw) {
    if (raw == null) {
      return null;
    }
    String t = raw.trim().toLowerCase();
    return t.isEmpty() ? null : t;
  }

  private static boolean passesFilter(String type, String id, String sizeF, String typeF, String roleF,
      String searchQ) {
    if (NomadAutoforge.SHIP.equals(type)) {
      ShipHullSpecAPI h = NomadAutoforge.hullSpec(id);
      if (h == null) {
        return false;
      }
      if (sizeF != null && (h.getHullSize() == null || !sizeF.equals(h.getHullSize().name()))) {
        return false;
      }
      return matchesSearch(searchQ, id, h.getHullName());
    }
    if (NomadAutoforge.FIGHTER.equals(type)) {
      FighterWingSpecAPI f = NomadAutoforge.wingSpec(id);
      if (f == null) {
        return false;
      }
      if (roleF != null && (f.getRole() == null || !roleF.equals(f.getRole().name()))) {
        return false;
      }
      return matchesSearch(searchQ, id, f.getWingName());
    }
    WeaponSpecAPI w = NomadAutoforge.weaponSpec(id);
    if (w == null) {
      return false;
    }
    if (sizeF != null && (w.getSize() == null || !sizeF.equals(w.getSize().name()))) {
      return false;
    }
    if (typeF != null && (w.getType() == null || !typeF.equals(w.getType().name()))) {
      return false;
    }
    return matchesSearch(searchQ, id, w.getWeaponName());
  }

  private static boolean matchesSearch(String searchQ, String id, String name) {
    if (searchQ == null) {
      return true;
    }
    if (id != null && id.toLowerCase().contains(searchQ)) {
      return true;
    }
    return name != null && name.toLowerCase().contains(searchQ);
  }

  public static int totalPages(int count) {
    return Math.max(1, (count + PAGE_SIZE - 1) / PAGE_SIZE);
  }

  public static int clampPage(int page, int totalPages) {
    if (page < 0) {
      return 0;
    }
    return Math.min(page, totalPages - 1);
  }

  public static List<String> pageSlice(List<String> ids, int page) {
    int from = page * PAGE_SIZE;
    int to = Math.min(ids.size(), from + PAGE_SIZE);
    if (from >= to) {
      return new ArrayList<String>();
    }
    return ids.subList(from, to);
  }

  private static void rowHover(TooltipMakerAPI cell, NomadAutoforge.Recipe r,
      TooltipMakerAPI.TooltipCreator fallback, TooltipMakerAPI.TooltipLocation loc) {
    if (r.isPart()) {
      cell.addTooltipToPrevious(fallback, loc);
      return;
    }
    boolean native_ = r.isShip() ? NomadShipHover.attach(cell, r.id)
        : r.isFighter() ? NomadShipHover.attachWing(cell, r.id) : NomadShipHover.attachWeapon(cell, r.id);
    if (!native_) {
      cell.addTooltipToPrevious(fallback, loc);
    }
  }

  static void hoverArea(CustomPanelAPI panel, float x, float y, float w, float h,
      NomadAutoforge.Recipe r, TooltipMakerAPI.TooltipCreator fallback,
      TooltipMakerAPI.TooltipLocation loc) {
    TooltipMakerAPI el = NomadCell.element(panel, w, h);
    CustomPanelAPI area = panel.createCustomPanel(w, h, new BaseCustomUIPanelPlugin());
    el.addCustom(area, 0f);
    rowHover(el, r, fallback, loc);
    NomadCell.place(panel, el, x, y);
  }

  static TooltipMakerAPI.TooltipCreator statTip(final NomadAutoforge.Recipe r,
      final NomadFleetData data, final NomadAutoforge.Line line) {
    return NomadTip.guard(new NomadTip(380f) {
      @Override
      public boolean isTooltipExpandable(Object param) {
        return true;
      }

      @Override
      public void createTooltip(TooltipMakerAPI t, boolean expanded, Object param) {
        try {
          if (r.isShip()) {
            shipTip(t, r, data, line, expanded);
            t.setCodexEntryId(CodexDataV2.getShipEntryId(r.id));
          } else if (r.isPart()) {
            partTip(t, r, data, line, expanded);
            t.setCodexEntryId(CodexDataV2.getCommodityEntryId(r.id));
          } else if (r.isFighter()) {
            wingTip(t, r, data, line, expanded);
            t.setCodexEntryId(CodexDataV2.getFighterEntryId(r.id));
          } else {
            weaponTip(t, r, data, line, expanded);
            t.setCodexEntryId(CodexDataV2.getWeaponEntryId(r.id));
          }
        } catch (Exception e) {
          t.addPara(r.name, r.nameColor, 0f);
          t.addPara(NomadStrings.get("nomad_autoforge", "af_codex_hint"), NomadUi.gray(), PAD);
        }
      }
    });
  }

  private static void shipTip(TooltipMakerAPI t, NomadAutoforge.Recipe r, NomadFleetData data, NomadAutoforge.Line line,
      boolean expanded) {
    ShipHullSpecAPI h = NomadAutoforge.hullSpec(r.id);
    t.addPara(r.name, r.nameColor, 0f);
    if (h == null) {
      return;
    }
    t.addPara(shipSubtitle(h), NomadUi.gray(), 2f);
    t.addPara("Hull integrity %s      Armor rating %s", PAD, NomadUi.hl(),
        NomadUi.i(h.getHitpoints()), NomadUi.i(h.getArmorRating()));
    t.addPara("Flux capacity %s      Flux dissipation %s", 0f, NomadUi.hl(),
        NomadUi.i(h.getFluxCapacity()), NomadUi.i(h.getFluxDissipation()));
    t.addPara("Top speed %s      Ordnance points %s", 0f, NomadUi.hl(),
        topSpeed(h), "" + h.getOrdnancePoints(null));
    t.addPara("Deployment points %s      Defense %s", 0f, NomadUi.hl(),
        "" + h.getFleetPoints(), shieldLabel(h));
    if (expanded) {
      t.addPara("Maintenance %s supplies/mo", PAD, NomadUi.hl(), NomadUi.i(h.getSuppliesPerMonth()));
      t.addPara("Crew %s (min %s)      Cargo capacity %s", 0f, NomadUi.hl(),
          NomadUi.i(h.getMaxCrew()), NomadUi.i(h.getMinCrew()), NomadUi.i(h.getCargo()));
      t.addPara("Fuel capacity %s      Base value %s", 0f, NomadUi.hl(),
          NomadUi.i(h.getFuel()), NomadUi.credits(NomadAutoforge.codexHullValue(h)));
    }
    costLine(t, r, data, line);
  }

  private static void weaponTip(TooltipMakerAPI t, NomadAutoforge.Recipe r, NomadFleetData data, NomadAutoforge.Line line,
      boolean expanded) {
    WeaponSpecAPI w = NomadAutoforge.weaponSpec(r.id);
    t.addPara(r.name, r.nameColor, 0f);
    if (w == null) {
      return;
    }
    t.addPara(weaponSubtitle(w), NomadUi.gray(), 2f);
    if (w.getDamageType() != null) {
      t.addPara("Damage type %s      OP cost %s", PAD, NomadUi.hl(),
          pretty(String.valueOf(w.getDamageType())), NomadUi.i(w.getOrdnancePointCost(null)));
    } else {
      t.addPara("OP cost %s", PAD, NomadUi.hl(), NomadUi.i(w.getOrdnancePointCost(null)));
    }
    if (expanded) {
      t.addPara("Base value %s", 0f, NomadUi.hl(), NomadUi.credits(w.getBaseValue()));
    }
    costLine(t, r, data, line);
  }

  private static void wingTip(TooltipMakerAPI t, NomadAutoforge.Recipe r, NomadFleetData data, NomadAutoforge.Line line,
      boolean expanded) {
    FighterWingSpecAPI f = NomadAutoforge.wingSpec(r.id);
    t.addPara(r.name, r.nameColor, 0f);
    if (f == null) {
      return;
    }
    t.addPara(wingSubtitle(f), NomadUi.gray(), 2f);
    t.addPara("Craft per wing %s      OP cost %s", PAD, NomadUi.hl(),
        "" + f.getNumFighters(), NomadUi.i(f.getOpCost(null)));
    if (expanded) {
      t.addPara("Deployment points %s      Base value %s", 0f, NomadUi.hl(),
          NomadUi.i(f.getFleetPoints()), NomadUi.credits(f.getBaseValue()));
    }
    costLine(t, r, data, line);
  }

  private static void partTip(TooltipMakerAPI t, NomadAutoforge.Recipe r, NomadFleetData data,
      NomadAutoforge.Line line, boolean expanded) {
    t.addPara(r.name, r.nameColor, 0f);
    t.addPara(NomadStrings.get("nomad_autoforge", "af_part_kind"), NomadUi.gray(), 2f);
    t.addPara(NomadStrings.get("nomad_autoforge", "af_part_desc"), PAD);
    if (expanded) {
      t.addPara("Base value %s", 0f, NomadUi.hl(), NomadUi.credits(NomadUi.basePrice(r.id)));
    }
    costLine(t, r, data, line);
  }

  private static void costLine(TooltipMakerAPI t, NomadAutoforge.Recipe r, NomadFleetData data, NomadAutoforge.Line line) {
    t.addPara(NomadStrings.get("nomad_autoforge", "af_cost_fabrication"), PAD, NomadUi.hl(),
        pts(r.pointCost));
    String d = daysStr(data, r, line);
    if (d != null) {
      t.addPara(NomadStrings.get("nomad_autoforge", "af_cost_days"), 0f, NomadUi.hl(), d);
    }
    t.addPara(NomadStrings.get("nomad_autoforge", "af_codex_hint"), NomadUi.gray(), PAD);
  }

  static TooltipMakerAPI.TooltipCreator tip(final String s) {
    return NomadTable.tip(s, PAD);
  }

  private static String pts(float v) {
    return String.format("%,d", (long) Math.max(0L, Math.round(v)));
  }

  private static String daysStr(NomadFleetData data, NomadAutoforge.Recipe r, NomadAutoforge.Line line) {
    if (line.pointsPerMonth <= 0f) {
      return null;
    }
    NomadFleetData d = data != null ? data : NomadFleetData.getInstance();
    float days = NomadAutoforge.daysFor(d, r, line);
    if (Float.isNaN(days) || Float.isInfinite(days) || days <= 0f) {
      return null;
    }
    return NomadUi.i(days);
  }

  private static String subtitle(NomadAutoforge.Recipe r) {
    return withDesignWord(kindSubtitle(r), r.design);
  }

  private static String withDesignWord(String base, String design) {
    if (design == null || design.trim().isEmpty()) {
      return base;
    }
    return base + " · " + design.trim();
  }

  private static String kindSubtitle(NomadAutoforge.Recipe r) {
    if (NomadAutoforge.SHIP.equals(r.type)) {
      ShipHullSpecAPI h = NomadAutoforge.hullSpec(r.id);
      if (h != null) {
        String d = h.getDesignation();
        if (d != null && !d.trim().isEmpty()) {
          return Misc.ucFirst(d.trim());
        }
        return hullSizeName(h.getHullSize());
      }
      return "Ship";
    }
    if (r.isFighter()) {
      FighterWingSpecAPI f = NomadAutoforge.wingSpec(r.id);
      return f != null ? wingSubtitle(f) : "Fighter wing";
    }
    if (r.isPart()) {
      return NomadStrings.get("nomad_autoforge", "af_part_kind");
    }
    WeaponSpecAPI w = NomadAutoforge.weaponSpec(r.id);
    return w != null ? weaponSubtitle(w) : "Weapon";
  }

  private static String hullSizeName(HullSize s) {
    if (s == HullSize.FRIGATE) {
      return "Frigate";
    }
    if (s == HullSize.DESTROYER) {
      return "Destroyer";
    }
    if (s == HullSize.CRUISER) {
      return "Cruiser";
    }
    if (s == HullSize.CAPITAL_SHIP) {
      return "Capital";
    }
    return "Ship";
  }

  private static String weaponSubtitle(WeaponSpecAPI w) {
    String size = pretty(String.valueOf(w.getSize()));
    String type = String.valueOf(w.getType()).toLowerCase().replace('_', ' ');
    return (size + " " + type).trim();
  }

  private static String wingSubtitle(FighterWingSpecAPI f) {
    String role = f.getRoleDesc();
    if (role == null || role.trim().isEmpty()) {
      role = pretty(String.valueOf(f.getRole()));
    }
    return (Misc.ucFirst(role.trim()) + " x" + f.getNumFighters()).trim();
  }

  private static String shipSubtitle(ShipHullSpecAPI h) {
    String size = hullSizeName(h.getHullSize());
    String d = h.getDesignation();
    if (d != null && !d.trim().isEmpty() && !d.trim().equalsIgnoreCase(size)) {
      return Misc.ucFirst(d.trim()) + " · " + size;
    }
    return size;
  }

  private static String topSpeed(ShipHullSpecAPI h) {
    try {
      if (h.getEngineSpec() != null) {
        return NomadUi.i(h.getEngineSpec().getMaxSpeed());
      }
    } catch (Exception ignore) {
    }
    return "-";
  }

  private static String shieldLabel(ShipHullSpecAPI h) {
    ShieldType st = h.getShieldType();
    if (st == ShieldType.FRONT) {
      return "Front shield";
    }
    if (st == ShieldType.OMNI) {
      return "Omni shield";
    }
    if (st == ShieldType.PHASE) {
      return "Phase cloak";
    }
    return "None";
  }

  static String spriteName(NomadAutoforge.Recipe r) {
    try {
      if (r.isShip()) {
        ShipHullSpecAPI h = NomadAutoforge.hullSpec(r.id);
        return h == null ? null : h.getSpriteName();
      }
      if (r.isFighter()) {
        FighterWingSpecAPI f = NomadAutoforge.wingSpec(r.id);
        if (f == null || f.getVariant() == null || f.getVariant().getHullSpec() == null) {
          return null;
        }
        return f.getVariant().getHullSpec().getSpriteName();
      }
      if (r.isPart()) {
        CommoditySpecAPI part = NomadAutoforge.commoditySpec(r.id);
        return part == null ? null : part.getIconName();
      }
      WeaponSpecAPI w = NomadAutoforge.weaponSpec(r.id);
      if (w == null) {
        return null;
      }
      String turret = w.getTurretSpriteName();
      return (turret == null || turret.isEmpty()) ? w.getHardpointSpriteName() : turret;
    } catch (Exception e) {
      return null;
    }
  }

  static void sprite(CustomPanelAPI panel, String name, float x, float y, float box) {
    if (name == null || name.isEmpty()) {
      return;
    }
    try {
      com.fs.starfarer.api.graphics.SpriteAPI s = Global.getSettings().getSprite(name);
      float w = box;
      float h = box;
      if (s != null && s.getWidth() > 0f && s.getHeight() > 0f) {
        float scale = Math.min(box / s.getWidth(), box / s.getHeight());
        w = Math.round(s.getWidth() * scale);
        h = Math.round(s.getHeight() * scale);
      }
      TooltipMakerAPI el = NomadCell.element(panel, w, h);
      el.addImage(name, w, h, 0f);
      NomadCell.place(panel, el, x + Math.round((box - w) / 2f), y + Math.round((box - h) / 2f));
    } catch (Exception e) {
    }
  }

  private static String pretty(String enumName) {
    if (enumName == null || enumName.isEmpty()) {
      return "";
    }
    return Misc.ucFirst(enumName.toLowerCase().replace('_', ' '));
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }

}
