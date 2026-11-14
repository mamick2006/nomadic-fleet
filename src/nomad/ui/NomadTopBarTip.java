package nomad.ui;

import java.awt.Color;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import nomad.NomadStrings;
import nomad.campaign.econ.model.BasicNeeds;
import nomad.campaign.econ.model.Capacity;
import nomad.campaign.econ.model.MiningSystem;
import nomad.campaign.econ.model.NeedsLevel;
import nomad.campaign.econ.model.Residents;
import nomad.campaign.econ.model.Rules;
import nomad.campaign.econ.model.TickSheet;
import nomad.campaign.econ.model.WorkState;
import nomad.campaign.econ.model.Works;
import nomad.campaign.split.NomadSplitter;

public final class NomadTopBarTip {

  private NomadTopBarTip() {
  }

  public static final float WIDTH = 380f;

  private static final float INNER = WIDTH - 2f * NomadText.INSET;
  private static final float PAD = 3f;
  private static final float OPAD = 10f;
  private static final float GROUP = 9f;
  private static final float UNDER_HEAD = 7f;
  private static final float PICTURE_H = 60f;
  private static final float BAR_Y = NomadHomeFigures.LINE_H + 4f;
  private static final float PARTS_Y = BAR_Y + NomadHomeFigures.BAR_H + 3f;
  private static final float BLOCK_H = PARTS_Y + NomadHomeFigures.LINE_H;
  private static final float RATE_W = 44f;

  public static Color levelColor(NeedsLevel level) {
    return NomadHomeColumn.levelColor(level == null ? NeedsLevel.NORMAL : level);
  }

  public static void build(TooltipMakerAPI tip, NomadEconView v) {
    if (v == null || v.sheet == null) {
      return;
    }
    TickSheet t = v.sheet;
    if (v.status == NomadEconView.Status.PAUSED) {
      tip.addPara(NomadStrings.get("nomad_overview", "ov_status_paused",
          NomadUi.pct(t.shared.pausedDecay)), NomadUi.bad(), 0f);
      return;
    }
    boolean detached = v.status == NomadEconView.Status.DETACHED;
    if (detached) {
      String where = NomadSplitter.detachedSummary();
      tip.addPara(NomadStrings.get("nomad_overview", "ov_status_detached",
          where.isEmpty() ? "" : " " + where), NomadUi.warn(), 0f);
    }
    needs(tip, v, t, detached);
    attention(tip, t);
    flotilla(tip, t);
    supplies(tip, v);
    mining(tip, v, t);
  }

  private static void needs(TooltipMakerAPI tip, NomadEconView v, TickSheet t, boolean detached) {
    NeedsLevel level = v.level == null ? NeedsLevel.NORMAL : v.level;
    String title = NomadStrings.get("nomad_flotilla", "ui2_tip_needs_title",
        NomadFlotillaView.levelName(level));
    if (detached) {
      tip.setParaFontOrbitron();
      tip.addPara(title, levelColor(level), GROUP);
      tip.setParaFontDefault();
    } else {
      tip.addTitle(title, levelColor(level));
    }

    String picture = picture(level);
    TooltipMakerAPI text = picture == null ? tip : tip.beginImageWithText(picture, PICTURE_H);
    String eff = NomadUi.pct(v.efficiency);
    line(text, NomadStrings.get("nomad_flotilla", "needs_effect_efficiency", eff), NomadUi.text(),
        picture == null ? OPAD : 0f, NomadUi.hl(), eff);
    if (!detached) {
      Rules k = t.k;
      float school = Residents.schoolGrowth(t);
      float change = NomadPanelFigures.residentsPerMonth(t.residents, t.residentRoom, level, k,
          school);
      if (change < -0.05f) {
        text.addPara(NomadStrings.get("nomad_flotilla", "needs_effect_leave",
            NomadUi.pct(k.leavePerMonth(level))), NomadUi.bad(), PAD * 2f);
      } else if (change > 0.05f) {
        String rate = NomadUi.pct(Residents.growthPerYear(k, level, school));
        line(text, NomadStrings.get("nomad_flotilla", "needs_effect_grow", rate), NomadUi.text(),
            PAD * 2f, NomadUi.good(), rate);
      } else {
        text.addPara(NomadStrings.get("nomad_flotilla", "panels_crew_holding"), NomadUi.text(),
            PAD * 2f);
      }
    }
    if (picture != null) {
      tip.addImageWithText(OPAD);
    }
  }

  private static String picture(NeedsLevel level) {
    try {
      String path = Global.getSettings().getSpriteName("illustrations",
          NomadNeedsPicture.key(level));
      return path == null || path.isEmpty() ? null : path;
    } catch (RuntimeException e) {
      return null;
    }
  }

