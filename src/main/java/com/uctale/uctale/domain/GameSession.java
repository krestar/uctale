package com.uctale.uctale.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class GameSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String ownerKey;

    @Column(nullable = false)
    private String worldSetting;

    @Column(nullable = false)
    private String characterSetting;

    @Column(nullable = false)
    private boolean isGameOver = false;

    @Column(nullable = false)
    private int currentTurn = 1;

    @Version
    @Column(nullable = false)
    private long version;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "gameSession", cascade = jakarta.persistence.CascadeType.ALL, orphanRemoval = true)
    private List<GameLog> logs = new ArrayList<>();

    public GameSession(String ownerKey, String worldSetting, String characterSetting) {
        this.ownerKey = ownerKey;
        this.worldSetting = worldSetting;
        this.characterSetting = characterSetting;
    }

    public void advanceTurn() {
        currentTurn += 1;
    }
}
