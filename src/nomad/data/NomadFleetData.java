package nomad.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;

import nomad.campaign.econ.Commod;
import nomad.campaign.econ.model.GlueRules;

public class NomadFleetData {

  public static final String KEY = "nomad_fleet_data";

  public static NomadFleetData getInstance() {
    Map<String, Object> pd = Global.getSector().getPersistentData();
    Object o = pd.get(KEY);
    if (o instanceof NomadFleetData) {
      return (NomadFleetData) o;
    }
    NomadFleetData data = new NomadFleetData();
    pd.put(KEY, data);
    return data;
  }

  public static NomadFleetData getOrNull() {
    if (Global.getSector() == null) {
      return null;
    }
    Object o = Global.getSector().getPersistentData().get(KEY);
    return o instanceof NomadFleetData ? (NomadFleetData) o : null;
  }

  public static boolean exists() {
    if (Global.getSector() == null) {
      return false;
    }
    Object o = Global.getSector().getPersistentData().get(KEY);
    return (o instanceof NomadFleetData) && ((NomadFleetData) o).founded;
  }

  private float residents;
  private boolean founded;
  private boolean needsFoundingDialog;

  private long assemblyStartTs;
  @SuppressWarnings("unused")
  private float assemblyDays;
  private boolean foundingStoresSeeded;
  @SuppressWarnings("unused")
  private boolean foundingFloor;
  @SuppressWarnings("unused")
  private boolean startingAllowance;
  @SuppressWarnings("unused")
  private Map<String, Float> allowanceUnits;

  private float cachedSpace;
  private float cachedBerths;

  private float frozenSpace;
  private float frozenBerths;
  private boolean capacityFrozen;

  private Map<String, String> dedication;
  private long allocSerial;
  private CargoAPI flotillaCargo;

  private List<String> autoforgeQueue;
  private List<String> installedItems;

  private List<String> history;
  private long lastTickTimestamp;
  private float monthResidentsGrown;
  private float monthResidentsLost;
  private float monthResidentsUsed;
  private int monthMarinesTrained;

  private Float marinesTarget;
  private float marinesPending;
  private float monthResidentsMade;

  private boolean paused;
  private boolean detached;
  private String detachedFleetId;

  private boolean lastPaused;
  private boolean lastCargoFull;
  private boolean lastFuelFull;

  private String harborMarketId;
  private String shuttlesMarketId;

  private boolean gateTransitThisMonth;
  private float gateFreightOfficeAccrued;

  private Map<String, Float> insuranceClaims;
  private Map<String, String> insuranceNames;

  private Vector2f lastFlagshipLoc;

  private Float topBarX;
  private Float topBarY;

  public float getResidents() {
    return residents;
  }

  public void setResidents(float residents) {
    changeResidents(residents, 0f, 0f, 0f, 0f);
  }

  public void changeResidents(float after, float in, float out, float made, float consumed) {
    float before = this.residents;
    this.residents = after;
    float[] month = GlueRules.monthCounts(before, after, in, out, made, consumed);
    monthResidentsGrown += month[0];
    monthResidentsLost += month[1];
    monthResidentsMade += month[2];
    monthResidentsUsed += month[3];
  }

  public boolean isFounded() {
    return founded;
  }

  public void setFounded(boolean founded) {
    this.founded = founded;
  }

  public boolean isNeedsFoundingDialog() {
    return needsFoundingDialog;
  }

  public void setNeedsFoundingDialog(boolean needsFoundingDialog) {
    this.needsFoundingDialog = needsFoundingDialog;
  }

  public long getFoundersOwedTs() {
    return assemblyStartTs;
  }

  public boolean isFoundersPending() {
    return assemblyStartTs != 0L;
  }

  public void awaitFounders(long nowTs) {
    this.assemblyStartTs = nowTs == 0L ? 1L : nowTs;
  }

  public void foundersAboard() {
    this.assemblyStartTs = 0L;
  }

  public boolean isFoundingStoresSeeded() {
    return foundingStoresSeeded;
  }

  public void setFoundingStoresSeeded(boolean foundingStoresSeeded) {
    this.foundingStoresSeeded = foundingStoresSeeded;
  }

