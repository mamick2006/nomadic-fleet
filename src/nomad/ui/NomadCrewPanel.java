package nomad.ui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import nomad.NomadStrings;
import nomad.campaign.NomadCrew;
import nomad.campaign.econ.model.BasicNeeds;
import nomad.campaign.econ.model.NeedsLevel;
import nomad.campaign.econ.model.Residents;
import nomad.campaign.econ.model.TickSheet;
import nomad.campaign.econ.model.Works;

public final class NomadCrewPanel {

  private NomadCrewPanel() {
  }

  private static final float PAD = 3f;
  private static final float OPAD = 10f;
  private static final float TIP_W = 360f;
  private static final float MOVE_TIP_W = 300f;

  private static final String[][] ARGS = {
      { "10", "100", NomadWorkActions.MAX_IN }, { "-10", "-100", NomadWorkActions.MAX_OUT } };

  static boolean ordersOpen(NomadFlotillaView view) {
    return view.live && NomadStoresPanel.crewOrdersOpen();
  }

  public static float build(CustomPanelAPI panel, float w, final NomadFlotillaView view) {
    TickSheet t = view.sheet;
    float berths = view.standing.berths;
    final NomadPanelFigures.ResidentParts parts = NomadPanelFigures.residentParts(t.residents,
        t.working, berths);
    final int over = view.paused ? 0 : NomadPanelFigures.overBerths(t.residents, berths);
    final boolean isShort = !view.paused && NomadHomeFigures.residentsShort(t.needs);
    boolean open = ordersOpen(view);
    String moveWord = NomadWorkActions.label(NomadWorkActions.Verb.CREW_OPEN);
    NomadPanelFigures.ResidentsLayout at = NomadPanelFigures.ResidentsLayout.of(w,
        over > 0 || isShort, open, NomadUi.BUTTON.width(moveWord));

    NomadStoresPanel.Paint paint = new NomadStoresPanel.Paint();
    CustomPanelAPI own = panel.createCustomPanel(w, at.height, paint);
    panel.addComponent(own).inTL(0f, 0f);

    TooltipMakerAPI.TooltipCreator tip = NomadTip.guard(new NomadTip(TIP_W) {
      @Override
      public void createTooltip(TooltipMakerAPI tt, boolean expanded, Object param) {
        residentsTip(tt, view, parts, isShort, over);
      }
    });

    if (at.mark != null) {
      NomadMark.add(own, at.mark.x, at.mark.y, at.mark.w, NomadUi.warn());
    }
    TooltipMakerAPI caption = NomadCell.element(own, w - at.caption.x, at.caption.h);
    caption.addPara(NomadStrings.get("nomad_flotilla", "panels_residents_title"), 0f);
    caption.addTooltipToPrevious(tip, TooltipMakerAPI.TooltipLocation.RIGHT, false);
    NomadCell.place(own, caption, at.caption.x, at.caption.y);

    moveButton(own, at.move, moveWord, open, view);

    TooltipMakerAPI value = NomadCell.element(own, at.value.w, at.value.h);
    LabelAPI count = value.addPara(NomadStrings.get("nomad_flotilla", "ui2_residents_value"), 0f,
        NomadUi.hl(), NomadPanelFigures.heads(parts.residents), NomadPanelFigures.heads(berths));
    count.setAlignment(Alignment.RMID);
    value.addTooltipToPrevious(tip, TooltipMakerAPI.TooltipLocation.RIGHT, false);
    NomadCell.place(own, value, at.value.x, at.value.y);

    bar(own, paint, at.bar, parts, NomadHomeColumn.needsColor(view), tip);
    partsLine(own, at, view, parts, isShort, over, tip);
    if (open) {
      crewOrders(own, paint, at, view, berths);
    }
    return at.height;
  }

