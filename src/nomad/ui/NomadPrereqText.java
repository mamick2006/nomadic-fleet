package nomad.ui;

import java.util.ArrayList;
import java.util.List;

import nomad.campaign.econ.model.Prereq;

public final class NomadPrereqText {

  private NomadPrereqText() {
  }

  public interface Names {
    String item(String id);

    String hull(String id);

    String faction(String id);

    String standing(String level);

    String work(String id);
  }

  public static String fragment(Prereq p, Names names,
      NomadWorkCardModel.Strings flotillaStrings) {
    switch (p.kind) {
      case ITEM:
        return flotillaStrings.get("prereq_item", or(p.values, names, true));
      case SHIP:
        return flotillaStrings.get("prereq_ship", or(p.values, names, false));
      case LEVEL:
        return flotillaStrings.get("prereq_level", Integer.toString(p.number));
      case OFFICERS:
        return p.number == 1 ? flotillaStrings.get("prereq_officers_one")
            : flotillaStrings.get("prereq_officers", Integer.toString(p.number));
      case STANDING: {
        List<String> factions = new ArrayList<String>();
        for (String id : p.values) {
          factions.add(names.faction(id));
        }
        return flotillaStrings.get("prereq_standing", names.standing(p.atLeast), join(factions));
      }
      case MISSION:
        return flotillaStrings.get("prereq_mission", quotedMissions(p));
      case COMMISSION: {
        if (p.values.isEmpty()) {
          return flotillaStrings.get("prereq_commission");
        }
        List<String> factions = new ArrayList<String>();
        for (String id : p.values) {
          factions.add(names.faction(id));
        }
        return flotillaStrings.get("prereq_commission_with", join(factions));
      }
      case COLONIES:
        return p.number == 1 ? flotillaStrings.get("prereq_colonies_one")
            : flotillaStrings.get("prereq_colonies", Integer.toString(p.number));
      case CONTACTS:
        return p.number == 1 ? flotillaStrings.get("prereq_contacts_one")
            : flotillaStrings.get("prereq_contacts", Integer.toString(p.number));
      case WORK: {
        String works = theWorks(p, names);
        return p.number <= 1 ? flotillaStrings.get("prereq_work", works)
            : flotillaStrings.get("prereq_work_size", works, Integer.toString(p.number));
      }
      case RESIDENTS:
        return flotillaStrings.get("prereq_residents", NomadPanelFigures.heads(p.number));
      case NONE:
      default:
        return "";
    }
  }

  private static String theWorks(Prereq p, Names names) {
    List<String> named = new ArrayList<String>();
    for (String id : p.values) {
      named.add(names.work(id));
    }
    return join(withThe(named, 1));
  }

  private static List<String> withThe(List<String> names, int from) {
    List<String> out = new ArrayList<String>();
    for (int i = 0; i < names.size(); i++) {
      out.add((i < from ? "" : "the ") + names.get(i));
    }
    return out;
  }

  public static String theWorksSubject(List<String> workNames,
      NomadWorkCardModel.Strings flotillaStrings) {
    if (workNames.isEmpty()) {
      return "";
    }
    List<String> the = withThe(workNames, 0);
    String last = the.remove(the.size() - 1);
    if (the.isEmpty()) {
      return NomadWorkCardModel.capFirst(last);
    }
    StringBuilder head = new StringBuilder();
    for (String n : the) {
      head.append(head.length() == 0 ? "" : ", ").append(n);
    }
    return NomadWorkCardModel.capFirst(
        flotillaStrings.get("needs_req_list_and", head.toString(), last));
  }

  public static String sentence(Prereq p, Names names,
      NomadWorkCardModel.Strings flotillaStrings) {
    String f = fragment(p, names, flotillaStrings);
    if (p.fromSize > 1) {
      return flotillaStrings.get("prereq_from_size", Integer.toString(p.fromSize), f);
    }
    return flotillaStrings.get("prereq_whole", NomadWorkCardModel.capFirst(f));
  }

  public static String itSentence(Prereq p, Names names,
      NomadWorkCardModel.Strings flotillaStrings) {
    if (p.fromSize > 1) {
      return sentence(p, names, flotillaStrings);
    }
    return flotillaStrings.get("prereq_whole_it", fragment(p, names, flotillaStrings));
  }

