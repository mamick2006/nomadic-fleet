package nomad.campaign;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.campaign.listeners.SurveyPlanetListener;
import com.fs.starfarer.api.impl.campaign.terrain.AsteroidBeltTerrainPlugin;
import com.fs.starfarer.api.util.Misc;

import nomad.campaign.econ.model.Mining;
import nomad.campaign.econ.model.MiningSystem;
import nomad.campaign.econ.model.Rules;
import nomad.campaign.econ.model.WorkCatalog;
import nomad.campaign.econ.model.WorkSize;
import nomad.campaign.econ.model.WorkState;
import nomad.campaign.econ.model.Works;

public final class NomadMiningResolver {

  private NomadMiningResolver() {
  }

  public static final class Result {
    public boolean active;
    public String bodyName = "";
    public final Map<String, Float> deposits = new LinkedHashMap<String, Float>();
  }

  public static final class Cache {

    private CampaignFleetAPI fleet;
    private LocationAPI location;
    private boolean stale = true;
    private final List<PlanetAPI> planets = new ArrayList<PlanetAPI>();
    private final List<MiningSystem.Planet> described = new ArrayList<MiningSystem.Planet>();
    private Mining.Sources sources = Mining.Sources.HYPERSPACE;
    private Mining.Body system;
    private Map<String, Float> pickedRates;
    private Map<String, Float> pickedPrices;
    private Rules pickedRules;
    private int reads;

    public void markStale() {
      stale = true;
    }

    public int reads() {
      return reads;
    }

    public int planetCount() {
      return planets.size();
    }

    public LocationAPI location() {
      return location;
    }
  }

  public static Mining.Site site(Cache cache, CampaignFleetAPI fleet, WorkState rig,
      WorkCatalog catalog, Map<String, Float> prices, Rules k) {
    if (fleet == null || rig == null || !rig.isBuilt() || catalog == null || k == null) {
      return Mining.Site.NONE;
    }
    LocationAPI loc = fleet.getContainingLocation();
    if (loc == null) {
      return Mining.Site.NONE;
    }
    Cache c = cache != null ? cache : new Cache();
    if (loc.isHyperspace()) {
      if (c.location != loc || c.fleet != fleet) {
        c.planets.clear();
        c.described.clear();
        c.sources = Mining.Sources.HYPERSPACE;
        c.system = null;
        c.pickedRates = null;
        c.location = loc;
        c.fleet = fleet;
      }
      return Mining.Site.NONE;
    }
    if (c.stale || c.location != loc || c.fleet != fleet) {
      read(c, fleet, loc);
    }

    float radius = NomadSettings.getFloat("nomad_mining_radius", 333f);
    List<MiningSystem.Planet> near = null;
    for (int i = 0; i < c.planets.size(); i++) {
      PlanetAPI p = c.planets.get(i);
      if (p.getMarket() == null) {
        continue;
      }
      MiningSystem.Planet kept = c.described.get(i);
      if (!kept.surveyed && p.getMarket().getSurveyLevel() == MarketAPI.SurveyLevel.FULL) {
        read(c, fleet, loc);
        return site(c, fleet, rig, catalog, prices, k);
      }
      float d = surface(fleet, p);
      if (d > radius) {
        continue;
      }
      if (near == null) {
        near = new ArrayList<MiningSystem.Planet>(2);
      }
      near.add(kept.at(d));
    }
    Mining.Body inRange = MiningSystem.inRange(near, radius);

    WorkSize row = catalog.size(Works.MINING_RIG, rig.size);
    Map<String, Float> rates = row == null ? null : row.makes;
    if (rates != c.pickedRates || prices != c.pickedPrices || k != c.pickedRules) {
      c.system = rates == null ? null : Mining.systemBody(c.sources, rates, prices, k);
      c.pickedRates = rates;
      c.pickedPrices = prices;
      c.pickedRules = k;
    }
    return inRange == null && c.system == null ? Mining.Site.NONE
        : new Mining.Site(inRange, c.system);
  }

  private static void read(Cache c, CampaignFleetAPI fleet, LocationAPI loc) {
    c.planets.clear();
    c.described.clear();
    for (PlanetAPI p : loc.getPlanets()) {
      if (p == null || p.getMarket() == null) {
        continue;
      }
      c.planets.add(p);
      c.described.add(describe(fleet, p));
    }
    c.sources = MiningSystem.sources(false, hasAsteroids(loc), c.described);
    c.system = null;
    c.pickedRates = null;
    c.location = loc;
    c.fleet = fleet;
    c.stale = false;
    c.reads++;
    listen(c);
  }