  private static void bar(CustomPanelAPI own, NomadStoresPanel.Paint paint, NomadGrid.Box at,
      NomadPanelFigures.ResidentParts parts, Color needs, TooltipMakerAPI.TooltipCreator tip) {
    paint.rect(at.x, at.y, at.w, at.h, NomadUi.dark(), NomadStoresPanel.TRACK_ALPHA);
    float[] px = NomadHomeFigures.barParts(at.w, parts.workingShare, parts.needsShare);
    if (px[0] > 0f) {
      paint.rect(at.x, at.y, px[0], at.h, NomadHomeColumn.usedColor(), 255);
    }
    if (px[2] > 0f) {
      paint.rect(at.x + px[1], at.y, px[2], at.h, needs, 255);
    }
    TooltipMakerAPI el = NomadCell.element(own, at.w, at.h);
    el.addCustom(own.createCustomPanel(at.w, at.h, null), 0f);
    el.addTooltipToPrevious(tip, TooltipMakerAPI.TooltipLocation.RIGHT, false);
    NomadCell.place(own, el, at.x, at.y);
  }

  private static void partsLine(CustomPanelAPI own, NomadPanelFigures.ResidentsLayout at,
      NomadFlotillaView view, NomadPanelFigures.ResidentParts parts, boolean isShort, int over,
      TooltipMakerAPI.TooltipCreator tip) {
    float box = at.parts.w;
    if (view.paused) {
      part(own, at.parts, NomadStrings.get("nomad_flotilla", "fixui_paused_residents"),
          NomadUi.gray(), null, null, Alignment.LMID, tip);
      return;
    }
    if (over > 0) {
      String n = NomadPanelFigures.heads(over);
      part(own, at.parts, NomadText.fit(NomadStrings.get("nomad_flotilla", "ui3_over_berths", n),
          box, NomadUi.PARA), NomadUi.warn(), null, null, Alignment.LMID, tip);
      return;
    }
    String working = NomadPanelFigures.heads(parts.working);
    String left = NomadStrings.get("nomad_flotilla", "top_residents_working", working);
    Color used = NomadHomeColumn.usedColor();
    if (isShort) {
      BasicNeeds.Reading r = view.sheet.needs;
      int[] shown = NomadHomeFigures.hasAndRequires(r.haveResidents, r.nextResidents,
          r.wantResidents);
      String right = NomadText.fit(NomadStrings.get("nomad_flotilla", "ui3_bar_short",
          NomadPanelFigures.heads(shown[0]), NomadFlotillaView.levelName(r.next),
          NomadPanelFigures.heads(shown[1])), box, NomadUi.PARA);
      if (NomadHomeFigures.partsBothFit(NomadText.need(left, NomadUi.PARA),
          NomadText.need(right, NomadUi.PARA), box)) {
        part(own, at.parts, left, NomadUi.text(), used, working, Alignment.LMID, tip);
      }
      part(own, at.parts, right, NomadUi.warn(), null, null, Alignment.RMID, tip);
      return;
    }
    String needs = NomadPanelFigures.heads(parts.needs);
    String right = NomadStrings.get("nomad_flotilla", "top_residents_needs", needs);
    if (NomadHomeFigures.partsBothFit(NomadText.need(left, NomadUi.PARA),
        NomadText.need(right, NomadUi.PARA), box)) {
      part(own, at.parts, left, NomadUi.text(), used, working, Alignment.LMID, tip);
    }
    part(own, at.parts, right, NomadUi.text(), NomadHomeColumn.needsColor(view), needs,
        Alignment.RMID, tip);
  }

  private static void part(CustomPanelAPI own, NomadGrid.Box at, String text, Color color,
      Color numberColor, String number, Alignment align, TooltipMakerAPI.TooltipCreator tip) {
    TooltipMakerAPI el = NomadCell.element(own, at.w, at.h);
    LabelAPI l = el.addPara(text, color, 0f);
    if (number != null) {
      l.setHighlight(number);
      l.setHighlightColor(numberColor);
    }
    l.setAlignment(align);
    el.addTooltipToPrevious(tip, TooltipMakerAPI.TooltipLocation.RIGHT, false);
    NomadCell.place(own, el, at.x, at.y);
  }

  private static void moveButton(CustomPanelAPI own, NomadGrid.Box at, String word, boolean open,
      final NomadFlotillaView view) {
    TooltipMakerAPI cell = NomadCell.element(own, at.w, at.h);
    ButtonAPI b = cell.addButton(word, NomadWorkActions.id(NomadWorkActions.Verb.CREW_OPEN),
        NomadUi.base(), NomadUi.dark(), Alignment.MID, CutStyle.NONE, at.w, at.h, 0f);
    NomadUi.setEnabledSayingWhy(b, view.live);
    if (open) {
      b.highlight();
    }
    cell.addTooltipToPrevious(NomadTip.guard(new NomadTip(MOVE_TIP_W) {
      @Override
      public void createTooltip(TooltipMakerAPI t, boolean expanded, Object param) {
        t.addPara(NomadStrings.get("nomad_flotilla", "ui3_move_crew_tip"), 0f);
        if (!view.live) {
          t.addPara(view.notLiveReason(), NomadUi.warn(), PAD * 2f);
        }
      }
    }), TooltipMakerAPI.TooltipLocation.BELOW, false);
    NomadCell.place(own, cell, at.x, at.y);
  }

