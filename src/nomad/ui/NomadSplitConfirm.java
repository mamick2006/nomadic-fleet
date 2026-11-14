package nomad.ui;

import java.awt.Color;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.DataGridAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import nomad.NomadStrings;
import nomad.campaign.econ.SplitShortfall;
import nomad.campaign.split.NomadSplitLines;
import nomad.campaign.split.NomadSplitter;

public final class NomadSplitConfirm {

  private NomadSplitConfirm() {
  }

  private static final float PAD = 3f;
  private static final float OPAD = 10f;
  private static final float ROW_H = 15f;
  private static final float ROW_GAP = 4f;
  private static final float BLOCK_GAP = 8f;

  private static final float FULL_W = 608f;
  private static final float NOW_RIGHT = 254f;
  private static final float AFTER_RIGHT = 354f;
  private static final float CARRY_RIGHT = 464f;
  private static final float FIGURE_W = 80f;
  private static final float OVER_GAP = 16f;
  private static final float NAME_GAP = 6f;

  private static final float VALUE_W = 40f;
  private static final float VALUE_GAP = 10f;

  static final float BLEED_BOX = 15f;
  static final float BLEED_FLOW = 5f;

  public static void body(TooltipMakerAPI t, float pad, float bleed) {
    float w = t.getWidthSoFar();
    NomadSplitter.SplitPreview p = NomadSplitter.preview();
    int count = p == null ? 0 : p.ships;
    String ships = "" + count;
    LabelAPI lead = t.addPara(NomadStrings.get("nomad_flotilla",
        count == 1 ? "ui4_split_body_one" : "ui4_split_body", ships), pad);
    lead.setHighlight(ships);
    lead.setHighlightColor(NomadUi.hl());
    if (p != null) {
      fleet(t, p, w, bleed);
    }
    flotilla(t, w, bleed);
  }

  private static final class Columns {

    final float now;
    final float after;
    final float carry;

    Columns(float w, float nameNeed) {
      float shift = Math.min(0f, w - FULL_W);
      shift = Math.max(shift, nameNeed + NAME_GAP + FIGURE_W - NOW_RIGHT);
      now = NOW_RIGHT + shift;
      after = AFTER_RIGHT + shift;
      carry = CARRY_RIGHT + shift;
    }
  }

  private static void fleet(TooltipMakerAPI t, NomadSplitter.SplitPreview p, float w,
      float bleed) {
    heading(t, NomadStrings.get("nomad_flotilla", "ui4_split_fleet"), w, bleed, OPAD);
    String rowCargo = NomadStrings.get("nomad_flotilla", "ui4_row_cargo");
    String rowFuel = NomadStrings.get("nomad_flotilla", "ui4_row_fuel");
    String rowCrew = NomadStrings.get("nomad_flotilla", "ui4_row_crew");
    String rowBurn = NomadStrings.get("nomad_flotilla", "ui4_row_burn");
    Columns at = new Columns(w, NomadText.need(NomadUi.PARA, rowCargo, rowFuel, rowCrew, rowBurn));

    CustomPanelAPI head = row(w);
    figure(head, at.now, NomadStrings.get("nomad_flotilla", "ui4_now"), NomadUi.gray());
    figure(head, at.after, NomadStrings.get("nomad_flotilla", "ui4_after"), NomadUi.gray());
    figure(head, at.carry, NomadStrings.get("nomad_flotilla", "ui4_carrying"), NomadUi.gray());
    t.addCustom(head, BLOCK_GAP);

    capacity(t, w, at, rowCargo, p.cargoBefore, p.cargoAfter, p.cargoHeld, p.cargoOver());
    capacity(t, w, at, rowFuel, p.fuelBefore, p.fuelAfter, p.fuelHeld, p.fuelOver());
    capacity(t, w, at, rowCrew, p.crewBefore, p.crewAfter, p.crewHeld, p.crewOver());
    if (p.burnGain > 0.5f) {
      CustomPanelAPI burn = row(w);
      name(burn, at, rowBurn);
      figure(burn, at.after, "+" + NomadSplitLines.units(p.burnGain), NomadUi.good());
      t.addCustom(burn, ROW_GAP);
    }
    if (p.anyOver()) {
      t.addPara(NomadStrings.get("nomad_overview", "ov_split_overcap_note"), NomadUi.warn(),
          BLOCK_GAP);
    }
  }

