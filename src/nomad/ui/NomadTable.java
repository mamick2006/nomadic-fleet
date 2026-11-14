package nomad.ui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;

public final class NomadTable {

  private NomadTable() {
  }

  static Alignment sf(NomadColumns.Align a) {
    if (a == NomadColumns.Align.MID) {
      return Alignment.MID;
    }
    if (a == NomadColumns.Align.RIGHT) {
      return Alignment.RMID;
    }
    return Alignment.LMID;
  }

  public static void header(CustomPanelAPI panel, TooltipMakerAPI body, NomadColumns.Layout cols,
      float panelW, float headH, float labelPad, float pad) {
    CustomPanelAPI head = panel.createCustomPanel(panelW, headH,
        NomadRowChrome.rule(null, NomadUi.base(), 110));
    for (int i = 0; i < cols.count(); i++) {
      String label = cols.header(i);
      if (label == null) {
        continue;
      }
      TooltipMakerAPI cell = head.createUIElement(Math.max(24f, cols.w(i)), headH, false);
      LabelAPI lab = cell.addPara(label, NomadUi.gray(), 0f);
      lab.setAlignment(sf(cols.align(i)));
      head.addUIElement(cell).inTL(cols.x(i), labelPad);
    }
    body.addCustom(head, pad);
  }

  public static Row row(CustomPanelAPI panel, TooltipMakerAPI body, NomadColumns.Layout cols, float panelW,
      float rowH, NomadRowChrome chrome, float pad) {
    CustomPanelAPI row = panel.createCustomPanel(panelW, rowH, chrome);
    return new Row(row, body, cols, rowH, pad);
  }

  public static final class Row {
    private final CustomPanelAPI row;
    private final TooltipMakerAPI body;
    private final NomadColumns.Layout cols;
    private final float rowH;
    private final float pad;

    private Row(CustomPanelAPI row, TooltipMakerAPI body, NomadColumns.Layout cols, float rowH, float pad) {
      this.row = row;
      this.body = body;
      this.cols = cols;
      this.rowH = rowH;
      this.pad = pad;
    }

    public CustomPanelAPI panel() {
      return row;
    }

    public TooltipMakerAPI cell(int col) {
      return row.createUIElement(cols.w(col), rowH, false);
    }

    public TooltipMakerAPI cell(int col, float h) {
      return row.createUIElement(cols.w(col), h, false);
    }

    public Row place(TooltipMakerAPI el, int col, float topInset) {
      row.addUIElement(el).inTL(cols.x(col), topInset);
      return this;
    }

    public void end() {
      body.addCustom(row, pad);
    }
  }

  private static final Pattern NUM = Pattern.compile("x?\\d+(?:[.,]\\d+)*%?");

  public static void highlightNumbers(LabelAPI para, String text, Color color) {
    List<String> out = new ArrayList<String>();
    Matcher m = NUM.matcher(text);
    while (m.find()) {
      out.add(m.group());
    }
    if (!out.isEmpty()) {
      para.setHighlightColor(color);
      para.setHighlight(out.toArray(new String[0]));
    }
  }

  public static TooltipMakerAPI.TooltipCreator tip(final String body, final float pad) {
    return NomadTip.guard(new NomadTip() {
      @Override
      public void createTooltip(TooltipMakerAPI t, boolean expanded, Object param) {
        t.addPara(body, pad);
      }
    });
  }
}
