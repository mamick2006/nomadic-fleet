package nomad.ui;

import java.awt.Color;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import nomad.NomadStrings;
import nomad.campaign.econ.model.NeedsLevel;
import nomad.campaign.econ.model.WorkDef;
import nomad.campaign.econ.model.WorkOrders;
import nomad.campaign.econ.model.WorkState;
import nomad.campaign.econ.model.Works;
import nomad.ui.NomadWorkActions.Verb;

public final class NomadSizeTip {

  private NomadSizeTip() {
  }

  public static final float WIDTH = 320f;

  private static final float PAD = 3f;
  private static final float OPAD = 10f;
  private static final float ROW_H = 18f;
  private static final float ICON = 16f;

  public static TooltipMakerAPI.TooltipCreator tip(NomadFlotillaView view, String workId,
      boolean upgrade) {
    return tip(view, workId, upgrade,
        upgrade ? view.quoteUpgrade(workId) : view.quoteDowngrade(workId));
  }

  static TooltipMakerAPI.TooltipCreator tip(final NomadFlotillaView view, final String workId,
      final boolean upgrade, final WorkOrders.Quote q) {
    return NomadTip.guard(new NomadTip(WIDTH) {
      @Override
      public void createTooltip(TooltipMakerAPI t, boolean expanded, Object param) {
        write(t, view, workId, upgrade, q);
      }
    });
  }

  private static void write(TooltipMakerAPI t, NomadFlotillaView view, String workId,
      boolean upgrade, WorkOrders.Quote q) {
    WorkState w = view.work(workId);
    WorkDef def = view.catalog.work(workId);
    int from = w == null ? 0 : Math.max(0, w.size);
    int to = upgrade ? from + 1 : from - 1;
    boolean step = def != null && to >= 1 && to <= def.maxSize();
    String verb = NomadWorkActions.label(upgrade ? Verb.UPGRADE : Verb.DOWNGRADE);
    t.addTitle(step ? NomadStrings.get("nomad_flotilla", "ui4_tip_to_size", verb,
        Integer.toString(to)) : verb, NomadUi.base());

    if (!view.live) {
      t.addPara(view.notLiveReason(), NomadUi.bad(), OPAD);
    } else if (!q.allowed()) {
      t.addPara(NomadFlotillaView.refusalText(q), NomadUi.bad(), OPAD);
    }
    if (!step) {
      return;
    }
    table(t, def, from, to);

    boolean pays = q.credits > 0.5f;
    boolean back = q.credits < -0.5f;
    boolean takes = q.days > 0.5f;
    if (pays || back || takes) {
      float valueW = NomadWorkHoverRows.tipValueW(def, NomadUi.days(q.days), NomadUi.PARA);
      t.beginGridFlipped(WIDTH - 2f * NomadText.INSET, 1, NomadUi.text(), valueW, OPAD);
      int at = 0;
      if (pays) {
        t.addToGrid(0, at++, NomadStrings.get("nomad_flotilla", "hover_picker_col_cost"),
            NomadUi.credits(q.credits), NomadUi.hl());
      } else if (back) {
        t.addToGrid(0, at++, NomadStrings.get("nomad_flotilla", "ui4_back"),
            NomadUi.credits(-q.credits), NomadUi.good());
      }
      if (takes) {
        t.addToGrid(0, at++, NomadStrings.get("nomad_flotilla", "hover_picker_col_days"),
            NomadUi.days(q.days), NomadUi.hl());
      }
      t.addGrid(OPAD);
    }
    if (upgrade && from >= 1) {
      t.addPara(NomadStrings.get("nomad_flotilla", "ui4_tip_runs", Integer.toString(from)),
          NomadUi.gray(), OPAD);
    }

    NeedsLevel now = NomadHomeColumn.level(view.sheet.needs);
    NeedsLevel then = NomadHomeColumn.level(q.after);
    if (then != now) {
      t.addPara(NomadWorkPanel.levelLine(now, then), NomadUi.text(), OPAD);
      if (NomadNeedsTag.stopsEveryWork(now, then)) {
        t.addPara(NomadStrings.get("nomad_flotilla", "needs_effect_none_run"), NomadUi.bad(),
            PAD);
      }
      if (q.residentsWouldLeave) {
        t.addPara(NomadStrings.get("nomad_flotilla", "cards_tip_leave"), NomadUi.bad(), PAD);
      }
    }
    String needs = NomadWorkPanel.needsLine(q);
    if (needs != null) {
      t.addPara(needs, NomadUi.text(), then != now ? PAD : OPAD);
    }
  }

