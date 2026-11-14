package nomad.campaign;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignClockAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.util.Misc;

import nomad.NomadStrings;
import nomad.campaign.econ.EconConstants;
import nomad.campaign.econ.model.TickFeed;
import nomad.campaign.econ.model.TickSheet;
import nomad.campaign.split.NomadSplitter;
import nomad.campaign.tables.TableEffects;
import nomad.campaign.works.WorksCampaign;
import nomad.campaign.works.WorksEffects;
import nomad.data.NomadFleetData;
import nomad.intel.NomadIntel;
import nomad.ui.NomadItems;
import nomad.ui.NomadProgress;

public class NomadEconomyScript implements EconomyTickListener {

  private static final String PLUGIN_NODE_PREFIX = "node_id_nomad_row_";

  @Override
  public void reportEconomyTick(int iterIndex) {
    if (!NomadFleetData.exists()) {
      return;
    }
    NomadFleetData data = NomadFleetData.getInstance();

    NomadIntel.ensureExists(data);

    TickSheet sheet = NomadModel.economyTick(data);
    afterTick(data, sheet, NomadModel.flotillaFleet());
  }

  private static void afterTick(NomadFleetData data, TickSheet sheet, CampaignFleetAPI fleet) {
    List<String> problems = new ArrayList<String>();
    TableEffects.Payload asked = TableEffects.runWorkTick(sheet, problems);
    merge(asked, TableEffects.runItemTick(sheet, problems));
    warn(problems);
    if (sheet.shared != null
        && (!sheet.shared.workPlugins.isEmpty() || data.getActiveWorkEffects() != null)) {
      data.setActiveWorkEffects(TableEffects.activeWorks(sheet.shared));
    }
    EconConstants k = NomadEcon.constants();
    if (sheet.s.paused || fleet == null) {
      takeOffEffects(data, k);
      return;
    }
    NomadModel.addToStores(data, asked.commodities, sheet, fleet);
    for (Map.Entry<String, Float> e : asked.credits.entrySet()) {
      if (e.getValue() != null) {
        NomadModel.payIncome(PLUGIN_NODE_PREFIX + e.getKey(), rowName(e.getKey(), sheet),
            e.getValue().floatValue());
      }
    }
    if (!sheet.s.detached) {
      NomadSplitter.collectOwed(data, fleet);
    }

    boolean needsMet = sheet.needs.level.atLeastSpartan();
    WorksEffects.applyFleetEffects(fleet, needsMet, asked.fleetStats, k);
    WorksCampaign.onTick(fleet, data, sheet.monthFrac);
    NomadProgress.advance(data, sheet.s.elapsedDays, false);
  }

  private static void takeOffEffects(NomadFleetData data, EconConstants k) {
    WorksEffects.clearAllFleetEffects(k);
    WorksCampaign.clearColonyEffects(data);
  }

  public static void standDown(NomadFleetData data) {
    List<String> problems = new ArrayList<String>();
    EconConstants k = NomadEcon.constants();
    TableEffects.clearAllActive(k, problems);
    warn(problems);
    takeOffEffects(data, k);
  }

  private static void merge(TableEffects.Payload into, TableEffects.Payload other) {
    for (Map.Entry<String, Float> e : other.credits.entrySet()) {
      Float held = into.credits.get(e.getKey());
      into.credits.put(e.getKey(), (held == null ? 0f : held) + e.getValue());
    }
    for (Map.Entry<String, Float> e : other.commodities.entrySet()) {
      Float held = into.commodities.get(e.getKey());
      into.commodities.put(e.getKey(), (held == null ? 0f : held) + e.getValue());
    }
    into.fleetStats.putAll(other.fleetStats);
  }

  private static String rowName(String id, TickSheet sheet) {
    return sheet.catalog.work(id) != null ? NomadModel.workName(id, sheet.catalog)
        : NomadItems.displayName(id);
  }

  private static void warn(List<String> problems) {
    for (String problem : problems) {
      Global.getLogger(NomadEconomyScript.class).warn("Nomad Flotilla: " + problem);
    }
  }

  @Override
  public void reportEconomyMonthEnd() {
    if (!NomadFleetData.exists()) {
      return;
    }
    NomadFleetData data = NomadFleetData.getInstance();

    if (!data.isPaused()) {
      WorksCampaign.onMonthEnd(data);
    }
    WorksCampaign.closeGateMonth(data);

    if (data.isPaused() && data.getResidents() >= 1f) {
      MessageIntel intel = new MessageIntel(
          NomadStrings.get("nomad_messages", "msg_paused"), Misc.getNegativeHighlightColor());
      String icon = NomadModel.icon("nomad_intel_paused");
      if (icon != null) {
        intel.setIcon(icon);
      }
      Global.getSector().getCampaignUI().addMessage(intel);
    }

    int owed = TickFeed.owedAtMonthEnd(data.isDetached(), data.getSuppliesOwed());
    if (owed > 0) {
      NomadTickLog.postOwed(owed);
    }

    int used = data.takeMonthUsed();
    if (used > 0) {
      NomadHistory.add(data, NomadStrings.get("nomad_history",
          used == 1 ? "hist_consumed_one" : "hist_consumed_many", Integer.valueOf(used)));
    }
    int trained = data.takeMonthMarines();
    if (trained > 0) {
      NomadHistory.add(data, NomadStrings.get("nomad_history",
          trained == 1 ? "hist_marines_trained_one" : "hist_marines_trained_many",
          Integer.valueOf(trained)));
    }
    int made = data.takeMonthMade();
    if (made > 0) {
      NomadHistory.add(data, NomadStrings.get("nomad_history",
          made == 1 ? "hist_made_one" : "hist_made_many", Integer.valueOf(made)));
    }

    float joined = data.getMonthResidentsGrown();
    float left = data.getMonthResidentsLost();
    boolean changed = joined >= 1f || left >= 1f;
    if (changed || NomadHistoryLines.hasUnheadedLines(data.getHistory())) {
      CampaignClockAPI ended = monthEnded(Global.getSector().getClock());
      Integer cycle = Integer.valueOf(ended.getCycle());
      String month = ended.getMonthString();
      Integer residents = Integer.valueOf(Math.round(data.getResidents()));
      Integer in = Integer.valueOf(Math.round(joined));
      Integer out = Integer.valueOf(Math.round(left));
      NomadHistory.addDigest(data, joined >= 1f && left >= 1f
          ? NomadStrings.get("nomad_history", "komsg_hist_month_both", cycle, month, residents,
              in, out)
          : joined >= 1f
              ? NomadStrings.get("nomad_history", "komsg_hist_month_joined", cycle, month,
                  residents, in)
              : left >= 1f
                  ? NomadStrings.get("nomad_history", "komsg_hist_month_left", cycle, month,
                      residents, out)
                  : NomadStrings.get("nomad_history", "hist_month_unchanged", cycle, month,
                      residents));
    }
    data.resetMonthCounters();
  }

  private static final long DAY_MS = 86400000L;

  private static CampaignClockAPI monthEnded(CampaignClockAPI now) {
    return now.createClock(now.getTimestamp() - now.getDay() * DAY_MS);
  }
}