  private static void capacity(TooltipMakerAPI t, float w, Columns at, String what, float before,
      float after, float held, float over) {
    boolean isOver = over > 0.5f;
    CustomPanelAPI line = row(w);
    name(line, at, what);
    figure(line, at.now, NomadSplitLines.units(before), NomadUi.text());
    figure(line, at.after, NomadSplitLines.units(after),
        before - after > 0.5f ? NomadUi.hl() : NomadUi.text());
    figure(line, at.carry, NomadSplitLines.units(held), isOver ? NomadUi.bad() : NomadUi.text());
    if (isOver) {
      float markX = at.carry + OVER_GAP;
      float textX = markX + NomadHomeFigures.MARK_STEP;
      NomadMark.add(line, markX, 0f, NomadHomeFigures.MARK, NomadUi.bad());
      NomadCell.box(line, textX, 0f, Math.max(1f, w - textX), ROW_H,
          NomadStrings.get("nomad_flotilla", "ui4_over", NomadSplitLines.units(over)),
          NomadUi.bad(), Alignment.LMID, 0f);
    }
    t.addCustom(line, ROW_GAP);
  }

  private static CustomPanelAPI row(float w) {
    return Global.getSettings().createCustom(w, ROW_H, null);
  }

  private static void name(CustomPanelAPI line, Columns at, String text) {
    NomadCell.box(line, 0f, 0f, Math.max(1f, at.now - FIGURE_W - NAME_GAP), ROW_H, text,
        NomadUi.text(), Alignment.LMID, 0f);
  }

  private static void figure(CustomPanelAPI line, float right, String text, Color color) {
    NomadCell.box(line, right - FIGURE_W, 0f, FIGURE_W, ROW_H, text, color, Alignment.RMID, 0f);
  }

  private static void flotilla(TooltipMakerAPI t, float w, float bleed) {
    heading(t, NomadStrings.get("nomad_flotilla", "ui4_split_flotilla"), w, bleed, OPAD);
    String upkeep = NomadUi.i(NomadSplitter.suppliesPerMonth());
    SplitShortfall s = NomadSplitter.shortfall();
    boolean runsDown = s != null && s.suppliesRunDown()
        && s.suppliesMonths() <= NomadSplitLines.LONG_ENOUGH;
    boolean lasts = runsDown && s.suppliesMonths() > 0f;
    String stock = lasts ? NomadSplitLines.units((float) Math.floor(s.suppliesStock)) : "";
    String days = lasts ? NomadUi.days(s.suppliesMonths() * 30f) : "";

    float valueW = Math.max(VALUE_W, NomadText.need(NomadUi.PARA, upkeep, stock));
    t.beginGridFlipped(w, 1, NomadUi.text(), valueW, VALUE_GAP);
    t.addToGrid(0, 0, NomadStrings.get("nomad_flotilla", "ui4_split_use"), upkeep);
    if (lasts) {
      Object cell = t.addToGrid(0, 1,
          NomadStrings.get("nomad_flotilla", "ui4_split_store", days), stock);
      if (cell instanceof DataGridAPI.DataGridRowAPI) {
        LabelAPI words = ((DataGridAPI.DataGridRowAPI) cell).getNameLabel();
        words.setHighlight(days);
        words.setHighlightColor(NomadUi.hl());
      }
    }
    t.addGrid(BLOCK_GAP);
    if (lasts) {
      t.addPara(NomadStrings.get("nomad_flotilla", "ui4_split_owed"), NomadUi.gray(), OPAD);
    } else if (runsDown) {
      t.addPara(NomadStrings.get("nomad_overview", "detach_split_supplies_empty"),
          NomadUi.warn(), OPAD);
    }

    if (s != null) {
      NomadSplitLines.rows(t, s, OPAD);
    }
    int officers = NomadSplitter.officersAtRisk();
    if (officers == 1) {
      t.addPara(NomadStrings.get("nomad_overview", "ov_split_confirm_officer_one"), PAD,
          NomadUi.warn(), "" + officers);
    } else if (officers > 1) {
      t.addPara(NomadStrings.get("nomad_overview", "ov_split_confirm_officer_many"), PAD,
          NomadUi.warn(), "" + officers);
    }
    if (s != null && s.levelFalls()) {
      NomadConfirm.levels(t, s.levelBefore, s.levelAfter, OPAD);
      if (s.residentsWouldLeave()) {
        NomadConfirm.marked(t, NomadStrings.get("nomad_flotilla", "ui4_leave",
            NomadUi.pct(s.leavePerMonthAfter)), NomadUi.bad(), BLOCK_GAP);
      }
    }
  }

  private static void heading(TooltipMakerAPI t, String text, float w, float bleed, float pad) {
    LabelAPI bar = t.addSectionHeading(text, NomadUi.base(), NomadUi.dark(), Alignment.MID, pad);
    if (bleed != BLEED_FLOW) {
      PositionAPI at = bar.getPosition();
      at.setXAlignOffset(-bleed);
      at.setSize(w + 2f * bleed, at.getHeight());
    }
  }
}
