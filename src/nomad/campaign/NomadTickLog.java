package nomad.campaign;

import java.awt.Color;
import java.util.Locale;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.util.Misc;

import nomad.NomadStrings;
import nomad.campaign.econ.model.FlotillaEvent;
import nomad.campaign.econ.model.FlotillaState;
import nomad.campaign.econ.model.Prereq;
import nomad.campaign.econ.model.GlueRules;
import nomad.campaign.econ.model.NeedsLevel;
import nomad.campaign.econ.model.TickFeed;
import nomad.campaign.econ.model.TickSheet;
import nomad.campaign.works.WorkPrereqChecks;
import nomad.campaign.works.WorkPrereqTable;
import nomad.data.NomadFleetData;
import nomad.ui.NomadFlotillaView;
import nomad.ui.NomadPrereqText;
import nomad.ui.NomadWorkCardModel;

final class NomadTickLog {

  private NomadTickLog() {
  }

  static void record(NomadFleetData data, TickSheet sheet) {
    FlotillaState s = sheet.s;
    boolean orderGiven = TickFeed.orderGiven(data.getLastTickOrders(),
        TickFeed.orders(s.works, s.target, s.items, s.detached), data.getLastTickResidents(),
        s.residents);
    data.setLastTickStamp(TickFeed.orders(sheet.works, s.target, s.items, s.detached),
        sheet.residents);
    if (sheet.events.isEmpty()) {
      return;
    }
    for (FlotillaEvent e : GlueRules.historyEvents(sheet.events)) {
      NomadHistory.add(data, line(e, sheet));
    }
    for (TickFeed.Line line : TickFeed.lines(sheet.events, orderGiven)) {
      message(line, sheet);
    }
  }

  static String pressLine(FlotillaEvent e) {
    return e != null && e.type == FlotillaEvent.Type.REMOTE_SWITCHED ? line(e, null) : null;
  }

  private static String line(FlotillaEvent e, TickSheet sheet) {
    switch (e.type) {
      case LEVEL_CHANGED: {
        NeedsLevel now = TickFeed.level(e.subject);
        if (now == null) {
          return null;
        }
        return NomadStrings.get("nomad_history",
            now.ordinal() > Math.round(e.amount) ? "hist_level_up" : "hist_level_down",
            levelName(now));
      }
      case WORK_SUSPENDED:
        return NomadStrings.get("nomad_history", "hist_work_suspended",
            workName(e.subject, sheet));
      case WORK_RESUMED:
        return NomadStrings.get("nomad_history", "hist_work_resumed",
            workName(e.subject, sheet));
      case WORK_STOOD_DOWN:
        return NomadStrings.get("nomad_history", "hist_work_stood_down",
            workName(e.subject, sheet), Integer.toString(Math.round(e.amount)));
      case WORK_STOOD_UP:
        return NomadStrings.get("nomad_history", "hist_work_stood_up",
            workName(e.subject, sheet));
      case WORK_PREREQ_LOST:
        return prereqLost(e.subject, e.detail, sheet);
      case WORK_PREREQ_MET:
        return NomadStrings.get("nomad_history", "hist_work_stood_up",
            workName(e.subject, sheet));
      case WORK_UNSTAFFED:
        return NomadStrings.get("nomad_history", "hist_work_unstaffed",
            workName(e.subject, sheet));
      case WORK_STAFFED:
        return NomadStrings.get("nomad_history", "hist_work_staffed",
            workName(e.subject, sheet));
      case BUILD_DONE: {
        int size = Math.round(e.amount);
        String name = workName(e.subject, sheet);
        return size <= 1 ? NomadStrings.get("nomad_history", "hist_work_online", name)
            : NomadStrings.get("nomad_history", "hist_work_upgraded", name,
                Integer.valueOf(size));
      }
      case REMOTE_SWITCHED:
        return NomadStrings.get("nomad_history",
            e.amount >= 0.5f ? "hist_remote_on" : "hist_remote_off");
      case RESIDENTS_LEFT:
        return counted("hist_needs_left_one", "hist_needs_left_many", e.amount);
      case BERTHS_LOST:
        return counted("hist_berths_left_one", "hist_berths_left_many", e.amount);
      case STOCK_MOVED:
        return NomadStrings.get("nomad_history", "hist_stock_moved", units(e.amount),
            goodName(e.subject));
      case STOCK_LOST:
        return NomadStrings.get("nomad_history", "hist_stock_lost", units(e.amount),
            goodName(e.subject));
      case UPKEEP_OWED:
        return NomadStrings.get("nomad_history", "detach_hist_upkeep_owed", units(e.amount));
      case HOLD_FULL:
        return NomadStrings.get("nomad_history", "koout_hist_hold_full", units(e.amount),
            goodName(e.subject));
      case PAUSED:
        return NomadStrings.get("nomad_history", "hist_paused");
      case RESUMED:
        return NomadStrings.get("nomad_history", "hist_resumed");
      default:
        return null;
    }
  }

