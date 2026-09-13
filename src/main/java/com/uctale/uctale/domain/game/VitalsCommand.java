package com.uctale.uctale.domain.game;

import java.util.Objects;

public sealed interface VitalsCommand permits
        VitalsCommand.Damage,
        VitalsCommand.Heal,
        VitalsCommand.SpendMana,
        VitalsCommand.RestoreMana,
        VitalsCommand.ApplyStatus,
        VitalsCommand.UpdateStatus,
        VitalsCommand.RemoveStatus,
        VitalsCommand.AdvanceStatusDurations {

    record Damage(int amount) implements VitalsCommand {
        public Damage { requirePositive(amount, "damage amount"); }
    }

    record Heal(int amount) implements VitalsCommand {
        public Heal { requirePositive(amount, "heal amount"); }
    }

    record SpendMana(int amount) implements VitalsCommand {
        public SpendMana { requirePositive(amount, "mana spend amount"); }
    }

    record RestoreMana(int amount) implements VitalsCommand {
        public RestoreMana { requirePositive(amount, "mana restore amount"); }
    }

    record ApplyStatus(StatusEffect effect) implements VitalsCommand {
        public ApplyStatus { Objects.requireNonNull(effect, "status effect는 필수입니다."); }
    }

    record UpdateStatus(StatusEffect effect) implements VitalsCommand {
        public UpdateStatus { Objects.requireNonNull(effect, "status effect는 필수입니다."); }
    }

    record RemoveStatus(String definitionId) implements VitalsCommand {
        public RemoveStatus { validateDefinitionId(definitionId); }
    }

    record AdvanceStatusDurations(StatusExpiryTrigger trigger) implements VitalsCommand {
        public AdvanceStatusDurations { Objects.requireNonNull(trigger, "expiry trigger는 필수입니다."); }
    }

    private static void requirePositive(int value, String fieldName) {
        if (value < 1) throw new IllegalArgumentException(fieldName + "는 1 이상이어야 합니다.");
    }

    private static void validateDefinitionId(String definitionId) {
        if (definitionId == null || definitionId.isBlank()) {
            throw new IllegalArgumentException("status effect definitionId는 비어 있을 수 없습니다.");
        }
    }
}
