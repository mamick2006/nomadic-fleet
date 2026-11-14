package nomad.ui;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;

final class NomadConsoleRoute extends BaseCustomUIPanelPlugin {

  static final NomadConsoleRoute INSTANCE = new NomadConsoleRoute();

  private NomadConsoleRoute() {
  }

  @Override
  public void buttonPressed(Object buttonId) {
    NomadConsolePanel.routeButton(buttonId);
  }
}