  private static void attention(TooltipMakerAPI tip, TickSheet t) {
    List<NomadPanelFigures.Attention> list = NomadPanelFigures.attention(t);
    if (list.isEmpty()) {
      return;
    }
    tip.addSectionHeading(NomadStrings.get("nomad_flotilla", "ui2_works_to_check",
        Integer.toString(list.size())), NomadUi.warn(), NomadUi.dark(), Alignment.MID, OPAD);
    String levelName = levelName(t);
    float box = INNER - NomadHomeFigures.MARK_STEP;
    float pad = UNDER_HEAD;
    for (NomadPanelFigures.Attention a : list) {
      String name = NomadFlotillaView.workName(t.catalog, a.workId);
      String text = NomadStrings.get("nomad_topbar", "topbar_tip_work", name,
          stateText(t, a, levelName));
      float h = Math.max(NomadHomeFigures.MARK,
          NomadText.lines(text, box, NomadUi.PARA) * NomadHomeFigures.LINE_H);
      CustomPanelAPI row = Global.getSettings().createCustom(INNER, h, null);
      NomadMark.add(row, 0f, 0f, NomadHomeFigures.MARK, NomadUi.warn());
      LabelAPI l = NomadCell.box(row, NomadHomeFigures.MARK_STEP, 0f, box, h, text,
          NomadUi.text(), Alignment.TL, 0f);
      l.setHighlight(name);
      l.setHighlightColor(NomadUi.hl());
      tip.addCustom(row, pad);
      pad = PAD;
    }
  }

  public static TooltipMakerAPI.TooltipCreator checkTip(final NomadFlotillaView view) {
    return NomadTip.guard(new NomadTip(NomadHomeFigures.SIDE_TIP_W) {
      @Override
      public void createTooltip(TooltipMakerAPI tip, boolean expanded, Object param) {
        List<NomadPanelFigures.Attention> list = NomadPanelFigures.attention(view.sheet);
        tip.addTitle(NomadStrings.get("nomad_flotilla", "ui2_tip_check_title"),
            !view.live || list.isEmpty() ? NomadUi.gray() : NomadUi.warn());
        if (!view.live) {
          tip.addPara(view.notLiveReason(), NomadUi.warn(), GROUP);
          return;
        }
        works(tip, view.sheet, list, GROUP);
      }
    });
  }

  public static void hangCheckTip(TooltipMakerAPI el, NomadFlotillaView view) {
    if (view.live && NomadPanelFigures.attention(view.sheet).isEmpty()) {
      return;
    }
    el.addTooltipToPrevious(checkTip(view), TooltipMakerAPI.TooltipLocation.BELOW, false);
  }

  private static void works(TooltipMakerAPI tip, TickSheet t,
      List<NomadPanelFigures.Attention> list, float firstPad) {
    float pad = firstPad;
    String levelName = levelName(t);
    for (NomadPanelFigures.Attention a : list) {
      String name = NomadFlotillaView.workName(t.catalog, a.workId);
      line(tip, NomadStrings.get("nomad_topbar", "topbar_tip_work", name,
          stateText(t, a, levelName)), NomadUi.text(), pad, NomadUi.hl(), name);
      pad = PAD;
    }
  }

  private static String levelName(TickSheet t) {
    return NomadFlotillaView.levelName(t.needs.level == null ? NeedsLevel.NORMAL : t.needs.level);
  }

  private static String stateText(TickSheet t, NomadPanelFigures.Attention a, String levelName) {
    WorkState w = t.work(a.workId);
    NomadWorkCardModel.Why why = a.state.why;
    if (a.state.shown == NomadWorkCardModel.Shown.SUSPENDED && w != null) {
      boolean returns = Capacity.wouldReturn(t.s, w.id, t.catalog, t.k, t.shared);
      boolean holdFull = !returns
          && Capacity.heldOutByHold(t.s, w.id, t.catalog, t.k, t.shared);
      why = NomadWorkCardModel.suspendedWhy(returns, holdFull, Capacity.spaceHeld(w, t.catalog), t.spaceNeeds());
    }
    return NomadWorkCardModel.stateText(a.state.shown, why,
        NomadFlotillaView.reasonName(a.state, Float.MAX_VALUE), levelName, 0,
        NomadFlotillaView.FLOTILLA);
  }

  private static final class Words {

    final String text;
    final Color base;
    final String number;
    final Color numberColor;

    Words(String text, Color base, String number, Color numberColor) {
      this.text = text;
      this.base = base;
      this.number = number;
      this.numberColor = numberColor;
    }
  }

