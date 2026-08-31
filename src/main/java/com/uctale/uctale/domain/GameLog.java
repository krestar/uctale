package com.uctale.uctale.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Immutable
@Table(uniqueConstraints = @UniqueConstraint(
        name = "uk_game_log_session_turn",
        columnNames = {"session_id", "turn_number"}
))
@Getter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class GameLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private GameSession gameSession;

    @Column(nullable = false)
    private int turnNumber;

    @Column(name = "input_choice_id")
    private Integer inputChoiceId;

    @Column(name = "input_choice_text")
    private String inputChoiceText;

    @Column(name = "previous_state_version", nullable = false)
    private int previousStateVersion;

    @Column(name = "state_version", nullable = false)
    private int stateVersion;

    @Column(name = "canonical_result_id", length = 128)
    private String canonicalResultId;

    @Column(name = "generated_story_id", length = 128)
    private String generatedStoryId;

    @Column(columnDefinition = "TEXT")
    private String storyText;

    @Column(columnDefinition = "TEXT")
    private String choicesJson;

    @Column(columnDefinition = "TEXT")
    private String imageUrl;

    @CreatedDate
    @Column(name = "created_at")
    private LocalDateTime committedAt;

    public static GameLog opening(
            GameSession gameSession,
            String storyText,
            String choicesJson,
            String imageUrl
    ) {
        return new GameLog(
                gameSession,
                1,
                null,
                null,
                0,
                1,
                null,
                null,
                storyText,
                choicesJson,
                imageUrl
        );
    }

    public static GameLog committedTurn(
            GameSession gameSession,
            int turnNumber,
            int inputChoiceId,
            String inputChoiceText,
            int previousStateVersion,
            int stateVersion,
            String storyText,
            String choicesJson,
            String imageUrl
    ) {
        return committedTurn(
                gameSession, turnNumber, inputChoiceId, inputChoiceText, previousStateVersion, stateVersion,
                null, null, storyText, choicesJson, imageUrl
        );
    }

    public static GameLog committedTurn(
            GameSession gameSession,
            int turnNumber,
            int inputChoiceId,
            String inputChoiceText,
            int previousStateVersion,
            int stateVersion,
            String canonicalResultId,
            String generatedStoryId,
            String storyText,
            String choicesJson,
            String imageUrl
    ) {
        return new GameLog(
                gameSession,
                turnNumber,
                inputChoiceId,
                inputChoiceText,
                previousStateVersion,
                stateVersion,
                canonicalResultId,
                generatedStoryId,
                storyText,
                choicesJson,
                imageUrl
        );
    }

    private GameLog(
            GameSession gameSession,
            int turnNumber,
            Integer inputChoiceId,
            String inputChoiceText,
            int previousStateVersion,
            int stateVersion,
            String canonicalResultId,
            String generatedStoryId,
            String storyText,
            String choicesJson,
            String imageUrl
    ) {
        this.gameSession = gameSession;
        this.turnNumber = turnNumber;
        this.inputChoiceId = inputChoiceId;
        this.inputChoiceText = inputChoiceText;
        this.previousStateVersion = previousStateVersion;
        this.stateVersion = stateVersion;
        this.canonicalResultId = canonicalResultId;
        this.generatedStoryId = generatedStoryId;
        this.storyText = storyText;
        this.choicesJson = choicesJson;
        this.imageUrl = imageUrl;
    }
}
