package com.uctale.uctale.application.narrative;

public interface NarrativeGenerator {

    NarrativeTurn createOpening(String worldSetting, String characterSetting);

    NarrativeTurn createNextTurn(NarrativeContext context);

    default NarrativeTurn repairOpening(String worldSetting, String characterSetting, String reasonCode) {
        return createOpening(worldSetting, characterSetting);
    }

    default NarrativeTurn repairNextTurn(NarrativeContext context, String reasonCode) {
        return createNextTurn(context);
    }
}