  static MiningSystem.Planet describe(CampaignFleetAPI fleet, PlanetAPI planet) {
    final MarketAPI market = planet.getMarket();
    return new MiningSystem.Planet(market.getName(), surface(fleet, planet),
        market.getSurveyLevel() == MarketAPI.SurveyLevel.FULL, conditions(market));
  }

  private static MiningSystem.Conditions conditions(final MarketAPI market) {
    return new MiningSystem.Conditions() {
      @Override
      public boolean has(String conditionId) {
        return market.hasCondition(conditionId);
      }
    };
  }

  private static float surface(CampaignFleetAPI fleet, PlanetAPI planet) {
    return Math.max(0f,
        Misc.getDistance(fleet.getLocation(), planet.getLocation()) - planet.getRadius());
  }

  private static boolean hasAsteroids(LocationAPI loc) {
    for (CampaignTerrainAPI terrain : loc.getTerrainCopy()) {
      if (terrain != null && terrain.getPlugin() instanceof AsteroidBeltTerrainPlugin) {
        return true;
      }
    }
    return false;
  }

  private static void listen(Cache c) {
    if (Global.getSector() == null) {
      return;
    }
    ListenerManagerAPI lm = Global.getSector().getListenerManager();
    List<SurveyListener> have = lm.getListeners(SurveyListener.class);
    if (have.isEmpty()) {
      SurveyListener l = new SurveyListener();
      l.cache = c;
      lm.addListener(l, true);
      return;
    }
    for (SurveyListener l : have) {
      l.cache = c;
    }
  }

  public static final class SurveyListener implements SurveyPlanetListener {

    private transient Cache cache;

    @Override
    public void reportPlayerSurveyedPlanet(PlanetAPI planet) {
      Cache c = cache;
      if (c != null && planet != null && planet.getContainingLocation() == c.location) {
        c.markStale();
      }
    }
  }

  public static Result resolve(CampaignFleetAPI fleet) {
    Result r = new Result();
    PlanetAPI planet = nearestEligibleBody(fleet, NomadSettings.getFloat("nomad_mining_radius", 333f));
    if (planet != null) {
      r.active = true;
      r.bodyName = planet.getMarket().getName();
      fillDeposits(planet.getMarket(), r.deposits);
    }
    return r;
  }

  public static PlanetAPI nearestEligibleBody(CampaignFleetAPI fleet, float radius) {
    if (fleet == null || fleet.getContainingLocation() == null) {
      return null;
    }
    PlanetAPI nearest = null;
    float nearestDist = Float.MAX_VALUE;
    for (PlanetAPI planet : fleet.getContainingLocation().getPlanets()) {
      if (planet == null || planet.getMarket() == null) {
        continue;
      }
      MarketAPI market = planet.getMarket();
      if (market.getSurveyLevel() != MarketAPI.SurveyLevel.FULL) {
        continue;
      }
      float dist = Misc.getDistance(fleet.getLocation(), planet.getLocation()) - planet.getRadius();
      if (dist > radius || dist >= nearestDist) {
        continue;
      }
      if (MiningSystem.tiers(conditions(market)).isEmpty()) {
        continue;
      }
      nearestDist = dist;
      nearest = planet;
    }
    return nearest;
  }

  public static Result resolveFor(PlanetAPI body) {
    Result r = new Result();
    if (body != null && body.getMarket() != null) {
      r.active = true;
      r.bodyName = body.getMarket().getName();
      fillDeposits(body.getMarket(), r.deposits);
    }
    return r;
  }

  public static boolean inRange(CampaignFleetAPI fleet, PlanetAPI body, float radius) {
    if (fleet == null || body == null || body.getMarket() == null
        || body.getContainingLocation() == null
        || body.getContainingLocation() != fleet.getContainingLocation()) {
      return false;
    }
    float reach = radius + body.getRadius();
    return Misc.getDistanceSq(fleet.getLocation(), body.getLocation()) <= reach * reach;
  }

  private static void fillDeposits(MarketAPI market, Map<String, Float> deposits) {
    deposits.putAll(MiningSystem.tiers(conditions(market)));
  }
}
