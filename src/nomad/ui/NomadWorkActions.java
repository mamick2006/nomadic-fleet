package nomad.ui;

import java.awt.Color;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import nomad.NomadStrings;
import nomad.campaign.NomadCrew;
import nomad.campaign.NomadHistory;
import nomad.campaign.econ.model.BasicNeeds;
import nomad.campaign.econ.model.FlotillaState;
import nomad.campaign.econ.model.Marines;
import nomad.campaign.econ.model.Priority;
import nomad.campaign.econ.model.StoreFlags;
import nomad.campaign.econ.model.Stores;
import nomad.campaign.econ.model.TickSheet;
import nomad.campaign.econ.model.WorkOrders;
import nomad.campaign.econ.model.WorkState;
import nomad.campaign.econ.model.Works;
import nomad.campaign.split.NomadSplitter;
import nomad.data.NomadFleetData;

public final class NomadWorkActions {

  private NomadWorkActions() {
  }

  static final String P = "fl_";
  private static final String SEP = "*";

  public static final String ON = "on";
  public static final String OFF = "off";
  public static final String MIN = "min";
  public static final String EXACT = "=";
  public static final String TYPED = "typed";
  public static final String MAX_IN = "max";
  public static final String MAX_OUT = "-max";
  public static final String FLAG_IN = "IN";
  public static final String FLAG_OUT = "OUT";
  public static final String FLAG_SELL = "SELL";
  public static final String FLAG_SELL_CARGO = "SELL_CARGO";

  public enum Verb {
    SELECT,
    PICK,
    PICK_CLOSE,
    BUILD,
    UPGRADE,
    DOWNGRADE,
    SCRAP,
    CANCEL,
    MOTHBALL,
    REVIVE,
    PRIORITY,
    REMOTE,
    TARGET,
    FLAG,
    FILL,
    CREW,
    SPLIT,
    STORE_OPEN,
    CREW_OPEN,
    CHECK;

    final String wire = name().toLowerCase(Locale.ROOT);
  }

  static final class Press {

    final Verb verb;
    private final String[] parts;

    Press(Verb verb, String[] parts) {
      this.verb = verb;
      this.parts = parts;
    }

    String arg(int i) {
      return i + 1 < parts.length ? parts[i + 1] : "";
    }
  }

  public static String id(Verb verb, Object... args) {
    StringBuilder sb = new StringBuilder(P).append(verb.wire);
    if (args != null) {
      for (Object arg : args) {
        sb.append(SEP).append(arg);
      }
    }
    return sb.toString();
  }

  static Press parse(Object buttonId) {
    if (!(buttonId instanceof String) || !((String) buttonId).startsWith(P)) {
      return null;
    }
    String[] parts = ((String) buttonId).substring(P.length()).split("\\*");
    for (Verb verb : Verb.values()) {
      if (verb.wire.equals(parts[0])) {
        return new Press(verb, parts);
      }
    }
    return null;
  }

  public static String label(Verb verb) {
    switch (verb) {
      case SELECT:
        return NomadStrings.get("nomad_flotilla", "screen_verb_select");
      case PICK:
        return NomadStrings.get("nomad_flotilla", "screen_verb_pick");
      case PICK_CLOSE:
        return NomadStrings.get("nomad_flotilla", "screen_verb_pick_close");
      case BUILD:
        return NomadStrings.get("nomad_flotilla", "screen_verb_build");
      case UPGRADE:
        return NomadStrings.get("nomad_flotilla", "screen_verb_upgrade");
      case DOWNGRADE:
        return NomadStrings.get("nomad_flotilla", "screen_verb_downgrade");
      case SCRAP:
        return NomadStrings.get("nomad_flotilla", "screen_verb_scrap");
      case CANCEL:
        return NomadStrings.get("nomad_flotilla", "screen_verb_cancel");
      case MOTHBALL:
        return NomadStrings.get("nomad_flotilla", "screen_verb_mothball");
      case REVIVE:
        return NomadStrings.get("nomad_flotilla", "screen_verb_revive");
      case PRIORITY:
        return NomadStrings.get("nomad_flotilla", "screen_verb_priority");
      case REMOTE:
        return NomadStrings.get("nomad_flotilla", "screen_verb_remote");
      case TARGET:
        return NomadStrings.get("nomad_flotilla", "screen_verb_target");
      case FLAG:
        return NomadStrings.get("nomad_flotilla", "screen_verb_flag");
      case FILL:
        return NomadStrings.get("nomad_flotilla", "screen_verb_fill");
      case CREW:
        return NomadStrings.get("nomad_flotilla", "screen_verb_crew");
      case SPLIT:
        return NomadStrings.get("nomad_flotilla", "screen_verb_split");
      case CREW_OPEN:
        return NomadStrings.get("nomad_flotilla", "ui3_move_crew");
      default:
        return verb.wire;
    }
  }

