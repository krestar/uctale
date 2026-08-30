package com.uctale.uctale.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(
        name = "uk_image_asset_session_turn",
        columnNames = {"session_id", "turn_number"}
))
@Getter
@NoArgsConstructor
public class ImageAsset {

    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private GameSession gameSession;

    @Column(nullable = false)
    private int turnNumber;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String prompt;

    @Column(nullable = false, length = 8)
    private String aspectRatio;

    @Column(length = 100)
    private String contentType;

    @Column(columnDefinition = "BYTEA")
    private byte[] imageBytes;

    private LocalDateTime createdAt;

    private LocalDateTime generatedAt;

    public ImageAsset(String id, GameSession gameSession, int turnNumber, String prompt, String aspectRatio) {
        this.id = id;
        this.gameSession = gameSession;
        this.turnNumber = turnNumber;
        this.prompt = prompt;
        this.aspectRatio = aspectRatio;
        this.createdAt = LocalDateTime.now();
    }

    public String publicUrl() {
        return "/api/game/image-assets/" + id;
    }

    public boolean generated() {
        return imageBytes != null && imageBytes.length > 0 && contentType != null && !contentType.isBlank();
    }

    public void storeGeneratedImage(byte[] bytes, String contentType) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("빈 이미지는 저장할 수 없습니다.");
        }
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("이미지 content type은 필수입니다.");
        }
        this.imageBytes = bytes.clone();
        this.contentType = contentType;
        this.generatedAt = LocalDateTime.now();
    }
}
