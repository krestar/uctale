package com.uctale.uctale.application.game;

import com.uctale.uctale.application.narrative.NarrativeTurn;
import com.uctale.uctale.domain.action.ActionType;
import com.uctale.uctale.domain.action.PlayerAction;
import com.uctale.uctale.dto.GameChoice;
import com.uctale.uctale.dto.GameProgressRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChoiceCodecTest {

    private ChoiceCodec codec;

    @BeforeEach
    void setUp() {
        codec = new ChoiceCodec(new ObjectMapper());
    }

    @Test
    @DisplayName("서버가 발급한 action은 token type sourceTurn arguments가 모두 일치할 때만 PlayerAction으로 변환된다")
    void issuedActionResolvesOnlyWithExactContract() {
        GameChoice issued = codec.issue(List.of(new NarrativeTurn.Choice(7, "문을 잠근다")), 3).getFirst();
        String stored = codec.serialize(List.of(issued));
        GameProgressRequest request = new GameProgressRequest(
                42L,
                issued.id(),
                3,
                issued.actionToken(),
                issued.actionType(),
                issued.sourceTurn(),
                issued.arguments()
        );

        PlayerAction action = codec.resolve(stored, request);

        assertThat(action.type()).isEqualTo(ActionType.NARRATIVE_CHOICE);
        assertThat(action.sourceTurn()).isEqualTo(3);
        assertThat(action.displayText()).isEqualTo("문을 잠근다");
        assertThat(action.arguments()).containsEntry("choiceId", "7");
    }

    @Test
    @DisplayName("action token type arguments 변조는 현재 턴 행동으로 인정하지 않는다")
    void tamperedActionIsRejected() {
        GameChoice issued = codec.issue(List.of(new NarrativeTurn.Choice(7, "문을 잠근다")), 3).getFirst();
        String stored = codec.serialize(List.of(issued));

        assertThatThrownBy(() -> codec.resolve(stored, new GameProgressRequest(
                42L, 7, 3, "tampered", issued.actionType(), issued.sourceTurn(), issued.arguments()
        ))).isInstanceOf(InvalidChoiceException.class);

        assertThatThrownBy(() -> codec.resolve(stored, new GameProgressRequest(
                42L, 7, 3, issued.actionToken(), "USE_ITEM", issued.sourceTurn(), issued.arguments()
        ))).isInstanceOf(InvalidChoiceException.class);

        assertThatThrownBy(() -> codec.resolve(stored, new GameProgressRequest(
                42L, 7, 3, issued.actionToken(), issued.actionType(), issued.sourceTurn(), Map.of("choiceId", "8")
        ))).isInstanceOf(InvalidChoiceException.class);
    }

    @Test
    @DisplayName("이전 턴에서 발급된 action은 현재 턴에 재사용할 수 없다")
    void expiredActionIsRejected() {
        GameChoice issued = codec.issue(List.of(new NarrativeTurn.Choice(7, "문을 잠근다")), 2).getFirst();
        String stored = codec.serialize(List.of(issued));

        assertThatThrownBy(() -> codec.resolve(stored, new GameProgressRequest(
                42L, 7, 3, issued.actionToken(), issued.actionType(), issued.sourceTurn(), issued.arguments()
        ))).isInstanceOf(InvalidChoiceException.class);
    }

    @Test
    @DisplayName("action metadata가 없는 기존 저장 선택지는 legacy compatibility adapter로만 수락한다")
    void legacyChoiceUsesCompatibilityAdapter() {
        String stored = codec.serialize(List.of(new GameChoice(7, "문을 잠근다")));

        PlayerAction action = codec.resolve(stored, new GameProgressRequest(42L, 7, 3));

        assertThat(action.type()).isEqualTo(ActionType.NARRATIVE_CHOICE);
        assertThat(action.sourceTurn()).isEqualTo(3);
        assertThat(action.displayText()).isEqualTo("문을 잠근다");

        assertThatThrownBy(() -> codec.resolve(stored, new GameProgressRequest(
                42L, 7, 3, "invented", ActionType.NARRATIVE_CHOICE.name(), 3, Map.of("choiceId", "7")
        ))).isInstanceOf(InvalidChoiceException.class);
    }
}