  public static String line(Prereq p, Names names, float box, NomadText.Measure m,
      NomadWorkCardModel.Strings flotillaStrings) {
    List<String> tries = new ArrayList<String>();
    switch (p.kind) {
      case ITEM:
      case SHIP:
        things(p, names, tries, flotillaStrings);
        tries.add(flotillaStrings.get(p.kind == Prereq.Kind.ITEM ? "prereq_card_item_any"
            : "prereq_card_ship_any"));
        break;
      case STANDING: {
        String level = names.standing(p.atLeast);
        if (p.values.size() == 1) {
          tries.add(flotillaStrings.get("prereq_card_standing_with", level,
              names.faction(p.values.get(0))));
        }
        tries.add(flotillaStrings.get("prereq_card_standing_level", level));
        tries.add(flotillaStrings.get("prereq_card_standing"));
        break;
      }
      case MISSION:
        tries.add(flotillaStrings.get("prereq_card_thing", quotedMissions(p)));
        tries.add(flotillaStrings.get("prereq_card_mission"));
        break;
      case LEVEL:
        tries.add(fragment(p, names, flotillaStrings));
        tries.add(flotillaStrings.get("prereq_card_level_short", Integer.toString(p.number)));
        tries.add(flotillaStrings.get("prereq_card_unmet"));
        break;
      case COMMISSION:
        tries.add(flotillaStrings.get("prereq_commission"));
        tries.add(flotillaStrings.get("prereq_card_unmet"));
        break;
      case WORK:
        if (p.values.size() == 1 && p.number > 1) {
          String name = names.work(p.values.get(0));
          String size = Integer.toString(p.number);
          tries.add(flotillaStrings.get("prereq_card_work_size", name, size));
          int space = name.lastIndexOf(' ');
          if (space > 0 && space < name.length() - 1) {
            tries.add(flotillaStrings.get("prereq_card_work_size", name.substring(space + 1), size));
          }
        } else {
          tries.add(fragment(p, names, flotillaStrings));
        }
        tries.add(flotillaStrings.get("prereq_card_work"));
        break;
      default:
        tries.add(fragment(p, names, flotillaStrings));
        tries.add(flotillaStrings.get("prereq_card_unmet"));
        break;
    }
    String text = tries.get(tries.size() - 1);
    for (String t : tries) {
      if (fits(t, box, m)) {
        text = t;
        break;
      }
    }
    return NomadWorkCardModel.capFirst(text);
  }

  private static void things(Prereq p, Names names, List<String> tries,
      NomadWorkCardModel.Strings flotillaStrings) {
    boolean item = p.kind == Prereq.Kind.ITEM;
    tries.add(flotillaStrings.get("prereq_card_thing", or(p.values, names, item)));
    if (p.values.size() > 1) {
      List<String> later = new ArrayList<String>();
      List<String> none = new ArrayList<String>();
      for (int i = 0; i < p.values.size(); i++) {
        String named = item ? names.item(p.values.get(i)) : names.hull(p.values.get(i));
        later.add(i == 0 ? named : bare(named));
        none.add(bare(named));
      }
      tries.add(flotillaStrings.get("prereq_card_thing", join(later)));
      tries.add(flotillaStrings.get("prereq_card_none", join(none)));
      String shared = null;
      for (String id : p.values) {
        String word = lastWord(item ? names.item(id) : names.hull(id));
        if (shared == null) {
          shared = word;
        } else if (!shared.equalsIgnoreCase(word)) {
          shared = "";
        }
      }
      if (shared != null && !shared.isEmpty()) {
        String lower = shared.toLowerCase(java.util.Locale.ROOT);
        tries.add(flotillaStrings.get("prereq_card_thing", article(lower) + " " + lower));
      }
    } else if (p.values.size() == 1) {
      String named = item ? names.item(p.values.get(0)) : names.hull(p.values.get(0));
      tries.add(flotillaStrings.get("prereq_card_none", bare(named)));
    }
  }

  private static String quotedMissions(Prereq p) {
    List<String> missions = new ArrayList<String>();
    for (String entry : p.values) {
      missions.add("\"" + nomad.campaign.econ.model.Prereqs.missionName(entry) + "\"");
    }
    return join(missions);
  }

  static String bare(String named) {
    if (named.startsWith("a ")) {
      return named.substring(2);
    }
    if (named.startsWith("an ")) {
      return named.substring(3);
    }
    return named;
  }

  private static String lastWord(String named) {
    String s = named.trim();
    int at = s.lastIndexOf(' ');
    return at < 0 ? s : s.substring(at + 1);
  }

  private static String article(String word) {
    return !word.isEmpty() && "aeiou".indexOf(word.charAt(0)) >= 0 ? "an" : "a";
  }

  public static String removeWarning(String workName,
      NomadWorkCardModel.Strings flotillaStrings) {
    return flotillaStrings.get("prereq_item_remove_warn", workName);
  }

  public static String lowerWarning(String workName,
      NomadWorkCardModel.Strings flotillaStrings) {
    return flotillaStrings.get("prereq_work_lower_warn", workName);
  }

  static boolean fits(String text, float box, NomadText.Measure m) {
    return NomadText.need(NomadWorkCardModel.capFirst(text), m) <= box;
  }

  private static String or(List<String> ids, Names names, boolean item) {
    List<String> named = new ArrayList<String>();
    for (String id : ids) {
      named.add(item ? names.item(id) : names.hull(id));
    }
    return join(named);
  }

  static String join(List<String> parts) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < parts.size(); i++) {
      if (i > 0) {
        sb.append(i == parts.size() - 1 ? " or " : ", ");
      }
      sb.append(parts.get(i));
    }
    return sb.toString();
  }
}
