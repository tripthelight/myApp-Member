package com.myapp.member.domain.user.dto;

public record AdminSummaryResponseDTO(
        long totalUsers,
        long localUsers,
        long socialUsers,
        long lockedUsers
) {
}