  public void endFlotilla() {
    founded = false;
    needsFoundingDialog = false;
    assemblyStartTs = 0L;
    foundingStoresSeeded = false;
    paused = false;
    lastPaused = false;
    lastCargoFull = false;
    lastFuelFull = false;
    lastNeedsLevel = null;
    lastTickTimestamp = 0L;
    lastTickOrders = 0;
    lastTickResidents = 0f;
    monthResidentsGrown = 0f;
    monthResidentsLost = 0f;
    monthResidentsUsed = 0f;
    monthMarinesTrained = 0;
    marinesPending = 0f;
    monthResidentsMade = 0f;
    storageRemainder = null;
    workOrderSeq = 0;
    activeWorkEffects = null;
    gateTransitThisMonth = false;
    gateFreightOfficeAccrued = 0f;
    insuranceClaims = null;
    insuranceNames = null;
    carrierReading = null;
    allocSerial++;
  }

  public float getCachedSpace() {
    return cachedSpace;
  }

  public float getCachedBerths() {
    return cachedBerths;
  }

  public void setCapacitySnapshot(float space, float berths) {
    this.cachedSpace = space;
    this.cachedBerths = berths;
  }

  public boolean isCapacityFrozen() {
    return capacityFrozen;
  }

  public float getFrozenSpace() {
    return frozenSpace;
  }

  public float getFrozenBerths() {
    return frozenBerths;
  }

  public void freezeCapacity(float space, float berths) {
    this.frozenSpace = Math.max(0f, space);
    this.frozenBerths = Math.max(0f, berths);
    this.capacityFrozen = true;
  }

  public void clearFrozenCapacity() {
    this.frozenSpace = 0f;
    this.frozenBerths = 0f;
    this.capacityFrozen = false;
  }

  public long getAllocSerial() {
    return allocSerial;
  }

  public void bumpAllocSerial() {
    allocSerial++;
  }

  public CargoAPI getFlotillaCargo() {
    if (flotillaCargo == null) {
      flotillaCargo = Global.getFactory().createCargo(true);
    }
    return flotillaCargo;
  }

  private Map<String, Float> storageRemainder;

  public float getStorageRemainder(String c) {
    if (storageRemainder == null) {
      return 0f;
    }
    Float v = storageRemainder.get(c);
    return v == null ? 0f : v;
  }

  public void setStorageRemainder(String c, float v) {
    if (storageRemainder == null) {
      storageRemainder = new LinkedHashMap<String, Float>();
    }
    if (Math.abs(v) < 1e-6f) {
      storageRemainder.remove(c);
    } else {
      storageRemainder.put(c, v);
    }
  }

  public Map<String, String> getDedication() {
    if (dedication == null) {
      dedication = new LinkedHashMap<String, String>();
    }
    return dedication;
  }

  public int reconcileDedication(java.util.Set<String> liveMemberIds) {
    if (liveMemberIds == null || liveMemberIds.isEmpty() || dedication == null) {
      return 0;
    }
    List<String> drop = new ArrayList<String>();
    for (String id : dedication.keySet()) {
      if (!liveMemberIds.contains(id)) {
        drop.add(id);
      }
    }
    for (String id : drop) {
      dedication.remove(id);
    }
    return drop.size();
  }

  public List<String> getAutoforgeQueue() {
    if (autoforgeQueue == null) {
      autoforgeQueue = new ArrayList<String>();
    }
    return autoforgeQueue;
  }

  public List<String> getInstalledItems() {
    if (installedItems == null) {
      installedItems = new ArrayList<String>();
    }
    return installedItems;
  }

  public List<String> getHistory() {
    if (history == null) {
      history = new ArrayList<String>();
    }
    return history;
  }

  public long getLastTickTimestamp() {
    return lastTickTimestamp;
  }

  public void setLastTickTimestamp(long lastTickTimestamp) {
    this.lastTickTimestamp = lastTickTimestamp;
  }

  public int takeMonthUsed() {
    int whole = (int) Math.floor(Math.max(0f, monthResidentsUsed) + 1e-3f);
    monthResidentsUsed = Math.max(0f, monthResidentsUsed - whole);
    return whole;
  }

  public void addMonthMarines(int marines) {
    monthMarinesTrained += Math.max(0, marines);
  }

  public int takeMonthMarines() {
    int n = monthMarinesTrained;
    monthMarinesTrained = 0;
    return n;
  }

  public boolean hasMarinesTarget() {
    return marinesTarget != null;
  }

  public float getMarinesTarget() {
    return marinesTarget == null ? Float.NaN : marinesTarget.floatValue();
  }

  public void setMarinesTarget(float marines) {
    marinesTarget = Float.valueOf(Math.max(0f, Math.round(marines)));
  }

  public float getMarinesPending() {
    return Math.max(0f, marinesPending);
  }

  public void setMarinesPending(float marines) {
    marinesPending = Math.max(0f, marines);
  }

