package nomad.ui;

import java.awt.Color;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

final class NomadHero {

  private NomadHero() {
  }

  private static final int GROUND_ALPHA = 200;
  private static final int BAND_ALPHA = 184;

  enum Face {
    PARA, CAPTION, VALUE
  }

  static SpriteAPI sprite(String key) {
    try {
      String path = Global.getSettings().getSpriteName("illustrations", key);
      SpriteAPI s = path == null || path.isEmpty() ? null : Global.getSettings().getSprite(path);
      return s == null || s.getWidth() <= 0f || s.getHeight() <= 0f ? null : s;
    } catch (Exception e) {
      return null;
    }
  }

  static SpriteAPI sprite(String key, float slotAspect) {
    SpriteAPI s = sprite(key);
    if (s != null && slotAspect > 0f) {
      cut(s, s.getWidth() / s.getHeight(), slotAspect, 0.5f);
    }
    return s;
  }

  static float cut(SpriteAPI s, float from, float to, float focus) {
    if (from > to) {
      float keep = to / from;
      float tw = s.getTexWidth();
      s.setTexX(s.getTexX() + tw * (1f - keep) * focus);
      s.setTexWidth(tw * keep);
    } else if (from < to) {
      float keep = from / to;
      float th = s.getTexHeight();
      s.setTexY(s.getTexY() + th * (1f - keep) / 2f);
      s.setTexHeight(th * keep);
    }
    return to;
  }

  static void picture(float x, float y, float w, float h, SpriteAPI picture, Color tint,
      float alphaMult) {
    NomadGl.fillRect(x, y, w, h, Color.BLACK, GROUND_ALPHA, alphaMult);
    if (picture != null) {
      picture.setSize(w, h);
      picture.setColor(tint);
      picture.setAlphaMult(alphaMult);
      picture.render(x, y);
    }
  }

  static void band(float x, float y, float w, float alphaMult) {
    NomadGl.fillRect(x + 1f, y + NomadHomeFigures.BAND_UP - NomadHomeFigures.BAND_H, w - 2f,
        NomadHomeFigures.BAND_H, Color.BLACK, BAND_ALPHA, alphaMult);
  }

  static void border(float x, float y, float w, float h, Color edge, float alphaMult) {
    NomadGl.box(x, y, w, h, Color.BLACK, 0, edge, 255, alphaMult);
  }

  static CustomPanelAPI panel(CustomPanelAPI parent, SpriteAPI picture, float w, float h,
      Color tint, boolean band) {
    return parent.createCustomPanel(w, h, new Panel(picture, tint, band));
  }

  static CustomPanelAPI whole(CustomPanelAPI parent, String key, float w, float h) {
    if (w <= 0f || h <= 0f) {
      return null;
    }
    SpriteAPI s = sprite(key, w / h);
    if (s == null) {
      Global.getLogger(NomadHero.class).warn("Nomad Flotilla: no picture for the illustrations"
          + " key " + key + "; the tab is built without it");
      return null;
    }
    return panel(parent, s, w, h, Color.WHITE, false);
  }

  private static final class Panel extends BaseCustomUIPanelPlugin {

    private final SpriteAPI picture;
    private final Color tint;
    private final boolean band;
    private final Color edge = NomadUi.dark();
    private PositionAPI pos;

    Panel(SpriteAPI picture, Color tint, boolean band) {
      this.picture = picture;
      this.tint = tint;
      this.band = band;
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
      float w = pos.getWidth();
      float h = pos.getHeight();
      picture(x, y, w, h, picture, tint, alphaMult);
      if (band) {
        band(x, y, w, alphaMult);
      }
      border(x, y, w, h, edge, alphaMult);
    }
  }

  static LabelAPI label(CustomPanelAPI panel, float boxX, float y, float boxW, float h,
      String text, Color c, Alignment align, Face face) {
    TooltipMakerAPI el = NomadCell.element(panel, boxW, h);
    if (face == Face.CAPTION) {
      el.setParaFontOrbitron();
    } else if (face == Face.VALUE) {
      el.setParaInsigniaVeryLarge();
    }
    LabelAPI l = el.addPara(text, c, 0f);
    l.setAlignment(align);
    NomadCell.place(panel, el, boxX, y);
    return l;
  }
}
