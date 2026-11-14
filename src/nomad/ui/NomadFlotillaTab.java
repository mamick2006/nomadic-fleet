package nomad.ui;

import java.awt.Color;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.Fonts;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import nomad.NomadStrings;
import nomad.campaign.econ.model.NeedsLevel;
import nomad.campaign.econ.model.Roster;
import nomad.campaign.econ.model.TickSheet;
import nomad.campaign.econ.model.WorkState;
import nomad.campaign.split.NomadSplitLines;
import nomad.campaign.split.NomadSplitter;
import nomad.data.NomadFleetData;
import nomad.intel.NomadIntel;

public final class NomadFlotillaTab {

  private NomadFlotillaTab() {
  }

  private static final float PAD = 3f;
  private static final Color CLEAR = new Color(0, 0, 0, 0);
  private static final String NO_VALUE = "---";
  private static final float SHIFT = NomadText.INSET;

  private static WeakReference<NomadFleetData> uiOwner;
  private static String uiSelected;
  private static boolean uiPicking;
  private static boolean uiReveal;
  private static boolean uiShowCounted;
  private static final Scroll GRID = new Scroll();
  private static final Scroll HOME = new Scroll();
  private static final Scroll STORES = new Scroll();
  private static final Scroll PICKER = new Scroll();

  private static final class Scroll {

    private WeakReference<TooltipMakerAPI> live;
    private float offset;

    void save() {
      TooltipMakerAPI el = live == null ? null : live.get();
      if (el != null) {
        offset = NomadTabbedPanels.scrollOf(el);
      }
    }

    void rest() {
      live = null;
    }

    void forget() {
      live = null;
      offset = 0f;
    }

    void place(TooltipMakerAPI el, float wanted, float contentH, float viewH) {
      offset = NomadGrid.clampScroll(wanted, contentH, viewH);
      NomadTabbedPanels.applyScroll(el, offset);
      live = new WeakReference<TooltipMakerAPI>(el);
    }
  }

  private static void claimUi(NomadFleetData data) {
    if (uiOwner == null || uiOwner.get() != data) {
      uiOwner = new WeakReference<NomadFleetData>(data);
      uiSelected = null;
      uiPicking = false;
      uiReveal = false;
      uiShowCounted = false;
      GRID.forget();
      HOME.forget();
      STORES.forget();
      PICKER.forget();
      NomadStoresPanel.forgetTypedTarget();
    }
  }

  public static void resetUi() {
    uiSelected = null;
    uiPicking = false;
    uiReveal = false;
    uiShowCounted = false;
    PICKER.forget();
    NomadStoresPanel.releaseTargetField();
    NomadStoresPanel.forgetTypedTarget();
  }

  static void toggleSelected(NomadFleetData data, String workId) {
    claimUi(data);
    uiSelected = workId == null || workId.isEmpty() || workId.equals(uiSelected) ? null : workId;
    uiReveal = uiSelected != null;
  }

  static void setPicking(NomadFleetData data, boolean picking) {
    claimUi(data);
    if (picking != uiPicking) {
      PICKER.forget();
    }
    uiPicking = picking;
    if (picking) {
      uiSelected = null;
      uiReveal = false;
    }
  }

  static void showFirstCounted(NomadFleetData data) {
    claimUi(data);
    uiShowCounted = true;
  }

  public static void saveScroll() {
    GRID.save();
    HOME.save();
    STORES.save();
    PICKER.save();
  }

  static void placeStoresScroll(TooltipMakerAPI list, float contentH, float viewH) {
    STORES.place(list, STORES.offset, contentH, viewH);
  }

  static void placePickerScroll(TooltipMakerAPI list, float contentH, float viewH) {
    PICKER.place(list, PICKER.offset, contentH, viewH);
  }

