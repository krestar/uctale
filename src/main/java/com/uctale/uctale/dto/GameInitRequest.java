package com.uctale.uctale.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GameInitRequest(
        @NotBlank(message = "세계관 설정은 필수입니다.")
        @Size(max = 255, message = "세계관 설정은 255자 이하여야 합니다.")
        String worldSetting,

        @NotBlank(message = "캐릭터 설정은 필수입니다.")
        @Size(max = 255, message = "캐릭터 설정은 255자 이하여야 합니다.")
        String characterSetting
) {}
