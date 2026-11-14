package nomad.ui;

import java.awt.Color;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.PositionAPI;

public final class NomadRowChrome extends BaseCustomUIPanelPlugin {

  public enum Style {
    NONE, RULE, BOX
  }

  private static final Color BOX_GROUND = new Color(8, 12, 16);
  private static final int BOX_GROUND_ALPHA = 110;
  private static final int BOX_BORDER_ALPHA = 170;
  private static final int RULE_WASH_ALPHA = 18;

  public static final NomadRowChrome NONE = new NomadRowChrome(Style.NONE, null, null, 0);

  private final Style style;
  private final Color fill;
  private final Color line;
  private final int lineAlpha;
  private PositionAPI pos;

  private NomadRowChrome(Style style, Color fill, Color line, int lineAlpha) {
    this.style = style;
    this.fill = fill;
    this.line = line;
    this.lineAlpha = lineAlpha;
  }

  public static NomadRowChrome rule(Color bgWash, Color line, int lineAlpha) {
    return new NomadRowChrome(Style.RULE, bgWash, line, lineAlpha);
  }

  public static NomadRowChrome box(Color border) {
    return new NomadRowChrome(Style.BOX, null, border, BOX_BORDER_ALPHA);
  }

  @Override
  public void positionChanged(PositionAPI position) {
    this.pos = style == Style.NONE ? null : position;
  }

  @Override
  public void buttonPressed(Object buttonId) {
    NomadConsolePanel.routeButton(buttonId);
  }

  @Override
  public void renderBelow(float alphaMult) {
    if (pos == null || style == Style.NONE) {
      return;
    }
    float x = pos.getX();
    float y = pos.getY();
    float w = pos.getWidth();
    float h = pos.getHeight();
    if (style == Style.BOX) {
      NomadGl.box(x, y, w, h, BOX_GROUND, BOX_GROUND_ALPHA, line, BOX_BORDER_ALPHA, alphaMult);
    } else {
      NomadGl.rule(x, y, w, h, fill, RULE_WASH_ALPHA, line, lineAlpha, alphaMult);
    }
  }
}
