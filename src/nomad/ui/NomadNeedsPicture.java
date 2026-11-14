package nomad.ui;

import java.awt.Color;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.ui.PositionAPI;

import nomad.campaign.econ.model.NeedsLevel;

public final class NomadNeedsPicture {

  private static final Color DIM = new Color(95, 95, 95);

  private final SpriteAPI sprite;
  private final boolean dim;

  private NomadNeedsPicture(SpriteAPI sprite, boolean dim) {
    this.sprite = sprite;
    this.dim = dim;
  }

  public static String key(NeedsLevel level) {
    return NomadHomeColumn.pictureKey(NomadDedicationLayout.pictureLevel(level));
  }

  public static NomadNeedsPicture of(NeedsLevel level, float slotAspect) {
    SpriteAPI s;
    try {
      String path = Global.getSettings().getSpriteName("illustrations", key(level));
      s = path == null || path.isEmpty() ? null : Global.getSettings().getSprite(path);
    } catch (Exception e) {
      return null;
    }
    if (s == null) {
      return null;
    }
    if (s.getWidth() > 0f && s.getHeight() > 0f && slotAspect > 0f) {
      float keep = Math.min(1f, (s.getWidth() / s.getHeight()) / slotAspect);
      float th = s.getTexHeight();
      s.setTexY(s.getTexY() + th * (1f - keep) / 2f);
      s.setTexHeight(th * keep);
    }
    return new NomadNeedsPicture(s, NomadDedicationLayout.pictureDimmed(level));
  }

  public boolean dimmed() {
    return dim;
  }

  public void draw(float x, float y, float w, float h, float alphaMult) {
    sprite.setNormalBlend();
    sprite.setSize(w, h);
    sprite.setColor(dim ? DIM : Color.WHITE);
    sprite.setAlphaMult(alphaMult);
    sprite.render(x, y);
  }

  public BaseCustomUIPanelPlugin asPanel() {
    return new BaseCustomUIPanelPlugin() {
      private PositionAPI pos;

      @Override
      public void positionChanged(PositionAPI position) {
        pos = position;
      }

      @Override
      public void render(float alphaMult) {
        if (pos != null && pos.getWidth() > 0f && pos.getHeight() > 0f) {
          draw(pos.getX(), pos.getY(), pos.getWidth(), pos.getHeight(), alphaMult);
        }
      }
    };
  }
}
