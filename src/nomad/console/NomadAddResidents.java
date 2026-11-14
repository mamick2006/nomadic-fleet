package nomad.console;

import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.Console;

import nomad.NomadStrings;

public class NomadAddResidents implements BaseCommand {

  @Override
  public CommandResult runCommand(String args, CommandContext context) {
    if (!context.isInCampaign()) {
      Console.showMessage(NomadStrings.get("nomad_console", "console_wrong_context"));
      return CommandResult.WRONG_CONTEXT;
    }
    NomadConsoleResidents.Result r = NomadConsoleResidents.run(args);
    Console.showMessage(r.message);
    if (r.badSyntax) {
      return CommandResult.BAD_SYNTAX;
    }
    return r.added > 0 ? CommandResult.SUCCESS : CommandResult.ERROR;
  }
}
