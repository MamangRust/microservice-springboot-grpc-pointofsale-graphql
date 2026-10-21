package com.sanedge.pointofsale.gateway.graphql.resolver;

import java.util.List;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import com.sanedge.pointofsale.gateway.dto.responses.stats.StatItemResponse;
import com.sanedge.pointofsale.gateway.graphql.Gql;
import com.sanedge.pointofsale.gateway.mapper.ProtoMapper;
import com.sanedge.pointofsale.pb.stats.StatsReaderServiceGrpc;
import com.sanedge.pointofsale.pb.stats.StatsRequest;

import lombok.RequiredArgsConstructor;

/**
 * GraphQL facade over the ClickHouse-backed {@code stats-reader} gRPC service.
 */
@Controller
@RequiredArgsConstructor
public class StatsResolver {

    private final StatsReaderServiceGrpc.StatsReaderServiceBlockingStub statsReaderStub;

    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public List<StatItemResponse> monthlyRevenue(@Argument Integer year, @Argument Integer month) {
        var resp = statsReaderStub.findMonthlyTotalRevenue(StatsRequest.newBuilder()
                .setYear(Gql.i(year)).setMonth(Gql.i(month)).build());
        return ProtoMapper.statItems(resp.getDataList());
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public List<StatItemResponse> yearlyRevenue(@Argument Integer year) {
        var resp = statsReaderStub.findYearlyTotalRevenue(StatsRequest.newBuilder()
                .setYear(Gql.i(year)).build());
        return ProtoMapper.statItems(resp.getDataList());
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public List<StatItemResponse> merchantSales(@Argument Long merchantId, @Argument Integer year) {
        var resp = statsReaderStub.findMerchantSales(StatsRequest.newBuilder()
                .setMerchantId(Gql.l(merchantId)).setYear(Gql.i(year)).build());
        return ProtoMapper.statItems(resp.getDataList());
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public List<StatItemResponse> productSales(@Argument Long productId, @Argument Integer year) {
        var resp = statsReaderStub.findProductSales(StatsRequest.newBuilder()
                .setProductId(Gql.l(productId)).setYear(Gql.i(year)).build());
        return ProtoMapper.statItems(resp.getDataList());
    }
}
