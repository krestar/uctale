package com.uctale.uctale.application.game;

import com.uctale.uctale.domain.game.GameResult;
import com.uctale.uctale.domain.game.StatusEffect;
import com.uctale.uctale.domain.game.StatusExpiryTrigger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VitalsAuditCodecTest {
    private final VitalsAuditCodec codec = new VitalsAuditCodec(new ObjectMapper());

    @Test
    @DisplayName("vitals/status state change는 GameLog audit JSON으로 손실 없이 round trip한다")
    void roundTrip() {
        StatusEffect poison = new StatusEffect("poison", 2, 3, 4, StatusExpiryTrigger.END_OF_TURN, false);
        List<GameResult.StateChange> changes = List.of(
                new GameResult.VitalsChanged(GameResult.VitalResource.HP, 10, 6, -4, GameResult.VitalsChangeReason.DAMAGE),
                new GameResult.StatusEffectApplied(poison),
                new GameResult.StatusDurationChanged("poison", 4, 3)
        );
        assertThat(codec.deserialize(codec.serialize(changes))).containsExactlyElementsOf(changes);
    }

    @Test
    @DisplayName("vitals/status와 무관한 state change는 전용 audit에 중복 저장하지 않는다")
    void ignoresUnrelatedChanges() {
        assertThat(codec.serialize(List.of(new GameResult.TurnAdvanced(1, 2)))).isNull();
    }

    @Test
    @DisplayName("알 수 없는 vitals/status audit type은 조용히 무시하지 않는다")
    void rejectsUnknownType() {
        assertThatThrownBy(() -> codec.deserialize("[{\"type\":\"UNKNOWN\"}]"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("지원하지 않는");
    }
}
