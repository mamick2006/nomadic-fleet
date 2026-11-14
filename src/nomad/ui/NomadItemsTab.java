package nomad.ui;

import static nomad.ui.NomadHero.label;

import java.awt.Color;
import java.lang.ref.WeakReference;
import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import nomad.NomadStrings;
import nomad.campaign.NomadEcon;
import nomad.campaign.NomadHistory;
import nomad.campaign.econ.EconConstants;
import nomad.campaign.econ.Work;
import nomad.data.NomadFleetData;
import nomad.intel.NomadIntel;
import nomad.ui.NomadHero.Face;

public final class NomadItemsTab {

  private NomadItemsTab() {
  }

  static final String P = "it_";

  private static final String PICTURE = "nomad_illus_items";

  private static final float OPAD = 10f;
  private static final float PAD = 3f;
  private static final float SHIFT = NomadText.INSET;
  private static final int RULE_ALPHA = 110;

  private static final float LINE_H = NomadHomeFigures.LINE_H;
  private static final int DOCK_LINES = 2;
  private static final float REACH_HEAD_DOWN = 36f;
  private static final float HEAD_H = NomadGrid.HEAD_H;
  private static final float ROWS_DOWN = 26f;

  private static final float ICON = 44f;
  private static final float ICON_Y = 4f;
  private static final float BUTTON_H = 22f;

  private static final float REACH_ROW_H = 58f;
  private static final int REACH_EFFECT_LINES = 3;
  private static final float REACH_TEXT_X = 52f;
  private static final float REACH_TEXT_SHORT = 140f;
  private static final float REACH_NAME_Y = 3f;
  private static final float REACH_EFFECT_Y = 20f;
  private static final float REACH_WHERE_Y = 37f;
  private static final float INSTALL_W = 80f;
  private static final float INSTALL_Y = 16f;

  private static final float WALL_ROW_H = 52f;
  private static final float WALL_ROW_TALL_H = 68f;
  private static final float WALL_TEXT_X = 54f;
  private static final float WALL_TEXT_SHORT = 154f;
  private static final float WALL_NAME_Y = 8f;
  private static final float WALL_EFFECT_Y = 26f;
  private static final float WALL_NOTE_Y = 44f;
  static final int WALL_EFFECT_LINES = 2;
  private static final float REMOVE_W = 90f;
  private static final float REMOVE_Y = 15f;

  private static final float TIP_W = 400f;
  private static final float GRID_VALUE_W = 44f;
  private static final float GRID_VALUE_PAD = 10f;
  private static final TooltipMakerAPI.TooltipLocation REACH_TIP_AT =
      TooltipMakerAPI.TooltipLocation.RIGHT;
  private static final TooltipMakerAPI.TooltipLocation WALL_TIP_AT =
      TooltipMakerAPI.TooltipLocation.BELOW;
  private static final float PICTURE_FOCUS = 0.5f;

  private static WeakReference<TooltipMakerAPI> reachLive;
  private static WeakReference<NomadFleetData> reachFor;
  private static float reachOffset;

  public static TooltipMakerAPI build(CustomPanelAPI panel, float width, float bodyHeight,
      NomadIntel intel, NomadFleetData data) {
    EconConstants k = NomadEcon.constants();
    NomadGrid.Screen at = NomadGrid.Screen.of(width, bodyHeight);
    float top = NomadTabbedPanels.BODY_TOP;
    NomadUi.DockState dock = NomadUi.dockState();
    List<String> installed = data.getInstalledItems();
    List<NomadItems.Owned> reach = NomadItems.owned(data);

    home(panel, at.home, top, dock, installed.size(), reach, data, k);
    NomadGrid.Box wall = new NomadGrid.Box(at.wall.x, 0f, at.wall.w, bodyHeight);
    return wall(panel, wall, top, dock, installed, data, k);
  }

