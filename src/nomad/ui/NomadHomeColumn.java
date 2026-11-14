package nomad.ui;

import static nomad.ui.NomadHero.label;

import java.awt.Color;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.Fonts;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import nomad.NomadStrings;
import nomad.campaign.econ.model.BasicNeeds;
import nomad.campaign.econ.model.NeedsLevel;
import nomad.campaign.econ.model.Residents;
import nomad.campaign.econ.model.Rules;
import nomad.campaign.econ.model.TickSheet;
import nomad.campaign.split.NomadSplitter;
import nomad.ui.NomadHero.Face;

public final class NomadHomeColumn {

  private NomadHomeColumn() {
  }

  static final String FALLBACK_PICTURE = "nomad_work_illus_basic_needs";

  private static final Color DIM = new Color(95, 95, 95);
  private static final int TRACK_ALPHA = 90;

  static final NomadText.Measure VALUE = new NomadText.Measure() {
    @Override
    public float width(String s) {
      return s == null || s.isEmpty() ? 0f
          : Global.getSettings().computeStringWidth(s, Fonts.INSIGNIA_VERY_LARGE);
    }
  };

  private static final NomadWorkCardModel.Strings SPLIT = new NomadWorkCardModel.Strings() {
    @Override
    public String get(String key, Object... args) {
      return NomadStrings.get("nomad_split", key, args);
    }
  };

  public static float build(CustomPanelAPI panel, float w, float h, NomadFlotillaView view) {
    float heroH = NomadGrid.Screen.heroH(w, h, NomadPanelFigures.listed(view.standing).size());
    NomadGrid.Box crewAt = NomadHomeFigures.layout(w, h, heroH, 0f).crew;
    float crewUsed = NomadCrewPanel.build(sub(panel, crewAt), w, view);
    NomadHomeFigures.Layout at = NomadHomeFigures.layout(w, h, heroH, crewUsed);

    hero(panel, at, view);
    lines(panel, at, view);
    hover(panel, at.heroHover, NomadNeedsTip.hero(view));
    space(panel, at, view);

    float storesUsed = NomadStoresPanel.build(sub(panel, at.stores), w, at.stores.h, view);
    return at.stores.y + storesUsed;
  }

  private static CustomPanelAPI sub(CustomPanelAPI panel, NomadGrid.Box box) {
    CustomPanelAPI p = panel.createCustomPanel(box.w, box.h, NomadUi.consoleRow());
    panel.addComponent(p).inTL(box.x, box.y);
    return p;
  }

  private static void hero(CustomPanelAPI panel, NomadHomeFigures.Layout at,
      NomadFlotillaView view) {
    boolean paused = view.paused;
    NeedsLevel level = paused ? view.state.priorLevel : level(view.sheet.needs);
    String caption = NomadStrings.get("nomad_flotilla", "panels_needs_title");
    float captionW = NomadText.need(caption, NomadUi.BUTTON);

    Hero chrome = new Hero(at, captionW);
    chrome.picture = NomadHero.sprite(pictureKey(level), at.hero.w / at.hero.h);
    chrome.dim = paused || level == NeedsLevel.NON_EXISTENT;
    chrome.pips = NomadHomeFigures.pipsDrawn(paused);
    chrome.lit = NomadHomeFigures.pipsLit(level, paused);
    chrome.pipColor = paused || level == null ? NomadUi.gray() : levelColor(level);
    CustomPanelAPI back = panel.createCustomPanel(at.hero.w, at.hero.h, chrome);
    panel.addComponent(back).inTL(at.hero.x, at.hero.y);

    float left = NomadHomeFigures.HERO_INSET;
    label(panel, left, at.captionY(), captionW, NomadHomeFigures.LINE_H, caption, NomadUi.base(),
        Alignment.LMID, Face.CAPTION);

    float rightW = 0f;
    if (!paused) {
      String effCaption = NomadStrings.get("nomad_flotilla", "panels_eff_title");
      String eff = NomadUi.pct(view.sheet.efficiency);
      float effCaptionW = NomadText.need(effCaption, NomadUi.BUTTON);
      float effW = NomadText.need(eff, VALUE);
      rightW = Math.max(effCaptionW, effW);
      label(panel, at.textRight() - effCaptionW, at.captionY(), effCaptionW,
          NomadHomeFigures.LINE_H, effCaption, NomadUi.base(), Alignment.RMID, Face.CAPTION);
      label(panel, at.textRight() - effW, at.valueY(), effW, NomadHomeFigures.VALUE_H, eff,
          efficiencyColor(view.sheet.efficiency), Alignment.RMID, Face.VALUE);
    }

    String name = paused
        ? NomadWorkCardModel.shownName(NomadWorkCardModel.Shown.PAUSED, NomadFlotillaView.FLOTILLA)
        : NomadFlotillaView.levelName(level);
    float nameBox = at.leftBox(rightW);
    label(panel, left, at.valueY(), nameBox, NomadHomeFigures.VALUE_H,
        NomadText.fit(name, nameBox, VALUE), paused ? NomadUi.bad() : levelColor(level),
        Alignment.LMID, Face.VALUE);
  }