  private static void crewOrders(CustomPanelAPI own, NomadStoresPanel.Paint paint,
      NomadPanelFigures.ResidentsLayout at, NomadFlotillaView view, float berths) {
    NomadGrid.Box box = at.orders;
    paint.rect(box.x, box.y, box.w, box.h, NomadUi.dark(), NomadStoresPanel.OPEN_ALPHA);
    paint.frame(box.x, box.y, box.w, box.h, NomadStoresPanel.openEdge(), 255);

    String room = NomadPanelFigures.heads(
        Math.max(0f, Math.round(berths) - Math.round(view.sheet.residents)));
    String[] labels = { NomadStrings.get("nomad_flotilla", "ui3_crew_in"),
        NomadStrings.get("nomad_flotilla", "ui3_crew_out") };
    String[] notes = { NomadStrings.get("nomad_flotilla", "ui3_crew_berths_free", room),
        NomadStrings.get("nomad_flotilla", "ui3_crew_price_each",
            NomadUi.credits(NomadCrew.crewBasePrice())) };
    for (int r = 0; r < ARGS.length; r++) {
      NomadGrid.Box label = at.crewLabel[r];
      NomadCell.box(own, label.x, label.y, label.w, label.h, labels[r], NomadUi.text(),
          Alignment.LMID, 0f);
      for (int b = 0; b < ARGS[r].length; b++) {
        button(own, at.crewButton[r][b], ARGS[r][b], view);
      }
      NomadGrid.Box note = at.crewNote[r];
      NomadCell.box(own, note.x, note.y, note.w, note.h,
          NomadText.fit(notes[r], note.w, NomadUi.PARA), NomadUi.gray(), Alignment.RMID, 0f);
    }
  }

  private static void button(CustomPanelAPI own, NomadGrid.Box at, final String arg,
      final NomadFlotillaView view) {
    final int n = NomadWorkActions.crewMoved(view, arg);
    final boolean affordable = n >= 0 || NomadCrew.canTakeCrew(-n);
    boolean enabled = view.live && n != 0 && affordable;

    TooltipMakerAPI cell = NomadCell.element(own, at.w, at.h);
    ButtonAPI b = cell.addButton(face(arg), NomadWorkActions.id(NomadWorkActions.Verb.CREW, arg),
        NomadUi.text(), NomadUi.dark(), Alignment.MID, CutStyle.NONE, at.w, at.h, 0f);
    NomadUi.setEnabledSayingWhy(b, enabled);
    cell.addTooltipToPrevious(NomadTip.guard(new NomadTip() {
      @Override
      public void createTooltip(TooltipMakerAPI t, boolean expanded, Object param) {
        tip(t, view, arg, n, affordable);
      }
    }), TooltipMakerAPI.TooltipLocation.BELOW, false);
    NomadCell.place(own, cell, at.x, at.y);
  }

  static String face(String arg) {
    if (NomadWorkActions.MAX_IN.equals(arg) || NomadWorkActions.MAX_OUT.equals(arg)) {
      return NomadStrings.get("nomad_flotilla", "ui3_crew_all");
    }
    return arg.startsWith("-") ? arg.substring(1) : arg;
  }

