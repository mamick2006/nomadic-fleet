package nomad.ui;

import java.awt.Color;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.CampaignUIAPI.CoreUITradeMode;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.Fonts;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import nomad.campaign.NomadManager;

public final class NomadUi {

  private NomadUi() {
  }

  public static Color base() {
    return Misc.getBasePlayerColor();
  }

  public static Color dark() {
    return Misc.getDarkPlayerColor();
  }

  public static Color bright() {
    return Misc.getBrightPlayerColor();
  }

  public static Color text() {
    return Misc.getTextColor();
  }

  public static Color gray() {
    return Misc.getGrayColor();
  }

  public static Color hl() {
    return Misc.getHighlightColor();
  }

  public static Color good() {
    return Misc.getPositiveHighlightColor();
  }

  public static Color bad() {
    return Misc.getNegativeHighlightColor();
  }

  public static Color warn() {
    return new Color(255, 160, 60);
  }

  public static CustomUIPanelPlugin consoleRow() {
    return NomadConsoleRoute.INSTANCE;
  }

  public static String i(float v) {
    return "" + Math.round(v);
  }

  public static String f1(float v) {
    return String.format("%.1f", v);
  }

  public static String pct(float frac) {
    return Math.round(frac * 100f) + "%";
  }

  public static String signed(float v) {
    String s = Math.abs(v) < 0.05f ? "0" : (v > 0 ? "+" + f1(v) : f1(v));
    return s;
  }

  public static String credits(float v) {
    return Misc.getDGSCredits(v);
  }

  public static Color colorFor(float net) {
    if (net > 0.05f) {
      return good();
    }
    if (net < -0.05f) {
      return bad();
    }
    return text();
  }

  public static Color darkGreen() {
    Color g = good();
    return new Color(g.getRed() / 3, g.getGreen() / 3, g.getBlue() / 3, 255);
  }

  private static NomadText.Measure engine(final String font, final float scale) {
    return new NomadText.Measure() {
      @Override
      public float width(String s) {
        if (s == null || s.isEmpty()) {
          return 0f;
        }
        String f = font == null ? Fonts.DEFAULT_SMALL : font;
        return Global.getSettings().computeStringWidth(s, f) * scale;
      }
    };
  }

  public static final NomadText.Measure PARA = engine(null, 1f);
  public static final NomadText.Measure NAME = engine(Fonts.INSIGNIA_LARGE, 18f / 21f);
  public static final NomadText.Measure BUTTON = engine(Fonts.ORBITRON_12, 1f);

  public static String days(float days) {
    int d = Math.max(1, Math.round(days));
    return d == 1 ? "1 day" : d + " days";
  }

  public static final String POINT_ICON = "nomad_autoforge_production";
  public static final String CREDITS_ICON = "nomad_credits";
  private static final float ROW_ICON_PX = 32f;
  static final float INLINE_ICON_PX = 18f;

  public static final float PANE_INSET = 10f;

  public static final float WIDE_MIN_W = 1000f;
  public static final float WIDE_SCREEN_FRAC = 0.70f;

  public static boolean isWide(float paneWidth) {
    return paneWidth >= WIDE_MIN_W
        && paneWidth >= WIDE_SCREEN_FRAC * Global.getSettings().getScreenWidth();
  }

  public static String commodityName(String id) {
    CommoditySpecAPI spec = Global.getSettings().getCommoditySpec(id);
    return spec != null ? spec.getName() : id;
  }

  public static float basePrice(String commodityId) {
    CommoditySpecAPI spec = Global.getSettings().getCommoditySpec(commodityId);
    return spec != null ? spec.getBasePrice() : 0f;
  }

  public static CommoditySpecAPI spec(String commodityId) {
    return Global.getSettings().getCommoditySpec(commodityId);
  }

  public static void icon(TooltipMakerAPI el, String commodityId, int num, float pad) {
    CommoditySpecAPI s = spec(commodityId);
    if (s != null) {
      el.beginIconGroup();
      el.addIcons(s, Math.max(1, num), com.fs.starfarer.api.ui.IconRenderMode.NORMAL);
      el.addIconGroup(pad);
      return;
    }
    String sprite = iconSprite(commodityId);
    if (sprite != null) {
      el.addImage(sprite, ROW_ICON_PX, ROW_ICON_PX, pad);
    }
  }

  public static void iconInline(TooltipMakerAPI el, String id, float px, float pad) {
    CommoditySpecAPI s = spec(id);
    if (s != null) {
      String sprite = s.getIconName();
      if (sprite != null && !sprite.isEmpty()) {
        el.addImage(sprite, px, px, pad);
        return;
      }
    }
    String custom = iconSprite(id);
    if (custom != null) {
      el.addImage(custom, px, px, pad);
    }
  }

  public static String iconSprite(String id) {
    try {
      String s = Global.getSettings().getSpriteName("icons", id);
      return s == null || s.isEmpty() ? null : s;
    } catch (Exception e) {
      return null;
    }
  }