  public static String pictureKey(NeedsLevel level) {
    if (level == null) {
      return FALLBACK_PICTURE;
    }
    NeedsLevel shown = level == NeedsLevel.NON_EXISTENT ? NeedsLevel.DECREPIT : level;
    String key = "nomad_needs_illus_" + shown.key;
    try {
      String path = Global.getSettings().getSpriteName("illustrations", key);
      return path == null || path.isEmpty() ? FALLBACK_PICTURE : key;
    } catch (Exception e) {
      return FALLBACK_PICTURE;
    }
  }

  private static final class Hero extends BaseCustomUIPanelPlugin {

    private final NomadHomeFigures.Layout at;
    private final float captionW;
    private final Color edge = NomadUi.dark();
    private final Color pipEdge = mix(NomadUi.dark(), NomadUi.base(), 0.25f);
    private final Color pipGround = mix(Color.BLACK, NomadUi.dark(), 0.3f);
    SpriteAPI picture;
    boolean dim;
    int pips;
    int lit;
    Color pipColor;
    private PositionAPI pos;

    Hero(NomadHomeFigures.Layout at, float captionW) {
      this.at = at;
      this.captionW = captionW;
    }

    @Override
    public void positionChanged(PositionAPI position) {
      this.pos = position;
    }

    @Override
    public void render(float alphaMult) {
      if (pos == null) {
        return;
      }
      float x = pos.getX();
      float y = pos.getY();
      float w = at.hero.w;
      float h = at.hero.h;
      NomadHero.picture(x, y, w, h, picture, dim ? DIM : Color.WHITE, alphaMult);
      NomadHero.band(x, y, w, alphaMult);
      for (int i = 0; i < pips; i++) {
        NomadGrid.Box p = at.pip(i, captionW);
        float px = x + p.x;
        float py = y + h - p.bottom();
        if (i < lit) {
          NomadGl.fillRect(px, py, p.w, p.h, pipColor, 255, alphaMult);
        } else {
          NomadGl.box(px, py, p.w, p.h, pipGround, 255, pipEdge, 255, alphaMult);
        }
      }
      NomadHero.border(x, y, w, h, edge, alphaMult);
    }
  }