  private static void home(CustomPanelAPI panel, NomadGrid.Box box, float top,
      NomadUi.DockState dock, int installed, List<NomadItems.Owned> reach, NomadFleetData data,
      EconConstants k) {
    float w = box.w;
    NomadHomeFigures.Layout at =
        NomadHomeFigures.layout(w, box.h, NomadGrid.Screen.heroH(w, box.h), 0f);
    CustomPanelAPI column = panel.createCustomPanel(w, box.h, NomadUi.consoleRow());
    panel.addComponent(column).inTL(box.x, top + box.y);

    hero(column, at, dock, installed);

    String refusal = dockRefusal(dock);
    String line = refusal == null ? NomadStrings.get("nomad_items", "it_intro_short") : refusal;
    int lines = NomadText.need(line, NomadUi.PARA) <= w ? 1 : DOCK_LINES;
    TooltipMakerAPI lineEl = NomadCell.element(column, w, lines * LINE_H);
    lineEl.addPara(NomadText.fitLines(line, w, lines, NomadUi.PARA),
        refusal == null ? NomadUi.gray() : NomadUi.warn(), 0f);
    NomadCell.place(column, lineEl, 0f, at.line1.y);

    float headY = at.hero.h + REACH_HEAD_DOWN + (lines - 1) * LINE_H;
    heading(column, new NomadGrid.Box(0f, headY, w, HEAD_H),
        NomadStrings.get("nomad_items", "it_head_reach", Integer.toString(reach.size())));

    float rowsY = headY + ROWS_DOWN;
    float wanted = reachFor != null && reachFor.get() == data ? reachOffset : 0f;
    reachFor = null;
    reachOffset = 0f;
    reachLive = null;
    if (reach.isEmpty()) {
      line(column, 0f, rowsY, w, NomadStrings.get("nomad_items", "it_available_none"),
          NomadUi.gray());
      return;
    }
    float viewH = Math.max(REACH_ROW_H, box.h - rowsY);
    float textBox = Math.max(0f, w - REACH_TEXT_SHORT);
    String[] effects = new String[reach.size()];
    int[] effectLines = new int[reach.size()];
    float contentH = 0f;
    for (int i = 0; i < reach.size(); i++) {
      effects[i] = NomadItems.effectLine(reach.get(i).id, k);
      effectLines[i] = Math.min(REACH_EFFECT_LINES,
          NomadText.lines(effects[i], textBox, NomadUi.PARA));
      contentH += reachRowH(effectLines[i]);
    }
    CustomPanelAPI content = panel.createCustomPanel(w, contentH, NomadUi.consoleRow());
    float y = 0f;
    for (int i = 0; i < reach.size(); i++) {
      reachRow(content, y, w, reach.get(i), effects[i], effectLines[i], dock);
      y += reachRowH(effectLines[i]);
    }
    TooltipMakerAPI scroll = panel.createUIElement(w + SHIFT + NomadGrid.GUTTER, viewH, true);
    scroll.addCustom(content, 0f);
    panel.addUIElement(scroll).inTL(box.x - SHIFT, top + box.y + rowsY);

    NomadTabbedPanels.applyScroll(scroll, NomadGrid.clampScroll(wanted, contentH, viewH));
    reachLive = new WeakReference<TooltipMakerAPI>(scroll);
  }

  private static void hero(CustomPanelAPI column, NomadHomeFigures.Layout at,
      NomadUi.DockState dock, int installed) {
    CustomPanelAPI back = NomadHero.panel(column, picture(at.hero.w, at.hero.h), at.hero.w,
        at.hero.h, Color.WHITE, true);
    column.addComponent(back).inTL(at.hero.x, at.hero.y);

    String dockCaption = NomadStrings.get("nomad_items", "it_hero_dock");
    String word = dockWord(dock);
    float dockCaptionW = NomadText.need(dockCaption, NomadUi.BUTTON);
    float wordW = NomadText.need(word, NomadHomeColumn.VALUE);
    label(column, at.textRight() - dockCaptionW, at.captionY(), dockCaptionW, LINE_H,
        dockCaption, NomadUi.base(), Alignment.RMID, Face.CAPTION);
    label(column, at.textRight() - wordW, at.valueY(), wordW, NomadHomeFigures.VALUE_H, word,
        dock == NomadUi.DockState.DOCKED ? NomadUi.good() : NomadUi.warn(), Alignment.RMID,
        Face.VALUE);

    float left = NomadHomeFigures.HERO_INSET;
    String caption = NomadStrings.get("nomad_items", "it_hero_caption");
    float captionBox = Math.min(NomadText.need(caption, NomadUi.BUTTON), at.leftBox(dockCaptionW));
    label(column, left, at.captionY(), captionBox, LINE_H,
        NomadText.fit(caption, captionBox, NomadUi.BUTTON), NomadUi.base(), Alignment.LMID,
        Face.CAPTION);
    String count = NomadStrings.get("nomad_items", "it_hero_count", Integer.toString(installed));
    float countBox = Math.min(NomadText.need(count, NomadHomeColumn.VALUE), at.leftBox(wordW));
    label(column, left, at.valueY(), countBox, NomadHomeFigures.VALUE_H,
        NomadText.fit(count, countBox, NomadHomeColumn.VALUE), NomadUi.bright(), Alignment.LMID,
        Face.VALUE);
  }

