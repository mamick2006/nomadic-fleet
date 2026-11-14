package nomad.campaign.econ.model;

import java.util.List;

import nomad.campaign.econ.Commod;

public final class Detached {

  private Detached() {
  }

  private static final float EPS = 1e-4f;
  private static final float REPORTED = 1f;

  public static final class Settlement {

    public final float supplies;
    public final float rest;
    public final float credits;
    public final float forgiven;

    Settlement(float supplies, float rest, float credits, float forgiven) {
      this.supplies = supplies;
      this.rest = rest;
      this.credits = credits;
      this.forgiven = forgiven;
    }

    public boolean isNothing() {
      return supplies <= 0f && rest <= 0f;
    }
  }

  static float claim(TickSheet t) {
    if (!t.s.detached) {
      return 0f;
    }
    return Math.max(0f, t.s.detachedSuppliesPerMonth) * t.monthFrac + Math.max(0f, t.suppliesOwed);
  }

  static void upkeep(TickSheet t) {
    if (!t.s.detached) {
      return;
    }
    float perMonth = Math.max(0f, t.s.detachedSuppliesPerMonth);
    TickSheet.Store store = t.stores.get(Commod.SUPPLIES);
    if (store != null) {
      store.netPerMonth -= perMonth;
    }
    boolean moves = !t.preview && t.monthFrac > 0f;
    float share = moves ? t.monthFrac : 1f / Math.max(1, t.s.ticksPerMonth);
    float due = perMonth * share;
    float have = Math.max(0f, t.stock(Commod.SUPPLIES));
    float paid = Math.min(have, due);
    t.upkeepDue = due;
    t.upkeepPaid = paid;
    if (!moves) {
      return;
    }
    float owedBefore = Math.max(0f, t.suppliesOwed);
    float repaid = Math.min(have - paid, owedBefore);
    float owed = owedBefore - repaid + (due - paid);
    if (owed < EPS) {
      owed = 0f;
    }
    if (paid + repaid > 0f) {
      t.stock.put(Commod.SUPPLIES, Float.valueOf(Math.max(0f, have - paid - repaid)));
    }
    t.suppliesOwed = owed;
    if (owedBefore < REPORTED && owed >= REPORTED) {
      t.event(FlotillaEvent.Type.UPKEEP_OWED, null, perMonth);
    }
  }

  public static Settlement settle(float owed, float cargoSupplies, float credits, float price) {
    float due = Math.round(Math.max(0f, owed));
    float supplies = Math.min(due, (float) Math.floor(Math.max(0f, cargoSupplies)));
    float rest = due - supplies;
    float each = Math.max(0f, price);
    float paid = Math.min(rest * each, Math.max(0f, credits));
    float forgiven = each > 0f ? rest - paid / each : rest;
    return new Settlement(supplies, rest, paid, forgiven < EPS ? 0f : forgiven);
  }

  public interface Side<S, K> {

    List<S> ships();

    void addShip(S ship);

    void removeShip(S ship);

    List<K> stacks();

    boolean isWeapons(K stack);

    boolean isWings(K stack);

    String commodity(K stack);

    float size(K stack);

    void addStack(K stack);

    void removeStack(K stack);
  }

  public static <S, K> int bringHome(Side<S, K> detached, Side<S, K> player) {
    int ships = 0;
    for (S ship : detached.ships()) {
      player.addShip(ship);
      detached.removeShip(ship);
      ships++;
    }
    for (K stack : detached.stacks()) {
      if (stack != null && comesHome(detached.isWeapons(stack), detached.isWings(stack),
          detached.commodity(stack), detached.size(stack))) {
        player.addStack(stack);
        detached.removeStack(stack);
      }
    }
    return ships;
  }

  public static boolean comesHome(boolean weapon, boolean wing, String commodity, float size) {
    return (weapon || wing || Works.PARTS.equals(commodity)) && size > 0f;
  }

  public static float afterLoss(float frozen, float survivors, float lost) {
    float kept = Math.max(0f, survivors);
    float gone = Math.max(0f, lost);
    if (frozen <= 0f || gone <= 0f) {
      return Math.max(0f, frozen);
    }
    return frozen * kept / (kept + gone);
  }
}
