package nomad.ui;

import java.awt.Color;
import java.util.Collections;
import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import nomad.NomadStrings;
import nomad.campaign.econ.Commod;
import nomad.campaign.econ.model.Capacity;
import nomad.campaign.econ.model.Marines;
import nomad.campaign.econ.model.Prereq;
import nomad.campaign.econ.model.Prereqs;
import nomad.campaign.econ.model.Production;
import nomad.campaign.econ.model.Requirement;
import nomad.campaign.econ.model.Roster;
import nomad.campaign.econ.model.Staffing;
import nomad.campaign.econ.model.TickSheet;
import nomad.campaign.econ.model.WorkDef;
import nomad.campaign.econ.model.WorkOrders;
import nomad.campaign.econ.model.WorkSize;
import nomad.campaign.econ.model.WorkState;
import nomad.campaign.econ.model.Works;
import nomad.campaign.works.WorkInfo;
import nomad.campaign.works.WorkPrereqChecks;
import nomad.campaign.works.WorkPrereqTable;

public final class NomadWorkHover {

  private NomadWorkHover() {
  }

  private static final float PAD = 3f;
  private static final float OPAD = 10f;
  private static final float LABEL_W = NomadWorkHoverRows.LABEL_W;
  private static final float ROW_H = 18f;
  private static final float ICON = NomadWorkHoverRows.GOOD_ICON;
  private static final float MIN_W = 400f;

  public static float width(WorkDef def, WorkState w) {
    int sizes = def == null ? 1 : def.maxSize();
    return Math.max(MIN_W, Math.max(labelW(def) + sizes * sizeW(def, w), nowWidth(def)) + 10f);
  }

  public static float width(WorkDef def) {
    return width(def, null);
  }

  private static float sizeW(WorkDef def, WorkState w) {
    return NomadWorkHoverRows.sizeW(def, NomadWorkHoverRows.builtSize(w),
        NomadWorkHoverRows.buildingSize(w), WorkInfo.tierRelief(), NomadFlotillaView.FLOTILLA,
        NomadUi.PARA);
  }

  private static float labelW(WorkDef def) {
    if (def == null) {
      return LABEL_W;
    }
    float goods = 0f;
    float texts = 0f;
    for (NomadWorkHoverRows.Row row : NomadWorkHoverRows.rows(def)) {
      String text = rowText(row);
      if (text != null) {
        texts = Math.max(texts, NomadText.need(text, NomadUi.PARA));
      } else {
        goods = Math.max(goods, NomadText.need(goodName(row.key), NomadUi.PARA));
      }
    }
    return NomadWorkHoverRows.labelW(goods, texts);
  }

  private static float nowLabelW(WorkDef def) {
    float widest = 0f;
    if (def != null) {
      for (int size = 1; size <= def.maxSize(); size++) {
        for (String good : def.size(size).makes.keySet()) {
          widest = Math.max(widest, NomadText.need(goodName(good), NomadUi.PARA));
        }
      }
    }
    return widest <= 0f ? 0f : widest + NomadWorkCardModel.NOW_LABEL_PAD;
  }

  private static float nowWidth(WorkDef def) {
    float label = nowLabelW(def);
    if (label <= 0f) {
      return 0f;
    }
    return label + NomadWorkCardModel.NOW_SIZE_W
        + (Works.MINING_RIG.equals(def.id) ? NomadWorkCardModel.NOW_DEPOSIT_W : 0f)
        + NomadWorkCardModel.NOW_EFFICIENCY_W + NomadWorkCardModel.NOW_OTHER_W
        + NomadWorkCardModel.NOW_RESULT_W;
  }

