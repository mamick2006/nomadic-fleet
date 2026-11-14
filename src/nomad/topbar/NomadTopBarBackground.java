package nomad.topbar;

import java.awt.Color;
import java.util.List;

import org.lwjgl.opengl.GL11;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.util.Misc;

public class NomadTopBarBackground extends BaseCustomUIPanelPlugin {

  private static final Color GROUND = new Color(6, 9, 12);

  private static final int GRIP_ALPHA = 130;
  private static final int GRIP_ALPHA_HOVER = 200;
  private static final int GRIP_ALPHA_HELD = 255;

  private PositionAPI pos;
  boolean visible;
  float usedWidth;
  float dragWidth;

  private boolean latched;
  private float grabX;
  private float grabY;
  private float leftAtPress;
  private float topAtPress;
  private float appliedLeft;
  private float appliedTop;
  private boolean commitPending;
  private float committedLeft;
  private float committedTop;
  private boolean resetPending;

  @Override
  public void positionChanged(PositionAPI position) {
    this.pos = position;
  }

  boolean isDragging() {
    return latched;
  }

  float appliedTop() {
    return appliedTop;
  }

  void settleDrag() {
    if (latched) {
      latched = false;
      commitPending = true;
      resetPending = false;
      committedLeft = appliedLeft;
      committedTop = appliedTop;
    }
  }

  void dropDrag() {
    latched = false;
    commitPending = false;
    resetPending = false;
  }

  boolean consumeCommit() {
    boolean out = commitPending;
    commitPending = false;
    return out;
  }

  float committedLeft() {
    return committedLeft;
  }

  float committedTop() {
    return committedTop;
  }

  boolean consumeReset() {
    boolean out = resetPending;
    resetPending = false;
    return out;
  }

  @Override
  public void processInput(List<InputEventAPI> events) {
    if (events == null || pos == null || !visible || dragWidth <= 0f) {
      return;
    }
    try {
      handle(events);
    } catch (Throwable t) {
      latched = false;
    }
  }

  private void handle(List<InputEventAPI> events) {
    for (InputEventAPI e : events) {
      if (e.isConsumed() || !e.isMouseEvent()) {
        continue;
      }
      int ex = e.getX();
      int ey = e.getY();
      if (latched) {
        if (e.isRMBDownEvent()) {
          moveTo(leftAtPress, topAtPress);
          latched = false;
          e.consume();
          continue;
        }
        if (e.isLMBUpEvent()) {
          latched = false;
          dragTo(ex, ey);
          commitPending = true;
          resetPending = false;
          committedLeft = appliedLeft;
          committedTop = appliedTop;
          e.consume();
        }
        continue;
      }
      if (e.isLMBDownEvent() && onDragBand(ex, ey)) {
        float screenH = Global.getSettings().getScreenHeight();
        leftAtPress = liveLeft();
        topAtPress = liveTop();
        appliedLeft = leftAtPress;
        appliedTop = topAtPress;
        grabX = NomadTopBarLayout.grabOffsetX(ex, leftAtPress);
        grabY = NomadTopBarLayout.grabOffsetY(ey, topAtPress, screenH);
        latched = true;
        e.consume();
      } else if (e.isRMBDownEvent() && onGrip(ex, ey)) {
        moveTo(NomadTopBarLayout.defaultLeft(Global.getSettings().getScreenWidth(), usedWidth,
            NomadTopBarScript.SCREEN_PAD), NomadTopBarScript.TOP_OFFSET);
        resetPending = true;
        commitPending = false;
        e.consume();
      }
    }
  }

  private boolean onDragBand(float px, float py) {
    return inLeftBand(px, py, dragWidth);
  }

  private boolean onGrip(float px, float py) {
    return inLeftBand(px, py, NomadTopBarLayout.GRIP_W);
  }

  private boolean inLeftBand(float px, float py, float w) {
    float left = pos.getX();
    float bottom = pos.getY();
    return w > 0f && px >= left && px <= left + w && py >= bottom && py <= bottom + pos.getHeight();
  }

