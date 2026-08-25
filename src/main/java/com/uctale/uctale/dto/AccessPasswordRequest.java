package com.uctale.uctale.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AccessPasswordRequest(
        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(max = 128, message = "비밀번호가 너무 깁니다.")
        String password
) {}
