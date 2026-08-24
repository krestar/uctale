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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
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

    @Column(columnDefinition = "TEXT")
    private String storyText;

    @Column(columnDefinition = "TEXT")
    private String choicesJson;

    @Column(columnDefinition = "TEXT")
    private String imageUrl;

    private String userChoice;

    @CreatedDate
    private LocalDateTime createdAt;

    public GameLog(GameSession gameSession, int turnNumber, String storyText, String choicesJson, String imageUrl) {
        this.gameSession = gameSession;
        this.turnNumber = turnNumber;
        this.storyText = storyText;
        this.choicesJson = choicesJson;
        this.imageUrl = imageUrl;
    }

    public void updateUserChoice(String userChoice) {
        this.userChoice = userChoice;
    }
}
