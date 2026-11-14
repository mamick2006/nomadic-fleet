package nomad.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import nomad.campaign.econ.EconConstants;
import nomad.campaign.econ.model.GlueRules;
import nomad.campaign.econ.model.WorkState;
import nomad.campaign.econ.model.WorkStatus;

public final class NomadAutoforgeQueue {

  private NomadAutoforgeQueue() {
  }

  static final float EPS = 1e-4f;

  static final float BANK_ROUNDING = 1e-5f;

  public static final String SHIP = "ship";
  public static final String WEAPON = "weapon";
  public static final String FIGHTER = "fighter";
  public static final String PART = "part";
  public static final List<String> TYPES =
      Collections.unmodifiableList(java.util.Arrays.asList(SHIP, WEAPON, FIGHTER, PART));

  public enum LineState {
    NO_FOUNDRY,
    NO_FLAGSHIP,
    CONSTRUCTING,
    MOTHBALLED,
    SUSPENDED,
    PAUSED,
    UNSTAFFED,
    STOPPED,
    IDLE,
    STALLED,
    BUILDING,
    LACKING
  }

  public static LineState state(WorkState foundry, WorkStatus status, WorkStatus.Reason reason,
      boolean paused, boolean queueEmpty) {
    return state(foundry, status, reason, paused, queueEmpty, false, 1f);
  }

  public static LineState state(WorkState foundry, WorkStatus status, WorkStatus.Reason reason,
      boolean paused, boolean queueEmpty, boolean noFlagship, float efficiency) {
    if (foundry == null) {
      return LineState.NO_FOUNDRY;
    }
    if (noFlagship) {
      return LineState.NO_FLAGSHIP;
    }
    if (!foundry.isBuilt()) {
      return LineState.CONSTRUCTING;
    }
    if (foundry.mothballed || status == WorkStatus.MOTHBALLED) {
      return LineState.MOTHBALLED;
    }
    if (foundry.suspended || status == WorkStatus.SUSPENDED) {
      return LineState.SUSPENDED;
    }
    if (foundry.prereqUnmet || status == WorkStatus.LACKING) {
      return LineState.LACKING;
    }
    if (paused) {
      return LineState.PAUSED;
    }
    if (status == WorkStatus.UNSTAFFED || (status == null && !foundry.staffed)) {
      return LineState.UNSTAFFED;
    }
    if (efficiency <= 0f) {
      return LineState.STOPPED;
    }
    if (queueEmpty) {
      return LineState.IDLE;
    }
    if (status == WorkStatus.IDLE && (reason == WorkStatus.Reason.INPUT_EMPTY
        || reason == WorkStatus.Reason.QUEUE_WAITING)) {
      return LineState.STALLED;
    }
    return LineState.BUILDING;
  }

  public static LineState noRateCause(WorkState foundry, boolean noFlagship) {
    if (foundry == null) {
      return LineState.NO_FOUNDRY;
    }
    if (!foundry.isBuilt()) {
      return LineState.CONSTRUCTING;
    }
    return noFlagship ? LineState.NO_FLAGSHIP : LineState.STOPPED;
  }

  public static boolean advancing(LineState st) {
    return st == LineState.BUILDING;
  }

  public interface Jobs {
    int size();

    float cost(int i);

    float banked(int i);

    default boolean held(int i) {
      return false;
    }
  }

  public static final class Spend {
    public final List<Boolean> removed;
    public final float headBanked;
    public final float bankLeft;

    Spend(List<Boolean> removed, float headBanked, float bankLeft) {
      this.removed = Collections.unmodifiableList(removed);
      this.headBanked = headBanked;
      this.bankLeft = bankLeft;
    }

    public int delivered() {
      int n = 0;
      for (Boolean b : removed) {
        if (b.booleanValue()) {
          n++;
        }
      }
      return n;
    }
  }

  public static Spend spend(Jobs jobs, float bank) {
    List<Boolean> removed = new ArrayList<Boolean>();
    float left = bank > 0f ? bank : 0f;
    float paid = Math.max(EPS, left * BANK_ROUNDING);
    float headBanked = 0f;
    for (int i = 0; i < jobs.size(); i++) {
      float cost = jobs.cost(i);
      if (!(cost >= 0f)) {
        removed.add(Boolean.FALSE);
        continue;
      }
      if (jobs.held(i)) {
        headBanked = Math.max(0f, jobs.banked(i));
        break;
      }
      float banked = Math.max(0f, jobs.banked(i));
      float need = cost - banked;
      if (need > EPS) {
        float spent = Math.min(need, left);
        left -= spent;
        banked += spent;
        need -= spent;
      }
      if (need > paid) {
        headBanked = banked;
        break;
      }
      removed.add(Boolean.TRUE);
    }
    return new Spend(removed, headBanked, Math.max(0f, left));
  }

  public static float wanted(Jobs jobs, float unspent) {
    float need = 0f;
    for (int i = 0; i < jobs.size(); i++) {
      float cost = jobs.cost(i);
      if (!(cost >= 0f)) {
        continue;
      }
      if (jobs.held(i)) {
        break;
      }
      need += Math.max(0f, cost - Math.max(0f, jobs.banked(i)));
    }
    return GlueRules.pointsWanted(need, 0f, unspent);
  }

  public static float daysForPoints(float points, float pointsPerMonth) {
    return pointsPerMonth <= 0f ? Float.POSITIVE_INFINITY
        : Math.max(0f, points) / (pointsPerMonth / 30f);
  }

  public static float hullValue(float hullBaseValue, int sizeIndex, int dmods, EconConstants k) {
    return Math.max(0f, hullBaseValue) * k.hullValueMult(sizeIndex) * k.hullDmodValueMult(dmods);
  }

  public static float partValue(float csvPrice, float survivalPriceMult, EconConstants k) {
    float mult = k.survivalAdjusted && survivalPriceMult > 0f ? survivalPriceMult : 1f;
    return Math.max(0f, csvPrice) * mult;
  }

  public static String entry(String type, String id, float banked) {
    return type + "|" + id + "|" + banked;
  }

  public static float banked(String entry) {
    if (entry == null) {
      return 0f;
    }
    String[] p = entry.split("\\|");
    if (p.length < 3) {
      return 0f;
    }
    try {
      float v = Float.parseFloat(p[2]);
      return v > 0f ? v : 0f;
    } catch (NumberFormatException e) {
      return 0f;
    }
  }

  public static boolean deliversAsCargo(String type) {
    return WEAPON.equals(type) || FIGHTER.equals(type) || PART.equals(type);
  }

  public static boolean held(String type, boolean domainPartsWorkshopOn) {
    return PART.equals(type) && !domainPartsWorkshopOn;
  }

  public static boolean clearsInflaterOrigin(boolean carrierIsPlayerFleet) {
    return !carrierIsPlayerFleet;
  }
}
