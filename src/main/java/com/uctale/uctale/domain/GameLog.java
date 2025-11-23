package com.uctale.uctale.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class GameLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private GameSession gameSession;

    private int turnNumber; // 몇 번째 턴인지

    @Column(columnDefinition = "TEXT")
    private String storyText; // AI가 생성한 스토리

    @Column(columnDefinition = "TEXT")
    private String choicesJson; // 선택지 목록 (JSON 문자열로 저장)

    @Column(columnDefinition = "TEXT")
    private String imageUrl; // 생성된 이미지 URL

    private String userChoice; // 사용자가 선택한 행동 (다음 턴 요청 시 업데이트됨)

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