package com.uctale.uctale.domain;

import jakarta.persistence.*;
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

    @Column(nullable = false)
    private String worldSetting;

    @Column(nullable = false)
    private String characterSetting;

    // 게임이 진행 중인지, 끝났는지 (나중에 엔딩 구현 시 사용)
    private boolean isGameOver = false;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    // 1:N 관계 - 하나의 세션에 여러 턴(로그)이 존재
    @OneToMany(mappedBy = "gameSession", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GameLog> logs = new ArrayList<>();

    public GameSession(String worldSetting, String characterSetting) {
        this.worldSetting = worldSetting;
        this.characterSetting = characterSetting;
    }
}