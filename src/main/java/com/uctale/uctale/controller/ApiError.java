package com.uctale.uctale.controller;

public record ApiError(
        String code,
        String message
) {}
