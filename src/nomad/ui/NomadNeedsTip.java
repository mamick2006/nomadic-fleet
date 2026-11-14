package nomad.ui;

import java.awt.Color;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import nomad.NomadStrings;
import nomad.campaign.econ.model.BasicNeeds;
import nomad.campaign.econ.model.NeedsLevel;
import nomad.campaign.econ.model.Requirement;
import nomad.campaign.econ.model.WorkDef;
import nomad.campaign.econ.model.Residents;
import nomad.campaign.econ.model.Rules;
import nomad.campaign.econ.model.TickSheet;
import nomad.campaign.split.NomadSplitter;

public final class NomadNeedsTip {

  private NomadNeedsTip() {
  }

  private static final float PAD = NomadHomeFigures.TIP_PAD;
  private static final float OPAD = NomadHomeFigures.TIP_OPAD;
  private static final float ROW_H = NomadHomeFigures.TIP_ROW_H;
  private static final int HERE_ALPHA = 130;

  public static TooltipMakerAPI.TooltipCreator hero(final NomadFlotillaView view) {
    if (view.paused) {
      return NomadTip.guard(new NomadTip(NomadHomeFigures.SIDE_TIP_W) {
        @Override
        public void createTooltip(TooltipMakerAPI tip, boolean expanded, Object param) {
          tip.addPara(NomadStrings.get("nomad_overview", "ov_status_paused",
              NomadUi.pct(view.shared.pausedDecay)), NomadUi.bad(), 0f);
        }
      });
    }
    return NomadTip.guard(new NomadTip(NomadHomeFigures.TIP_W) {
      @Override
      public boolean isTooltipExpandable(Object param) {
        return true;
      }

      @Override
      public void createTooltip(TooltipMakerAPI tip, boolean expanded, Object param) {
        writeHero(tip, view, expanded);
      }
    });
  }

  private static void writeHero(TooltipMakerAPI t, NomadFlotillaView view, boolean expanded) {
    TickSheet sheet = view.sheet;
    BasicNeeds.Reading r = sheet.needs;
    NeedsLevel level = NomadHomeColumn.level(r);
    if (!expanded) {
      widen(t, NomadHomeFigures.TIP_W);
      float w = NomadHomeFigures.TIP_W;
      top(t, view, level);
      levelSection(t, view, level);
      requirement(t, view, false, OPAD, w);
      efficiency(t, view, level, false, w);
      ending(t, view, r, level);
      return;
    }
    if (widen(t, NomadHomeFigures.TIP_EXPANDED_W)) {
      keepOnScreen(t);
      TooltipMakerAPI left = t.beginSubTooltip(NomadHomeFigures.TIP_W);
      top(left, view, level);
      levelSection(left, view, level);
      everyLevelSection(left, view);
      ending(left, view, r, level);
      t.endSubTooltip();
      float rw = NomadHomeFigures.TIP_RIGHT_W;
      TooltipMakerAPI right = t.beginSubTooltip(rw);
      requirement(right, view, true, 0f, rw);
      efficiency(right, view, level, true, rw);
      t.endSubTooltip();
      float h = Math.max(left.getHeightSoFar(), right.getHeightSoFar());
      left.getPosition().setHeight(h);
      right.getPosition().setHeight(h);
      left.setHeightSoFar(h);
      right.setHeightSoFar(h);
      t.addCustom(left, 0f);
      t.addCustomDoNotSetPosition(right).getPosition().rightOfTop(left,
          NomadHomeFigures.TIP_COL_GAP);
      return;
    }
    float w = NomadHomeFigures.TIP_W;
    top(t, view, level);
    levelSection(t, view, level);
    requirement(t, view, true, OPAD, w);
    efficiency(t, view, level, true, w);
    everyLevelSection(t, view);
    ending(t, view, r, level);
  }