  private static void flotilla(TooltipMakerAPI tip, TickSheet t) {
    tip.addSectionHeading(NomadStrings.get("nomad_flotilla", "ui4_bar_flotilla"), NomadUi.base(),
        NomadUi.dark(), Alignment.MID, OPAD);
    BasicNeeds.Reading r = t.needs;
    Color used = NomadHomeColumn.usedColor();
    Color needs = NomadHomeColumn.levelColor(NomadHomeColumn.level(r));

    NomadPanelFigures.ResidentParts res = NomadPanelFigures.residentParts(t.residents,
        t.working, t.berths);
    int over = NomadPanelFigures.overBerths(t.residents, t.berths);
    boolean resShort = NomadHomeFigures.residentsShort(r);
    String count = NomadPanelFigures.heads(res.residents);
    String berths = NomadPanelFigures.heads(t.berths);
    Words left;
    Words right;
    if (over > 0) {
      left = new Words(NomadStrings.get("nomad_flotilla", "ui3_over_berths",
          NomadPanelFigures.heads(over)), NomadUi.warn(), null, null);
      right = null;
    } else {
      String working = NomadPanelFigures.heads(res.working);
      left = new Words(NomadStrings.get("nomad_flotilla", "top_residents_working", working),
          NomadUi.text(), working, used);
      if (resShort) {
        int[] shown = NomadHomeFigures.hasAndRequires(r.haveResidents, r.nextResidents,
            r.wantResidents);
        right = new Words(NomadStrings.get("nomad_flotilla", "ui3_bar_short",
            NomadPanelFigures.heads(shown[0]), NomadFlotillaView.levelName(r.next),
            NomadPanelFigures.heads(shown[1])), NomadUi.warn(), null, null);
      } else {
        String inNeeds = NomadPanelFigures.heads(res.needs);
        right = new Words(NomadStrings.get("nomad_flotilla", "top_residents_needs", inNeeds),
            NomadUi.text(), inNeeds, needs);
      }
    }
    bar(tip, UNDER_HEAD, NomadStrings.get("nomad_flotilla", "panels_residents_title"),
        over > 0 || resShort,
        new Words(NomadStrings.get("nomad_flotilla", "ui2_residents_value", count, berths),
            NomadUi.text(), null, NomadUi.hl()),
        new String[] { count, berths },
        NomadHomeFigures.barParts(INNER, res.workingShare, res.needsShare), used, needs, left,
        right);

    NomadHomeFigures.SpaceParts space = NomadHomeFigures.spaceParts(t.space, t.spaceWorks,
        t.spaceStores);
    boolean spaceShort = NomadHomeFigures.spaceShort(r);
    String total = NomadHomeFigures.whole(space.total);
    String usedN = NomadHomeFigures.whole(space.used);
    left = new Words(NomadStrings.get("nomad_flotilla", "ui3_space_used", usedN), NomadUi.text(),
        usedN, space.over ? NomadUi.warn() : used);
    if (spaceShort) {
      int[] shown = NomadHomeFigures.hasAndRequires(r.haveSpace, r.nextSpace, r.wantSpace);
      right = new Words(NomadStrings.get("nomad_flotilla", "ui3_bar_short",
          NomadHomeFigures.whole(shown[0]), NomadFlotillaView.levelName(r.next),
          NomadHomeFigures.whole(shown[1])), NomadUi.warn(), null, null);
    } else {
      String needsN = NomadHomeFigures.whole(space.needs);
      right = new Words(NomadStrings.get("nomad_flotilla", "top_space_needs", needsN),
          NomadUi.text(), needsN, needs);
    }
    bar(tip, GROUP, NomadStrings.get("nomad_flotilla", "panels_space_title"), spaceShort,
        new Words(total, NomadUi.hl(), null, null), null,
        NomadHomeFigures.barParts(INNER, space.usedShare, 1f - space.usedShare), used, needs,
        left, right);
  }

