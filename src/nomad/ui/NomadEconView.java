package nomad.ui;

import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;

import nomad.campaign.NomadCapacityUtil;
import nomad.campaign.NomadModel;
import nomad.campaign.econ.Commod;
import nomad.campaign.econ.model.MiningSystem;
import nomad.campaign.econ.model.NeedsLevel;
import nomad.campaign.econ.model.TickSheet;
import nomad.campaign.econ.model.Works;
import nomad.campaign.split.NomadSplitter;
import nomad.data.NomadFleetData;

public final class NomadEconView {

  public enum Status {
    OK, SHORT, PAUSED, DETACHED
  }

  public Status status = Status.OK;
  public boolean detached;

  public float residents;
  public float miningSpace;

  public final Map<String, Float> netPerMonth = new LinkedHashMap<String, Float>();
  public final Map<String, Float> storage = new LinkedHashMap<String, Float>();
  public final Map<String, Float> cap = new LinkedHashMap<String, Float>();

  public NomadMining.State mining = new NomadMining.State();

  public NeedsLevel level = NeedsLevel.NORMAL;
  public float efficiency = 1f;
  public float space;
  public float spaceWorks;
  public float spaceStores;
  public float freeSpace;
  public float berths;
  public float working;
  public float residentsInNeeds;

  public TickSheet sheet;
  public NomadPanelFigures.Standing standing;
  public int attention;
  public float suppliesMade;
  public float suppliesUsed;
  public float fleetSupplies;
  public float suppliesCover = -1f;
  public float suppliesOwed;
  public MiningSystem.Readout miningReadout;
  public String locationId;

  private static WeakReference<NomadFleetData> cachedFor;
  private static long cachedTick;
  private static long cachedSerial;
  private static NomadEconView cached;

  public static NomadEconView compute(NomadFleetData data) {
    long tick = data.getLastTickTimestamp();
    long serial = data.getAllocSerial();
    NomadEconView held = cached;
    if (held != null && cachedFor != null && cachedFor.get() == data && cachedTick == tick
        && cachedSerial == serial) {
      return held;
    }
    NomadEconView v = build(data);
    cached = v;
    cachedFor = new WeakReference<NomadFleetData>(data);
    cachedTick = tick;
    cachedSerial = serial;
    return v;
  }

  public static void invalidate() {
    cached = null;
    cachedFor = null;
  }

  private static NomadEconView build(NomadFleetData data) {
    NomadEconView v = new NomadEconView();
    v.residents = data.getResidents();

    CampaignFleetAPI fleet = NomadUi.flotillaFleet();
    boolean paused = fleet == null || data.isPaused();
    v.detached = !paused && (data.isDetached() || NomadSplitter.isDetached());

    TickSheet sheet = NomadModel.preview(data, fleet);
    read(v, sheet);
    v.status = paused ? Status.PAUSED : v.detached ? Status.DETACHED
        : v.standing.needsOrder() ? Status.SHORT : Status.OK;
    if (!paused && !v.detached) {
      v.fleetSupplies = NomadCapacityUtil.suppliesPerMonth(fleet.getFleetData());
      if (NomadPanelFigures.hasWorkMaking(sheet, Commod.SUPPLIES)) {
        v.suppliesCover = NomadPanelFigures.suppliesCover(v.suppliesMade, v.suppliesUsed,
            v.fleetSupplies);
      }
    }
    if (!paused && v.miningSpace > 0f) {
      v.miningReadout = NomadMining.readout(fleet, sheet.s, sheet.k);
    }
    v.locationId = locationId(fleet);
    return v;
  }

  public static String locationId(CampaignFleetAPI fleet) {
    return fleet == null || fleet.getContainingLocation() == null ? null
        : fleet.getContainingLocation().getId();
  }

  static void read(NomadEconView v, TickSheet sheet) {
    v.residents = sheet.residents;
    v.level = sheet.needs.level;
    v.efficiency = sheet.efficiency;
    v.space = sheet.space;
    v.spaceWorks = sheet.spaceWorks;
    v.spaceStores = sheet.spaceStores;
    v.freeSpace = sheet.spaceNeeds();
    v.berths = sheet.berths;
    v.working = sheet.working;
    v.residentsInNeeds = sheet.residentsInNeeds();
    v.sheet = sheet;
    v.standing = NomadPanelFigures.standing(sheet);
    v.attention = v.standing.attention();
    v.suppliesMade = NomadPanelFigures.madePerMonth(sheet, Commod.SUPPLIES);
    v.suppliesUsed = NomadPanelFigures.usedPerMonth(sheet, Commod.SUPPLIES);
    v.suppliesOwed = sheet.suppliesOwed;

    for (String c : Commod.STORED) {
      float stock = sheet.stock(c);
      TickSheet.Store store = sheet.stores.get(c);
      v.storage.put(c, Float.valueOf(stock));
      v.cap.put(c, Float.valueOf(store == null ? 0f : store.target));
      v.netPerMonth.put(c, Float.valueOf(store == null ? 0f : store.netPerMonth));
    }

    v.miningSpace = NomadPanelFigures.miningSpace(sheet.run(Works.MINING_RIG),
        sheet.efficiency);
  }
}
