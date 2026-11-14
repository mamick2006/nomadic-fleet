package nomad.ui;

import java.awt.Color;
import java.lang.ref.WeakReference;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;

import nomad.campaign.econ.model.TickSheet;

public final class NomadMark {

  private NomadMark() {
  }

  private static final String PATH = "graphics/hud/attention_required_16x.png";

  private static SpriteAPI sprite;
  private static boolean spriteAsked;

  private static WeakReference<TickSheet> countedFor;
  private static NomadGrid.Counted countedNow;

  public static void add(CustomPanelAPI panel, float x, float y, float px, Color color) {
    SpriteAPI s = sprite();
    if (s == null || panel == null || px <= 0f) {
      return;
    }
    CustomPanelAPI mark = panel.createCustomPanel(px, px, new Draw(s, px, color));
    panel.addComponent(mark).inTL(x, y);
  }

  private static SpriteAPI sprite() {
    if (!spriteAsked) {
      spriteAsked = true;
      try {
        sprite = Global.getSettings().getSprite(PATH);
      } catch (Exception e) {
        Global.getLogger(NomadMark.class).warn("Nomad Flotilla: no attention mark sprite", e);
      }
    }
    return sprite;
  }

  private static final class Draw extends BaseCustomUIPanelPlugin {

    private final SpriteAPI s;
    private final float px;
    private final Color color;
    private PositionAPI pos;

    Draw(SpriteAPI s, float px, Color color) {
      this.s = s;
      this.px = px;
      this.color = color == null ? Color.WHITE : color;
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
      s.setSize(px, px);
      s.setColor(color);
      s.setAlphaMult(alphaMult);
      s.render(Math.round(pos.getX()), Math.round(pos.getY()));
    }
  }

  public static boolean counted(NomadFlotillaView view, String workId) {
    return of(view).has(workId);
  }

  static NomadGrid.Counted of(NomadFlotillaView view) {
    TickSheet sheet = view == null ? null : view.sheet;
    if (sheet == null) {
      return NomadGrid.Counted.of(null);
    }
    if (countedNow == null || countedFor == null || countedFor.get() != sheet) {
      countedNow = NomadGrid.Counted.of(sheet);
      countedFor = new WeakReference<TickSheet>(sheet);
    }
    return countedNow;
  }
}