  private static void keepOnScreen(TooltipMakerAPI t) {
    PositionAPI p = t.getPosition();
    float over = p.getX() + NomadHomeFigures.TIP_EXPANDED_W + NomadHomeFigures.TIP_FRAME_W
        + OPAD - Global.getSettings().getScreenWidth();
    if (over <= 0f) {
      return;
    }
    try {
      MethodHandle get = MethodHandles.privateLookupIn(p.getClass(), MethodHandles.lookup())
          .findVirtual(p.getClass(), "getXAlignOffset", MethodType.methodType(float.class));
      float now = (float) get.invoke(p);
      p.setXAlignOffset(now - Math.min(over, Math.max(0f, p.getX())));
    } catch (Throwable e) {
      synchronized (WIDTH_LOCK) {
        if (!offsetFailed) {
          offsetFailed = true;
          Global.getLogger(NomadNeedsTip.class).warn("Nomad Flotilla: the Basic Needs hover's"
              + " long form cannot be moved left onto the screen", e);
        }
      }
    }
  }

  private static boolean offsetFailed;

  private static void top(TooltipMakerAPI t, NomadFlotillaView view, NeedsLevel level) {
    t.addTitle(NomadStrings.get("nomad_flotilla", "ui2_tip_needs_title",
        NomadFlotillaView.levelName(level)), NomadHomeColumn.levelColor(level));
    if (view.state.detached) {
      String where = NomadSplitter.detachedSummary();
      t.addPara(NomadStrings.get("nomad_overview", "ov_status_detached",
          where.isEmpty() ? "" : " " + where), NomadUi.warn(), OPAD);
    }
    t.addPara(NomadStrings.get("nomad_flotilla", "needs_tip_what"), OPAD);
  }

  private static void levelSection(TooltipMakerAPI t, NomadFlotillaView view, NeedsLevel level) {
    heading(t, NomadStrings.get("nomad_flotilla", "ui2_tip_level_calc_head"), OPAD);
    levelTable(t, view.sheet.needs, view.rules, level);
  }

  private static void everyLevelSection(TooltipMakerAPI t, NomadFlotillaView view) {
    TickSheet sheet = view.sheet;
    heading(t, NomadStrings.get("nomad_flotilla", "ui2_tip_levels_head",
        NomadHomeFigures.whole(sheet.residents)), OPAD);
    everyLevel(t, sheet.needs, view.rules, Residents.schoolGrowth(sheet));
  }

  private static final Object WIDTH_LOCK = new Object();
  private static Class<?> widthClass;
  private static MethodHandle widthSetter;
  private static boolean widthFailed;

  private static boolean widen(TooltipMakerAPI t, float width) {
    MethodHandle set = widthSetter(t.getClass());
    if (set == null) {
      return false;
    }
    try {
      set.invoke(t, width);
      return true;
    } catch (Throwable e) {
      failWidth(e);
      return false;
    }
  }