  public int takeMonthMade() {
    int whole = (int) Math.floor(Math.max(0f, monthResidentsMade) + 1e-3f);
    monthResidentsMade = Math.max(0f, monthResidentsMade - whole);
    return whole;
  }

  public float getMonthResidentsGrown() {
    return monthResidentsGrown;
  }

  public float getMonthResidentsLost() {
    return monthResidentsLost;
  }

  public void resetMonthCounters() {
    monthResidentsGrown = 0f;
    monthResidentsLost = 0f;
  }

  public boolean isPaused() {
    return paused;
  }

  public void setPaused(boolean paused) {
    this.paused = paused;
  }

  public boolean isLastPaused() {
    return lastPaused;
  }

  public void setLastPaused(boolean lastPaused) {
    this.lastPaused = lastPaused;
  }

  public boolean isLastCargoFull() {
    return lastCargoFull;
  }

  public boolean isLastFuelFull() {
    return lastFuelFull;
  }

  public void setLastHoldFull(boolean cargoFull, boolean fuelFull) {
    this.lastCargoFull = cargoFull;
    this.lastFuelFull = fuelFull;
  }

  public boolean isDetached() {
    return detached;
  }

  public void setDetached(boolean detached) {
    this.detached = detached;
  }

  public String getDetachedFleetId() {
    return detachedFleetId;
  }

  public void setDetachedFleetId(String detachedFleetId) {
    this.detachedFleetId = detachedFleetId;
  }

  public String getHarborMarketId() {
    return harborMarketId;
  }

  public void setHarborMarketId(String harborMarketId) {
    this.harborMarketId = harborMarketId;
  }

  public String getShuttlesMarketId() {
    return shuttlesMarketId;
  }

  public void setShuttlesMarketId(String shuttlesMarketId) {
    this.shuttlesMarketId = shuttlesMarketId;
  }

  public boolean isGateTransitThisMonth() {
    return gateTransitThisMonth;
  }

  public void setGateTransitThisMonth(boolean gateTransitThisMonth) {
    this.gateTransitThisMonth = gateTransitThisMonth;
  }

  public float getGateFreightOfficeAccrued() {
    return gateFreightOfficeAccrued;
  }

  public void setGateFreightOfficeAccrued(float gateFreightOfficeAccrued) {
    this.gateFreightOfficeAccrued = gateFreightOfficeAccrued;
  }

  public void addInsuranceClaim(String memberId, String shipName, float credits) {
    if (insuranceClaims == null) {
      insuranceClaims = new LinkedHashMap<String, Float>();
    }
    if (insuranceClaims.containsKey(memberId)) {
      return;
    }
    nomad.campaign.econ.SupportPay.claim(insuranceClaims, memberId, credits);
    if (insuranceClaims.containsKey(memberId)) {
      if (insuranceNames == null) {
        insuranceNames = new LinkedHashMap<String, String>();
      }
      insuranceNames.put(memberId, shipName);
    }
  }

  public Map<String, Float> getInsuranceClaims() {
    return insuranceClaims;
  }

  public boolean hasInsuranceClaims() {
    return insuranceClaims != null && !insuranceClaims.isEmpty();
  }

  public String getInsuranceName(String memberId) {
    return insuranceNames == null ? null : insuranceNames.get(memberId);
  }

  public void clearInsuranceClaims() {
    insuranceClaims = null;
    insuranceNames = null;
  }

  public Vector2f getLastFlagshipLoc() {
    return lastFlagshipLoc;
  }

  public void setLastFlagshipLoc(Vector2f lastFlagshipLoc) {
    this.lastFlagshipLoc = lastFlagshipLoc;
  }

  public Float getTopBarX() {
    return topBarX;
  }

  public Float getTopBarY() {
    return topBarY;
  }

  public void setTopBarPos(float x, float y) {
    this.topBarX = x;
    this.topBarY = y;
  }

  public void clearTopBarPos() {
    this.topBarX = null;
    this.topBarY = null;
  }

  private List<nomad.campaign.econ.model.WorkState> workStates;
  private int workOrderSeq;
  private Map<String, Float> storeTargets;
  private Map<String, Integer> storeFlags;
  private String lastNeedsLevel;

  public List<nomad.campaign.econ.model.WorkState> getWorkStates() {
    if (workStates == null) {
      workStates = new ArrayList<nomad.campaign.econ.model.WorkState>();
    }
    return workStates;
  }