  public static TooltipMakerAPI build(CustomPanelAPI panel, float width, float bodyHeight,
      NomadIntel intel, NomadFleetData data) {
    claimUi(data);
    GRID.rest();
    HOME.rest();
    STORES.rest();
    PICKER.rest();
    NomadStoresPanel.releaseTargetField();

    NomadFlotillaView view = NomadFlotillaView.of(data, uiSelected, uiPicking);
    uiSelected = view.selected;
    uiPicking = view.picking;

    float top = NomadTabbedPanels.BODY_TOP;
    NomadGrid.Screen at = NomadGrid.Screen.of(width, bodyHeight,
        NomadPanelFigures.listed(view.standing).size());
    home(panel, at, top, view);
    if (view.picking) {
      NomadBuildPicker.build(place(panel, at.picker, top), at.picker.w, at.picker.h, view);
    } else {
      toolbar(panel, at.toolbar, top, view);
      wall(panel, at.wall, top, view);
    }
    return null;
  }

  private static CustomPanelAPI place(CustomPanelAPI parent, NomadGrid.Box box, float top) {
    CustomPanelAPI p = parent.createCustomPanel(box.w, box.h, NomadUi.consoleRow());
    parent.addComponent(p).inTL(box.x, top + box.y);
    return p;
  }

  private static void home(CustomPanelAPI panel, NomadGrid.Screen at, float top,
      NomadFlotillaView view) {
    NomadGrid.Box box = at.home;
    if (!at.homeScrolls) {
      NomadHomeColumn.build(place(panel, box, top), box.w, box.h, view);
      return;
    }
    float layoutH = at.homeLayoutH;
    CustomPanelAPI column = panel.createCustomPanel(box.w, layoutH, NomadUi.consoleRow());
    float used = NomadHomeColumn.build(column, box.w, layoutH, view);
    float contentH = used > 0f ? Math.min(layoutH, used) : layoutH;
    CustomPanelAPI content = panel.createCustomPanel(box.w, contentH, NomadUi.consoleRow());
    content.addComponent(column).inTL(0f, 0f);

    TooltipMakerAPI scroll = panel.createUIElement(box.w + SHIFT + NomadGrid.GUTTER, box.h, true);
    scroll.addCustom(content, 0f);
    panel.addUIElement(scroll).inTL(box.x - SHIFT, top + box.y);
    HOME.place(scroll, at.homeFollowsOrders ? Math.max(0f, contentH - box.h) : HOME.offset,
        contentH, box.h);
  }

  private static void toolbar(CustomPanelAPI panel, NomadGrid.Box box, float top,
      NomadFlotillaView view) {
    CustomPanelAPI row = place(panel, box, top);
    NomadGrid.Toolbar at = NomadGrid.Toolbar.of(box.w);
    check(row, at, view);

    TooltipMakerAPI purse = NomadCell.element(row, at.credits.w, at.credits.h);
    LabelAPI credits = purse.addPara(NomadStrings.get("nomad_flotilla", "screen_credits"), 0f,
        NomadUi.hl(), NomadUi.credits(view.credits));
    credits.setAlignment(at.creditsRight ? Alignment.RMID : Alignment.LMID);
    NomadCell.place(row, purse, at.credits.x, at.credits.y);

    final String noAdd = !view.live ? view.notLiveReason()
        : view.unbuilt().isEmpty() ? NomadStrings.get("nomad_flotilla", "hover_picker_empty")
        : null;
    TooltipMakerAPI adder = row.createUIElement(at.add.w, at.add.h, false);
    ButtonAPI add = adder.addButton(NomadWorkActions.label(NomadWorkActions.Verb.PICK),
        NomadWorkActions.id(NomadWorkActions.Verb.PICK), NomadUi.base(), NomadUi.dark(),
        at.add.w, at.add.h, 0f);
    NomadUi.setEnabledSayingWhy(add, noAdd == null);
    if (noAdd != null) {
      adder.addTooltipToPrevious(NomadTip.guard(new NomadTip() {
        @Override
        public void createTooltip(TooltipMakerAPI t, boolean expanded, Object param) {
          t.addPara(noAdd, NomadUi.warn(), PAD);
        }
      }), TooltipMakerAPI.TooltipLocation.BELOW, false);
    }
    row.addUIElement(adder).inTL(at.add.x - SHIFT, at.add.y);

    TooltipMakerAPI side = row.createUIElement(at.split.w, at.split.h, false);
    final String refused = NomadSplitter.canSplit();
    ButtonAPI split = side.addButton(NomadWorkActions.label(NomadWorkActions.Verb.SPLIT),
        NomadWorkActions.id(NomadWorkActions.Verb.SPLIT), NomadUi.base(), NomadUi.dark(),
        at.split.w, at.split.h, 0f);
    NomadUi.setEnabledSayingWhy(split, refused == null);
    side.addTooltipToPrevious(NomadTip.guard(new NomadTip(NomadSplitLines.HOVER_W) {
      @Override
      public void createTooltip(TooltipMakerAPI t, boolean expanded, Object param) {
        if (refused == null) {
          splitLines(t, false);
        } else {
          t.addPara(refused, NomadUi.warn(), PAD);
        }
      }
    }), TooltipMakerAPI.TooltipLocation.BELOW, false);
    row.addUIElement(side).inTL(at.split.x - SHIFT, at.split.y);
  }