  private static String dockWord(NomadUi.DockState dock) {
    if (dock == NomadUi.DockState.DOCKED) {
      return NomadStrings.get("nomad_items", "it_dock_yes");
    }
    return dock == NomadUi.DockState.NO_PORT ? NomadStrings.get("nomad_items", "it_dock_no_port")
        : NomadStrings.get("nomad_items", "it_dock_no");
  }

  private static String dockRefusal(NomadUi.DockState dock) {
    if (dock == NomadUi.DockState.DOCKED) {
      return null;
    }
    return dock == NomadUi.DockState.NO_PORT
        ? NomadStrings.get("nomad_items", "it_install_no_port")
        : NomadStrings.get("nomad_items", "it_install_dock_tip");
  }

  private static String installRefusal(NomadItems.Owned o, NomadUi.DockState dock) {
    return o.effective ? dockRefusal(dock)
        : NomadStrings.get("nomad_items", "it_install_no_effect");
  }

  private static String removeRefusal(NomadUi.DockState dock) {
    return dock == NomadUi.DockState.DOCKED ? null
        : NomadStrings.get("nomad_items", "it_uninstall_dock_tip");
  }

  private static SpriteAPI picture(float w, float h) {
    SpriteAPI s = NomadHero.sprite(PICTURE);
    if (s == null || w <= 0f || h <= 0f) {
      return null;
    }
    float hero = NomadHero.cut(s, s.getWidth() / s.getHeight(),
        1f / NomadGrid.Screen.HERO_ASPECT, PICTURE_FOCUS);
    NomadHero.cut(s, hero, w / h, 0.5f);
    return s;
  }

  private static float reachRowH(int effectLines) {
    return REACH_ROW_H + (effectLines - 1) * LINE_H;
  }

  private static void reachRow(CustomPanelAPI content, float y, float w, NomadItems.Owned o,
      String effect, int effectLines, NomadUi.DockState dock) {
    float h = reachRowH(effectLines);
    CustomPanelAPI row = content.createCustomPanel(w, h, rowChrome());
    content.addComponent(row).inTL(0f, y);

    String name = NomadItems.displayName(o.id);
    String where = whereLine(o);
    String refusal = installRefusal(o, dock);
    float box = Math.max(0f, w - REACH_TEXT_SHORT);

    icon(row, o.id);
    line(row, REACH_TEXT_X, REACH_NAME_Y, box, name,
        o.effective ? NomadUi.bright() : NomadUi.gray());
    effect(row, REACH_TEXT_X, REACH_EFFECT_Y, box, effect, o.effective, effectLines);
    line(row, REACH_TEXT_X, REACH_WHERE_Y + (effectLines - 1) * LINE_H, box, where,
        NomadUi.gray());

    TooltipMakerAPI.TooltipCreator tip = tip(name, o.effective ? effect : null, null, where,
        refusal != null ? refusal
            : NomadStrings.get("nomad_items", "it_install_row_tip", name),
        refusal != null);
    hover(row, w, h, tip, REACH_TIP_AT);
    button(row, w - INSTALL_W, INSTALL_Y, INSTALL_W,
        NomadStrings.get("nomad_items", "it_install"), P + "install*" + o.id,
        NomadUi.base(), refusal == null, null, REACH_TIP_AT);
  }

