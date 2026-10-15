package com.uctale.uctale.domain.game;

/**
 * Canonical game rule contract version. This is distinct from the snapshot JSON schema.
 * Increment only when a changed deterministic rule needs a new compatibility decision.
 */
public final class GameRuleset {
    public static final int CURRENT_VERSION = 2;

    private GameRuleset() {}
}
