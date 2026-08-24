package com.uctale.uctale.application.narrative;

public interface NarrativeGenerator {

    NarrativeTurn createOpening(String worldSetting, String characterSetting);

    NarrativeTurn createNextTurn(NarrativeContext context);
}
