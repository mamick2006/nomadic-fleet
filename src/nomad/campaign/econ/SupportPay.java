package nomad.campaign.econ;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SupportPay {

  private SupportPay() {
  }

  public static float gateAccrue(float accrued, float perMonth, float monthFrac) {
    float held = accrued > 0f ? accrued : 0f;
    if (perMonth > 0f && monthFrac > 0f) {
      held += perMonth * monthFrac;
    }
    return held;
  }

  public static float gateDue(float accrued, boolean transitThisMonth) {
    return transitThisMonth && accrued > 0f ? accrued : 0f;
  }

  public static final class GateMonth {
    public boolean transit;
    public float accrued;

    public GateMonth(boolean transit, float accrued) {
      this.transit = transit;
      this.accrued = accrued;
    }

    public float tick(float perMonth, float monthFrac) {
      accrued = gateAccrue(accrued, perMonth, monthFrac);
      float due = gateDue(accrued, transit);
      if (due > 0f) {
        accrued = 0f;
      }
      return due;
    }

    public float close() {
      float due = gateDue(accrued, transit);
      accrued = 0f;
      transit = false;
      return due;
    }
  }

  public static float patrolPay(float perMonth, float monthFrac) {
    return perMonth > 0f && monthFrac > 0f ? perMonth * monthFrac : 0f;
  }

  public static float insurancePayout(float hullBaseValue, float share) {
    return hullBaseValue > 0f && share > 0f ? hullBaseValue * share : 0f;
  }

  public static void claim(Map<String, Float> claims, String memberId, float credits) {
    if (claims == null || memberId == null || !(credits > 0f) || claims.containsKey(memberId)) {
      return;
    }
    claims.put(memberId, Float.valueOf(credits));
  }

  public static Map<String, Float> settle(Map<String, Float> claims, Collection<String> inFleet) {
    Map<String, Float> pay = new LinkedHashMap<String, Float>();
    if (claims == null) {
      return pay;
    }
    for (Map.Entry<String, Float> e : claims.entrySet()) {
      if (e.getKey() == null || e.getValue() == null || !(e.getValue().floatValue() > 0f)) {
        continue;
      }
      if (inFleet != null && inFleet.contains(e.getKey())) {
        continue;
      }
      pay.put(e.getKey(), e.getValue());
    }
    return pay;
  }
}