  private static void bar(TooltipMakerAPI tip, float pad, String caption, boolean mark,
      Words whole, String[] wholeNumbers, float[] px, Color first, Color second, Words left,
      Words right) {
    CustomPanelAPI block = Global.getSettings().createCustom(INNER, BLOCK_H, null);
    float captionX = 0f;
    if (mark) {
      NomadMark.add(block, 0f, -1f, NomadHomeFigures.MARK, NomadUi.warn());
      captionX = NomadHomeFigures.MARK_STEP;
    }
    NomadCell.box(block, captionX, 0f, NomadText.need(caption, NomadUi.PARA),
        NomadHomeFigures.LINE_H, caption, NomadUi.text(), Alignment.LMID, 0f);
    float wholeW = NomadText.need(whole.text, NomadUi.PARA);
    LabelAPI w = NomadCell.box(block, INNER - wholeW, 0f, wholeW, NomadHomeFigures.LINE_H,
        whole.text, whole.base, Alignment.RMID, 0f);
    if (wholeNumbers != null) {
      w.setHighlight(wholeNumbers);
      w.setHighlightColor(whole.numberColor);
    }

    block.addComponent(block.createCustomPanel(INNER, NomadHomeFigures.BAR_H,
        new NomadHomeColumn.Bar(px, first, second))).inTL(0f, BAR_Y);

    float rightW = 0f;
    if (right != null) {
      rightW = Math.min(INNER, NomadText.need(right.text, NomadUi.PARA));
      words(block, INNER - rightW, rightW, right, Alignment.RMID);
    }
    if (left != null) {
      float leftW = Math.min(INNER, NomadText.need(left.text, NomadUi.PARA));
      if (right == null || NomadHomeFigures.partsBothFit(leftW, rightW, INNER)) {
        words(block, 0f, leftW, left, Alignment.LMID);
      }
    }
    tip.addCustom(block, pad);
  }

  private static void words(CustomPanelAPI block, float x, float w, Words words,
      Alignment align) {
    LabelAPI l = NomadCell.box(block, x, PARTS_Y, w, NomadHomeFigures.LINE_H,
        NomadText.fit(words.text, w, NomadUi.PARA), words.base, align, 0f);
    if (words.number != null) {
      l.setHighlight(words.number);
      l.setHighlightColor(words.numberColor);
    }
  }

  private static void supplies(TooltipMakerAPI tip, NomadEconView v) {
    if (v.suppliesCover < 0f) {
      return;
    }
    tip.addSectionHeading(NomadStrings.get("nomad_flotilla", "ui4_bar_supplies"), NomadUi.base(),
        NomadUi.dark(), Alignment.MID, OPAD);
    String pct = NomadPanelFigures.coverPercent(v.suppliesCover) + "%";
    line(tip, NomadStrings.get("nomad_flotilla", "ui4_bar_cover", pct), NomadUi.text(),
        UNDER_HEAD, NomadUi.hl(), pct);
    float spare = Math.max(0f, v.suppliesMade - v.suppliesUsed);
    String made = (spare > 0f ? "+" : "") + NomadWorkHoverRows.amount(spare);
    String fleet = NomadWorkHoverRows.amount(v.fleetSupplies);
    tip.beginGridFlipped(INNER, 1, NomadUi.text(), RATE_W, OPAD);
    tip.addToGrid(0, 0, NomadStrings.get("nomad_flotilla", "ui4_bar_made"), made, NomadUi.hl());
    tip.addToGrid(0, 1, NomadStrings.get("nomad_flotilla", "ui4_bar_consumed"), fleet,
        NomadUi.hl());
    tip.addGrid(PAD * 2f);
  }

  private static void mining(TooltipMakerAPI tip, NomadEconView v, TickSheet t) {
    WorkState rig = t.work(Works.MINING_RIG);
    if (rig == null || v.miningSpace <= 0f) {
      return;
    }
    if (v.mining != null && v.mining.active) {
      String body = v.mining.bodyName == null ? "" : v.mining.bodyName;
      tip.addSectionHeading(NomadStrings.get("nomad_flotilla", "ui4_bar_mining", body),
          NomadUi.base(), NomadUi.dark(), Alignment.MID, OPAD);
      if (v.mining.tiers.isEmpty()) {
        return;
      }
      tip.beginGridFlipped(INNER, 1, NomadUi.text(), RATE_W, OPAD);
      int at = 0;
      for (Map.Entry<String, Float> e : v.mining.tiers.entrySet()) {
        float mult = e.getValue().floatValue();
        tip.addToGrid(0, at++,
            NomadUi.commodityName(e.getKey()) + ", " + NomadMining.tierLabel(mult),
            NomadWorkCardModel.times(mult), NomadMining.tierColor(mult));
      }
      tip.addGrid(UNDER_HEAD);
      return;
    }
    MiningSystem.Readout r = v.miningReadout;
    if (r == null || r.kind == MiningSystem.Kind.IN_RANGE) {
      return;
    }
    tip.addSectionHeading(NomadFlotillaView.workName(t.catalog, Works.MINING_RIG), NomadUi.base(),
        NomadUi.dark(), Alignment.MID, OPAD);
    NomadMining.addReadout(tip, r, rig, t.k, UNDER_HEAD);
  }

  private static void line(TooltipMakerAPI tip, String text, Color base, float pad, Color hl,
      String... values) {
    LabelAPI l = tip.addPara(text, base, pad);
    l.setHighlight(values);
    l.setHighlightColor(hl);
  }
}