  private static String whereLine(NomadItems.Owned o) {
    if (o.where == NomadItems.Where.FLOTILLA) {
      return NomadStrings.get("nomad_items", "it_where_flotilla");
    }
    if (o.where == NomadItems.Where.DETACHED) {
      return NomadStrings.get("nomad_items", "it_where_detached");
    }
    if (o.where == NomadItems.Where.STORAGE) {
      return NomadStrings.get("nomad_items", "it_where_storage",
          o.marketName == null ? NomadStrings.get("nomad_items", "it_a_colony") : o.marketName);
    }
    return NomadStrings.get("nomad_items", "it_where_fleet");
  }

  private static TooltipMakerAPI wall(CustomPanelAPI panel, NomadGrid.Box box, float top,
      NomadUi.DockState dock, List<String> installed, NomadFleetData data, EconConstants k) {
    float v = Math.max(0f, box.w - NomadGrid.GUTTER);
    heading(panel, new NomadGrid.Box(box.x, top + box.y, v, HEAD_H),
        NomadStrings.get("nomad_items", "it_head_installed",
            Integer.toString(installed.size())));

    float rowsY = top + box.y + ROWS_DOWN;
    if (installed.isEmpty()) {
      TooltipMakerAPI none = NomadCell.element(panel, v, DOCK_LINES * LINE_H);
      none.addPara(NomadText.fitLines(NomadStrings.get("nomad_items", "it_empty"), v,
          DOCK_LINES, NomadUi.PARA), NomadUi.gray(), 0f);
      NomadCell.place(panel, none, box.x, rowsY);
      return null;
    }

    boolean pristine = NomadItems.isInstalled(data, Work.PRISTINE_NANOFORGE);
    float textBox = Math.max(0f, v - WALL_TEXT_SHORT);
    String[] effects = new String[installed.size()];
    int[] lines = new int[installed.size()];
    float contentH = 0f;
    for (int i = 0; i < installed.size(); i++) {
      effects[i] = NomadItems.effectLine(installed.get(i), k);
      lines[i] = Math.max(1, Math.min(WALL_EFFECT_LINES,
          NomadText.lines(effects[i], textBox, NomadUi.PARA)));
      contentH += wallRowH(superseded(installed.get(i), pristine), lines[i]);
    }
    CustomPanelAPI content = panel.createCustomPanel(v, contentH, NomadUi.consoleRow());
    float y = 0f;
    for (int i = 0; i < installed.size(); i++) {
      String id = installed.get(i);
      boolean superseded = superseded(id, pristine);
      float h = wallRowH(superseded, lines[i]);
      installedRow(content, y, v, h, id, superseded, dock, effects[i], lines[i]);
      y += h;
    }
    float viewH = Math.max(WALL_ROW_H, box.h - ROWS_DOWN);
    TooltipMakerAPI scroll = panel.createUIElement(box.w + SHIFT, viewH, true);
    scroll.addCustom(content, 0f);
    panel.addUIElement(scroll).inTL(box.x - SHIFT, rowsY);
    return scroll;
  }

  private static boolean superseded(String id, boolean pristineInstalled) {
    return pristineInstalled && Work.CORRUPTED_NANOFORGE.equals(id);
  }

  private static float wallRowH(boolean superseded, int effectLines) {
    return (superseded ? WALL_ROW_TALL_H : WALL_ROW_H) + (effectLines - 1) * LINE_H;
  }

