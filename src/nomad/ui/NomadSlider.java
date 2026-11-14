package nomad.ui;

import java.awt.Color;
import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.input.InputEventAPI;

public final class NomadSlider {

  public enum Change {
    NONE,
    MOVED,
    SETTLED
  }

  private static final Color TRACK_GROUND = new Color(8, 12, 16);

  private final String axis;

  private float pct;
  private float min;
  private boolean interactive;
  private float x;
  private float y;
  private float w;
  private float h;
  private boolean held;
  private float pctAtPress;

  public NomadSlider(String axis) {
    this.axis = axis;
  }

  public String axis() {
    return axis;
  }

  public float pct() {
    return pct;
  }

  public void setPct(float value) {
    this.pct = Math.max(0f, Math.min(100f, Math.round(value)));
  }

  public void setMin(float value) {
    this.min = Math.max(0f, Math.min(100f, value));
  }

  public void setInteractive(boolean value) {
    this.interactive = value;
    if (!value) {
      held = false;
    }
  }

  public float[] rect() {
    return new float[] { x, y, w, h };
  }

  public void place(float left, float bottom, float width, float height) {
    this.x = left;
    this.y = bottom;
    this.w = width;
    this.h = height;
  }

  public boolean contains(float px, float py) {
    return w > 0f && h > 0f && px >= x && px <= x + w && py >= y && py <= y + h;
  }

  public Change handle(List<InputEventAPI> events) {
    if (events == null || w <= 0f) {
      return Change.NONE;
    }
    Change out = Change.NONE;
    for (InputEventAPI e : events) {
      if (e.isConsumed() || !e.isMouseEvent()) {
        continue;
      }
      if (held && e.isRMBDownEvent()) {
        pct = pctAtPress;
        held = false;
        e.consume();
        return Change.SETTLED;
      }
      int ex = e.getX();
      int ey = e.getY();
      if (held && e.isLMBUpEvent()) {
        held = false;
        pct = NomadDedicationLayout.pctAt(ex, x, w, min);
        e.consume();
        return Change.SETTLED;
      }
      if (!held && interactive && e.isLMBDownEvent() && contains(ex, ey)) {
        pctAtPress = pct;
        held = true;
        float now = NomadDedicationLayout.pctAt(ex, x, w, min);
        e.consume();
        if (now != pct) {
          pct = now;
          out = Change.MOVED;
        }
      }
    }
    return out;
  }

  public Change advance() {
    if (!held || !interactive || w <= 0f) {
      return Change.NONE;
    }
    float now = NomadDedicationLayout.pctAt(Global.getSettings().getMouseX(), x, w, min);
    if (now == pct) {
      return Change.NONE;
    }
    pct = now;
    return Change.MOVED;
  }

  public void render(float alphaMult) {
    if (w <= 0f || h <= 0f) {
      return;
    }
    float trackY = y + (h - NomadDedicationLayout.TRACK_H) / 2f;
    NomadGl.opaqueBox(x, trackY, w, NomadDedicationLayout.TRACK_H, TRACK_GROUND,
        interactive ? NomadUi.dark() : NomadUi.gray());

    float fill = NomadDedicationLayout.fillW(pct, w);
    if (fill > 2f) {
      NomadGl.opaqueRect(x + 1f, trackY + 1f, Math.min(fill, w) - 2f,
          NomadDedicationLayout.TRACK_H - 2f, interactive ? NomadUi.base() : NomadUi.gray());
    }

    float knobX = NomadDedicationLayout.knobX(pct, x, w, NomadDedicationLayout.KNOB_W);
    Color edge = !interactive ? NomadUi.gray() : held ? NomadUi.bright() : NomadUi.base();
    NomadGl.opaqueBox(knobX, y, NomadDedicationLayout.KNOB_W, h, NomadUi.dark(), edge);
  }
}