  private static MethodHandle widthSetter(Class<?> cls) {
    synchronized (WIDTH_LOCK) {
      if (widthFailed) {
        return null;
      }
      if (widthSetter != null && widthClass.isAssignableFrom(cls)) {
        return widthSetter;
      }
      for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
        try {
          MethodHandles.Lookup in = MethodHandles.privateLookupIn(c, MethodHandles.lookup());
          widthSetter = in.findSetter(c, "width", float.class);
          widthClass = c;
          return widthSetter;
        } catch (NoSuchFieldException e) {
        } catch (Throwable e) {
          failWidth(e);
          return null;
        }
      }
      failWidth(new IllegalStateException("no float field \"width\" on " + cls.getName()));
      return null;
    }
  }

  private static void failWidth(Throwable e) {
    synchronized (WIDTH_LOCK) {
      if (!widthFailed) {
        widthFailed = true;
        Global.getLogger(NomadNeedsTip.class).warn("Nomad Flotilla: the Basic Needs hover cannot"
            + " widen for its long form; it shows in one column", e);
      }
    }
  }

  private static void levelTable(TooltipMakerAPI t, BasicNeeds.Reading r, Rules k,
      NeedsLevel level) {
    float[] cols = NomadHomeFigures.LEVEL_COLS;
    float width = NomadHomeFigures.TIP_W - 10f;
    CustomPanelAPI head = row(width, null);
    cell(head, cols, 1, NomadStrings.get("nomad_flotilla", "ui2_tip_col_has"), NomadUi.gray(),
        Alignment.RMID);
    cell(head, cols, 2, NomadStrings.get("nomad_flotilla", "ui2_tip_col_requires",
        NomadFlotillaView.levelName(NeedsLevel.NORMAL)), NomadUi.gray(), Alignment.RMID);
    cell(head, cols, 3, NomadStrings.get("nomad_flotilla", "ui2_tip_col_has_of"), NomadUi.gray(),
        Alignment.RMID);
    t.addCustom(head, PAD);

    NomadHomeFigures.LevelRow[] rows = NomadHomeFigures.levelRows(r, k);
    String[] names = { NomadStrings.get("nomad_flotilla", "panels_space_title"),
        NomadStrings.get("nomad_flotilla", "panels_residents_title") };
    for (int i = 0; i < rows.length; i++) {
      CustomPanelAPI line = row(width, null);
      cell(line, cols, 0, names[i], NomadUi.text(), Alignment.LMID);
      cell(line, cols, 1, NomadHomeFigures.whole(rows[i].hasShown), NomadUi.hl(), Alignment.RMID);
      cell(line, cols, 2, NomadHomeFigures.whole(rows[i].requiresShown), NomadUi.hl(),
          Alignment.RMID);
      cell(line, cols, 3, rows[i].percent + "%", NomadUi.hl(), Alignment.RMID);
      t.addCustom(line, 0f);
    }

    boolean space = NomadHomeFigures.spaceDecides(rows);
    String at = (space ? rows[0].percent : rows[1].percent) + "%";
    StringBuilder text = new StringBuilder(space
        ? NomadStrings.get("nomad_flotilla", "ui2_tip_lower_space", at)
        : NomadStrings.get("nomad_flotilla", "ui2_tip_lower_residents", at));
    NeedsLevel next = level.above();
    if (next != null) {
      text.append(' ').append(NomadStrings.get("nomad_flotilla", "ui2_tip_level_edges",
          NomadFlotillaView.levelName(level), NomadUi.pct(k.provision(level)),
          NomadFlotillaView.levelName(next), NomadUi.pct(k.provision(next))));
    }
    para(t, text.toString(), PAD * 2f, NomadUi.hl(), at);
  }

  private static void efficiency(TooltipMakerAPI t, NomadFlotillaView view, NeedsLevel level,
      boolean parts, float width) {
    TickSheet sheet = view.sheet;
    heading(t, NomadStrings.get("nomad_flotilla", "panels_eff_title"), OPAD);
    t.addPara(NomadStrings.get("nomad_overview", "ov_efficiency_tip_what"), PAD * 2f);
    String total = NomadUi.pct(sheet.efficiency);
    para(t, NomadStrings.get("nomad_overview", "ov_efficiency_tip_net", total), PAD * 2f,
        NomadHomeColumn.efficiencyColor(sheet.efficiency), total);
    if (!parts) {
      return;
    }
    List<NomadHomeFigures.EffRow> rows =
        NomadHomeFigures.efficiencyRows(sheet.efficiencySources, level, view.rules);
    t.beginGridFlipped(width, 1, NomadHomeFigures.GRID_VALUE_W,
        NomadHomeFigures.GRID_VALUE_PAD);
    int y = 0;
    for (NomadHomeFigures.EffRow row : rows) {
      t.addToGrid(0, y++, sourceName(view, row), NomadHomeFigures.value(row), sourceColor(row));
    }
    t.addGrid(PAD);
  }

  private static void requirement(TooltipMakerAPI t, NomadFlotillaView view, boolean parts,
      float headPad, float width) {
    Rules k = view.rules;
    heading(t, NomadStrings.get("nomad_flotilla", "needs_req_head"), headPad);
    String cap = NomadHomeFigures.changePercent(k.needsReliefCap);
    if (parts) {
      String one = NomadHomeFigures.changePercent(k.needsTierRelief);
      String item = NomadHomeFigures.changePercent(k.needsTierRelief * k.needsItemTiers);
      t.addPara(NomadStrings.get("nomad_flotilla", "needs_req_what", one, item, cap), PAD * 2f);
    }
    Requirement.Reading r = view.sheet.requirement;
    float change = r == null ? 0f : r.change();
    if (NomadHomeFigures.noChange(change)) {
      t.addPara(NomadStrings.get("nomad_flotilla", "needs_req_total_none"), NomadUi.gray(),
          PAD * 2f);
      return;
    }
    String total = NomadHomeFigures.changePercent(change);
    para(t, NomadStrings.get("nomad_flotilla",
        change < 0f ? "needs_req_total_less" : "needs_req_total_more", total), PAD * 2f,
        change < 0f ? NomadUi.good() : NomadUi.bad(), total);
    if (!parts) {
      return;
    }
    t.beginGridFlipped(width, 1, NomadHomeFigures.GRID_VALUE_W,
        NomadHomeFigures.GRID_VALUE_PAD);
    int y = 0;
    for (NomadHomeFigures.ReqRow row : NomadHomeFigures.requirementRows(r)) {
      t.addToGrid(0, y++, requirementName(view, row, cap),
          NomadHomeFigures.signedPercent(row.value),
          row.value < 0f ? NomadUi.good() : NomadUi.bad());
    }
    t.addGrid(PAD);
  }

  private static String requirementName(NomadFlotillaView view, NomadHomeFigures.ReqRow row,
      String cap) {
    switch (row.kind) {
      case WORKS:
        return NomadStrings.get("nomad_flotilla", "needs_req_row_works",
            Integer.toString(row.count));
      case ITEMS:
        return NomadStrings.get("nomad_flotilla", "needs_req_row_items",
            Integer.toString(row.count));
      case LIMIT:
        return NomadStrings.get("nomad_flotilla", "needs_req_row_limit", cap);
      case AMENITY:
      case RAISE:
      default: {
        WorkDef def = view.catalog.work(row.workId);
        String name = view.workName(row.workId);
        return def != null && def.isSized() ? NomadStrings.get("nomad_flotilla",
            "needs_req_row_sized", name, Integer.toString(row.count)) : name;
      }
    }
  }

  private static String sourceName(NomadFlotillaView view, NomadHomeFigures.EffRow row) {
    switch (row.kind) {
      case LEVEL: {
        NeedsLevel named = NeedsLevel.NORMAL;
        for (NeedsLevel l : NeedsLevel.values()) {
          if (l.key.equals(row.id)) {
            named = l;
          }
        }
        return NomadStrings.get("nomad_flotilla", "ui2_tip_source_needs",
            NomadFlotillaView.levelName(named));
      }
      case DIFFICULTY:
        return NomadStrings.get("nomad_flotilla", "ui2_tip_source_difficulty",
            BasicNeeds.SOURCE_STORY_DIFFICULTY.equals(row.id)
                ? NomadStrings.get("nomad_flotilla", "panels_source_story_difficulty")
                : NomadStrings.get("nomad_flotilla", "panels_source_normal_difficulty"));
      case SHARE:
      default:
        return view.catalog.work(row.id) != null ? view.workName(row.id)
            : NomadItems.displayName(row.id);
    }
  }

  private static Color sourceColor(NomadHomeFigures.EffRow row) {
    if (row.kind == NomadHomeFigures.EffKind.SHARE) {
      return row.value < 0f ? NomadUi.bad() : NomadUi.good();
    }
    return NomadUi.hl();
  }

  private static void everyLevel(TooltipMakerAPI t, BasicNeeds.Reading r, Rules k,
      float schoolGrowth) {
    float[] cols = NomadHomeFigures.EVERY_COLS;
    float width = NomadHomeFigures.TIP_W - 10f;
    CustomPanelAPI head = row(width, null);
    cell(head, cols, 0, NomadStrings.get("nomad_flotilla", "ui2_tip_level_calc_head"),
        NomadUi.gray(), Alignment.LMID);
    cell(head, cols, 1, NomadStrings.get("nomad_flotilla", "panels_space_title"), NomadUi.gray(),
        Alignment.RMID);
    cell(head, cols, 2, NomadStrings.get("nomad_flotilla", "panels_residents_title"),
        NomadUi.gray(), Alignment.RMID);
    cell(head, cols, 3, NomadStrings.get("nomad_flotilla", "panels_eff_title"), NomadUi.gray(),
        Alignment.RMID);
    cell(head, cols, 4, NomadStrings.get("nomad_flotilla", "ui2_tip_col_population"),
        NomadUi.gray(), Alignment.RMID);
    t.addCustom(head, PAD);

    for (NomadHomeFigures.LevelLine l : NomadHomeFigures.everyLevel(r, k, schoolGrowth)) {
      CustomPanelAPI line = row(width, l.current ? new Here() : null);
      Color c = l.current ? NomadUi.hl() : NomadUi.text();
      cell(line, cols, 0, NomadFlotillaView.levelName(l.level),
          l.current ? NomadUi.hl() : NomadHomeColumn.levelColor(l.level), Alignment.LMID);
      cell(line, cols, 1, NomadHomeFigures.whole(l.space), c, Alignment.RMID);
      cell(line, cols, 2, NomadHomeFigures.whole(l.residents), c, Alignment.RMID);
      cell(line, cols, 3, NomadUi.pct(l.efficiency), c, Alignment.RMID);
      cell(line, cols, 4, population(l), c, Alignment.RMID);
      t.addCustom(line, 0f);
    }
  }

  private static String population(NomadHomeFigures.LevelLine l) {
    if (l.leave > 0f) {
      return NomadStrings.get("nomad_flotilla", "ui2_tip_pop_leave", NomadUi.pct(l.leave));
    }
    if (l.grow > 0f) {
      return NomadStrings.get("nomad_flotilla", "ui2_tip_pop_grow", NomadUi.pct(l.grow));
    }
    return NomadStrings.get("nomad_flotilla", "ui2_tip_pop_hold");
  }

  private static void ending(TooltipMakerAPI t, NomadFlotillaView view, BasicNeeds.Reading r,
      NeedsLevel level) {
    Rules k = view.rules;
    float pad = OPAD;
    if (r.next != null) {
      String up = NomadFlotillaView.levelName(r.next);
      int space = NomadHomeFigures.moreRequired(r.haveSpace, r.nextSpace, r.wantSpace);
      if (space > 0) {
        String n = NomadHomeFigures.whole(space);
        para(t, NomadStrings.get("nomad_flotilla", "ui2_tip_up_space", up, n), pad, NomadUi.hl(),
            n);
        pad = PAD;
      }
      int residents = NomadHomeFigures.moreRequired(r.haveResidents, r.nextResidents,
          r.wantResidents);
      if (residents > 0) {
        String n = NomadHomeFigures.whole(residents);
        para(t, NomadStrings.get("nomad_flotilla", "ui2_tip_up_residents", up, n), pad,
            NomadUi.hl(), n);
        pad = PAD;
      }
    }
    NeedsLevel down = level.below();
    if (down != null) {
      float p = k.provision(level);
      String name = NomadFlotillaView.levelName(down);
      String space = NomadHomeFigures.whole(p * r.wantSpace);
      String residents = NomadHomeFigures.whole(p * r.wantResidents);
      LabelAPI l = para(t, NomadStrings.get("nomad_flotilla", "needs_tip_falls", name, space,
          residents), pad, NomadUi.hl(), name, space, residents);
      l.setHighlightColors(NomadHomeColumn.levelColor(down), NomadUi.hl(), NomadUi.hl());
      pad = PAD;
    }
    if (view.state.normalOrStory) {
      String difficulty = view.state.storyDifficulty
          ? NomadStrings.get("nomad_flotilla", "panels_source_story_difficulty")
          : NomadStrings.get("nomad_flotilla", "panels_source_normal_difficulty");
      String less = NomadUi.pct(1f - k.wantMult(view.state.normalOrStory,
          view.state.storyDifficulty));
      para(t, NomadStrings.get("nomad_flotilla", "needs_tip_difficulty", difficulty, less), pad,
          NomadUi.hl(), difficulty, less);
    }
  }

  public static TooltipMakerAPI.TooltipCreator space(final NomadFlotillaView view) {
    return NomadTip.guard(new NomadTip(NomadHomeFigures.SIDE_TIP_W) {
      @Override
      public void createTooltip(TooltipMakerAPI tip, boolean expanded, Object param) {
        writeSpace(tip, view);
      }
    });
  }

  private static void writeSpace(TooltipMakerAPI t, final NomadFlotillaView view) {
    TickSheet s = view.standing;
    NomadHomeFigures.SpaceParts parts =
        NomadHomeFigures.spaceParts(s.space, s.spaceWorks, s.spaceStores);
    t.addTitle(NomadStrings.get("nomad_flotilla", "panels_space_title"), NomadUi.base());
    t.addPara(NomadStrings.get("nomad_flotilla", "top_space_tip"), OPAD);

    String total = NomadHomeFigures.whole(parts.total);
    para(t, NomadStrings.get("nomad_flotilla", "panels_space_total", total), OPAD, NomadUi.hl(),
        total);
    float founding = s.s.foundingSpace;
    if (founding >= 0.5f) {
      String ships = NomadHomeFigures.whole(
          nomad.campaign.econ.model.Capacity.flotillaSpace(s.s.space, s.s.items, s.k));
      String extra = NomadHomeFigures.whole(founding);
      para(t, NomadStrings.get("nomad_flotilla", "panels_space_allowance", ships, extra), PAD,
          NomadUi.hl(), ships, extra);
    }
    t.beginGridFlipped(NomadHomeFigures.SIDE_TIP_W, 1, NomadHomeFigures.GRID_VALUE_W,
        NomadHomeFigures.GRID_VALUE_PAD);
    t.addToGrid(0, 0, NomadStrings.get("nomad_flotilla", "ui2_tip_row_works"),
        NomadHomeFigures.whole(parts.works), NomadHomeColumn.usedColor());
    t.addToGrid(0, 1, NomadStrings.get("nomad_flotilla", "panels_stores_title"),
        NomadHomeFigures.whole(parts.stores), NomadHomeColumn.usedColor());
    t.addToGrid(0, 2, NomadStrings.get("nomad_flotilla", "panels_needs_title"),
        NomadHomeFigures.whole(parts.needs), NomadHomeColumn.needsColor(view));
    t.addGrid(PAD);

    List<TickSheet.WorkRun> runs = new ArrayList<TickSheet.WorkRun>();
    for (TickSheet.WorkRun run : s.runs.values()) {
      if (run.space >= 0.5f) {
        runs.add(run);
      }
    }
    if (!runs.isEmpty()) {
      Collections.sort(runs, new Comparator<TickSheet.WorkRun>() {
        @Override
        public int compare(TickSheet.WorkRun a, TickSheet.WorkRun b) {
          return Float.compare(b.space, a.space);
        }
      });
      heading(t, NomadStrings.get("nomad_flotilla", "ui2_tip_works_head",
          NomadHomeFigures.whole(parts.works)), OPAD);
      t.beginGridFlipped(NomadHomeFigures.SIDE_TIP_W, 1, NomadHomeFigures.GRID_VALUE_W,
          NomadHomeFigures.GRID_VALUE_PAD);
      int y = 0;
      for (TickSheet.WorkRun run : runs) {
        t.addToGrid(0, y++, view.workName(run.id), NomadHomeFigures.whole(run.space),
            NomadUi.hl());
      }
      t.addGrid(PAD);
    }

    BasicNeeds.Reading r = view.sheet.needs;
    if (!view.paused && NomadHomeFigures.spaceShort(r)) {
      String n = NomadHomeFigures.whole(
          NomadHomeFigures.moreRequired(r.haveSpace, r.nextSpace, r.wantSpace));
      para(t, NomadStrings.get("nomad_flotilla", "ui2_tip_up_space",
          NomadFlotillaView.levelName(r.next), n), OPAD, NomadUi.hl(), n);
    }
    if (parts.over) {
      t.addPara(NomadStrings.get("nomad_flotilla", "top_space_tip_over"), NomadUi.warn(), OPAD);
    }
  }

  private static void heading(TooltipMakerAPI t, String text, float pad) {
    t.addSectionHeading(text, NomadUi.base(), NomadUi.dark(), Alignment.MID, pad);
  }

  private static LabelAPI para(TooltipMakerAPI t, String text, float pad, Color hl,
      String... values) {
    LabelAPI l = t.addPara(text, NomadUi.text(), pad);
    l.setHighlight(values);
    l.setHighlightColor(hl);
    return l;
  }

  private static CustomPanelAPI row(float width, BaseCustomUIPanelPlugin plugin) {
    return Global.getSettings().createCustom(width, ROW_H, plugin);
  }

  private static void cell(CustomPanelAPI line, float[] cols, int i, String text, Color c,
      Alignment align) {
    NomadCell.text(line, NomadHomeFigures.colX(cols, i), 0f, cols[i], ROW_H, text, c, align, 1f);
  }

  private static final class Here extends BaseCustomUIPanelPlugin {

    private PositionAPI pos;

    @Override
    public void positionChanged(PositionAPI position) {
      this.pos = position;
    }

    @Override
    public void renderBelow(float alphaMult) {
      if (pos != null) {
        NomadGl.fillRect(pos.getX(), pos.getY(), pos.getWidth(), pos.getHeight(), NomadUi.dark(),
            HERE_ALPHA, alphaMult);
      }
    }
  }
}