  private static void table(TooltipMakerAPI t, WorkDef def, int from, int to) {
    float width = WIDTH - 2f * NomadText.INSET;
    float relief = nomad.campaign.works.WorkInfo.tierRelief();
    float sizeW = NomadWorkHoverRows.tipSizeW(def, relief, NomadFlotillaView.FLOTILLA,
        NomadUi.PARA);
    float labelW = width - 2f * sizeW;

    CustomPanelAPI head = Global.getSettings().createCustom(width, ROW_H, null);
    figure(head, labelW, sizeW, NomadWorkHoverRows.tipHead(from, NomadFlotillaView.FLOTILLA),
        NomadUi.gray());
    figure(head, labelW + sizeW, sizeW,
        NomadWorkHoverRows.tipHead(to, NomadFlotillaView.FLOTILLA), NomadUi.gray());
    t.addCustom(head, OPAD);

    for (NomadWorkHoverRows.Row row : NomadWorkHoverRows.rows(def)) {
      if (row.kind == NomadWorkHoverRows.Kind.COST || row.kind == NomadWorkHoverRows.Kind.NEEDS) {
        continue;
      }
      float has = row.at(from);
      float gets = row.at(to);
      if (Float.isNaN(has) && Float.isNaN(gets)) {
        continue;
      }
      CustomPanelAPI line = Global.getSettings().createCustom(width, ROW_H, null);
      label(line, row, labelW);
      figure(line, labelW, sizeW, NomadWorkHoverRows.tipFigure(row, has, relief),
          NomadUi.text());
      figure(line, labelW + sizeW, sizeW, NomadWorkHoverRows.tipFigure(row, gets, relief),
          targetColor(row.kind));
      t.addCustom(line, 0f);
    }
  }

  private static void label(CustomPanelAPI line, NomadWorkHoverRows.Row row, float labelW) {
    float x = 0f;
    String text;
    switch (row.kind) {
      case SPACE:
        text = NomadStrings.get("nomad_flotilla", "hover_row_space");
        break;
      case STAFF:
        text = NomadStrings.get("nomad_flotilla", "hover_row_staff");
        break;
      case PARAM:
        text = NomadWorkHover.paramName(row.label != null ? row.label : row.key);
        break;
      default: {
        text = NomadWorkHover.goodName(row.key);
        String sprite = NomadWorkHover.goodIcon(row.key);
        if (sprite != null) {
          TooltipMakerAPI ic = line.createUIElement(ICON, ROW_H, false);
          ic.addImage(sprite, ICON, ICON, 1f);
          line.addUIElement(ic).inTL(-NomadText.INSET, 0f);
          x = ICON + 4f;
        }
        break;
      }
    }
    float box = labelW - x - 4f;
    NomadCell.box(line, x, 0f, box, ROW_H, NomadText.fit(text, box, NomadUi.PARA), NomadUi.text(),
        Alignment.LMID, 1f);
  }

  private static void figure(CustomPanelAPI line, float x, float w, String text, Color c) {
    NomadCell.box(line, x, 0f, w, ROW_H, text, c, Alignment.RMID, 1f);
  }

  private static Color targetColor(NomadWorkHoverRows.Kind kind) {
    switch (kind) {
      case MAKES:
        return NomadUi.good();
      case USES:
        return NomadUi.bad();
      default:
        return NomadUi.hl();
    }
  }
}