  private static void installedRow(CustomPanelAPI content, float y, float v, float h, String id,
      boolean superseded, NomadUi.DockState dock, String effect, int effectLines) {
    CustomPanelAPI row = content.createCustomPanel(v, h, rowChrome());
    content.addComponent(row).inTL(0f, y);

    String name = NomadItems.displayName(id);
    String note = superseded ? NomadStrings.get("nomad_items", "it_superseded") : null;
    String refusal = removeRefusal(dock);
    float box = Math.max(0f, v - WALL_TEXT_SHORT);

    icon(row, id);
    line(row, WALL_TEXT_X, WALL_NAME_Y, box, name, NomadUi.bright());
    effect(row, WALL_TEXT_X, WALL_EFFECT_Y, box, effect, true, effectLines);
    if (note != null) {
      line(row, WALL_TEXT_X, WALL_NOTE_Y + (effectLines - 1) * LINE_H, box, note,
          NomadUi.warn());
    }

    String disables = nomad.campaign.works.WorkPrereqChecks.removeWarning(
        NomadFleetData.getOrNull(), id, NomadFlotillaView.FLOTILLA);
    String tipNote = note == null ? disables : disables == null ? note : note + "\n" + disables;
    TooltipMakerAPI.TooltipCreator tip = tip(name, effect, tipNote, null,
        refusal != null ? refusal : NomadStrings.get("nomad_items", "it_uninstall_tip", name),
        refusal != null);
    hover(row, WALL_TEXT_X + box, h, tip, WALL_TIP_AT);
    button(row, v - REMOVE_W, REMOVE_Y, REMOVE_W, NomadStrings.get("nomad_items", "it_remove"),
        P + "uninstall*" + id, NomadUi.warn(), refusal == null, tip, WALL_TIP_AT);
  }

  private static NomadRowChrome rowChrome() {
    return NomadRowChrome.rule(null, NomadUi.dark(), RULE_ALPHA);
  }

  private static void icon(CustomPanelAPI row, String id) {
    String path = NomadItems.iconName(id);
    if (path == null) {
      return;
    }
    float w = ICON;
    float h = ICON;
    try {
      SpriteAPI s = Global.getSettings().getSprite(path);
      float longest = Math.max(s.getWidth(), s.getHeight());
      if (longest > 0f) {
        w = Math.max(1f, Math.round(ICON * s.getWidth() / longest));
        h = Math.max(1f, Math.round(ICON * s.getHeight() / longest));
      }
    } catch (RuntimeException e) {
      return;
    }
    TooltipMakerAPI el = row.createUIElement(w, h, false);
    el.addImage(path, w, h, 0f);
    row.addUIElement(el).inTL((ICON - w) / 2f - SHIFT, ICON_Y + (ICON - h) / 2f);
  }

  private static LabelAPI line(CustomPanelAPI row, float x, float y, float box, String text,
      Color c) {
    return NomadCell.box(row, x, y, box, LINE_H, NomadText.fit(text, box, NomadUi.PARA), c,
        Alignment.LMID, 0f);
  }

  private static void effect(CustomPanelAPI row, float x, float y, float box, String effect,
      boolean effective, int lines) {
    String shown = NomadText.fitLines(effect, box, lines, NomadUi.PARA);
    LabelAPI l = NomadCell.box(row, x, y, box, lines * LINE_H, shown,
        effective ? NomadUi.text() : NomadUi.gray(), Alignment.LMID, 0f);
    if (effective) {
      NomadTable.highlightNumbers(l, shown, NomadUi.hl());
    }
  }

  private static void heading(CustomPanelAPI parent, NomadGrid.Box box, String text) {
    TooltipMakerAPI el = parent.createUIElement(box.w, box.h, false);
    el.addSectionHeading(text, NomadUi.base(), NomadUi.dark(), Alignment.MID, 0f);
    parent.addUIElement(el).inTL(box.x, box.y);
  }

  private static void hover(CustomPanelAPI row, float w, float h,
      TooltipMakerAPI.TooltipCreator tip, TooltipMakerAPI.TooltipLocation where) {
    TooltipMakerAPI el = NomadCell.element(row, w, h);
    CustomPanelAPI area = row.createCustomPanel(w, h, new BaseCustomUIPanelPlugin());
    el.addCustom(area, 0f);
    el.addTooltipToPrevious(tip, where, false);
    NomadCell.place(row, el, 0f, 0f);
  }

  private static void button(CustomPanelAPI row, float x, float y, float w, String text,
      String id, Color face, boolean enabled, TooltipMakerAPI.TooltipCreator tip,
      TooltipMakerAPI.TooltipLocation where) {
    TooltipMakerAPI el = row.createUIElement(w, BUTTON_H, false);
    ButtonAPI b = el.addButton(text, id, face, NomadUi.dark(), w, BUTTON_H, 0f);
    NomadUi.setEnabledSayingWhy(b, enabled);
    if (tip != null) {
      el.addTooltipToPrevious(tip, where, false);
    }
    row.addUIElement(el).inTL(x - SHIFT, y);
  }