  private static void check(CustomPanelAPI row, NomadGrid.Toolbar at,
      final NomadFlotillaView view) {
    List<String> ids = NomadMark.of(view).ids;
    boolean marked = view.live && !ids.isEmpty();
    String text = NomadStrings.get("nomad_flotilla", "ui2_works_to_check",
        view.live ? Integer.toString(ids.size()) : NO_VALUE);
    float x = at.countX(marked);
    float countW = NomadText.need(text, NomadUi.PARA);

    if (marked) {
      NomadMark.add(row, at.mark.x, at.mark.y, at.mark.w, NomadUi.warn());
    }
    TooltipMakerAPI count = NomadCell.element(row, countW, NomadGrid.Toolbar.TEXT_H);
    count.addPara(text, marked ? NomadUi.warn() : NomadUi.gray(), 0f);
    if (!marked) {
      NomadTopBarTip.hangCheckTip(count, view);
    }
    NomadCell.place(row, count, x, NomadGrid.Toolbar.TEXT_Y);
    if (!marked) {
      return;
    }

    List<String> names = new ArrayList<String>();
    for (String id : ids) {
      names.add(view.workName(id));
    }
    NomadGrid.Box box = at.names(x + countW);
    NomadGrid.Toolbar.Names line = NomadGrid.Toolbar.fit(names, box.w, NomadUi.PARA,
        new NomadGrid.Toolbar.More() {
          @Override
          public String text(String shown, int rest) {
            return NomadStrings.get("nomad_flotilla", "ui3_check_names_more", shown,
                Integer.toString(rest));
          }
        });
    if (!line.text.isEmpty()) {
      NomadCell.box(row, box.x, box.y, box.w, box.h, line.text, NomadUi.text(), Alignment.TL, 0f);
    }

    float pressW = Math.max(x + countW, at.namesEnd);
    TooltipMakerAPI press = row.createUIElement(pressW, NomadGrid.Toolbar.ROW_H, false);
    press.addButton("", NomadWorkActions.id(NomadWorkActions.Verb.CHECK), CLEAR, CLEAR,
        Alignment.MID, (CutStyle) null, pressW, NomadGrid.Toolbar.ROW_H, 0f);
    NomadTopBarTip.hangCheckTip(press, view);
    row.addUIElement(press).inTL(-SHIFT, 0f);
  }

