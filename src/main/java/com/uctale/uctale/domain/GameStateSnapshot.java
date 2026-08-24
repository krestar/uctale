package com.uctale.uctale.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "game_state_snapshot")
@Getter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class GameStateSnapshot {

    @Id
    @Column(name = "session_id")
    private Long sessionId;

    @MapsId
    @OneToOne(optional = false)
    @JoinColumn(name = "session_id")
    private GameSession gameSession;

    @Column(name = "state_json", nullable = false, columnDefinition = "text")
    private String stateJson;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public GameStateSnapshot(GameSession gameSession, String stateJson) {
        this.gameSession = gameSession;
        this.stateJson = stateJson;
    }

    public void updateStateJson(String stateJson) {
        this.stateJson = stateJson;
    }
}