  public static TooltipMakerAPI.TooltipCreator tip(final NomadFlotillaView view,
      final String workId) {
    return NomadTip.guard(new NomadTip(width(view.catalog.work(workId), view.work(workId))) {
      @Override
      public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object param) {
        write(tooltip, view, workId);
        if (view.work(workId) != null) {
          tooltip.addPara(NomadStrings.get("nomad_flotilla", "hover_click_to_manage"),
              NomadUi.good(), OPAD);
        }
      }
    });
  }

  public static void write(TooltipMakerAPI t, NomadFlotillaView view, String workId) {
    WorkDef def = view.catalog.work(workId);
    t.addTitle(view.workName(workId), NomadUi.base());
    if (def == null) {
      t.addPara(NomadStrings.get("nomad_flotilla", "hover_unknown_work"), NomadUi.gray(), OPAD);
      return;
    }
    WorkState w = view.work(workId);

    t.addPara(categoryLine(def), NomadUi.gray(), PAD);
    String desc = description(def);
    if (!desc.isEmpty()) {
      t.addPara(desc, OPAD);
    }
    String says = WorkInfo.ownLine(workId, Math.max(1, NomadWorkHoverRows.builtSize(w)));
    if (says != null) {
      LabelAPI line = t.addPara(says, PAD);
      NomadTable.highlightNumbers(line, says, NomadUi.hl());
    }
    if (Works.ORGAN_HARVESTER.equals(workId) && view.sheet != null
        && Production.inputFactor(workId, Collections.singletonList(Commod.ORGANS),
            Works.RESIDENTS, view.sheet.s.items, view.sheet.s.normalOrStory, view.rules,
            view.shared) <= 0f) {
      t.addPara(NomadStrings.get("nomad_flotilla", "hover_harvester_embryo"), NomadUi.good(),
          PAD);
    }
    if (Works.GATE_FREIGHT_OFFICE.equals(workId) && w != null && w.isBuilt() && view.data != null) {
      boolean done = view.data.isGateTransitThisMonth();
      t.addPara(NomadStrings.get("nomad_flotilla",
          done ? "hover_gate_freight_office_done" : "hover_gate_freight_office_none"),
          done ? NomadUi.good() : NomadUi.gray(), PAD);
    }
    stateLine(t, view, def, w);
    doingLines(t, view, def, w);
    marinesLine(t, view, w);
    nowGrid(t, view, def, w);
    sizes(t, view, workId);
    nextLine(t, view, def, w);
  }

  static void sizes(TooltipMakerAPI t, NomadFlotillaView view, String workId) {
    WorkDef def = view.catalog.work(workId);
    if (def == null) {
      return;
    }
    t.addSectionHeading(def.isSized() ? NomadStrings.get("nomad_flotilla", "hover_sizes")
        : NomadStrings.get("nomad_flotilla", "hover_size_one"),
        NomadUi.base(), NomadUi.dark(), Alignment.MID, OPAD);
    table(t, def, view.work(workId), width(def, view.work(workId)) - 10f,
        view.rules.needsTierRelief);
    needsLines(t, view, def);
    if (Works.MINING_RIG.equals(def.id)) {
      t.addPara(NomadStrings.get("nomad_flotilla", "hover_mining_note"), NomadUi.gray(), PAD);
    }
    if (Works.BROKERAGE.equals(def.id)) {
      brokerageLines(t, view, def, view.work(workId));
    }
    prereqLines(t, view, def.id);
  }

  private static void prereqLines(TooltipMakerAPI t, NomadFlotillaView view, String workId) {
    String named = null;
    if (view.work(workId) != null) {
      NomadWorkCardModel.State st = view.cardState(workId);
      if (st.why == NomadWorkCardModel.Why.NEEDS_PREREQ && st.prereq != null
          && st.shown != NomadWorkCardModel.Shown.RUNNING
          && st.shown != NomadWorkCardModel.Shown.BUILDING) {
        named = st.prereq.id;
      }
    }
    boolean first = true;
    for (Prereq row : WorkPrereqTable.read()) {
      if (!row.work.equals(workId) || row.id.equals(named)) {
        continue;
      }
      boolean unmet = false;
      for (Prereqs.Reading r : view.state.prereqs) {
        unmet |= r.row.id.equals(row.id) && !r.met;
      }
      t.addPara(NomadPrereqText.sentence(row, WorkPrereqChecks.NAMES, NomadFlotillaView.FLOTILLA),
          unmet ? NomadUi.bad() : NomadUi.text(), first ? OPAD : PAD);
      first = false;
    }
  }

  private static void brokerageLines(TooltipMakerAPI t, NomadFlotillaView view, WorkDef def,
      WorkState w) {
    String share = NomadUi.pct(view.rules.brokerageBonusShare);
    t.addPara(NomadStrings.get("nomad_flotilla", "hover_brokerage_note", share), NomadUi.gray(),
        PAD);
    int built = NomadWorkHoverRows.builtSize(w);
    if (built <= 0 || view.paused || view.sheet == null
        || view.cardState(w.id).shown != NomadWorkCardModel.Shown.RUNNING) {
      return;
    }
    TickSheet sheet = view.sheet;
    NomadWorkHoverRows.Fee fee = NomadWorkHoverRows.fee(def.size(built), sheet.efficiency,
        sheet.s.items, sheet.s.normalOrStory, view.rules, view.shared);
    String at = NomadWorkHoverRows.factor(sheet.efficiency);
    String row = NomadUi.credits(fee.row);
    String paidAt = NomadWorkHoverRows.factor(fee.paidAt);
    String credits = NomadUi.credits(fee.credits);
    String units = NomadWorkHoverRows.amount(fee.units);
    if (fee.hasYield()) {
      NomadWorkActions.para(t, NomadStrings.get("nomad_flotilla", "hover_brokerage_now_yield",
          at, row, paidAt, NomadWorkHoverRows.factor(fee.yield), credits, units), NomadUi.hl(),
          credits, units);
    } else {
      NomadWorkActions.para(t, NomadStrings.get("nomad_flotilla", "hover_brokerage_now", at,
          row, paidAt, credits, units), NomadUi.hl(), credits, units);
    }
  }

  private static String categoryLine(WorkDef def) {
    String kind = def.isProduction()
        ? NomadStrings.get("nomad_flotilla", "hover_kind_production")
        : NomadStrings.get("nomad_flotilla", "hover_kind_support");
    return NomadStrings.get("nomad_flotilla", "hover_kind_line", kind, categoryName(def.category));
  }

  static String categoryName(String category) {
    if (category == null) {
      return "";
    }
    switch (category) {
      case WorkDef.CAT_ECONOMY:
        return NomadStrings.get("nomad_flotilla", "hover_cat_economy");
      case WorkDef.CAT_CAPACITY:
        return NomadStrings.get("nomad_flotilla", "hover_cat_capacity");
      case WorkDef.CAT_FLEET:
        return NomadStrings.get("nomad_flotilla", "hover_cat_fleet");
      case WorkDef.CAT_COMBAT:
        return NomadStrings.get("nomad_flotilla", "hover_cat_combat");
      case WorkDef.CAT_EVENT:
        return NomadStrings.get("nomad_flotilla", "hover_cat_fortune");
      default:
        return category;
    }
  }

  static String description(WorkDef def) {
    if (!def.ownRow) {
      return def.effect == null ? "" : def.effect;
    }
    switch (def.id) {
      case Works.HYDROPONIC_FARM:
        return NomadStrings.get("nomad_flotilla", "hover_desc_hydroponic_farm");
      case Works.DOMESTIC_GOODS_PLANT:
        return NomadStrings.get("nomad_flotilla", "hover_desc_domestic_goods_plant");
      case Works.SUPPLIES_FACTORY:
        return NomadStrings.get("nomad_flotilla", "hover_desc_supplies_factory");
      case Works.LUXURY_GOODS_FACTORY:
        return NomadStrings.get("nomad_flotilla", "hover_desc_luxury_goods_factory");
      case Works.FUEL_PLANT:
        return NomadStrings.get("nomad_flotilla", "hover_desc_fuel_plant");
      case Works.REFINERY:
        return NomadStrings.get("nomad_flotilla", "hover_desc_refinery");
      case Works.MACHINERY_FACTORY:
        return NomadStrings.get("nomad_flotilla", "hover_desc_machinery_factory");
      case Works.MINING_RIG:
        return NomadStrings.get("nomad_flotilla", "hover_desc_mining_rig");
      case Works.FOUNDRY:
        return NomadStrings.get("nomad_flotilla", "hover_desc_foundry");
      case Works.BROKERAGE:
        return NomadStrings.get("nomad_flotilla", "hover_desc_brokerage");
      case Works.MILITARY_FABRICATOR:
        return NomadStrings.get("nomad_flotilla", "hover_desc_military_fabricator");
      case Works.DRUG_LAB:
        return NomadStrings.get("nomad_flotilla", "hover_desc_drug_lab");
      case Works.ORGAN_HARVESTER:
        return NomadStrings.get("nomad_flotilla", "hover_desc_organ_harvester");
      case Works.MARINE_BARRACKS:
        return NomadStrings.get("nomad_flotilla", "hover_desc_marine_barracks");
      case Works.CLONING_VATS:
        return NomadStrings.get("nomad_flotilla", "hover_desc_cloning_vats");
      case Works.SURVEY_OFFICE:
        return NomadStrings.get("nomad_flotilla", "hover_desc_survey_office");
      case Works.STEALTH_CONTROL_ROOM:
        return NomadStrings.get("nomad_flotilla", "hover_desc_stealth_control_room");
      case Works.SENSOR_STATION:
        return NomadStrings.get("nomad_flotilla", "hover_desc_sensor_station");
      case Works.SLIPSTREAM_CHART_ROOM:
        return NomadStrings.get("nomad_flotilla", "hover_desc_slipstream_chart_room");
      case Works.REPAIR_YARD:
        return NomadStrings.get("nomad_flotilla", "hover_desc_repair_yard");
      case Works.RAID_TRAINING_GROUND:
        return NomadStrings.get("nomad_flotilla", "hover_desc_raid_training_ground");
      case Works.FAMILY_CABINS:
        return NomadStrings.get("nomad_flotilla", "hover_desc_family_cabins");
      case Works.GUILD_HALL:
        return NomadStrings.get("nomad_flotilla", "hover_desc_guild_hall");
      case Works.DAMAGE_CONTROL_ROOM:
        return NomadStrings.get("nomad_flotilla", "hover_desc_damage_control_room");
      case Works.GUNNERY_RANGE:
        return NomadStrings.get("nomad_flotilla", "hover_desc_gunnery_range");
      case Works.DORMITORY:
        return NomadStrings.get("nomad_flotilla", "hover_desc_dormitory");
      case Works.OFFICER_ACADEMY:
        return NomadStrings.get("nomad_flotilla", "hover_desc_officer_academy");
      case Works.HOSPITAL:
        return NomadStrings.get("nomad_flotilla", "hover_desc_hospital");
      case Works.WATCH_STATIONS:
        return NomadStrings.get("nomad_flotilla", "hover_desc_watch_stations");
      case Works.TINKERING_FACILITIES:
        return NomadStrings.get("nomad_flotilla", "hover_desc_tinkering_facilities");
      case Works.STORM_SHIELDING_WORKSHOP:
        return NomadStrings.get("nomad_flotilla", "hover_desc_storm_shielding_workshop");
      case Works.PASSENGER_TERMINAL:
        return NomadStrings.get("nomad_flotilla", "hover_desc_passenger_terminal");
      case Works.PURCHASING_OFFICE:
        return NomadStrings.get("nomad_flotilla", "hover_desc_purchasing_office");
      case Works.TUG_BAY:
        return NomadStrings.get("nomad_flotilla", "hover_desc_tug_bay");
      case Works.PANOPTICON:
        return NomadStrings.get("nomad_flotilla", "hover_desc_panopticon", "").trim();
      case Works.RESTORATION_BAY:
        return NomadStrings.get("nomad_flotilla", "hover_desc_restoration_bay");
      case Works.DOMAIN_PARTS_WORKSHOP:
        return NomadStrings.get("nomad_flotilla", "hover_desc_domain_parts_workshop");
      case Works.LOBSTER_FARM:
        return NomadStrings.get("nomad_flotilla", "hover_desc_lobster_farm");
      case Works.GATE_FREIGHT_OFFICE:
        return NomadStrings.get("nomad_flotilla", "hover_desc_gate_freight_office");
      case Works.PATROL_SUPPLY_DEPOT:
        return NomadStrings.get("nomad_flotilla", "hover_desc_patrol_supply_depot");
      case Works.SCHOOL:
        return NomadStrings.get("nomad_flotilla", "hover_desc_school");
      case Works.CARGO_SHUTTLE_BAY:
        return NomadStrings.get("nomad_flotilla", "hover_desc_cargo_shuttle_bay");
      case Works.INSURANCE_OFFICE:
        return nomad.campaign.NomadSurvival.isSurvivalCampaign()
            ? NomadStrings.get("nomad_flotilla", "hover_desc_insurance_office_survival")
            : NomadStrings.get("nomad_flotilla", "hover_desc_insurance_office");
      case Works.ASSAULT_COURSE:
        return NomadStrings.get("nomad_flotilla", "hover_desc_assault_course");
      case Works.WELDING_SHOP:
        return NomadStrings.get("nomad_flotilla", "hover_desc_welding_shop");
      case Works.SHOPS:
        return NomadStrings.get("nomad_flotilla", "hover_desc_shops");
      case Works.THEATER:
        return NomadStrings.get("nomad_flotilla", "hover_desc_theater");
      case Works.LUXURY_QUARTERS:
        return NomadStrings.get("nomad_flotilla", "hover_desc_luxury_quarters");
      default:
        return def.effect == null ? "" : def.effect;
    }
  }

  private static void stateLine(TooltipMakerAPI t, NomadFlotillaView view, WorkDef def,
      WorkState w) {
    if (w == null) {
      return;
    }
    NomadWorkCardModel.State st = view.cardState(w.id);
    int shownSize = NomadWorkCardModel.shownSize(w);
    TickSheet.WorkRun run = view.sheet.run(w.id);
    float need = Staffing.staffNeed(w.id, def.size(shownSize), view.sheet.s.items,
        view.sheet.s.normalOrStory, view.sheet.shared);
    float has = run == null ? 0f : Math.min(need, run.staffOwn);
    String state = NomadWorkCardModel.shownName(st.shown, NomadFlotillaView.FLOTILLA);
    String size = Integer.toString(shownSize);
    String hasText = NomadPanelFigures.heads(has);
    String needText = NomadPanelFigures.heads(need);
    LabelAPI line = t.addPara(NomadStrings.get("nomad_flotilla", "ui2_tip_state", state, size,
        NomadFlotillaView.priorityName(w.priority()), hasText, needText), OPAD);
    line.setHighlight(state, size, hasText, needText);
    line.setHighlightColors(NomadWorkCard.toneColor(NomadWorkCardModel.tone(st.shown)),
        NomadUi.hl(), NomadUi.hl(), NomadUi.hl());

    int built = NomadWorkHoverRows.builtSize(w);
    int building = NomadWorkHoverRows.buildingSize(w);
    String daysLeft = NomadUi.days(Math.max(0f, view.shared.workBuildDays - w.buildDays));
    if (building > 0 && built == 0) {
      t.addPara(NomadStrings.get("nomad_flotilla", "hover_state_first_build"),
          PAD, NomadUi.hl(), daysLeft);
    } else if (building > 0) {
      t.addPara(NomadStrings.get("nomad_flotilla", "hover_state_upgrading"), PAD, NomadUi.hl(),
          Integer.toString(built), Integer.toString(building), daysLeft);
    }
  }

  private static void doingLines(TooltipMakerAPI t, NomadFlotillaView view, WorkDef def,
      WorkState w) {
    if (w == null) {
      return;
    }
    NomadWorkCardModel.State st = view.cardState(w.id);
    if (st.shown == NomadWorkCardModel.Shown.RUNNING
        || st.shown == NomadWorkCardModel.Shown.BUILDING) {
      return;
    }
    String name = view.workName(w.id);
    String good = st.good == null ? null : goodName(st.good);
    String why = st.why == NomadWorkCardModel.Why.NEEDS_PREREQ && st.prereq != null
        ? NomadPrereqText.sentence(st.prereq, WorkPrereqChecks.NAMES, NomadFlotillaView.FLOTILLA)
        : NomadWorkCardModel.capFirst(NomadWorkCardModel.whyText(st.why, good,
            NomadFlotillaView.levelName(view.sheet.needs.level), NomadFlotillaView.FLOTILLA));
    if (!why.isEmpty()) {
      t.addPara(why, NomadWorkCard.toneColor(NomadWorkCardModel.tone(st.shown)), PAD);
    }
    switch (st.shown) {
      case IDLE: {
        boolean detached = view.sheet != null && view.sheet.s.detached;
        if (good != null && st.why == NomadWorkCardModel.Why.INPUT_EMPTY) {
          t.addPara(NomadStrings.get("nomad_flotilla",
              detached ? "hover_idle_input_detached" : "hover_idle_input", good), PAD);
        } else if (good != null && st.why == NomadWorkCardModel.Why.OUTPUT_FULL) {
          t.addPara(NomadStrings.get("nomad_flotilla",
              detached ? "hover_idle_output_detached" : "hover_idle_output", good), PAD);
        } else if (st.why == NomadWorkCardModel.Why.NO_RESIDENTS_TO_SPARE) {
          t.addPara(NomadStrings.get("nomad_flotilla", "goods_idle_no_residents_to_spare",
              name), PAD);
        } else if (st.why == NomadWorkCardModel.Why.MARINES_DETACHED) {
          t.addPara(NomadStrings.get("nomad_flotilla", "marines_idle_detached"), PAD);
        } else if (st.why == NomadWorkCardModel.Why.MARINES_AT_TARGET) {
          String have = NomadUi.i(view.fleetMarines);
          String want = NomadUi.i(Marines.target(view.state, view.shared));
          if (Math.round(view.fleetMarines) == 1) {
            t.addPara(NomadStrings.get("nomad_flotilla", "marines_idle_at_target_one"), PAD,
                NomadUi.hl(), have, want);
          } else {
            t.addPara(NomadStrings.get("nomad_flotilla", "marines_idle_at_target"), PAD,
                NomadUi.hl(), have, want);
          }
        } else if (st.why == NomadWorkCardModel.Why.MARINES_NO_ROOM) {
          t.addPara(NomadStrings.get("nomad_flotilla", "marines_idle_no_room"), PAD);
        } else if (st.why == NomadWorkCardModel.Why.NO_BERTHS_FOR_RESIDENTS) {
          t.addPara(NomadStrings.get("nomad_flotilla", "vats_idle_berths"), PAD);
        }
        break;
      }
      case UNSTAFFED:
        t.addPara(NomadStrings.get("nomad_flotilla", "card_unstaffed_tip", name), PAD);
        break;
      case MOTHBALLED:
        t.addPara(NomadStrings.get("nomad_flotilla", "card_mothballed_tip", name), PAD);
        break;
      case STOPPED:
        if (def.isProduction()) {
          t.addPara(NomadStrings.get("nomad_flotilla", "needs_effect_none_run"), PAD);
        } else if (!view.sheet.needs.level.atLeastSpartan()) {
          t.addPara(NomadStrings.get("nomad_flotilla", "hover_support_rule"), PAD);
        }
        break;
      case SUSPENDED:
        suspendedLines(t, view, w, st);
        break;
      case STANDING_DOWN:
        standingLines(t, view);
        break;
      case LACKING:
        t.addPara(NomadStrings.get("nomad_flotilla", "prereq_disabled_tip"), PAD);
        break;
      default:
        break;
    }
  }

  private static void marinesLine(TooltipMakerAPI t, NomadFlotillaView view, WorkState w) {
    if (w == null || !Works.MARINE_BARRACKS.equals(w.id) || !w.isBuilt()
        || view.cardState(w.id).shown != NomadWorkCardModel.Shown.RUNNING) {
      return;
    }
    NomadWorkPanel.marinesLine(t, view.fleetMarines, Marines.target(view.state, view.shared),
        PAD);
  }

  private static void standingLines(TooltipMakerAPI t, NomadFlotillaView view) {
    TickSheet sheet = view.sheet;
    String limit = Integer.toString(sheet.supportLimit);
    NomadWorkActions.para(t, NomadStrings.get("nomad_flotilla", "roster_standing_tip", limit),
        NomadUi.hl(), limit);
    float next = Roster.nextAt(sheet.residents, view.rules);
    if (Float.isInfinite(next)) {
      t.addPara(NomadStrings.get("nomad_flotilla", "roster_standing_back_top"), PAD);
    } else {
      String at = NomadPanelFigures.heads(next);
      NomadWorkActions.para(t,
          NomadStrings.get("nomad_flotilla", "roster_standing_back_next", at), NomadUi.hl(), at);
    }
  }

  private static void suspendedLines(TooltipMakerAPI t, NomadFlotillaView view, WorkState w,
      NomadWorkCardModel.State st) {
    t.addPara(NomadStrings.get("nomad_flotilla", "fixui_suspended_tip"), PAD);
    switch (st.why) {
      case RETURNING:
        t.addPara(NomadStrings.get("nomad_flotilla", "fixui_suspended_returning"), PAD);
        break;
      case NO_SPACE: {
        String held = NomadUi.i(Capacity.spaceHeld(w, view.catalog));
        String free = NomadUi.i(view.sheet.spaceNeeds());
        NomadWorkActions.para(t,
            NomadStrings.get("nomad_flotilla", "fixui_suspended_no_space", held, free),
            NomadUi.hl(), held, free);
        break;
      }
      case NEEDS_SHORT:
        t.addPara(NomadStrings.get("nomad_flotilla", "fixui_suspended_needs_short"), PAD);
        break;
      default:
        break;
    }
  }

  private static void nowGrid(TooltipMakerAPI t, NomadFlotillaView view, WorkDef def,
      WorkState w) {
    if (w == null || !def.isProduction()
        || !NomadWorkCardModel.works(view.cardState(w.id).shown)) {
      return;
    }
    List<NomadWorkCardModel.Now> rows = NomadWorkCardModel.nowRows(view.sheet,
        view.sheet.run(w.id));
    if (rows.isEmpty()) {
      return;
    }
    boolean deposit = false;
    boolean other = false;
    for (NomadWorkCardModel.Now row : rows) {
      deposit |= row.mined();
      other |= row.hasOther();
    }
    t.addSectionHeading(NomadStrings.get("nomad_flotilla", "ui2_tip_now_head"), NomadUi.base(),
        NomadUi.dark(), Alignment.MID, OPAD);

    float labelW = Math.max(nowLabelW(def), NomadWorkCardModel.NOW_LABEL_PAD);
    float width = width(def, w) - 10f;
    CustomPanelAPI head = Global.getSettings().createCustom(width, ROW_H, null);
    float x = labelW;
    x = nowCell(head, x, NomadWorkCardModel.NOW_SIZE_W,
        NomadStrings.get("nomad_flotilla", "cards_size", Integer.toString(w.size)),
        NomadUi.gray());
    if (deposit) {
      x = nowCell(head, x, NomadWorkCardModel.NOW_DEPOSIT_W,
          NomadStrings.get("nomad_flotilla", "ui2_tip_col_deposit"), NomadUi.gray());
    }
    x = nowCell(head, x, NomadWorkCardModel.NOW_EFFICIENCY_W,
        NomadStrings.get("nomad_flotilla", "panels_eff_title"), NomadUi.gray());
    if (other) {
      x = nowCell(head, x, NomadWorkCardModel.NOW_OTHER_W,
          NomadStrings.get("nomad_flotilla", "ui2_tip_col_other"), NomadUi.gray());
    }
    nowCell(head, x, NomadWorkCardModel.NOW_RESULT_W,
        NomadStrings.get("nomad_flotilla", "ui2_tip_col_now"), NomadUi.gray());
    t.addCustom(head, PAD);

    for (NomadWorkCardModel.Now row : rows) {
      CustomPanelAPI line = Global.getSettings().createCustom(width, ROW_H, null);
      goodLabel(line, row.good, labelW);
      x = labelW;
      x = nowCell(line, x, NomadWorkCardModel.NOW_SIZE_W, NomadWorkHoverRows.amount(row.base),
          NomadUi.text());
      if (deposit) {
        x = nowCell(line, x, NomadWorkCardModel.NOW_DEPOSIT_W,
            row.mined() ? NomadWorkCardModel.times(row.deposit) : "", NomadUi.text());
      }
      x = nowCell(line, x, NomadWorkCardModel.NOW_EFFICIENCY_W, NomadUi.pct(row.efficiency),
          NomadUi.text());
      if (other) {
        x = nowCell(line, x, NomadWorkCardModel.NOW_OTHER_W,
            NomadWorkCardModel.times(row.other), NomadUi.text());
      }
      nowCell(line, x, NomadWorkCardModel.NOW_RESULT_W, NomadWorkCardModel.compact(row.now),
          NomadUi.hl());
      t.addCustom(line, 0f);
    }
  }

  private static float nowCell(CustomPanelAPI line, float x, float w, String text, Color c) {
    cell(line, x, w, text, c, Alignment.MID);
    return x + w;
  }

  private static void table(TooltipMakerAPI t, WorkDef def, WorkState w, float width,
      float relief) {
    int n = def.maxSize();
    int built = NomadWorkHoverRows.builtSize(w);
    int building = NomadWorkHoverRows.buildingSize(w);
    float sizeW = sizeW(def, w);

    float labelW = labelW(def);
    float firstPad = PAD;
    if (NomadWorkHoverRows.headRow(def)) {
      CustomPanelAPI head = Global.getSettings().createCustom(width, ROW_H, null);
      cell(head, 0f, labelW, NomadStrings.get("nomad_flotilla", "hover_col_size"),
          NomadUi.gray(), Alignment.LMID);
      for (int s = 1; s <= n; s++) {
        String label = NomadWorkHoverRows.headCell(s, built, building, NomadFlotillaView.FLOTILLA);
        Color c = s == built ? NomadUi.hl() : s == building ? NomadUi.base() : NomadUi.gray();
        cell(head, labelW + (s - 1) * sizeW, sizeW, label, c, Alignment.MID);
      }
      t.addCustom(head, PAD);
      firstPad = 0f;
    }

    List<NomadWorkHoverRows.Row> rows = NomadWorkHoverRows.rows(def);
    NomadWorkHoverRows.Kind group = null;
    for (NomadWorkHoverRows.Row row : rows) {
      boolean goods = row.kind == NomadWorkHoverRows.Kind.MAKES
          || row.kind == NomadWorkHoverRows.Kind.USES;
      if (goods && row.kind != group) {
        t.addPara(row.kind == NomadWorkHoverRows.Kind.MAKES
            ? NomadStrings.get("nomad_flotilla", "hover_group_makes")
            : NomadStrings.get("nomad_flotilla", "hover_group_uses"), NomadUi.gray(), PAD);
      }
      group = row.kind;
      CustomPanelAPI line = Global.getSettings().createCustom(width, ROW_H, null);
      label(line, row, labelW);
      for (int s = 1; s <= n; s++) {
        Color c = s == built ? NomadUi.hl() : NomadUi.text();
        cell(line, labelW + (s - 1) * sizeW, sizeW, NomadWorkHoverRows.cell(row, s, relief), c,
            Alignment.MID);
      }
      t.addCustom(line, firstPad);
      firstPad = 0f;
    }
  }

  private static void label(CustomPanelAPI line, NomadWorkHoverRows.Row row, float w) {
    String text = rowText(row);
    if (text != null) {
      cell(line, 0f, w, text, NomadUi.text(), Alignment.LMID);
      return;
    }
    goodLabel(line, row.key, w);
  }

  private static String rowText(NomadWorkHoverRows.Row row) {
    String text;
    switch (row.kind) {
      case SPACE:
        text = NomadStrings.get("nomad_flotilla", "hover_row_space");
        break;
      case STAFF:
        text = NomadStrings.get("nomad_flotilla", "hover_row_staff");
        break;
      case COST:
        text = NomadStrings.get("nomad_flotilla", "hover_row_cost");
        break;
      case NEEDS:
        text = NomadStrings.get("nomad_flotilla", "hover_row_needs");
        break;
      case PARAM:
        text = paramName(row.label != null ? row.label : row.key);
        break;
      default:
        text = null;
        break;
    }
    return text;
  }

  private static void goodLabel(CustomPanelAPI line, String good, float w) {
    float x = NomadWorkHoverRows.GOOD_INDENT;
    String sprite = goodIcon(good);
    if (sprite != null) {
      TooltipMakerAPI ic = line.createUIElement(ICON, ROW_H, false);
      ic.addImage(sprite, ICON, ICON, 1f);
      line.addUIElement(ic).inTL(x, 0f);
      x += NomadWorkHoverRows.GOOD_ICON + NomadWorkHoverRows.GOOD_ICON_GAP;
    }
    cell(line, x, w - x, goodName(good), NomadUi.text(), Alignment.LMID);
  }

  private static void cell(CustomPanelAPI line, float x, float w, String text, Color c,
      Alignment align) {
    NomadCell.text(line, x, 0f, w, ROW_H, text, c, align, 1f);
  }

  private static void needsLines(TooltipMakerAPI t, NomadFlotillaView view, WorkDef def) {
    if (view.sheet == null || view.paused) {
      return;
    }
    WorkState w = view.work(def.id);
    int size = w == null ? 1 : Math.max(1, w.size);
    if (w != null && w.isBuilt() && !Requirement.counts(w)) {
      t.addPara(NomadStrings.get("nomad_flotilla", "hover_needs_off"), NomadUi.gray(), PAD);
      return;
    }
    Requirement.Reading r = view.sheet.requirement;
    if (r != null && r.atLimit() && Requirement.isOrdinary(def.size(size))) {
      String limit = NomadHomeFigures.changePercent(r.limit);
      NomadWorkActions.para(t, NomadStrings.get("nomad_flotilla", "hover_needs_limit", limit),
          NomadUi.hl(), limit);
    }
  }

  private static void nextLine(TooltipMakerAPI t, NomadFlotillaView view, WorkDef def,
      WorkState w) {
    int next = NomadWorkHoverRows.nextSize(def, w);
    if (next == 0) {
      if (w != null && !w.isBuilding() && def.isSized()) {
        t.addPara(NomadStrings.get("nomad_flotilla", "hover_next_largest"), NomadUi.gray(), OPAD);
      }
      return;
    }
    WorkSize step = def.size(next);
    String days = NomadUi.days(view.shared.workBuildDays);
    WorkOrders.Quote q;
    if (w == null) {
      q = view.quoteBuild(def.id);
      t.addPara(NomadStrings.get("nomad_flotilla", "hover_next_build"), OPAD, NomadUi.hl(),
          NomadUi.credits(step.cost), days);
    } else {
      q = view.quoteUpgrade(def.id);
      t.addPara(NomadStrings.get("nomad_flotilla", "hover_next_upgrade"), OPAD, NomadUi.hl(),
          Integer.toString(next), NomadUi.credits(step.cost), days);
    }
    if (!view.live) {
      t.addPara(view.notLiveReason(), NomadUi.bad(), PAD);
    } else if (!q.allowed() && q.refusal != WorkOrders.Refusal.NEEDS_PREREQ) {
      t.addPara(NomadFlotillaView.refusalText(q), NomadUi.bad(), PAD);
    }
  }

  static String goodName(String good) {
    if (Works.POINTS.equals(good)) {
      return NomadStrings.get("nomad_flotilla", "hover_good_points");
    }
    if (Works.CREDITS.equals(good)) {
      return NomadStrings.get("nomad_flotilla", "hover_good_credits");
    }
    if (Works.RESIDENTS.equals(good)) {
      return NomadStrings.get("nomad_flotilla", "panels_residents_title");
    }
    return NomadUi.commodityName(good);
  }

  static String goodIcon(String good) {
    if (Works.POINTS.equals(good)) {
      return NomadUi.iconSprite(NomadUi.POINT_ICON);
    }
    if (Works.CREDITS.equals(good)) {
      return NomadUi.iconSprite(NomadUi.CREDITS_ICON);
    }
    CommoditySpecAPI spec = NomadUi.spec(Works.RESIDENTS.equals(good)
        ? NomadWorkCardModel.pseudoIcon(good) : good);
    String icon = spec == null ? null : spec.getIconName();
    return icon == null || icon.isEmpty() ? null : icon;
  }

  static String paramName(String param) {
    if (param == null) {
      return "";
    }
    if ("insure_frac".equals(param) && nomad.campaign.NomadSurvival.isSurvivalCampaign()) {
      return NomadStrings.get("nomad_flotilla", "hover_param_insure_frac_survival");
    }
    switch (param) {
      case "survey_mult":
        return NomadStrings.get("nomad_flotilla", "hover_param_survey_mult");
      case "detected_mult":
        return NomadStrings.get("nomad_flotilla", "hover_param_detected_mult");
      case "sensor_pct":
        return NomadStrings.get("nomad_flotilla", "hover_param_sensor_pct");
      case "reveal_ly":
        return NomadStrings.get("nomad_flotilla", "hover_param_reveal_ly");
      case "cr_recovery_pct":
        return NomadStrings.get("nomad_flotilla", "hover_param_cr_recovery_pct");
      case "repair_pct":
        return NomadStrings.get("nomad_flotilla", "hover_param_repair_pct");
      case "ground_raid_pct":
        return NomadStrings.get("nomad_flotilla", "hover_param_ground_raid_pct");
      case "cr":
        return NomadStrings.get("nomad_flotilla", "hover_param_cr");
      case "efficiency":
        return NomadStrings.get("nomad_flotilla", "hover_param_efficiency");
      case "spartan_efficiency":
        return NomadStrings.get("nomad_flotilla", "hover_param_spartan_efficiency");
      case "repair_mult":
        return NomadStrings.get("nomad_flotilla", "hover_param_repair_mult");
      case "pd_range":
        return NomadStrings.get("nomad_flotilla", "hover_param_pd_range");
      case "pd_damage_pct":
        return NomadStrings.get("nomad_flotilla", "hover_param_pd_damage_pct");
      case "sale_units":
        return NomadStrings.get("nomad_flotilla", "hover_param_sale_units");
      case "berths":
        return NomadStrings.get("nomad_flotilla", "hover_param_berths");
      case "credits_per_month":
        return NomadStrings.get("nomad_flotilla", "hover_param_credits_per_month");
      case "growth_per_year":
        return NomadStrings.get("nomad_flotilla", "hover_param_growth_per_year");
      case "accessibility":
        return NomadStrings.get("nomad_flotilla", "hover_param_accessibility");
      case "insure_frac":
        return NomadStrings.get("nomad_flotilla", "hover_param_insure_frac");
      case "recover_mult":
        return NomadStrings.get("nomad_flotilla", "hover_param_recover_mult");
      case "marine_join_xp":
        return NomadStrings.get("nomad_flotilla", "hover_param_marine_join_xp");
      case "gate_credits":
        return NomadStrings.get("nomad_flotilla", "hover_param_gate_credits");
      case "needs_amenity":
        return NomadStrings.get("nomad_flotilla", "hover_param_needs_amenity");
      case "needs_raise":
        return NomadStrings.get("nomad_flotilla", "hover_param_needs_raise");
      default:
        return param;
    }
  }
}