  private static void message(TickFeed.Line l, TickSheet sheet) {
    switch (l.kind) {
      case LEVEL_FELL_LOW: {
        NeedsLevel now = TickFeed.level(l.subject);
        boolean leaving = sheet.k.leavePerMonth(now) > 0f;
        post(leaving
            ? NomadStrings.get("nomad_messages", "komsg_msg_level_fell_leaving", levelName(now),
                percent(sheet.efficiency))
            : NomadStrings.get("nomad_messages", "komsg_msg_level_fell", levelName(now),
                percent(sheet.efficiency)), Tone.BAD);
        break;
      }
      case LEVEL_FELL:
        post(NomadStrings.get("nomad_messages", "komsg_msg_level_fell",
            levelName(TickFeed.level(l.subject)), percent(sheet.efficiency)), Tone.PLAIN);
        break;
      case LEVEL_ROSE:
        post(NomadStrings.get("nomad_messages", "komsg_msg_level_rose",
            levelName(TickFeed.level(l.subject)), percent(sheet.efficiency)), Tone.GOOD);
        break;
      case LEVEL_ROSE_LOW:
        post(NomadStrings.get("nomad_messages", "komsg_msg_level_rose_low",
            levelName(TickFeed.level(l.subject)), percent(sheet.efficiency)), Tone.PLAIN);
        break;
      case LEVEL_ROSE_BACK:
        post(NomadStrings.get("nomad_messages", "komsg_msg_level_rose_back",
            levelName(TickFeed.level(l.subject)), percent(sheet.efficiency)), Tone.GOOD);
        break;
      case WORKS_SUSPENDED:
        post(l.count == 1
            ? NomadStrings.get("nomad_messages", "msg_work_suspended",
                workName(l.subject, sheet))
            : NomadStrings.get("nomad_messages", "komsg_msg_works_suspended",
                Integer.valueOf(l.count), workName(l.subject, sheet),
                Integer.valueOf(l.count - 1)), Tone.BAD);
        break;
      case WORKS_UNSTAFFED:
        post(l.count == 1
            ? NomadStrings.get("nomad_messages", "msg_work_unstaffed",
                workName(l.subject, sheet))
            : NomadStrings.get("nomad_messages", "komsg_msg_works_unstaffed",
                Integer.valueOf(l.count), workName(l.subject, sheet),
                Integer.valueOf(l.count - 1)), Tone.BAD);
        break;
      case STOCK_LOST:
        post(l.count == 1
            ? NomadStrings.get("nomad_history", "hist_stock_lost", units(l.amount),
                goodName(l.subject))
            : NomadStrings.get("nomad_messages", "komsg_msg_stock_lost", units(l.amount),
                Integer.valueOf(l.count)), Tone.BAD);
        break;
      case UPKEEP_OWED:
        post(NomadStrings.get("nomad_messages", "detach_msg_upkeep_owed", units(l.amount)),
            Tone.BAD);
        break;
      case CARGO_FULL:
        post(NomadStrings.get("nomad_messages", "koout_msg_cargo_full"), Tone.BAD);
        break;
      case FUEL_FULL:
        post(NomadStrings.get("nomad_messages", "koout_msg_fuel_full"), Tone.BAD);
        break;
      case WORKS_DISABLED:
        post(l.count == 1 ? prereqLost(l.subject, firstPrereqLost(sheet, l.subject), sheet)
            : NomadStrings.get("nomad_messages", "prereq_msg_works_disabled",
                Integer.valueOf(l.count), workName(l.subject, sheet),
                Integer.valueOf(l.count - 1)), Tone.BAD);
        break;
      case BUILDS_DONE: {
        int size = Math.round(l.amount);
        String name = workName(l.subject, sheet);
        post(l.count > 1
            ? NomadStrings.get("nomad_messages", "komsg_msg_builds_done",
                Integer.valueOf(l.count), name, Integer.valueOf(l.count - 1))
            : size <= 1 ? NomadStrings.get("nomad_history", "hist_work_online", name)
            : NomadStrings.get("nomad_history", "hist_work_upgraded", name,
                Integer.valueOf(size)), Tone.GOOD);
        break;
      }
      default:
        break;
    }
  }

