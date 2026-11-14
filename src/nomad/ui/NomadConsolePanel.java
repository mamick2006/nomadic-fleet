package nomad.ui;

import java.util.List;

import org.lwjgl.opengl.GL11;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.impl.campaign.intel.misc.MapMarkerIntel;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import nomad.data.NomadFleetData;
import nomad.intel.NomadIntel;

public final class NomadConsolePanel implements CustomUIPanelPlugin {

  private static final float CONTENT_PAD = 10f;
  private static final float FAILED_H = 18f;

  public static final String CONFIRM_OK = "nomad_confirm_ok";
  public static final String CONFIRM_CANCEL = "nomad_confirm_cancel";

  private static NomadConsolePanel live;
  private float lastAlphaMult = -1f;

  public static float lastAlphaOfLive() {
    return live == null ? -1f : live.lastAlphaMult;
  }

  private final CustomPanelAPI root;
  private final float width;
  private final float height;
  private CustomPanelAPI content;
  private PositionAPI pos;
  private boolean rebuildingLive;
  private Object pendingConfirm;
  private CustomPanelAPI confirmBox;

  public static CustomPanelAPI confirmBoxOfLive() {
    return live == null ? null : live.confirmBox;
  }

  public NomadConsolePanel(float width, float height) {
    this.width = width;
    this.height = height;
    this.root = Global.getSettings().createCustom(width, height, this);
    live = this;
    rebuild();
  }

  public static void routeButton(Object buttonId) {
    if (live != null) {
      live.handleButton(buttonId);
    }
  }

  public static void dropLive(NomadConsolePanel console) {
    if (console != null && live == console) {
      live = null;
      NomadAutoforgeTab.releaseSearchPoll();
      NomadFlotillaTab.resetUi();
    }
  }

  public static boolean isLiveRebuild() {
    NomadConsolePanel l = live;
    return l != null && l.rebuildingLive;
  }

  public static void resetStaticsForLoad() {
    live = null;
    NomadAutoforgeTab.releaseSearchPoll();
    NomadFlotillaTab.resetUi();
  }

  public CustomPanelAPI getPanel() {
    return root;
  }

  private void rebuild() {
    NomadAutoforgeTab.releaseSearchPoll();
    NomadStoresPanel.releaseTargetField();
    confirmBox = null;
    if (content != null) {
      try {
        root.removeComponent(content);
      } catch (Throwable ignore) {
      }
    }
    float cw = width - 2f * CONTENT_PAD;
    float ch = height - 2f * CONTENT_PAD;
    content = root.createCustomPanel(cw, ch, NomadUi.consoleRow());
    root.addComponent(content).inTL(CONTENT_PAD, CONTENT_PAD);
    NomadIntel intel = NomadIntel.get();
    boolean asking = pendingConfirm != null && NomadFleetData.exists();
    if (intel != null && NomadFleetData.exists()) {
      rebuildingLive = (live == this) && !asking;
      try {
        intel.createLargeDescription(content, cw, ch);
      } catch (Throwable t) {
        buildFailed(cw, ch, "tab", t);
      } finally {
        rebuildingLive = false;
      }
    } else if (!NomadFleetData.exists()) {
      try {
        NomadFlotillaTab.buildNoFlotilla(content, cw, ch);
      } catch (Throwable t) {
        buildFailed(cw, ch, "tab", t);
      }
    }
    if (asking) {
      openConfirm(cw, ch);
    }
  }

  private void openConfirm(float cw, float ch) {
    CustomPanelAPI box = null;
    try {
      box = NomadConfirmBox.build(content, cw, ch, pendingConfirm, NomadFleetData.getInstance());
      if (box == null) {
        pendingConfirm = null;
        return;
      }
      content.addComponent(box).inTL(0f, 0f);
      confirmBox = box;
    } catch (Throwable t) {
      pendingConfirm = null;
      confirmBox = null;
      if (box != null) {
        try {
          content.removeComponent(box);
        } catch (Throwable ignore) {
        }
      }
      buildFailed(cw, ch, "confirm", t);
    }
  }

