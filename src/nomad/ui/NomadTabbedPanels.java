package nomad.ui;

import java.util.List;

import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.ScrollPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;

public final class NomadTabbedPanels {

  private NomadTabbedPanels() {
  }

  public static final String TAB_PREFIX = "nomad_tab*";
  public static final float BAR_HEIGHT = 26f;
  public static final float BODY_GAP = NomadGrid.Screen.TOOLBAR_GAP;
  public static final float BODY_TOP = BAR_HEIGHT + BODY_GAP;
  private static final float TAB_PAD = 4f;

  public static void buildTabBar(CustomPanelAPI panel, float width, List<String> ids, List<String> labels,
      String active) {
    int n = ids.size();
    if (n <= 0) {
      return;
    }
    float boxW = (width - TAB_PAD * (n - 1)) / n;
    UIComponentAPI prev = null;
    for (int i = 0; i < n; i++) {
      TooltipMakerAPI cell = panel.createUIElement(boxW, BAR_HEIGHT, false);
      com.fs.starfarer.api.ui.ButtonAPI box = cell.addAreaCheckbox(labels.get(i), TAB_PREFIX + ids.get(i),
          NomadUi.base(), NomadUi.dark(), NomadUi.bright(), boxW, BAR_HEIGHT, 0f);
      box.setChecked(ids.get(i).equals(active));
      if (prev == null) {
        panel.addUIElement(cell).inTL(0f, 0f);
      } else {
        panel.addUIElement(cell).rightOfTop(prev, TAB_PAD);
      }
      prev = cell;
    }
  }

  public static boolean isTabButton(Object buttonId) {
    return buttonId instanceof String && ((String) buttonId).startsWith(TAB_PREFIX);
  }

  public static String tabIdOf(Object buttonId) {
    return ((String) buttonId).substring(TAB_PREFIX.length());
  }

  public static float scrollOf(TooltipMakerAPI body) {
    if (body == null) {
      return 0f;
    }
    ScrollPanelAPI scroller = body.getExternalScroller();
    return scroller == null ? 0f : scroller.getYOffset();
  }

  public static void applyScroll(TooltipMakerAPI body, float yOffset) {
    if (body == null || yOffset <= 0f) {
      return;
    }
    ScrollPanelAPI scroller = body.getExternalScroller();
    if (scroller != null) {
      scroller.setYOffset(yOffset);
    }
  }
}
