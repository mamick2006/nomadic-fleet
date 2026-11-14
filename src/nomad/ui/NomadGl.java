package nomad.ui;

import java.awt.Color;

import org.lwjgl.opengl.GL11;

public final class NomadGl {

  private NomadGl() {
  }

  public static void fillRect(float x, float y, float w, float h, Color c, int alpha, float mult) {
    begin();
    quad(x, y, w, h, c, alpha, mult);
    end();
  }

  public static void box(float x, float y, float w, float h, Color ground, int groundAlpha,
      Color border, int borderAlpha, float mult) {
    begin();
    quad(x, y, w, h, ground, groundAlpha, mult);
    color(border, borderAlpha, mult);
    GL11.glLineWidth(1f);
    GL11.glBegin(GL11.GL_LINE_LOOP);
    GL11.glVertex2f(x + 0.5f, y + 0.5f);
    GL11.glVertex2f(x + 0.5f, y + h - 0.5f);
    GL11.glVertex2f(x + w - 0.5f, y + h - 0.5f);
    GL11.glVertex2f(x + w - 0.5f, y + 0.5f);
    GL11.glEnd();
    end();
  }

  public static void rule(float x, float y, float w, float h, Color bgWash, int bgAlpha,
      Color line, int lineAlpha, float mult) {
    begin();
    if (bgWash != null) {
      quad(x, y, w, h, bgWash, bgAlpha, mult);
    }
    color(line, lineAlpha, mult);
    GL11.glLineWidth(1f);
    GL11.glBegin(GL11.GL_LINES);
    GL11.glVertex2f(x + 0.5f, y + 0.5f);
    GL11.glVertex2f(x + w - 0.5f, y + 0.5f);
    GL11.glEnd();
    end();
  }

  public static void opaqueRect(float x, float y, float w, float h, Color c) {
    GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT);
    GL11.glDisable(GL11.GL_TEXTURE_2D);
    GL11.glDisable(GL11.GL_BLEND);
    GL11.glColor4ub((byte) c.getRed(), (byte) c.getGreen(), (byte) c.getBlue(), (byte) 255);
    GL11.glBegin(GL11.GL_QUADS);
    GL11.glVertex2f(x, y);
    GL11.glVertex2f(x, y + h);
    GL11.glVertex2f(x + w, y + h);
    GL11.glVertex2f(x + w, y);
    GL11.glEnd();
    GL11.glPopAttrib();
  }

  public static void opaqueBox(float x, float y, float w, float h, Color ground, Color border) {
    opaqueRect(x, y, w, h, ground);
    GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_LINE_BIT);
    GL11.glDisable(GL11.GL_TEXTURE_2D);
    GL11.glDisable(GL11.GL_BLEND);
    GL11.glColor4ub((byte) border.getRed(), (byte) border.getGreen(), (byte) border.getBlue(),
        (byte) 255);
    GL11.glLineWidth(1f);
    GL11.glBegin(GL11.GL_LINE_LOOP);
    GL11.glVertex2f(x + 0.5f, y + 0.5f);
    GL11.glVertex2f(x + 0.5f, y + h - 0.5f);
    GL11.glVertex2f(x + w - 0.5f, y + h - 0.5f);
    GL11.glVertex2f(x + w - 0.5f, y + 0.5f);
    GL11.glEnd();
    GL11.glPopAttrib();
  }

  private static void begin() {
    GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_LINE_BIT);
    GL11.glDisable(GL11.GL_TEXTURE_2D);
    GL11.glEnable(GL11.GL_BLEND);
    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
  }

  private static void end() {
    GL11.glPopAttrib();
  }

  private static void quad(float x, float y, float w, float h, Color c, int alpha, float mult) {
    color(c, alpha, mult);
    GL11.glBegin(GL11.GL_QUADS);
    GL11.glVertex2f(x, y);
    GL11.glVertex2f(x, y + h);
    GL11.glVertex2f(x + w, y + h);
    GL11.glVertex2f(x + w, y);
    GL11.glEnd();
  }

  private static void color(Color c, int alpha, float mult) {
    GL11.glColor4ub((byte) c.getRed(), (byte) c.getGreen(), (byte) c.getBlue(), (byte) (alpha * mult));
  }
}