  public static float targetAfter(NomadFlotillaView view, String good, String arg) {
    if (Works.MARINES.equals(good)) {
      return marinesTargetAfter(view, arg);
    }
    TickSheet.Store store = view.sheet.stores.get(good);
    if (store == null) {
      return 0f;
    }
    float now = view.state.target(good);
    if (MIN.equals(arg)) {
      return store.minTarget;
    }
    String a = TYPED.equals(arg) ? exactArg(NomadStoresPanel.typedTarget()) : arg;
    if (a == null) {
      return now;
    }
    float perUnit = Stores.spacePerUnit(view.state.storyDifficulty, view.rules, view.shared);
    boolean exact = a.startsWith(EXACT);
    float n = number(exact ? a.substring(EXACT.length()) : a);
    return NomadPanelFigures.targetAfter(now, view.sheet.stock(good), store.minTarget,
        view.sheet.spaceNeeds(), perUnit, exact, n);
  }

  static float marinesTargetAfter(NomadFlotillaView view, String arg) {
    float now = Marines.target(view.state, view.shared);
    if (MIN.equals(arg)) {
      return 0f;
    }
    String a = TYPED.equals(arg) ? exactArg(NomadWorkPanel.typedMarines()) : arg;
    if (a == null) {
      return now;
    }
    boolean exact = a.startsWith(EXACT);
    float n = number(exact ? a.substring(EXACT.length()) : a);
    return NomadPanelFigures.targetAfter(now, 0f, 0f, 0f, 0f, exact, n);
  }

  public static String exactArg(String typed) {
    long n = NomadPanelFigures.parseTarget(typed);
    return n < 0 ? null : EXACT + n;
  }

  public static int crewMoved(NomadFlotillaView view, String arg) {
    int in = (int) Math.floor(view.crewInMax);
    int out = (int) Math.floor(view.crewOutMax);
    if (MAX_IN.equals(arg)) {
      return in;
    }
    if (MAX_OUT.equals(arg)) {
      return -out;
    }
    int n = Math.round(number(arg));
    return n >= 0 ? Math.min(n, in) : -Math.min(-n, out);
  }

  public static BasicNeeds.Reading needsAfterTarget(NomadFlotillaView view, String good,
      String arg) {
    FlotillaState changed = view.state.copy();
    changed.target.put(good, Float.valueOf(targetAfter(view, good, arg)));
    return view.needsAfter(changed);
  }

  public static BasicNeeds.Reading needsAfterCrew(NomadFlotillaView view, String arg) {
    FlotillaState changed = view.state.copy();
    changed.residents = Math.max(0f, changed.residents + crewMoved(view, arg));
    return view.needsAfter(changed);
  }