  private static void lines(CustomPanelAPI panel, NomadHomeFigures.Layout at,
      NomadFlotillaView view) {
    float w = at.w;
    if (view.paused) {
      String rate = NomadUi.pct(view.shared.pausedDecay);
      line(panel, at.line1, NomadStrings.get("nomad_flotilla", "needs_effect_none_run") + " "
          + NomadStrings.get("nomad_flotilla", "needs_effect_leave", rate), NomadUi.bad(), null);
      line(panel, at.line2, NomadStrings.get("nomad_flotilla", "ui2_needs_paused_cure"),
          NomadUi.text(), null);
      return;
    }
    if (view.state.detached) {
      String detached = NomadStrings.get("nomad_flotilla", "screen_not_live_detached");
      float owed = view.sheet.suppliesOwed;
      NomadSplitter.DetachedWhere where = owed >= 0.5f ? null : NomadSplitter.detachedWhere();
      if (where == null) {
        line(panel, at.line1, detached, NomadUi.warn(), null);
        if (owed >= 0.5f) {
          line(panel, at.line2, NomadStrings.get("nomad_split", "detach_summary_owed",
              Misc.getWithDGS(Math.round(owed))), NomadUi.warn(), null);
        }
        return;
      }
      String[] two = NomadHomeFigures.detachedLines(detached, where.system, where.ships, w,
          NomadUi.PARA, SPLIT);
      if (two[0].equals(detached)) {
        line(panel, at.line1, detached, NomadUi.warn(), null);
      } else {
        LabelAPI l = line(panel, at.line1, two[0], NomadUi.text(), null);
        l.setHighlight(detached);
        l.setHighlightColor(NomadUi.warn());
      }
      line(panel, at.line2, two[1], NomadUi.text(), null);
      return;
    }

    TickSheet t = view.sheet;
    Rules k = view.rules;
    NeedsLevel level = level(t.needs);
    float school = Residents.schoolGrowth(t);
    float change = NomadPanelFigures.residentsPerMonth(t.residents, t.residentRoom, level, k,
        school);
    boolean leaving = change < -0.05f;
    boolean growing = change > 0.05f;
    String rate = leaving ? NomadUi.pct(k.leavePerMonth(level))
        : growing ? NomadUi.pct(Residents.growthPerYear(k, level, school)) : null;
    String effect = leaving ? NomadStrings.get("nomad_flotilla", "needs_effect_leave", rate)
        : growing ? NomadStrings.get("nomad_flotilla", "needs_effect_grow", rate)
            : NomadStrings.get("nomad_flotilla", "panels_crew_holding");
    if (level.atLeastNormal()) {
      LabelAPI l = line(panel, at.line1, effect, leaving ? NomadUi.bad() : NomadUi.text(), null);
      if (growing) {
        l.setHighlight(rate);
        l.setHighlightColor(NomadUi.good());
      }
      requirementLine(panel, at, t);
      return;
    }
    Color base = leaving ? NomadUi.bad() : NomadUi.warn();
    if (level.atLeastSpartan()) {
      line(panel, at.line1, effect, base, null);
      requirementLine(panel, at, t);
      return;
    }
    String off = NomadStrings.get("nomad_flotilla", "needs_effect_support_off");
    String both = effect + " " + off;
    if (NomadText.fits(both, w, NomadUi.PARA)) {
      line(panel, at.line1, both, base, null);
      requirementLine(panel, at, t);
    } else {
      line(panel, at.line1, effect, base, null);
      line(panel, at.line2, off, NomadUi.warn(), null);
    }
  }

  private static void requirementLine(CustomPanelAPI panel, NomadHomeFigures.Layout at,
      TickSheet t) {
    float change = t.requirement == null ? 0f : t.requirement.change();
    if (NomadHomeFigures.noChange(change)) {
      return;
    }
    String pct = NomadHomeFigures.changePercent(change);
    LabelAPI l = line(panel, at.line2, NomadStrings.get("nomad_flotilla",
        change < 0f ? "needs_req_card_less" : "needs_req_card_more", pct), NomadUi.text(), null);
    l.setHighlight(pct);
    l.setHighlightColor(change < 0f ? NomadUi.good() : NomadUi.bad());
  }

  private static LabelAPI line(CustomPanelAPI panel, NomadGrid.Box box, String text, Color c,
      Alignment align) {
    return label(panel, box.x, box.y, box.w, box.h, NomadText.fit(text, box.w, NomadUi.PARA), c,
        align == null ? Alignment.LMID : align, Face.PARA);
  }