  private static void what(TooltipMakerAPI t, NomadFlotillaView view, String arg) {
    String price = NomadUi.credits(NomadCrew.crewBasePrice());
    if (NomadWorkActions.MAX_IN.equals(arg)) {
      t.addPara(NomadStrings.get("nomad_stores", "st_crew_in_all_tip"), 0f);
    } else if (NomadWorkActions.MAX_OUT.equals(arg)) {
      t.addPara(NomadStrings.get("nomad_stores", "st_crew_out_all_tip"), 0f, NomadUi.hl(), price,
          NomadUi.i(view.shared.crewExtractReserve));
    } else if ("-100".equals(arg)) {
      t.addPara(NomadStrings.get("nomad_stores", "st_crew_out_100_tip"), 0f, NomadUi.hl(), price);
    } else if ("-10".equals(arg)) {
      t.addPara(NomadStrings.get("nomad_stores", "st_crew_out_10_tip"), 0f, NomadUi.hl(), price);
    } else if ("100".equals(arg)) {
      t.addPara(NomadStrings.get("nomad_stores", "st_crew_in_100_tip"), 0f);
    } else if ("10".equals(arg)) {
      t.addPara(NomadStrings.get("nomad_stores", "st_crew_in_10_tip"), 0f);
    }
  }

  private static void tip(TooltipMakerAPI t, NomadFlotillaView view, String arg, int n,
      boolean affordable) {
    boolean in = !arg.startsWith("-");
    what(t, view, arg);
    if (!view.live) {
      t.addPara(view.notLiveReason(), NomadUi.warn(), PAD * 2f);
      return;
    }
    if (n == 0) {
      if (!in) {
        t.addPara(NomadStrings.get("nomad_flotilla", "panels_crew_tip_reserve"), PAD * 2f,
            NomadUi.warn(), NomadUi.hl(), NomadUi.i(view.shared.crewExtractReserve));
      } else if (view.sheet.residents >= view.sheet.berths - 0.5f) {
        t.addPara(NomadStrings.get("nomad_stores", "st_crew_in_no_berths"), NomadUi.warn(),
            PAD * 2f);
      } else {
        t.addPara(NomadStrings.get("nomad_stores", "st_crew_in_no_spare"), NomadUi.warn(),
            PAD * 2f);
      }
      return;
    }
    if (in) {
      t.addPara(NomadStrings.get("nomad_flotilla", "panels_crew_tip_in"), PAD * 2f, NomadUi.hl(),
          "" + n);
    } else {
      t.addPara(NomadStrings.get("nomad_flotilla", "panels_crew_tip_out"), PAD * 2f,
          NomadUi.hl(), "" + (-n), NomadUi.credits(NomadCrew.priceToTake(-n)));
      if (!affordable) {
        t.addPara(NomadStrings.get("nomad_flotilla", "screen_refusal_no_credits"), NomadUi.bad(),
            PAD * 2f);
        return;
      }
    }
    levelAfter(t, view, NomadWorkActions.needsAfterCrew(view, arg));
  }

  private static void residentsTip(TooltipMakerAPI t, NomadFlotillaView view,
      NomadPanelFigures.ResidentParts parts, boolean isShort, int over) {
    float grid = TIP_W - 2f * NomadText.INSET;
    t.addTitle(NomadStrings.get("nomad_flotilla", "panels_residents_title"), NomadUi.base());
    t.addPara(NomadStrings.get("nomad_flotilla", "top_residents_tip"), OPAD);

    t.addPara(NomadStrings.get("nomad_flotilla", "panels_residents_total"), OPAD, NomadUi.hl(),
        NomadPanelFigures.heads(parts.residents));
    t.beginGridFlipped(grid, 1, NomadUi.text(), 50f, OPAD);
    t.addToGrid(0, 0, NomadStrings.get("nomad_flotilla", "ui2_tip_row_working"),
        NomadPanelFigures.heads(parts.working), NomadHomeColumn.usedColor());
    t.addToGrid(0, 1, view.workName(Works.BASIC_NEEDS), NomadPanelFigures.heads(parts.needs),
        NomadHomeColumn.needsColor(view));
    t.addGrid(PAD);

    List<NomadPanelFigures.Staff> staff =
        new ArrayList<NomadPanelFigures.Staff>(NomadPanelFigures.staffByWork(view.sheet));
    if (!staff.isEmpty()) {
      Collections.sort(staff, new Comparator<NomadPanelFigures.Staff>() {
        @Override
        public int compare(NomadPanelFigures.Staff a, NomadPanelFigures.Staff b) {
          return Float.compare(b.staff, a.staff);
        }
      });
      t.addSectionHeading(NomadStrings.get("nomad_flotilla", "ui2_tip_working_head",
          NomadPanelFigures.heads(parts.working)), NomadUi.base(), NomadUi.dark(), Alignment.MID,
          OPAD);
      t.beginGridFlipped(grid, 1, NomadUi.text(), 50f, OPAD);
      int at = 0;
      for (NomadPanelFigures.Staff s : staff) {
        t.addToGrid(0, at++, view.workName(s.workId), NomadPanelFigures.heads(s.staff),
            NomadHomeColumn.usedColor());
      }
      t.addGrid(PAD);
    }

    if (over > 0) {
      t.addPara(NomadStrings.get("nomad_flotilla", "ui3_over_berths"), OPAD, NomadUi.warn(),
          NomadPanelFigures.heads(over));
    }
    if (isShort) {
      BasicNeeds.Reading r = view.sheet.needs;
      t.addPara(NomadStrings.get("nomad_flotilla", "ui2_tip_up_residents"), OPAD, NomadUi.warn(),
          NomadFlotillaView.levelName(r.next), NomadPanelFigures.heads(
              NomadHomeFigures.moreRequired(r.haveResidents, r.nextResidents, r.wantResidents)));
    }
    growthLine(t, view);
  }