  static boolean handle(Object buttonId, NomadFleetData data) {
    Press p = parse(buttonId);
    if (p == null || data == null) {
      return false;
    }
    switch (p.verb) {
      case SELECT:
        NomadFlotillaTab.toggleSelected(data, p.arg(0));
        return true;
      case PICK:
        NomadFlotillaTab.setPicking(data, true);
        return true;
      case PICK_CLOSE:
        NomadFlotillaTab.setPicking(data, false);
        return true;
      case SPLIT:
        if (NomadSplitter.canSplit() == null) {
          NomadSplitter.split();
          data.markChanged();
        }
        return true;
      case STORE_OPEN:
        NomadStoresPanel.pressStore(p.arg(0));
        return true;
      case CREW_OPEN:
        NomadStoresPanel.pressCrew();
        return true;
      case CHECK:
        NomadFlotillaTab.showFirstCounted(data);
        return true;
      default:
        break;
    }

    NomadFlotillaView view = NomadFlotillaView.forOrders(data);
    if (!view.live) {
      return true;
    }
    List<WorkState> works = data.getWorkStates();
    String id = p.arg(0);
    switch (p.verb) {
      case BUILD: {
        WorkOrders.Quote q = view.quoteBuild(id);
        if (q.allowed() && settle(q)) {
          WorkOrders.build(works, id, data.nextWorkOrder(), view.catalog);
          NomadFlotillaTab.setPicking(data, false);
        }
        break;
      }
      case UPGRADE: {
        WorkOrders.Quote q = view.quoteUpgrade(id);
        if (q.allowed() && settle(q)) {
          WorkOrders.upgrade(works, id, view.catalog);
        }
        break;
      }
      case DOWNGRADE: {
        WorkOrders.Quote q = view.quoteDowngrade(id);
        if (q.allowed() && settle(q)) {
          int to = sizeOf(view, id) - 1;
          WorkOrders.downgrade(works, id);
          NomadHistory.add(data, NomadStrings.get("nomad_history", "hist_work_downgraded",
              view.workName(id), "" + to));
        }
        break;
      }
      case SCRAP: {
        WorkOrders.Quote q = view.quoteScrap(id);
        if (q.allowed() && settle(q)) {
          WorkOrders.scrap(works, id);
          NomadHistory.add(data, NomadStrings.get("nomad_history", "hist_work_scrapped",
              view.workName(id)));
        }
        break;
      }
      case CANCEL: {
        WorkOrders.Quote q = view.quoteCancel(id);
        if (q.allowed() && settle(q)) {
          WorkOrders.cancel(works, id);
          NomadHistory.add(data, NomadStrings.get("nomad_history", "hist_work_cancelled",
              view.workName(id)));
        }
        break;
      }
      case MOTHBALL:
      case REVIVE: {
        boolean mothball = p.verb == Verb.MOTHBALL;
        if (view.quoteMothball(id, mothball).allowed()) {
          WorkOrders.setMothballed(works, id, mothball);
          NomadHistory.add(data, NomadStrings.get("nomad_history",
              mothball ? "hist_work_mothballed" : "hist_work_revived", view.workName(id)));
        }
        break;
      }
      case PRIORITY: {
        Priority priority = priority(p.arg(1));
        if (priority != null && view.work(id) != null) {
          WorkOrders.setPriority(works, id, priority);
        }
        break;
      }
      case REMOTE: {
        boolean on = ON.equals(id);
        if (view.quoteRemote(on).allowed()) {
          NomadHistory.addPressEvent(data, WorkOrders.switchRemote(works, on));
        }
        break;
      }
      case TARGET:
        if (Works.MARINES.equals(id)) {
          if (view.work(Works.MARINE_BARRACKS) != null) {
            data.setMarinesTarget(targetAfter(view, id, p.arg(1)));
          }
        } else if (view.sheet.stores.containsKey(id)) {
          data.setStoreTarget(id, targetAfter(view, id, p.arg(1)));
        }
        if (TYPED.equals(p.arg(1))) {
          NomadStoresPanel.releaseTargetField();
        }
        break;
      case FLAG: {
        int bit = flagBit(p.arg(1));
        if (bit != 0 && view.sheet.stores.containsKey(id)) {
          int flags = data.getStoreFlags(id);
          data.setStoreFlags(id, StoreFlags.with(flags, bit, !StoreFlags.has(flags, bit)));
        }
        break;
      }
      case FILL:
        fill(view, data);
        break;
      case CREW: {
        int n = crewMoved(view, id);
        if (n > 0) {
          NomadCrew.addCrewToFlotilla(n);
        } else if (n < 0 && NomadCrew.canTakeCrew(-n)) {
          NomadCrew.takeCrewFromFlotilla(-n);
        }
        break;
      }
      default:
        break;
    }
    data.markChanged();
    return true;
  }

  private static boolean settle(WorkOrders.Quote q) {
    CampaignFleetAPI fleet = NomadFlotillaView.playerFleet();
    CargoAPI purse = fleet == null ? null : fleet.getCargo();
    if (purse == null) {
      return false;
    }
    if (q.credits > 0f) {
      if (purse.getCredits().get() < q.credits) {
        return false;
      }
      purse.getCredits().subtract(q.credits);
    } else if (q.credits < 0f) {
      purse.getCredits().add(-q.credits);
    }
    return true;
  }

  private static void fill(NomadFlotillaView view, NomadFleetData data) {
    CampaignFleetAPI fleet = NomadFlotillaView.playerFleet();
    CargoAPI yours = fleet == null ? null : fleet.getCargo();
    CargoAPI hold = data.getFlotillaCargo();
    if (yours == null || hold == null) {
      return;
    }
    Map<String, Float> take = Stores.fill(view.state, view.catalog, view.rules, view.shared);
    for (Map.Entry<String, Float> e : take.entrySet()) {
      float units = Math.min(e.getValue().floatValue(), yours.getCommodityQuantity(e.getKey()));
      if (units > 0.001f) {
        yours.removeCommodity(e.getKey(), units);
        hold.addCommodity(e.getKey(), units);
      }
    }
  }