  public static void pointCost(CustomPanelAPI outer, TooltipMakerAPI cell, float width, String number,
      Color col, float pad) {
    CustomPanelAPI p = outer.createCustomPanel(width, 22f, null);
    String sprite = iconSprite(POINT_ICON);
    TooltipMakerAPI ic = p.createUIElement(22f, 22f, false);
    boolean drew = false;
    if (sprite != null) {
      ic.addImage(sprite, INLINE_ICON_PX, INLINE_ICON_PX, 0f);
      drew = true;
    }
    p.addUIElement(ic).inTL(0f, 2f);
    TooltipMakerAPI tx = p.createUIElement(Math.max(20f, width - 26f), 22f, false);
    tx.addPara(number, col, 3f);
    if (drew) {
      p.addUIElement(tx).rightOfMid(ic, 3f);
    } else {
      p.addUIElement(tx).inTL(0f, 2f);
    }
    cell.addCustom(p, pad);
  }

  public static CampaignFleetAPI flotillaFleet() {
    NomadManager m = NomadManager.getInstance();
    return m == null ? null : m.getFlagshipFleet();
  }

  public static InteractionDialogAPI currentDialog() {
    CampaignUIAPI ui = Global.getSector() == null ? null : Global.getSector().getCampaignUI();
    return ui == null ? null : ui.getCurrentInteractionDialog();
  }

  public enum DockState {
    DOCKED,
    NOT_DOCKED,
    NO_PORT
  }

  public static DockState dockState() {
    InteractionDialogAPI dialog = currentDialog();
    if (dialog == null || dialog.getInteractionTarget() == null) {
      return DockState.NOT_DOCKED;
    }
    SectorEntityToken target = dialog.getInteractionTarget();
    MarketAPI market = target.getMarket();
    if (market == null) {
      return DockState.NOT_DOCKED;
    }
    Object tradeMode = target.getMemory().get("$tradeMode");
    if (tradeMode == null || tradeMode.equals(CoreUITradeMode.NONE) || tradeMode.equals("NONE")) {
      return DockState.NOT_DOCKED;
    }
    for (Industry ind : market.getIndustries()) {
      if (ind.getSpec().hasTag(Industries.TAG_STATION) || ind.getSpec().hasTag(Industries.TAG_SPACEPORT)) {
        return DockState.DOCKED;
      }
    }
    return DockState.NO_PORT;
  }

  public static boolean playerIsDocked() {
    return dockState() == DockState.DOCKED;
  }

  public static MarketAPI dockedMarket() {
    if (dockState() != DockState.DOCKED) {
      return null;
    }
    InteractionDialogAPI dialog = currentDialog();
    return dialog == null || dialog.getInteractionTarget() == null
        ? null : dialog.getInteractionTarget().getMarket();
  }

  public static boolean addIllustration(CustomPanelAPI outer, TooltipMakerAPI el, float width, float maxWidth,
      String spriteId, float pad) {
    String sprite;
    try {
      sprite = Global.getSettings().getSpriteName("illustrations", spriteId);
    } catch (Exception e) {
      return false;
    }
    if (sprite == null || sprite.isEmpty()) {
      return false;
    }
    float w = Math.min(maxWidth, Math.max(120f, width - 20f));
    float h = w * 300f / 480f;
    CustomPanelAPI holder = outer.createCustomPanel(w, h, null);
    TooltipMakerAPI img = holder.createUIElement(w, h, false);
    img.addImage(sprite, w, h, 0f);
    holder.addUIElement(img).inTL(0f, 0f);
    el.addCustom(holder, pad);
    return true;
  }

  public static CustomPanelAPI bar(CustomPanelAPI outer, float width, float height,
      float[] fractions, Color[] colors) {
    CustomPanelAPI holder = outer.createCustomPanel(width, height, new BaseCustomUIPanelPlugin());
    UIElementPrev prev = new UIElementPrev();
    float used = 0f;
    for (int i = 0; i < fractions.length; i++) {
      float frac = Math.max(0f, Math.min(1f - used, fractions[i]));
      if (frac <= 0.0001f) {
        continue;
      }
      addSegment(holder, width * frac, height, colors[i], prev);
      used += frac;
    }
    float remainder = 1f - used;
    if (remainder > 0.0001f) {
      addSegment(holder, width * remainder, height, trackColor(), prev);
    }
    return holder;
  }

  private static void addSegment(CustomPanelAPI holder, float w, float h, Color c, UIElementPrev prev) {
    TooltipMakerAPI seg = holder.createUIElement(w, h, false);
    ButtonAPI block = seg.addButton("", new Object(), c, c, Alignment.MID, CutStyle.NONE, w, h, 0f);
    block.setClickable(false);
    block.setMouseOverSound(null);
    block.setHighlightBrightness(0f);
    if (prev.el == null) {
      holder.addUIElement(seg).inTL(0f, 0f);
    } else {
      holder.addUIElement(seg).rightOfTop(prev.el, 0f);
    }
    prev.el = seg;
  }

  private static Color trackColor() {
    Color d = dark();
    return new Color(d.getRed(), d.getGreen(), d.getBlue(), 90);
  }

  private static final class UIElementPrev {
    TooltipMakerAPI el;
  }

  public static void setEnabledSayingWhy(ButtonAPI button, boolean enabled) {
    button.setEnabled(enabled);
    button.setShowTooltipWhileInactive(true);
  }
}