  private static void wall(CustomPanelAPI panel, NomadGrid.Box box, float top,
      NomadFlotillaView view) {
    List<WorkState> production = new ArrayList<WorkState>();
    List<WorkState> support = new ArrayList<WorkState>();
    for (WorkState w : view.works()) {
      (NomadGrid.production(view.catalog.work(w.id)) ? production : support).add(w);
    }
    boolean addCard = NomadGrid.addCard(production.size(), support.size(),
        view.unbuilt().size(), NomadGrid.columnsFor(box.w));
    NomadGrid grid = NomadGrid.wall(production.size(), support.size(), addCard, box.w, box.h);

    List<WorkState> works = new ArrayList<WorkState>(production);
    works.addAll(support);
    int[] index = new int[works.size()];
    int open = -1;
    int firstCounted = -1;
    for (int i = 0; i < works.size(); i++) {
      index[i] = i < production.size() ? i : grid.supportStart() + i - production.size();
      String id = works.get(i).id;
      if (id.equals(view.selected)) {
        open = i;
      }
      if (firstCounted < 0 && NomadMark.counted(view, id)) {
        firstCounted = i;
      }
    }
    if (open >= 0) {
      grid = grid.withPanel(index[open], NomadWorkPanel.height(view, works.get(open).id));
    }

    TooltipMakerAPI scroll = panel.createUIElement(box.w + SHIFT, box.h, true);
    CustomPanelAPI content = panel.createCustomPanel(grid.width, grid.contentH,
        NomadUi.consoleRow());
    headings(content, grid, view, production, support);
    for (int i = 0; i < works.size(); i++) {
      NomadGrid.Box c = grid.card(index[i]);
      NomadWorkCard.build(place(content, c, 0f), c.w, c.h, view, works.get(i).id,
          grid.hit(index[i]));
    }
    if (addCard) {
      NomadGrid.Box c = grid.card(grid.addIndex());
      NomadWorkCard.buildAdd(place(content, c, 0f), c.w, c.h, view, grid.hit(grid.addIndex()));
    }
    NomadGrid.Box over = grid.panel();
    if (over != null) {
      CustomPanelAPI clickPanel = place(content, over, 0f);
      NomadWorkPanel.build(clickPanel, over.w, over.h, view, works.get(open).id);
      content.bringComponentToTop(clickPanel);
    }
    scroll.addCustom(content, 0f);
    panel.addUIElement(scroll).inTL(box.x - SHIFT, top + box.y);

    float wanted = NomadGrid.clampScroll(GRID.offset, grid.contentH, grid.viewH);
    if (uiShowCounted && firstCounted >= 0) {
      wanted = grid.scrollToShow(index[firstCounted], wanted);
    }
    if (open >= 0 && uiReveal) {
      wanted = grid.scrollToShow(index[open], wanted);
    }
    uiReveal = false;
    uiShowCounted = false;
    GRID.place(scroll, wanted, grid.contentH, grid.viewH);
  }

  private static void headings(CustomPanelAPI content, NomadGrid grid,
      final NomadFlotillaView view, List<WorkState> production, List<WorkState> support) {
    heading(content, grid.heading(0), NomadStrings.get("nomad_flotilla", "ui2_head_production",
        Integer.toString(running(view, production)), Integer.toString(production.size())),
        NomadUi.base(), null);

    NeedsLevel level = view.paused || view.sheet.needs == null ? null : view.sheet.needs.level;
    boolean off = level != null && !level.atLeastSpartan();
    String text = off ? NomadStrings.get("nomad_flotilla", "ui2_head_support_off")
        : NomadStrings.get("nomad_flotilla", "roster_heading",
            Integer.toString(running(view, support)), Integer.toString(support.size()));
    TooltipMakerAPI.TooltipCreator tip = view.paused ? null : NomadTip.guard(new NomadTip(400f) {
      @Override
      public void createTooltip(TooltipMakerAPI t, boolean expanded, Object param) {
        supportLines(t, view);
      }
    });
    heading(content, grid.heading(1), text, off ? NomadUi.warn() : NomadUi.base(), tip);
  }

  private static int running(NomadFlotillaView view, List<WorkState> works) {
    int n = 0;
    for (WorkState w : works) {
      if (NomadWorkCardModel.works(view.cardState(w.id).shown)) {
        n++;
      }
    }
    return n;
  }

  private static void heading(CustomPanelAPI content, NomadGrid.Box box, String text, Color color,
      TooltipMakerAPI.TooltipCreator tip) {
    if (box == null) {
      return;
    }
    TooltipMakerAPI el = content.createUIElement(box.w, box.h, false);
    el.addSectionHeading(text, color, NomadUi.dark(), Alignment.MID, 0f);
    if (tip != null) {
      el.addTooltipToPrevious(tip, TooltipMakerAPI.TooltipLocation.BELOW, false);
    }
    content.addUIElement(el).inTL(box.x, box.y);
  }

