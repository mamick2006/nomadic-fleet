package nomad.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

public abstract class NomadTip implements TooltipMakerAPI.TooltipCreator {

  private final float width;

  public NomadTip() {
    this(320f);
  }

  public NomadTip(float width) {
    this.width = width;
  }

  @Override
  public boolean isTooltipExpandable(Object param) {
    return false;
  }

  @Override
  public float getTooltipWidth(Object param) {
    return width;
  }

  @Override
  public abstract void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object param);

  public static final float BG_ALPHA = 1f;

  private static void opaque(TooltipMakerAPI tooltip) {
    tooltip.setBgAlpha(BG_ALPHA);
  }

  public static TooltipMakerAPI.TooltipCreator guard(final TooltipMakerAPI.TooltipCreator inner) {
    return new TooltipMakerAPI.TooltipCreator() {
      @Override
      public boolean isTooltipExpandable(Object param) {
        return inner.isTooltipExpandable(param);
      }

      @Override
      public float getTooltipWidth(Object param) {
        return inner.getTooltipWidth(param);
      }

      @Override
      public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object param) {
        opaque(tooltip);
        try {
          inner.createTooltip(tooltip, expanded, param);
        } catch (RuntimeException e) {
          Global.getLogger(NomadTip.class).error("Nomad Flotilla: a hover could not be drawn", e);
          tooltip.addPara("Display error. See starsector.log.", NomadUi.bad(), 3f);
        }
      }
    };
  }
}