  static boolean needsConfirm(Object buttonId) {
    Press p = parse(buttonId);
    if (p == null) {
      return false;
    }
    switch (p.verb) {
      case SPLIT:
        return true;
      case TARGET:
      case CREW:
        return NomadFleetData.exists() && leavingAfter(p, NomadFleetData.getInstance()) != null;
      default:
        return false;
    }
  }

  private static BasicNeeds.Reading leavingAfter(Press p, NomadFleetData data) {
    NomadFlotillaView view = NomadFlotillaView.forOrders(data);
    if (!view.live) {
      return null;
    }
    String id = p.arg(0);
    BasicNeeds.Reading after;
    switch (p.verb) {
      case TARGET:
        if (Works.MARINES.equals(id)) {
          return null;
        }
        after = needsAfterTarget(view, id, p.arg(1));
        break;
      case CREW:
        after = needsAfterCrew(view, id);
        break;
      default:
        return null;
    }
    return view.wouldStartLeaving(after) ? after : null;
  }

  static void confirmPrompt(Object buttonId, TooltipMakerAPI prompt, NomadFleetData data) {
    Press p = parse(buttonId);
    if (p == null || data == null) {
      return;
    }
    if (p.verb == Verb.SPLIT) {
      NomadFlotillaTab.splitLines(prompt, true);
      return;
    }
    NomadFlotillaView view = NomadFlotillaView.forOrders(data);
    String id = p.arg(0);
    switch (p.verb) {
      case TARGET:
        flotillaPara(prompt, "confirm_target", NomadUi.hl(), NomadUi.commodityName(id),
            NomadUi.i(targetAfter(view, id, p.arg(1))));
        break;
      case CREW: {
        int n = crewMoved(view, id);
        flotillaPara(prompt, n >= 0 ? "confirm_crew_in" : "screen_confirm_crew_out",
            NomadUi.hl(), "" + Math.abs(n));
        if (n < 0) {
          flotillaPara(prompt, "cards_tip_cost", NomadUi.hl(),
              NomadUi.credits(NomadCrew.priceToTake(-n)));
        }
        break;
      }
      default:
        return;
    }
    BasicNeeds.Reading after = leavingAfter(p, data);
    if (after != null) {
      flotillaPara(prompt, "confirm_leave", NomadUi.warn(),
          NomadFlotillaView.levelName(after.level),
          NomadUi.pct(view.rules.leavePerMonth(after.level)));
    }
  }

  private static void flotillaPara(TooltipMakerAPI t, String key, Color hl, String... args) {
    para(t, NomadStrings.get("nomad_flotilla", key, (Object[]) args), hl, args);
  }

  static void para(TooltipMakerAPI t, String filled, Color hl, String... args) {
    LabelAPI line = t.addPara(filled, 3f);
    line.setHighlight(args);
    line.setHighlightColor(hl);
  }

  static String lowerWarning(NomadFlotillaView view, String workId, int toSize) {
    if (view == null || workId == null) {
      return null;
    }
    return nomad.campaign.works.WorkPrereqChecks.lowerWarning(view.works(), workId, toSize,
        NomadFlotillaView.FLOTILLA);
  }

  private static int sizeOf(NomadFlotillaView view, String workId) {
    WorkState w = view.work(workId);
    return w == null ? 0 : w.size;
  }

  static String confirmLabel(Object buttonId) {
    Press p = parse(buttonId);
    if (p == null) {
      return "";
    }
    switch (p.verb) {
      case SPLIT:
        return NomadStrings.get("nomad_flotilla", "screen_split_accept");
      case CANCEL:
      case TARGET:
      case CREW:
        return NomadStrings.get("nomad_flotilla", "screen_verb_confirm");
      default:
        return label(p.verb);
    }
  }

  private static float number(String s) {
    try {
      return Float.parseFloat(s);
    } catch (NumberFormatException e) {
      return 0f;
    }
  }

  private static Priority priority(String name) {
    for (Priority p : Priority.values()) {
      if (p.name().equals(name)) {
        return p;
      }
    }
    return null;
  }

  public static int flagBit(String name) {
    if (FLAG_IN.equals(name)) {
      return StoreFlags.IN;
    }
    if (FLAG_OUT.equals(name)) {
      return StoreFlags.OUT;
    }
    if (FLAG_SELL.equals(name)) {
      return StoreFlags.SELL;
    }
    if (FLAG_SELL_CARGO.equals(name)) {
      return StoreFlags.SELL_CARGO;
    }
    return 0;
  }
}