  private static void space(CustomPanelAPI panel, NomadHomeFigures.Layout at,
      NomadFlotillaView view) {
    TickSheet s = view.standing;
    NomadHomeFigures.SpaceParts parts =
        NomadHomeFigures.spaceParts(s.space, s.spaceWorks, s.spaceStores);
    BasicNeeds.Reading r = view.sheet.needs;
    boolean isShort = !view.paused && NomadHomeFigures.spaceShort(r);
    Color used = usedColor();
    Color needs = needsColor(view);

    float captionX = 0f;
    if (isShort) {
      NomadMark.add(panel, 0f, at.spaceCaption.y - 1f, NomadHomeFigures.MARK, NomadUi.warn());
      captionX = NomadHomeFigures.MARK_STEP;
    }
    String caption = NomadStrings.get("nomad_flotilla", "panels_space_title");
    label(panel, captionX, at.spaceCaption.y, NomadText.need(caption, NomadUi.PARA),
        at.spaceCaption.h, caption, NomadUi.text(), Alignment.LMID, Face.PARA);
    String total = NomadHomeFigures.whole(parts.total);
    float totalW = NomadText.need(total, NomadUi.PARA);
    label(panel, at.w - totalW, at.spaceCaption.y, totalW, at.spaceCaption.h, total,
        NomadUi.hl(), Alignment.RMID, Face.PARA);

    float[] px = NomadHomeFigures.barParts(at.spaceBar.w, parts.usedShare, 1f - parts.usedShare);
    CustomPanelAPI bar = panel.createCustomPanel(at.spaceBar.w, at.spaceBar.h,
        new Bar(px, used, needs));
    panel.addComponent(bar).inTL(at.spaceBar.x, at.spaceBar.y);

    String usedN = NomadHomeFigures.whole(parts.used);
    String left = NomadStrings.get("nomad_flotilla", "ui3_space_used", usedN);
    String right;
    String rightN = null;
    if (isShort) {
      int[] shown = NomadHomeFigures.hasAndRequires(r.haveSpace, r.nextSpace, r.wantSpace);
      right = NomadStrings.get("nomad_flotilla", "ui3_bar_short",
          NomadHomeFigures.whole(shown[0]), NomadFlotillaView.levelName(r.next),
          NomadHomeFigures.whole(shown[1]));
    } else {
      rightN = NomadHomeFigures.whole(parts.needs);
      right = NomadStrings.get("nomad_flotilla", "top_space_needs", rightN);
    }
    float leftW = NomadText.need(left, NomadUi.PARA);
    float rightW = Math.min(at.w, NomadText.need(right, NomadUi.PARA));
    if (NomadHomeFigures.partsBothFit(leftW, rightW, at.w)) {
      LabelAPI l = label(panel, 0f, at.spaceParts.y, leftW, at.spaceParts.h, left,
          NomadUi.text(), Alignment.LMID, Face.PARA);
      l.setHighlight(usedN);
      l.setHighlightColor(parts.over ? NomadUi.warn() : used);
    }
    LabelAPI l = label(panel, at.w - rightW, at.spaceParts.y, rightW, at.spaceParts.h,
        NomadText.fit(right, rightW, NomadUi.PARA), isShort ? NomadUi.warn() : NomadUi.text(),
        Alignment.RMID, Face.PARA);
    if (rightN != null) {
      l.setHighlight(rightN);
      l.setHighlightColor(needs);
    }

    hover(panel, at.space, NomadNeedsTip.space(view));
  }

  static final class Bar extends BaseCustomUIPanelPlugin {

    private final float[] px;
    private final Color first;
    private final Color second;
    private final Color track = NomadUi.dark();
    private PositionAPI pos;

    Bar(float[] px, Color first, Color second) {
      this.px = px;
      this.first = first;
      this.second = second;
    }

    @Override
    public void positionChanged(PositionAPI position) {
      this.pos = position;
    }

    @Override
    public void render(float alphaMult) {
      if (pos == null) {
        return;
      }
      float x = pos.getX();
      float y = pos.getY();
      float h = pos.getHeight();
      NomadGl.fillRect(x, y, pos.getWidth(), h, track, TRACK_ALPHA, alphaMult);
      if (px[0] > 0f) {
        NomadGl.fillRect(x, y, px[0], h, first, 255, alphaMult);
      }
      if (px[2] > 0f) {
        NomadGl.fillRect(x + px[1], y, px[2], h, second, 255, alphaMult);
      }
    }
  }

  public static NeedsLevel level(BasicNeeds.Reading r) {
    return r == null || r.level == null ? NeedsLevel.NORMAL : r.level;
  }

  public static Color levelColor(NeedsLevel level) {
    if (level == null) {
      return NomadUi.hl();
    }
    if (level.ordinal() < NeedsLevel.SPARTAN.ordinal()) {
      return NomadUi.bad();
    }
    if (level == NeedsLevel.SPARTAN) {
      return NomadUi.warn();
    }
    return level == NeedsLevel.NORMAL ? NomadUi.hl() : NomadUi.good();
  }

  public static Color efficiencyColor(float efficiency) {
    return efficiency >= 0.999f ? NomadUi.good()
        : efficiency <= 0.001f ? NomadUi.bad() : NomadUi.warn();
  }

  public static Color usedColor() {
    return NomadUi.base();
  }

  public static Color needsColor(NomadFlotillaView view) {
    return view.paused ? NomadUi.gray() : levelColor(level(view.sheet.needs));
  }

  private static Color mix(Color a, Color b, float t) {
    return new Color(Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
        Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
        Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
  }

  private static void hover(CustomPanelAPI panel, NomadGrid.Box box,
      TooltipMakerAPI.TooltipCreator tip) {
    TooltipMakerAPI el = NomadCell.element(panel, box.w, box.h);
    CustomPanelAPI area = panel.createCustomPanel(box.w, box.h, new BaseCustomUIPanelPlugin());
    el.addCustom(area, 0f);
    el.addTooltipToPrevious(tip, TooltipMakerAPI.TooltipLocation.RIGHT, false);
    NomadCell.place(panel, el, box.x, box.y);
  }
}
