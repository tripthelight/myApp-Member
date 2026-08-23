package com.myapp.member.domain.traffic.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record AdminTrafficResponseDTO(
        OffsetDateTime generatedAt,
        OffsetDateTime periodStart,
        OffsetDateTime periodEnd,
        long totalRequests,
        long externalIpCount,
        long browserLikeIpCount,
        long estimatedVisitorCount,
        long scannerRequestCount,
        long scannerIpCount,
        List<DailyTrafficDTO> dailyTraffic,
        List<PopularPageDTO> popularPages
) {

    public record DailyTrafficDTO(
            LocalDate date,
            long requests,
            long estimatedVisitors
    ) {
    }

    public record PopularPageDTO(
            String path,
            long requests
    ) {
    }
}