  private void buildFailed(float cw, float ch, String what, Throwable cause) {
    Global.getLogger(NomadConsolePanel.class).error(
        "Nomad Flotilla: the console's " + what + " could not be drawn", cause);
    try {
      TooltipMakerAPI note = content.createUIElement(cw, FAILED_H, false);
      note.addPara("Display error. See starsector.log.", NomadUi.bad(), 0f);
      content.addUIElement(note).inTL(0f, ch - FAILED_H);
    } catch (Throwable ignore) {
    }
  }

  @Override
  public void buttonPressed(Object buttonId) {
    handleButton(buttonId);
  }

  private void handleButton(Object buttonId) {
    if (!NomadFleetData.exists()) {
      return;
    }
    NomadIntel intel = NomadIntel.get();
    if (intel == null) {
      return;
    }
    NomadFleetData data = NomadFleetData.getInstance();

    if (pendingConfirm != null) {
      if (!CONFIRM_OK.equals(buttonId) && !CONFIRM_CANCEL.equals(buttonId)) {
        return;
      }
      Object parked = pendingConfirm;
      pendingConfirm = null;
      if (CONFIRM_CANCEL.equals(buttonId)) {
        rebuild();
        return;
      }
      buttonId = parked;
    } else if (CONFIRM_OK.equals(buttonId) || CONFIRM_CANCEL.equals(buttonId)) {
      return;
    } else if (NomadConfirm.needed(buttonId)) {
      pendingConfirm = buttonId;
      intel.saveConsoleScroll();
      rebuild();
      return;
    }

    intel.saveConsoleScroll();

    if (NomadTabbedPanels.isTabButton(buttonId)) {
      intel.setCurrentTab(NomadTabbedPanels.tabIdOf(buttonId));
      rebuild();
      return;
    }

    IntelUIAPI shim = new RefreshShim();
    boolean handled = NomadFlotillaTab.handle(buttonId, intel, data, shim)
        || NomadItemsTab.handle(buttonId, intel, data, shim)
        || NomadAutoforgeTab.handle(buttonId, intel, data, shim);
    if (handled) {
      rebuild();
    }
  }

  @Override
  public void positionChanged(PositionAPI position) {
    this.pos = position;
  }

  @Override
  public void renderBelow(float alphaMult) {
    lastAlphaMult = alphaMult;
    if (pos == null) {
      return;
    }
    float x = pos.getX();
    float y = pos.getY();
    float w = pos.getWidth();
    float h = pos.getHeight();
    GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT);
    GL11.glDisable(GL11.GL_TEXTURE_2D);
    GL11.glEnable(GL11.GL_BLEND);
    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    GL11.glColor4ub((byte) 4, (byte) 6, (byte) 9, (byte) (255f * alphaMult));
    GL11.glBegin(GL11.GL_QUADS);
    GL11.glVertex2f(x, y);
    GL11.glVertex2f(x, y + h);
    GL11.glVertex2f(x + w, y + h);
    GL11.glVertex2f(x + w, y);
    GL11.glEnd();
    GL11.glPopAttrib();
  }

  @Override
  public void render(float alphaMult) {
  }

  @Override
  public void advance(float amount) {
  }

  @Override
  public void processInput(List<InputEventAPI> events) {
  }

  private final class RefreshShim implements IntelUIAPI {
    @Override
    public void updateUIForItem(IntelInfoPlugin plugin) {
      rebuild();
    }

    @Override
    public void recreateIntelUI() {
      rebuild();
    }

    @Override
    public void showDialog(SectorEntityToken target, String trigger) {
    }

    @Override
    public void showDialog(SectorEntityToken target, InteractionDialogPlugin plugin) {
    }

    @Override
    public void showEditIntelMarkerDialog(MapMarkerIntel intel) {
    }

    @Override
    public void updateIntelList() {
    }

    @Override
    public void updateIntelList(boolean retainCurrentSelection) {
    }

    @Override
    public void updateIntelList(boolean retainCurrentSelection, List<IntelInfoPlugin> show) {
    }

    @Override
    public void selectItem(IntelInfoPlugin plugin) {
    }

    @Override
    public boolean isShowingCustomIntelSubset() {
      return false;
    }

    @Override
    public void showOnMap(SectorEntityToken token) {
    }

    @Override
    public void saveSmallDescScrollState() {
    }

    @Override
    public void restoreSmallDescScrollState() {
    }
  }
}
