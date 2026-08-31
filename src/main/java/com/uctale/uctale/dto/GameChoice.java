package com.uctale.uctale.dto;

import java.util.Map;

public record GameChoice(
        int id,
        String text,
        String actionToken,
        String actionType,
        Integer sourceTurn,
        Map<String, String> arguments
) {
    public GameChoice(int id, String text) {
        this(id, text, null, null, null, null);
    }
}
