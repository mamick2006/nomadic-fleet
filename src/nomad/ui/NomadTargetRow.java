package nomad.ui;

import java.lang.ref.WeakReference;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.Fonts;
import com.fs.starfarer.api.ui.TextFieldAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import nomad.NomadStrings;

final class NomadTargetRow {

  private static final float PAD = 3f;

  interface Target {

    String id();

    String name();

    float now();

    float min();

    float after(String arg);

    boolean live();

    String notLive();

    void atMinLine(TooltipMakerAPI t);

    void cannotRaiseLine(TooltipMakerAPI t);

    void moveLines(TooltipMakerAPI t, float after, boolean down, String arg);

    String fieldTip();
  }

  private WeakReference<TextFieldAPI> field;
  private String typed = "";
  private String typedFor;

  String typed() {
    TextFieldAPI f = field == null ? null : field.get();
    if (f != null) {
      String now = f.getText();
      typed = now == null ? "" : now;
    }
    return typed;
  }

  void release() {
    TextFieldAPI f = field == null ? null : field.get();
    if (f != null) {
      typed();
      if (f.hasFocus()) {
        Global.getFocusManager().popFocusOwnerIfOwner(f);
      }
    }
    field = null;
  }

  void forget() {
    typed = "";
    typedFor = null;
  }

  void reopen() {
    typedFor = null;
  }

  static String[] stepWords() {
    int[] steps = NomadPanelFigures.TARGET_STEPS;
    String[] words = new String[steps.length + 1];
    words[0] = NomadStrings.get("nomad_flotilla", "panels_target_min");
    for (int i = 0; i < steps.length; i++) {
      words[i + 1] = NomadPanelFigures.stepLabel(steps[i]);
    }
    return words;
  }

  static String stepArg(int i) {
    return i == 0 ? NomadWorkActions.MIN : "" + NomadPanelFigures.TARGET_STEPS[i - 1];
  }

  void step(CustomPanelAPI own, NomadGrid.Box at, float y, final Target target,
      final String arg, String word) {
    final float now = target.now();
    final boolean atMin = NomadPanelFigures.atMinimum(now, target.min());
    final boolean toMin = NomadWorkActions.MIN.equals(arg);
    final boolean down = toMin || arg.startsWith("-");
    final float after = target.after(arg);
    final boolean moves = Math.abs(after - now) >= 0.5f;
    TooltipMakerAPI cell = NomadCell.element(own, at.w, at.h);
    ButtonAPI b = cell.addButton(word,
        NomadWorkActions.id(NomadWorkActions.Verb.TARGET, target.id(), arg), NomadUi.text(),
        NomadUi.dark(), Alignment.MID, CutStyle.NONE, at.w, at.h, 0f);
    NomadUi.setEnabledSayingWhy(b, target.live() && moves);
    cell.addTooltipToPrevious(NomadTip.guard(new NomadTip() {
      @Override
      public void createTooltip(TooltipMakerAPI t, boolean expanded, Object param) {
        String name = target.name();
        if (toMin) {
          t.addPara(NomadStrings.get("nomad_flotilla", "store_target_min_tip"), 0f,
              NomadUi.hl(), name, NomadUi.i(target.min()));
        } else if (down) {
          t.addPara(NomadStrings.get("nomad_flotilla", "store_target_lower_tip"), 0f,
              NomadUi.hl(), name);
        } else {
          t.addPara(NomadStrings.get("nomad_flotilla", "store_target_raise_tip"), 0f,
              NomadUi.hl(), name);
        }
        if (!target.live()) {
          t.addPara(target.notLive(), NomadUi.warn(), PAD * 2f);
          return;
        }
        if (!moves) {
          if (down || atMin) {
            target.atMinLine(t);
          } else {
            target.cannotRaiseLine(t);
          }
          return;
        }
        moveLines(t, target, now, after, down, arg);
      }
    }), TooltipMakerAPI.TooltipLocation.BELOW, false);
    NomadCell.place(own, cell, at.x, y + at.y);
  }

  void field(CustomPanelAPI own, NomadGrid.Box at, float y, final Target target) {
    float now = target.now();
    String shows = target.id() + "=" + Math.round(now);
    if (!shows.equals(typedFor)) {
      typed = Integer.toString(Math.round(now));
      typedFor = shows;
    }
    TooltipMakerAPI box = NomadCell.element(own, at.w, at.h);
    TextFieldAPI f = box.addTextField(at.w, at.h, Fonts.DEFAULT_SMALL, 0f);
    f.setText(typed);
    f.setColor(NomadUi.text());
    final String tip = target.fieldTip();
    box.addTooltipToPrevious(NomadTip.guard(new NomadTip() {
      @Override
      public void createTooltip(TooltipMakerAPI t, boolean expanded, Object param) {
        t.addPara(tip, 0f);
      }
    }), TooltipMakerAPI.TooltipLocation.BELOW, false);
    NomadCell.place(own, box, at.x, y + at.y);
    field = new WeakReference<TextFieldAPI>(f);
  }

  void set(CustomPanelAPI own, NomadGrid.Box at, float y, final Target target) {
    final float now = target.now();
    TooltipMakerAPI cell = NomadCell.element(own, at.w, at.h);
    ButtonAPI b = cell.addButton(NomadStrings.get("nomad_flotilla", "uifix_target_set"),
        NomadWorkActions.id(NomadWorkActions.Verb.TARGET, target.id(), NomadWorkActions.TYPED),
        NomadUi.text(), NomadUi.dark(), Alignment.MID, CutStyle.NONE, at.w, at.h, 0f);
    NomadUi.setEnabledSayingWhy(b, target.live());
    cell.addTooltipToPrevious(NomadTip.guard(new NomadTip() {
      @Override
      public void createTooltip(TooltipMakerAPI t, boolean expanded, Object param) {
        String name = target.name();
        if (!target.live()) {
          t.addPara(target.notLive(), NomadUi.warn(), 0f);
          return;
        }
        String arg = NomadWorkActions.exactArg(typed());
        if (arg == null) {
          t.addPara(NomadStrings.get("nomad_flotilla", "ui3_target_set_empty"), NomadUi.warn(),
              0f);
          return;
        }
        float after = target.after(arg);
        String to = NomadUi.i(after);
        NomadWorkActions.para(t,
            NomadStrings.get("nomad_flotilla", "uifix_target_set_tip", name, to), NomadUi.hl(),
            name, to);
        if (Math.abs(after - now) < 0.5f) {
          return;
        }
        moveLines(t, target, now, after, after < now, arg);
      }
    }), TooltipMakerAPI.TooltipLocation.BELOW, false);
    NomadCell.place(own, cell, at.x, y + at.y);
  }

  private static void moveLines(TooltipMakerAPI t, Target target, float now, float after,
      boolean down, String arg) {
    t.addPara(NomadStrings.get("nomad_flotilla", "panels_target_tip_move"), PAD * 2f,
        NomadUi.hl(), NomadUi.i(now), NomadUi.i(after));
    target.moveLines(t, after, down, arg);
  }
}
