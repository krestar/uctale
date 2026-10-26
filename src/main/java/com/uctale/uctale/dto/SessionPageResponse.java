package com.uctale.uctale.dto;

import java.util.List;

public record SessionPageResponse(
        List<SessionSummaryResponse> items,
        String nextCursor,
        boolean hasMore
) {}
