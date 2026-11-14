package nomad.crossmod;

import java.util.Collections;
import java.util.List;

import cq.api.BaseCqFurnishingEffect;
import cq.items.CqCatalog.Furnishing;

import nomad.NomadStrings;
import nomad.campaign.NomadSettings;
import nomad.campaign.econ.EconConstants;

public class NomadFurnishingEffect extends BaseCqFurnishingEffect {

  @Override
  public List<String> effectLines(Furnishing furnishing) {
    return Collections.singletonList(NomadStrings.get("nomad_crossmod", "cq_effect", pct()));
  }

  @Override
  public String[] effectHighlights(Furnishing furnishing) {
    return new String[] {"+" + pct() + "%"};
  }

  private static String pct() {
    return String.valueOf(Math.round(NomadSettings.getFloat(EconConstants.CQ_BOARD_KEY,
        EconConstants.CQ_BOARD_DEFAULT) * 100f));
  }
}
