package com.uctale.uctale.application.narrative;

public interface NarrativeGenerator {

    NarrativeTurn createOpening(String worldSetting, String characterSetting);

    NarrativeTurn createNextTurn(String worldSetting, String characterSetting, String previousStory, String userChoice);
}
