package com.uctale.uctale.dto;

public record GameProgressRequest(
        Long sessionId, // 어떤 게임 세션인지 식별
        int choiceId    // 사용자가 고른 선택지 번호 (1, 2, 3)
) {}