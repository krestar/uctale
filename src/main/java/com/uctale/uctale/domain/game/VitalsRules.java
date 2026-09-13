package com.uctale.uctale.domain.game;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class VitalsRules {

    public Result apply(CharacterVitals vitals, List<VitalsCommand> commands) {
        Objects.requireNonNull(vitals, "vitals는 필수입니다.");
        if (commands == null || commands.isEmpty()) {
            return new Result(vitals, List.of());
        }

        CharacterVitals current = vitals;
        List<GameResult.StateChange> changes = new ArrayList<>();
        Set<String> refreshedStatusIds = new HashSet<>();
        EnumSet<StatusExpiryTrigger> advancedTriggers = EnumSet.noneOf(StatusExpiryTrigger.class);

        for (VitalsCommand command : commands) {
            Objects.requireNonNull(command, "vitals command는 null일 수 없습니다.");
            Applied applied = applyOne(current, command, refreshedStatusIds, advancedTriggers);
            current = applied.vitals();
            changes.addAll(applied.stateChanges());
        }
        return new Result(current, changes);
    }

    public static CharacterVitals replay(CharacterVitals vitals, List<GameResult.StateChange> stateChanges) {
        Objects.requireNonNull(vitals, "vitals는 필수입니다.");
        if (stateChanges == null || stateChanges.isEmpty()) {
            return vitals;
        }

        CharacterVitals current = vitals;
        for (GameResult.StateChange stateChange : stateChanges) {
            Objects.requireNonNull(stateChange, "stateChange는 null일 수 없습니다.");
            if (stateChange instanceof GameResult.VitalsChanged changed) {
                current = replayVitalsChange(current, changed);
            } else if (stateChange instanceof GameResult.StatusEffectApplied applied) {
                if (current.statusEffects().containsKey(applied.effect().definitionId())) {
                    throw new IllegalStateException("StatusEffectApplied audit 대상이 이미 canonical state에 존재합니다.");
                }
                current = putStatus(current, applied.effect());
            } else if (stateChange instanceof GameResult.StatusEffectUpdated updated) {
                StatusEffect existing = current.requireStatus(updated.previous().definitionId());
                if (!existing.equals(updated.previous())) {
                    throw new IllegalStateException("StatusEffectUpdated audit의 이전 상태가 canonical state와 일치하지 않습니다.");
                }
                current = putStatus(current, updated.next());
            } else if (stateChange instanceof GameResult.StatusDurationChanged durationChanged) {
                StatusEffect existing = current.requireStatus(durationChanged.definitionId());
                if (existing.remainingTurns() != durationChanged.previousRemainingTurns()) {
                    throw new IllegalStateException("StatusDurationChanged audit의 이전 duration이 canonical state와 일치하지 않습니다.");
                }
                current = putStatus(current, existing.withRuntime(
                        existing.stacks(),
                        existing.intensity(),
                        durationChanged.nextRemainingTurns()
                ));
            } else if (stateChange instanceof GameResult.StatusEffectRemoved removed) {
                StatusEffect existing = current.requireStatus(removed.effect().definitionId());
                if (!existing.equals(removed.effect())) {
                    throw new IllegalStateException("StatusEffectRemoved audit의 기존 상태가 canonical state와 일치하지 않습니다.");
                }
                current = removeStatus(current, removed.effect().definitionId());
            }
        }
        return current;
    }

    private Applied applyOne(
            CharacterVitals vitals,
            VitalsCommand command,
            Set<String> refreshedStatusIds,
            EnumSet<StatusExpiryTrigger> advancedTriggers
    ) {
        if (command instanceof VitalsCommand.Damage damage) {
            return changeHp(vitals, damage.amount(), false);
        }
        if (command instanceof VitalsCommand.Heal heal) {
            return changeHp(vitals, heal.amount(), true);
        }
        if (command instanceof VitalsCommand.SpendMana spendMana) {
            return spendMana(vitals, spendMana.amount());
        }
        if (command instanceof VitalsCommand.RestoreMana restoreMana) {
            return restoreMana(vitals, restoreMana.amount());
        }
        if (command instanceof VitalsCommand.ApplyStatus applyStatus) {
            StatusEffect effect = applyStatus.effect();
            if (vitals.statusEffects().containsKey(effect.definitionId())) {
                throw new IllegalArgumentException("같은 definitionId의 status effect를 중복 적용할 수 없습니다: " + effect.definitionId());
            }
            refreshedStatusIds.add(effect.definitionId());
            return new Applied(
                    putStatus(vitals, effect),
                    List.of(new GameResult.StatusEffectApplied(effect))
            );
        }
        if (command instanceof VitalsCommand.UpdateStatus updateStatus) {
            StatusEffect next = updateStatus.effect();
            StatusEffect previous = vitals.requireStatus(next.definitionId());
            requireSameStatusDefinition(previous, next);
            if (previous.equals(next)) {
                throw new IllegalArgumentException("status effect update는 실제 상태를 변경해야 합니다.");
            }
            refreshedStatusIds.add(next.definitionId());
            return new Applied(
                    putStatus(vitals, next),
                    List.of(new GameResult.StatusEffectUpdated(previous, next))
            );
        }
        if (command instanceof VitalsCommand.RemoveStatus removeStatus) {
            StatusEffect removed = vitals.requireStatus(removeStatus.definitionId());
            refreshedStatusIds.remove(removeStatus.definitionId());
            return new Applied(
                    removeStatus(vitals, removeStatus.definitionId()),
                    List.of(new GameResult.StatusEffectRemoved(removed, GameResult.StatusRemovalReason.EXPLICIT))
            );
        }
        if (command instanceof VitalsCommand.AdvanceStatusDurations advance) {
            if (!advancedTriggers.add(advance.trigger())) {
                throw new IllegalArgumentException("같은 status expiry timing은 한 transition에서 두 번 진행할 수 없습니다: " + advance.trigger());
            }
            return advanceDurations(vitals, advance.trigger(), refreshedStatusIds);
        }
        throw new IllegalArgumentException("지원하지 않는 vitals command입니다: " + command.getClass().getName());
    }

    private Applied changeHp(CharacterVitals vitals, int amount, boolean healing) {
        ResourcePool hp = vitals.hp();
        int nextValue;
        GameResult.VitalsChangeReason reason;
        if (healing) {
            nextValue = (int) Math.min((long) hp.max(), (long) hp.current() + amount);
            reason = GameResult.VitalsChangeReason.HEAL;
            if (nextValue == hp.current()) {
                throw new IllegalArgumentException("최대 HP에서는 heal로 상태를 변경할 수 없습니다.");
            }
        } else {
            nextValue = (int) Math.max(0L, (long) hp.current() - amount);
            reason = GameResult.VitalsChangeReason.DAMAGE;
            if (nextValue == hp.current()) {
                throw new IllegalArgumentException("0 HP에서는 damage로 상태를 변경할 수 없습니다.");
            }
        }
        GameResult.VitalsChanged change = new GameResult.VitalsChanged(
                GameResult.VitalResource.HP,
                hp.current(),
                nextValue,
                nextValue - hp.current(),
                reason
        );
        return new Applied(vitals.withHp(hp.withCurrent(nextValue)), List.of(change));
    }

    private Applied spendMana(CharacterVitals vitals, int amount) {
        ResourcePool mp = vitals.mp();
        if (amount > mp.current()) {
            throw new IllegalArgumentException("현재 MP보다 많이 소비할 수 없습니다.");
        }
        int nextValue = mp.current() - amount;
        GameResult.VitalsChanged change = new GameResult.VitalsChanged(
                GameResult.VitalResource.MP,
                mp.current(),
                nextValue,
                -amount,
                GameResult.VitalsChangeReason.SPEND
        );
        return new Applied(vitals.withMp(mp.withCurrent(nextValue)), List.of(change));
    }

    private Applied restoreMana(CharacterVitals vitals, int amount) {
        ResourcePool mp = vitals.mp();
        int nextValue = (int) Math.min((long) mp.max(), (long) mp.current() + amount);
        if (nextValue == mp.current()) {
            throw new IllegalArgumentException("최대 MP에서는 restore로 상태를 변경할 수 없습니다.");
        }
        GameResult.VitalsChanged change = new GameResult.VitalsChanged(
                GameResult.VitalResource.MP,
                mp.current(),
                nextValue,
                nextValue - mp.current(),
                GameResult.VitalsChangeReason.RESTORE
        );
        return new Applied(vitals.withMp(mp.withCurrent(nextValue)), List.of(change));
    }

    private Applied advanceDurations(
            CharacterVitals vitals,
            StatusExpiryTrigger trigger,
            Set<String> refreshedStatusIds
    ) {
        CharacterVitals current = vitals;
        List<GameResult.StateChange> changes = new ArrayList<>();
        List<StatusEffect> candidates = new ArrayList<>(vitals.statusEffects().values());
        for (StatusEffect effect : candidates) {
            if (effect.expiryTrigger() != trigger || refreshedStatusIds.contains(effect.definitionId())) {
                continue;
            }
            if (effect.remainingTurns() == 1) {
                current = removeStatus(current, effect.definitionId());
                changes.add(new GameResult.StatusEffectRemoved(effect, GameResult.StatusRemovalReason.EXPIRED));
            } else {
                StatusEffect next = effect.withRuntime(
                        effect.stacks(),
                        effect.intensity(),
                        effect.remainingTurns() - 1
                );
                current = putStatus(current, next);
                changes.add(new GameResult.StatusDurationChanged(
                        effect.definitionId(),
                        effect.remainingTurns(),
                        next.remainingTurns()
                ));
            }
        }
        return new Applied(current, changes);
    }

    private static CharacterVitals replayVitalsChange(CharacterVitals vitals, GameResult.VitalsChanged changed) {
        ResourcePool pool = changed.resource() == GameResult.VitalResource.HP ? vitals.hp() : vitals.mp();
        if (pool.current() != changed.previousValue()) {
            throw new IllegalStateException("VitalsChanged audit의 이전 값이 canonical state와 일치하지 않습니다.");
        }
        ResourcePool nextPool = pool.withCurrent(changed.nextValue());
        return changed.resource() == GameResult.VitalResource.HP
                ? vitals.withHp(nextPool)
                : vitals.withMp(nextPool);
    }

    private static CharacterVitals putStatus(CharacterVitals vitals, StatusEffect effect) {
        Map<String, StatusEffect> next = new LinkedHashMap<>(vitals.statusEffects());
        next.put(effect.definitionId(), effect);
        return vitals.withStatusEffects(next);
    }

    private static CharacterVitals removeStatus(CharacterVitals vitals, String definitionId) {
        Map<String, StatusEffect> next = new LinkedHashMap<>(vitals.statusEffects());
        next.remove(definitionId);
        return vitals.withStatusEffects(next);
    }

    private static void requireSameStatusDefinition(StatusEffect previous, StatusEffect next) {
        if (!previous.definitionId().equals(next.definitionId())
                || previous.expiryTrigger() != next.expiryTrigger()
                || previous.incapacitating() != next.incapacitating()) {
            throw new IllegalArgumentException("status effect update로 definition metadata를 변경할 수 없습니다.");
        }
    }

    public record Result(CharacterVitals vitals, List<GameResult.StateChange> stateChanges) {
        public Result {
            Objects.requireNonNull(vitals, "vitals는 필수입니다.");
            stateChanges = stateChanges == null ? List.of() : List.copyOf(stateChanges);
        }
    }

    private record Applied(CharacterVitals vitals, List<GameResult.StateChange> stateChanges) {
        private Applied {
            Objects.requireNonNull(vitals, "vitals는 필수입니다.");
            stateChanges = stateChanges == null ? List.of() : List.copyOf(stateChanges);
        }
    }
}