  public nomad.campaign.econ.model.WorkState getWorkState(String workId) {
    if (workStates == null || workId == null) {
      return null;
    }
    for (nomad.campaign.econ.model.WorkState w : workStates) {
      if (workId.equals(w.id)) {
        return w;
      }
    }
    return null;
  }

  public int effectSize(String workId) {
    return nomad.api.NomadWorks.effectSize(workStates, workId);
  }

  public void markChanged() {
    allocSerial++;
  }

  public void putWorkState(nomad.campaign.econ.model.WorkState state) {
    if (state == null || state.id == null) {
      return;
    }
    allocSerial++;
    List<nomad.campaign.econ.model.WorkState> list = getWorkStates();
    for (int i = 0; i < list.size(); i++) {
      if (state.id.equals(list.get(i).id)) {
        list.set(i, state);
        return;
      }
    }
    list.add(state);
  }

  public void setWorkStates(List<nomad.campaign.econ.model.WorkState> states) {
    List<nomad.campaign.econ.model.WorkState> list = getWorkStates();
    if (states == list) {
      return;
    }
    allocSerial++;
    list.clear();
    if (states != null) {
      for (nomad.campaign.econ.model.WorkState state : states) {
        putWorkState(state);
      }
    }
  }

  public int nextWorkOrder() {
    return workOrderSeq++;
  }

  public float getStoreTarget(String good) {
    Float v = storeTargets == null ? null : storeTargets.get(good);
    return v == null ? 0f : v.floatValue();
  }

  public void setStoreTarget(String good, float units) {
    if (!Commod.STORED.contains(good)) {
      return;
    }
    if (storeTargets == null) {
      storeTargets = new LinkedHashMap<String, Float>();
    }
    storeTargets.put(good, Float.valueOf(Math.max(0f, units)));
    allocSerial++;
  }

  public Map<String, Float> getStoreTargets() {
    if (storeTargets == null) {
      storeTargets = new LinkedHashMap<String, Float>();
    }
    return storeTargets;
  }

  public int getStoreFlags(String good) {
    return nomad.campaign.econ.model.StoreFlags.of(storeFlags == null ? null : storeFlags.get(good));
  }

  public void setStoreFlags(String good, int flags) {
    if (!Commod.STORED.contains(good)) {
      return;
    }
    if (storeFlags == null) {
      storeFlags = new LinkedHashMap<String, Integer>();
    }
    storeFlags.put(good, Integer.valueOf(flags));
    allocSerial++;
  }

  public String getLastNeedsLevel() {
    return lastNeedsLevel;
  }

  public void setLastNeedsLevel(String levelKey) {
    this.lastNeedsLevel = levelKey;
  }

  private int lastTickOrders;
  private float lastTickResidents;

  public int getLastTickOrders() {
    return lastTickOrders;
  }

  public float getLastTickResidents() {
    return lastTickResidents;
  }

  public void setLastTickStamp(int orders, float residents) {
    this.lastTickOrders = orders;
    this.lastTickResidents = residents;
  }

  private float foundryPoints;

  public float getFoundryPoints() {
    return foundryPoints;
  }

  public void addFoundryPoints(float points) {
    if (points > 0f) {
      foundryPoints += points;
    }
  }

  public float takeFoundryPoints(float want) {
    float taken = Math.max(0f, Math.min(want, foundryPoints));
    foundryPoints -= taken;
    return taken;
  }

  public void clearFoundryPoints() {
    foundryPoints = 0f;
  }

  private float suppliesOwed;

  public float getSuppliesOwed() {
    return suppliesOwed;
  }

  public void setSuppliesOwed(float supplies) {
    this.suppliesOwed = supplies > 0f ? supplies : 0f;
  }

  private transient nomad.campaign.NomadMiningResolver.Cache miningCache;

  public nomad.campaign.NomadMiningResolver.Cache getMiningCache() {
    if (miningCache == null) {
      miningCache = new nomad.campaign.NomadMiningResolver.Cache();
    }
    return miningCache;
  }

  private transient nomad.campaign.econ.ShellRules.Carrier carrierReading;

  public nomad.campaign.econ.ShellRules.Carrier getCarrierReading() {
    return carrierReading;
  }

  public void setCarrierReading(nomad.campaign.econ.ShellRules.Carrier reading) {
    this.carrierReading = reading;
  }

  private List<String> activeWorkEffects;

  public List<String> getActiveWorkEffects() {
    return activeWorkEffects;
  }

  public void setActiveWorkEffects(java.util.Collection<String> ids) {
    activeWorkEffects = ids == null ? new ArrayList<String>() : new ArrayList<String>(ids);
  }
}
