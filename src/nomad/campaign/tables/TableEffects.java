package nomad.campaign.tables;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import nomad.api.NomadFleetStat;
import nomad.api.NomadItemEffect;
import nomad.api.NomadItemTick;
import nomad.api.NomadTableTick;
import nomad.api.NomadWorkEffect;
import nomad.api.NomadWorkTick;
import nomad.campaign.econ.Commod;
import nomad.campaign.econ.EconConstants;
import nomad.campaign.econ.ItemRow;
import nomad.campaign.econ.model.GlueRules;
import nomad.campaign.econ.model.TickSheet;
import nomad.campaign.econ.model.WorkDef;
import nomad.campaign.econ.model.WorkSize;
import nomad.campaign.econ.model.WorkState;

public final class TableEffects {

  private TableEffects() {
  }

  private static final int MAX_PLUGINS = 256;

  private static final Map<String, Object> PLUGINS = new LinkedHashMap<String, Object>();
  private static final Set<String> BROKEN = new LinkedHashSet<String>();
  private static final Set<String> UNTRACKED_WARNED = new LinkedHashSet<String>();

  private static final Map<String, String> ACTIVE = new LinkedHashMap<String, String>();

  public static List<String> onGameLoad() {
    List<String> problems = new ArrayList<String>();
    clearAllActive(null, problems);
    PLUGINS.clear();
    BROKEN.clear();
    UNTRACKED_WARNED.clear();
    ACTIVE.clear();
    return problems;
  }

  public static List<String> restoreActive(TickSheet sheet) {
    return restoreActive(sheet, null);
  }

  public static List<String> restoreActive(TickSheet sheet, Collection<String> ranAtLastTick) {
    List<String> problems = new ArrayList<String>();
    EconConstants k = sheet == null ? null : sheet.shared;
    if (k == null || sheet.s.paused) {
      return problems;
    }
    if (!k.workPlugins.isEmpty()) {
      reconcile(k, k.workPlugins.keySet(), savedActive(sheet, ranAtLastTick), "Work", problems);
    }
    if (!k.itemPlugins.isEmpty()) {
      Set<String> live = new LinkedHashSet<String>(sheet.s.items);
      live.retainAll(k.itemPlugins.keySet());
      reconcile(k, k.itemPlugins.keySet(), live, "colony item", problems);
    }
    return problems;
  }

  static Set<String> savedActive(TickSheet sheet, Collection<String> ranAtLastTick) {
    Set<String> active = new LinkedHashSet<String>();
    EconConstants k = sheet == null ? null : sheet.shared;
    if (k == null || k.workPlugins.isEmpty() || sheet.s.paused) {
      return active;
    }
    Set<String> atWork = ranAtLastTick != null ? null : runningPlugins(sheet).keySet();
    for (WorkState saved : sheet.s.works) {
      WorkDef def = saved == null ? null : k.workPlugins.get(saved.id);
      if (def == null) {
        continue;
      }
      boolean on = def.isProduction() ? atWork != null && atWork.contains(saved.id)
          : saved.effectOn && saved.isBuilt();
      if (on) {
        active.add(saved.id);
      }
    }
    if (ranAtLastTick != null) {
      for (String id : ranAtLastTick) {
        WorkDef def = k.workPlugins.get(id);
        if (def != null && def.isProduction()) {
          active.add(id);
        }
      }
    }
    return active;
  }

  static Set<String> savedActive(TickSheet sheet) {
    return savedActive(sheet, null);
  }

  public static List<String> activeWorks(EconConstants k) {
    List<String> ids = new ArrayList<String>();
    if (k == null) {
      return ids;
    }
    for (String id : ACTIVE.keySet()) {
      if (k.workPlugins.containsKey(id)) {
        ids.add(id);
      }
    }
    return ids;
  }

  public static void clearAllActive(EconConstants k, List<String> problems) {
    if (ACTIVE.isEmpty()) {
      return;
    }
    for (Map.Entry<String, String> entry : new ArrayList<Map.Entry<String, String>>(
        ACTIVE.entrySet())) {
      clear(k, entry.getKey(), entry.getValue(), problems);
    }
    ACTIVE.clear();
  }

  private static void reconcile(EconConstants k, Set<String> candidates, Set<String> active,
      String noun, List<String> problems) {
    for (String id : candidates) {
      if (active.contains(id)) {
        if (!ACTIVE.containsKey(id)) {
          String plugin = pluginOf(k, id);
          ACTIVE.put(id, plugin);
          apply(k, id, plugin, noun, problems);
        }
      } else {
        String plugin = ACTIVE.remove(id);
        if (plugin != null) {
          clear(k, id, plugin, problems);
        }
      }
    }
  }