  private static void supportLines(TooltipMakerAPI t, NomadFlotillaView view) {
    TickSheet sheet = view.sheet;
    String limit = Integer.toString(sheet.supportLimit);
    NomadWorkActions.para(t, NomadStrings.get("nomad_flotilla", "roster_heading_tip", limit),
        NomadUi.hl(), limit);
    t.addPara(NomadStrings.get("nomad_flotilla", "roster_heading_down"), PAD);
    float next = Roster.nextAt(sheet.residents, view.rules);
    if (Float.isInfinite(next)) {
      t.addPara(NomadStrings.get("nomad_flotilla", "roster_heading_top"), PAD);
    } else {
      String to = Integer.toString(Roster.limit(next, view.rules));
      String at = NomadPanelFigures.heads(next);
      NomadWorkActions.para(t,
          NomadStrings.get("nomad_flotilla", "roster_heading_next", to, at), NomadUi.hl(), to,
          at);
    }
  }

  public static void splitLines(TooltipMakerAPI t, boolean confirm) {
    if (confirm) {
      NomadSplitConfirm.body(t, 0f, NomadSplitConfirm.BLEED_FLOW);
    } else {
      NomadSplitLines.hover(t);
    }
  }

  private static final Color DIM = new Color(95, 95, 95);
  private static final float WORD_X = 10f;
  private static final float WORD_UP = 31f;
  private static final float LINES_DOWN = 6f;

  public static void buildNoFlotilla(CustomPanelAPI panel, float width, float height) {
    float top = NomadTabbedPanels.BODY_TOP;
    TooltipMakerAPI lines = notice(panel, width, height - top, top,
        NomadStrings.get("nomad_flotilla", "screen_none_title"), NomadUi.gray());
    lines.addPara(NomadStrings.get("nomad_flotilla", "screen_none_cure"), NomadUi.text(), 0f);
  }

  private static TooltipMakerAPI notice(CustomPanelAPI panel, float width, float bodyHeight,
      float top, String word, Color wordColor) {
    NomadGrid.Screen at = NomadGrid.Screen.of(width, bodyHeight);
    NomadGrid.Box home = at.home;
    float heroH = NomadGrid.Screen.heroH(home.w, at.homeLayoutH);

    CustomPanelAPI picture = NomadHero.panel(panel,
        NomadHero.sprite(NomadHomeColumn.pictureKey(NeedsLevel.NON_EXISTENT), home.w / heroH),
        home.w, heroH, DIM, true);
    panel.addComponent(picture).inTL(home.x, top);

    float wordW = home.w - 2f * WORD_X;
    TooltipMakerAPI title = NomadCell.element(panel, wordW, WORD_UP);
    title.setParaFont(Fonts.INSIGNIA_VERY_LARGE);
    title.addPara(word, wordColor, 0f);
    NomadCell.place(panel, title, home.x + WORD_X, top + heroH - WORD_UP);

    TooltipMakerAPI lines = NomadCell.element(panel, home.w,
        Math.max(0f, bodyHeight - heroH - LINES_DOWN));
    NomadCell.place(panel, lines, home.x, top + heroH + LINES_DOWN);
    return lines;
  }

  public static boolean handle(Object buttonId, NomadIntel intel, NomadFleetData data,
      IntelUIAPI ui) {
    return NomadWorkActions.handle(buttonId, data);
  }

  public static boolean needsConfirm(Object buttonId) {
    return NomadWorkActions.needsConfirm(buttonId);
  }

  public static void confirmPrompt(Object buttonId, TooltipMakerAPI prompt, NomadFleetData data) {
    NomadWorkActions.confirmPrompt(buttonId, prompt, data);
  }

  public static String confirmLabel(Object buttonId) {
    return NomadWorkActions.confirmLabel(buttonId);
  }
}
