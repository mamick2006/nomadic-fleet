package nomad.console;

import nomad.NomadStrings;
import nomad.campaign.NomadCrew;
import nomad.campaign.NomadModel;
import nomad.campaign.econ.model.TickSheet;
import nomad.data.NomadFleetData;
import nomad.ui.NomadFlotillaView;

public final class NomadConsoleResidents {

  private NomadConsoleResidents() {
  }

  public static final class Result {
    public final String message;
    public final int added;
    public final boolean badSyntax;

    Result(String message, int added, boolean badSyntax) {
      this.message = message;
      this.added = added;
      this.badSyntax = badSyntax;
    }
  }

  public static Result run(String args) {
    int amount;
    try {
      amount = Integer.parseInt(args == null ? "" : args.trim());
    } catch (NumberFormatException e) {
      amount = 0;
    }
    if (amount <= 0) {
      return new Result(NomadStrings.get("nomad_console", "console_syntax"), 0, true);
    }
    if (!NomadFleetData.exists() || !NomadFleetData.getInstance().isFounded()) {
      return new Result(NomadStrings.get("nomad_console", "console_no_flotilla"), 0, false);
    }
    NomadFleetData data = NomadFleetData.getInstance();
    int added = NomadCrew.addResidents(data, amount);
    if (added <= 0) {
      return new Result(NomadStrings.get("nomad_console", "console_full"), 0, false);
    }
    String message = added < amount ? NomadStrings.get("nomad_console", "console_added_full", added)
        : NomadStrings.get("nomad_console", "console_added", added);
    return new Result(message + levelAfter(data), added, false);
  }

  private static String levelAfter(NomadFleetData data) {
    TickSheet sheet = NomadModel.preview(data, null);
    if (sheet.s.paused) {
      return "";
    }
    return " " + NomadStrings.get("nomad_console", "komsg_console_level",
        NomadFlotillaView.levelName(sheet.needs.level));
  }
}