  private static String pluginOf(EconConstants k, String id) {
    WorkDef work = k == null ? null : k.workPlugins.get(id);
    if (work != null) {
      return work.plugin;
    }
    ItemRow item = k == null ? null : k.itemPlugins.get(id);
    return item == null ? "" : item.plugin;
  }

  private static void apply(EconConstants k, String id, String plugin, String noun,
      List<String> problems) {
    String sourceMod = sourceModOf(k, id);
    if ("Work".equals(noun)) {
      NomadWorkEffect effect = resolve(plugin, NomadWorkEffect.class, noun, id, sourceMod, problems);
      if (effect == null) {
        return;
      }
      try {
        effect.onApplied(id);
      } catch (Throwable t) {
        broken(plugin, noun, id, sourceMod, "threw on becoming active (" + t + ")", problems);
      }
      return;
    }
    NomadItemEffect effect = resolve(plugin, NomadItemEffect.class, noun, id, sourceMod, problems);
    if (effect == null) {
      return;
    }
    try {
      effect.onApplied(id);
    } catch (Throwable t) {
      broken(plugin, noun, id, sourceMod, "threw on becoming active (" + t + ")", problems);
    }
  }

  private static void clear(EconConstants k, String id, String plugin, List<String> problems) {
    if (plugin == null || plugin.isEmpty()) {
      return;
    }
    String sourceMod = sourceModOf(k, id);
    Object effect = PLUGINS.get(plugin);
    if (effect instanceof NomadWorkEffect) {
      try {
        ((NomadWorkEffect) effect).onCleared(id);
      } catch (Throwable t) {
        broken(plugin, "Work", id, sourceMod, "threw on going inactive (" + t + ")", problems);
      }
      return;
    }
    if (effect instanceof NomadItemEffect) {
      try {
        ((NomadItemEffect) effect).onCleared(id);
      } catch (Throwable t) {
        broken(plugin, "colony item", id, sourceMod, "threw on going inactive (" + t + ")", problems);
      }
    }
  }

  private static String sourceModOf(EconConstants k, String id) {
    WorkDef work = k == null ? null : k.workPlugins.get(id);
    if (work != null) {
      return work.sourceMod;
    }
    ItemRow item = k == null ? null : k.itemPlugins.get(id);
    return item == null ? "" : item.sourceMod;
  }

  public static Set<String> activeIds() {
    return Collections.unmodifiableSet(new LinkedHashSet<String>(ACTIVE.keySet()));
  }

  public static final class Payload {
    public final Map<String, Float> credits = new LinkedHashMap<String, Float>();
    public final Map<String, Float> commodities = new LinkedHashMap<String, Float>();
    public final Map<String, Map<NomadFleetStat, Float>> fleetStats =
        new LinkedHashMap<String, Map<NomadFleetStat, Float>>();

    public boolean isEmpty() {
      return credits.isEmpty() && commodities.isEmpty() && fleetStats.isEmpty();
    }

    public float fleetStat(String rowId, NomadFleetStat stat) {
      Map<NomadFleetStat, Float> byStat = fleetStats.get(rowId);
      Float held = byStat == null ? null : byStat.get(stat);
      return held == null ? 0f : held;
    }
  }

  public static Map<String, Integer> runningPlugins(TickSheet sheet) {
    Map<String, Integer> running = new LinkedHashMap<String, Integer>();
    EconConstants k = sheet == null ? null : sheet.shared;
    if (k == null || k.workPlugins.isEmpty() || sheet.s.paused) {
      return running;
    }
    for (String id : GlueRules.working(sheet)) {
      TickSheet.WorkRun run = sheet.runs.get(id);
      if (k.workPlugins.containsKey(id) && run != null && run.size >= 1) {
        running.put(id, run.size);
      }
    }
    return running;
  }

  public static Payload runWorkTick(TickSheet sheet, List<String> problems) {
    if (sheet == null || sheet.preview || sheet.shared == null) {
      return new Payload();
    }
    if (sheet.s.paused) {
      clearAllActive(sheet.shared, problems);
      return new Payload();
    }
    return runWorkTick(sheet.shared, runningPlugins(sheet), sheet.efficiency, sheet.monthFrac,
        sheet.residents, sheet.s.detached, sheet.needs.level.atLeastSpartan(), problems);
  }

