package com.sanedge.pointofsale.gateway.dto.responses.stats;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatItemResponse {
    private String period;
    private Long dimensionId;
    private Double value;
}
