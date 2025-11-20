package com.uctale.uctale.dto;

import jakarta.validation.constraints.NotBlank;

public record GameInitRequest(
        @NotBlank(message = "세계관 설정은 필수입니다.")
        String worldSetting,

        @NotBlank(message = "캐릭터 설정은 필수입니다.")
        String characterSetting
) {}