  public static Payload runItemTick(TickSheet sheet, List<String> problems) {
    if (sheet == null || sheet.preview || sheet.shared == null) {
      return new Payload();
    }
    if (sheet.s.paused) {
      clearAllActive(sheet.shared, problems);
      return new Payload();
    }
    return runItemTick(sheet.shared, sheet.s.items, sheet.efficiency, sheet.monthFrac,
        sheet.residents, sheet.s.detached, sheet.needs.level.atLeastSpartan(), problems);
  }

  public static Payload runWorkTick(EconConstants k, Map<String, Integer> running,
      float productionEfficiency, float monthFraction, float residents, boolean detached,
      boolean needsMet, List<String> problems) {
    Payload payload = new Payload();
    if (k == null || running == null || k.workPlugins.isEmpty()) {
      return payload;
    }
    Set<String> live = new LinkedHashSet<String>(running.keySet());
    live.retainAll(k.workPlugins.keySet());
    reconcile(k, k.workPlugins.keySet(), live, "Work", problems);
    for (Map.Entry<String, WorkDef> entry : k.workPlugins.entrySet()) {
      String id = entry.getKey();
      Integer built = running.get(id);
      if (built == null) {
        continue;
      }
      WorkDef row = entry.getValue();
      WorkSize size = row.size(Math.min(Math.max(1, built), row.maxSize()));
      if (size == null) {
        continue;
      }
      NomadWorkEffect effect = resolve(row.plugin, NomadWorkEffect.class, "Work", id, row.sourceMod,
          problems);
      if (effect == null) {
        continue;
      }
      WorkTick tick = new WorkTick(id, size.size, size.space, size.staff, productionEfficiency,
          monthFraction, residents, detached, needsMet, payload, problems);
      try {
        effect.applyTick(tick);
      } catch (Throwable t) {
        broken(row.plugin, "Work", id, row.sourceMod, "threw " + t, problems);
      }
    }
    return payload;
  }

  public static Payload runItemTick(EconConstants k, List<String> installedItems,
      float productionEfficiency, float monthFraction, float residents, boolean detached,
      boolean needsMet, List<String> problems) {
    Payload payload = new Payload();
    if (k == null || installedItems == null || k.itemPlugins.isEmpty()) {
      return payload;
    }
    Set<String> live = new LinkedHashSet<String>(installedItems);
    live.retainAll(k.itemPlugins.keySet());
    reconcile(k, k.itemPlugins.keySet(), live, "colony item", problems);
    for (Map.Entry<String, ItemRow> entry : k.itemPlugins.entrySet()) {
      String id = entry.getKey();
      if (!installedItems.contains(id)) {
        continue;
      }
      ItemRow row = entry.getValue();
      NomadItemEffect effect = resolve(row.plugin, NomadItemEffect.class, "colony item", id,
          row.sourceMod, problems);
      if (effect == null) {
        continue;
      }
      ItemTick tick = new ItemTick(id, productionEfficiency, monthFraction, residents, detached,
          needsMet, payload, problems);
      try {
        effect.applyTick(tick);
      } catch (Throwable t) {
        broken(row.plugin, "colony item", id, row.sourceMod, "threw " + t, problems);
      }
    }
    return payload;
  }

  public static Set<String> pluginRowIds(EconConstants k) {
    if (k == null) {
      return Collections.emptySet();
    }
    Set<String> ids = new LinkedHashSet<String>(k.workPlugins.keySet());
    ids.addAll(k.itemPlugins.keySet());
    return ids;
  }

  public static <T> T resolve(String className, Class<T> api, String noun, String rowId,
      String sourceMod, List<String> problems) {
    if (className == null || className.isEmpty()) {
      return null;
    }
    Object cached = PLUGINS.get(className);
    if (cached != null) {
      if (api.isInstance(cached)) {
        return api.cast(cached);
      }
      broken(className, noun, rowId, sourceMod, "is not a " + api.getName(), problems);
      return null;
    }
    if (BROKEN.contains(className)) {
      return null;
    }
    if (PLUGINS.size() >= MAX_PLUGINS) {
      broken(className, noun, rowId, sourceMod, "could not be built: this session has already loaded "
          + MAX_PLUGINS + " effect classes, which is the cap", problems);
      return null;
    }
    try {
      Class<?> type = Class.forName(className, true, TableEffects.class.getClassLoader());
      Object built = MethodHandles.lookup()
          .findConstructor(type, MethodType.methodType(void.class)).invoke();
      if (api.isInstance(built)) {
        PLUGINS.put(className, built);
        return api.cast(built);
      }
      broken(className, noun, rowId, sourceMod, "is not a " + api.getName(), problems);
    } catch (Throwable t) {
      broken(className, noun, rowId, sourceMod,
          "could not be built (" + t + "); it needs one public no-argument constructor", problems);
    }
    return null;
  }