  @Override
  public void advance(float amount) {
    if (!latched || pos == null) {
      return;
    }
    try {
      dragTo(Global.getSettings().getMouseX(), Global.getSettings().getMouseY());
    } catch (Throwable t) {
      latched = false;
    }
  }

  private void dragTo(float cursorX, float cursorY) {
    float screenH = Global.getSettings().getScreenHeight();
    moveTo(NomadTopBarLayout.dragLeft(cursorX, grabX),
        NomadTopBarLayout.dragTop(cursorY, grabY, screenH));
  }

  private void moveTo(float left, float top) {
    float screenW = Global.getSettings().getScreenWidth();
    float screenH = Global.getSettings().getScreenHeight();
    appliedLeft = NomadTopBarLayout.clampLeft(left, usedWidth, screenW, NomadTopBarScript.SCREEN_PAD);
    appliedTop = NomadTopBarLayout.clampTop(top, pos.getHeight(), screenH, NomadTopBarScript.SCREEN_PAD);
    pos.inTL(appliedLeft, appliedTop);
  }

  private float liveLeft() {
    return pos.getX();
  }

  private float liveTop() {
    return NomadTopBarLayout.topFromBottom(pos.getY(), pos.getHeight(),
        Global.getSettings().getScreenHeight());
  }

  @Override
  public void renderBelow(float alphaMult) {
    if (!visible || pos == null || usedWidth <= 0f) {
      return;
    }
    float x = pos.getX();
    float y = pos.getY();
    float w = usedWidth;
    float h = pos.getHeight();
    Color border = Misc.getDarkPlayerColor();

    GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_LINE_BIT);
    GL11.glDisable(GL11.GL_TEXTURE_2D);
    GL11.glEnable(GL11.GL_BLEND);
    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

    GL11.glColor4ub((byte) GROUND.getRed(), (byte) GROUND.getGreen(), (byte) GROUND.getBlue(),
        (byte) (200f * alphaMult));
    GL11.glBegin(GL11.GL_QUADS);
    GL11.glVertex2f(x, y);
    GL11.glVertex2f(x, y + h);
    GL11.glVertex2f(x + w, y + h);
    GL11.glVertex2f(x + w, y);
    GL11.glEnd();

    GL11.glColor4ub((byte) border.getRed(), (byte) border.getGreen(), (byte) border.getBlue(),
        (byte) (160f * alphaMult));
    GL11.glLineWidth(1f);
    GL11.glBegin(GL11.GL_LINE_LOOP);
    GL11.glVertex2f(x + 0.5f, y + 0.5f);
    GL11.glVertex2f(x + 0.5f, y + h - 0.5f);
    GL11.glVertex2f(x + w - 0.5f, y + h - 0.5f);
    GL11.glVertex2f(x + w - 0.5f, y + 0.5f);
    GL11.glEnd();

    if (dragWidth > 0f) {
      GL11.glColor4ub((byte) border.getRed(), (byte) border.getGreen(), (byte) border.getBlue(),
          (byte) (gripAlpha() * alphaMult));
      GL11.glBegin(GL11.GL_LINES);
      gripLine(x + NomadTopBarLayout.GRIP_LINE_1, y, h);
      gripLine(x + NomadTopBarLayout.GRIP_LINE_2, y, h);
      GL11.glEnd();
    }

    GL11.glPopAttrib();
  }

  private static void gripLine(float lineX, float y, float h) {
    GL11.glVertex2f(lineX + 0.5f, y + NomadTopBarLayout.GRIP_INSET_V);
    GL11.glVertex2f(lineX + 0.5f, y + h - NomadTopBarLayout.GRIP_INSET_V);
  }

  private int gripAlpha() {
    if (latched) {
      return GRIP_ALPHA_HELD;
    }
    return onGrip(Global.getSettings().getMouseX(), Global.getSettings().getMouseY())
        ? GRIP_ALPHA_HOVER : GRIP_ALPHA;
  }
}