  static void postOwed(int supplies) {
    post(NomadStrings.get("nomad_messages", "komsg_msg_owed_month", Misc.getWithDGS(supplies)),
        Tone.BAD);
  }

  private static String prereqLost(String workId, String rowId, TickSheet sheet) {
    Prereq row = WorkPrereqTable.row(rowId);
    String why = row == null ? "" : NomadPrereqText.itSentence(row, WorkPrereqChecks.NAMES,
        FLOTILLA);
    return NomadStrings.get("nomad_history", "hist_work_prereq_lost", workName(workId, sheet),
        why).trim();
  }

  private static String firstPrereqLost(TickSheet sheet, String workId) {
    for (FlotillaEvent e : sheet.events) {
      if (e.type == FlotillaEvent.Type.WORK_PREREQ_LOST && e.subject != null
          && e.subject.equals(workId)) {
        return e.detail;
      }
    }
    return null;
  }

  private static final NomadWorkCardModel.Strings FLOTILLA = NomadFlotillaView.FLOTILLA;

  private static String counted(String oneKey, String manyKey, float amount) {
    int n = Math.max(1, Math.round(amount));
    return n == 1 ? NomadStrings.get("nomad_history", oneKey, Integer.valueOf(n))
        : NomadStrings.get("nomad_history", manyKey, Integer.valueOf(n));
  }

  private static String units(float amount) {
    return Misc.getRoundedValueMaxOneAfterDecimal(amount);
  }

  private static String workName(String workId, TickSheet sheet) {
    return NomadModel.workName(workId, sheet.catalog);
  }

  private static String goodName(String good) {
    try {
      return Global.getSettings().getCommoditySpec(good).getName().toLowerCase(Locale.ROOT);
    } catch (RuntimeException e) {
      return String.valueOf(good).replace('_', ' ');
    }
  }

  private static String levelName(NeedsLevel level) {
    String key = "screen_level_" + level.key;
    return NomadStrings.get("nomad_flotilla", key);
  }

  private static String percent(float frac) {
    return Math.round(frac * 100f) + "%";
  }

  private enum Tone {
    BAD, PLAIN, GOOD
  }

  private static void post(String text, Tone tone) {
    if (Global.getSector() == null || Global.getSector().getCampaignUI() == null) {
      return;
    }
    Color color = tone == Tone.BAD ? Misc.getNegativeHighlightColor()
        : tone == Tone.GOOD ? Misc.getPositiveHighlightColor() : Misc.getBasePlayerColor();
    MessageIntel intel = new MessageIntel(text, color);
    String icon = NomadModel.icon(tone == Tone.BAD ? "nomad_intel_short" : "nomad_intel");
    if (icon != null) {
      intel.setIcon(icon);
    }
    Global.getSector().getCampaignUI().addMessage(intel);
  }
}