  private static void broken(String className, String noun, String rowId, String sourceMod,
      String what, List<String> problems) {
    if (!BROKEN.add(className)) {
      return;
    }
    PLUGINS.remove(className);
    problems.add("the " + noun + " '" + rowId + "'"
        + (sourceMod == null || sourceMod.isEmpty() ? "" : " from " + sourceMod)
        + " names effect class '" + className + "', which " + what
        + ". Its code does nothing this session.");
  }

  private static boolean acceptsCommodity(String rowId, String commodityId, float units,
      List<String> problems) {
    if (commodityId == null || units <= 0f || Float.isNaN(units) || Float.isInfinite(units)) {
      return false;
    }
    if (Commod.STORED.contains(commodityId)) {
      return true;
    }
    if (UNTRACKED_WARNED.add(rowId + "\n" + commodityId)) {
      problems.add("'" + rowId + "' added '" + commodityId + "', which the Flotilla does not"
          + " track, so it was ignored. The tracked commodities are " + Commod.STORED + ".");
    }
    return false;
  }

  private abstract static class Recorder implements NomadTableTick {

    private final String rowId;
    private final float efficiency;
    private final float monthFraction;
    private final float residents;
    private final boolean detached;
    private final boolean needsMet;
    private final Payload payload;
    private final List<String> problems;

    Recorder(String rowId, float efficiency, float monthFraction, float residents, boolean detached,
        boolean needsMet, Payload payload, List<String> problems) {
      this.rowId = rowId;
      this.efficiency = efficiency;
      this.monthFraction = monthFraction;
      this.residents = residents;
      this.detached = detached;
      this.needsMet = needsMet;
      this.payload = payload;
      this.problems = problems;
    }

    @Override
    public float getProductionEfficiency() {
      return efficiency;
    }

    @Override
    public float getMonthFraction() {
      return monthFraction;
    }

    @Override
    public float getResidents() {
      return residents;
    }

    @Override
    public boolean isDetached() {
      return detached;
    }

    @Override
    public boolean areNeedsMet() {
      return needsMet;
    }

    @Override
    public void addCredits(float credits) {
      if (credits <= 0f || Float.isNaN(credits) || Float.isInfinite(credits)) {
        return;
      }
      Float held = payload.credits.get(rowId);
      payload.credits.put(rowId, (held == null ? 0f : held) + credits);
    }

    @Override
    public void addCommodity(String commodityId, float units) {
      if (!acceptsCommodity(rowId, commodityId, units, problems)) {
        return;
      }
      Float held = payload.commodities.get(commodityId);
      payload.commodities.put(commodityId, (held == null ? 0f : held) + units);
    }

    @Override
    public void addFleetStat(NomadFleetStat stat, float amount) {
      if (stat == null || amount == 0f || Float.isNaN(amount) || Float.isInfinite(amount)) {
        return;
      }
      Map<NomadFleetStat, Float> byStat = payload.fleetStats.get(rowId);
      if (byStat == null) {
        byStat = new EnumMap<NomadFleetStat, Float>(NomadFleetStat.class);
        payload.fleetStats.put(rowId, byStat);
      }
      Float held = byStat.get(stat);
      byStat.put(stat, (held == null ? 0f : held) + amount);
    }
  }

  private static final class WorkTick extends Recorder implements NomadWorkTick {

    private final String id;
    private final int size;
    private final float space;
    private final float staff;

    WorkTick(String id, int size, float space, float staff, float efficiency, float monthFraction,
        float residents, boolean detached, boolean needsMet, Payload payload, List<String> problems) {
      super(id, efficiency, monthFraction, residents, detached, needsMet, payload, problems);
      this.id = id;
      this.size = size;
      this.space = space;
      this.staff = staff;
    }

    @Override
    public String getWorkId() {
      return id;
    }

    @Override
    public float getSpace() {
      return space;
    }

    @Override
    public float getStaff() {
      return staff;
    }

    @Override
    public int getSize() {
      return size;
    }
  }

  private static final class ItemTick extends Recorder implements NomadItemTick {

    private final String id;

    ItemTick(String id, float efficiency, float monthFraction, float residents, boolean detached,
        boolean needsMet, Payload payload, List<String> problems) {
      super(id, efficiency, monthFraction, residents, detached, needsMet, payload, problems);
      this.id = id;
    }

    @Override
    public String getItemId() {
      return id;
    }
  }

  public static List<String> ids(EconConstants k) {
    return k == null ? new ArrayList<String>() : new ArrayList<String>(pluginRowIds(k));
  }
}