  private static TooltipMakerAPI.TooltipCreator tip(final String name, final String effect,
      final String note, final String where, final String last, final boolean refused) {
    return NomadTip.guard(new NomadTip(TIP_W) {
      @Override
      public void createTooltip(TooltipMakerAPI t, boolean expanded, Object param) {
        t.addTitle(name, NomadUi.base());
        effectRows(t, effect);
        if (note != null) {
          t.addPara(note, NomadUi.warn(), OPAD);
        }
        if (where != null) {
          t.addPara(where, NomadUi.gray(), OPAD);
        }
        t.addPara(last, refused ? NomadUi.warn() : NomadUi.gray(), where == null ? OPAD : PAD);
      }
    });
  }

  private static void effectRows(TooltipMakerAPI t, String effect) {
    if (effect == null) {
      return;
    }
    float gridW = TIP_W - 2f * NomadText.INSET;
    float labelW = gridW - GRID_VALUE_W - GRID_VALUE_PAD;
    boolean grid = false;
    int at = 0;
    float pad = OPAD;
    for (NomadItems.EffectRow row : NomadItems.effectRows(effect)) {
      boolean cell = row.value != null && NomadText.fits(row.value, GRID_VALUE_W, NomadUi.PARA)
          && NomadText.fits(row.label, labelW, NomadUi.PARA);
      if (cell) {
        if (!grid) {
          t.beginGridFlipped(gridW, 1, NomadUi.text(), GRID_VALUE_W, GRID_VALUE_PAD);
          grid = true;
          at = 0;
        }
        t.addToGrid(0, at++, row.label, row.value, NomadUi.hl());
        continue;
      }
      if (grid) {
        t.addGrid(pad);
        grid = false;
        pad = PAD;
      }
      LabelAPI l = t.addPara(row.sentence, NomadUi.text(), pad);
      NomadTable.highlightNumbers(l, row.sentence, NomadUi.hl());
      pad = PAD;
    }
    if (grid) {
      t.addGrid(pad);
    }
  }

  public static boolean handle(Object buttonId, NomadIntel intel, NomadFleetData data, IntelUIAPI ui) {
    if (!(buttonId instanceof String) || !((String) buttonId).startsWith(P)) {
      return false;
    }
    String[] p = ((String) buttonId).split("\\*");
    String kind = p[0].substring(P.length());
    if ("install".equals(kind)) {
      if (p.length < 2) {
        return false;
      }
      keepReachScroll(data);
      installItem(data, p[1]);
    } else if ("uninstall".equals(kind)) {
      keepReachScroll(data);
      uninstallItem(data, p[1]);
    } else {
      return false;
    }
    return true;
  }

  private static void keepReachScroll(NomadFleetData data) {
    TooltipMakerAPI live = reachLive == null ? null : reachLive.get();
    reachOffset = live == null ? 0f : NomadTabbedPanels.scrollOf(live);
    reachFor = new WeakReference<NomadFleetData>(data);
  }

  private static void installItem(NomadFleetData data, String id) {
    if (!NomadUi.playerIsDocked()) {
      return;
    }
    if (NomadItems.install(data, id)) {
      data.markChanged();
      NomadHistory.add(data, NomadStrings.get("nomad_history", "hist_item_installed",
          NomadItems.displayName(id)));
    }
  }

  private static void uninstallItem(NomadFleetData data, String id) {
    if (!NomadUi.playerIsDocked()) {
      return;
    }
    if (data.getInstalledItems().remove(id)) {
      data.markChanged();
      CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
      if (!NomadItems.gameDefines(id)) {
        Global.getLogger(NomadItemsTab.class).warn("Nomad Flotilla: uninstalled colony item '" + id
            + "', which no installed mod defines any more, so nothing was returned to cargo.");
      } else if (NomadItems.isCore(id)) {
        cargo.addCommodity(id, 1f);
      } else {
        cargo.addSpecial(new SpecialItemData(id, null), 1f);
      }
      NomadHistory.add(data, NomadStrings.get("nomad_history", "hist_item_removed",
          NomadItems.displayName(id)));
    }
  }
}