  private static void growthLine(TooltipMakerAPI el, NomadFlotillaView view) {
    TickSheet t = view.sheet;
    if (view.state.paused || view.state.detached) {
      el.addPara(view.notLiveReason(), NomadUi.warn(), OPAD);
      return;
    }
    float g = NomadPanelFigures.residentsPerMonth(t.residents, t.residentRoom,
        NomadHomeColumn.level(t.needs), view.rules, Residents.schoolGrowth(t));
    if (g > 0.05f) {
      el.addPara(NomadStrings.get("nomad_stores", "st_crew_tip_growing"), OPAD, NomadUi.good(),
          NomadUi.f1(g));
    } else if (g < -0.05f) {
      el.addPara(NomadStrings.get("nomad_flotilla", "panels_crew_losing"), OPAD, NomadUi.bad(),
          NomadUi.f1(-g));
    } else {
      el.addPara(NomadStrings.get("nomad_flotilla", "panels_crew_holding"), NomadUi.gray(), OPAD);
    }
    float made = NomadPanelFigures.madePerMonth(t, Works.RESIDENTS);
    if (made > 0.05f) {
      el.addPara(NomadStrings.get("nomad_stores", "st_crew_tip_made"), PAD, NomadUi.good(),
          NomadUi.f1(made));
    }
    float consumed = NomadPanelFigures.usedPerMonth(t, Works.RESIDENTS);
    if (consumed > 0.05f) {
      el.addPara(NomadStrings.get("nomad_stores", "st_crew_tip_consumed"), PAD, NomadUi.bad(),
          NomadUi.f1(consumed));
    }
  }

  static void levelAfter(TooltipMakerAPI t, NomadFlotillaView view, BasicNeeds.Reading after) {
    NeedsLevel now = NomadHomeColumn.level(view.sheet.needs);
    NeedsLevel then = NomadHomeColumn.level(after);
    String name = NomadFlotillaView.levelName(then);
    Color c = NomadHomeColumn.levelColor(then);
    if (then == now) {
      t.addPara(NomadStrings.get("nomad_flotilla", "needs_after_same"), PAD * 2f, c, name);
      return;
    }
    if (then.ordinal() > now.ordinal()) {
      t.addPara(NomadStrings.get("nomad_flotilla", "needs_after_up"), PAD * 2f, c, name);
    } else {
      t.addPara(NomadStrings.get("nomad_flotilla", "needs_after_down"), PAD * 2f, c, name);
    }
    if (NomadNeedsTag.stopsEveryWork(now, then)) {
      t.addPara(NomadStrings.get("nomad_flotilla", "needs_effect_none_run"), NomadUi.bad(), PAD);
    } else {
      t.addPara(NomadStrings.get("nomad_flotilla", "needs_effect_efficiency"), PAD, NomadUi.hl(),
          NomadUi.pct(nomad.campaign.econ.model.BasicNeeds.efficiencyAt(view.sheet, then)));
    }
    if (view.wouldStartLeaving(after)) {
      t.addPara(NomadStrings.get("nomad_flotilla", "needs_effect_leave"), PAD, NomadUi.warn(),
          NomadUi.pct(view.rules.leavePerMonth(then)));
    }
  }
}
