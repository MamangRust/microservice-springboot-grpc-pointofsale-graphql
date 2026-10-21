package com.sanedge.pointofsale.gateway.graphql.resolver;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import com.google.protobuf.Empty;
import com.sanedge.pointofsale.common.grpc.ProtoMappers;
import com.sanedge.pointofsale.gateway.dto.responses.CashierResponse;
import com.sanedge.pointofsale.gateway.dto.responses.CashierResponseDeleteAt;
import com.sanedge.pointofsale.gateway.graphql.Gql;
import com.sanedge.pointofsale.gateway.graphql.dto.Page;
import com.sanedge.pointofsale.gateway.mapper.ProtoMapper;
import com.sanedge.pointofsale.pb.cashier.CashierCommandServiceGrpc;
import com.sanedge.pointofsale.pb.cashier.CashierQueryServiceGrpc;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class CashierResolver {

    private final CashierQueryServiceGrpc.CashierQueryServiceBlockingStub cashierQueryStub;
    private final CashierCommandServiceGrpc.CashierCommandServiceBlockingStub cashierCommandStub;

    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public Page<CashierResponse> cashiers(@Argument Integer page, @Argument Integer pageSize,
            @Argument String search) {
        var resp = cashierQueryStub.findAll(com.sanedge.pointofsale.pb.cashier.FindAllCashierRequest.newBuilder()
                .setPage(Gql.i(page)).setPageSize(Gql.i(pageSize)).setSearch(Gql.s(search)).build());
        return Page.of(ProtoMapper.cashiers(resp.getDataList()),
                ProtoMappers.fromProtoPagination(resp.getPaginationMeta()));
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public CashierResponse cashier(@Argument Long id) {
        var resp = cashierQueryStub.findById(com.sanedge.pointofsale.pb.cashier.FindByIdCashierRequest.newBuilder()
                .setCashierId(Gql.i(id)).build());
        return ProtoMapper.cashier(resp.getData());
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public Page<CashierResponseDeleteAt> activeCashiers(@Argument Integer page, @Argument Integer pageSize,
            @Argument String search) {
        var resp = cashierQueryStub.findByActive(com.sanedge.pointofsale.pb.cashier.FindAllCashierRequest.newBuilder()
                .setPage(Gql.i(page)).setPageSize(Gql.i(pageSize)).setSearch(Gql.s(search)).build());
        return Page.of(ProtoMapper.cashiersDeleteAt(resp.getDataList()),
                ProtoMappers.fromProtoPagination(resp.getPaginationMeta()));
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public Page<CashierResponseDeleteAt> trashedCashiers(@Argument Integer page, @Argument Integer pageSize,
            @Argument String search) {
        var resp = cashierQueryStub.findByTrashed(com.sanedge.pointofsale.pb.cashier.FindAllCashierRequest.newBuilder()
                .setPage(Gql.i(page)).setPageSize(Gql.i(pageSize)).setSearch(Gql.s(search)).build());
        return Page.of(ProtoMapper.cashiersDeleteAt(resp.getDataList()),
                ProtoMappers.fromProtoPagination(resp.getPaginationMeta()));
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public Page<CashierResponse> cashiersByMerchant(@Argument Long merchantId, @Argument Integer page,
            @Argument Integer pageSize, @Argument String search) {
        var resp = cashierQueryStub.findByMerchant(
                com.sanedge.pointofsale.pb.cashier.FindByMerchantCashierRequest.newBuilder()
                        .setMerchantId(Gql.i(merchantId))
                        .setPage(Gql.i(page)).setPageSize(Gql.i(pageSize)).setSearch(Gql.s(search))
                        .build());
        return Page.of(ProtoMapper.cashiers(resp.getDataList()),
                ProtoMappers.fromProtoPagination(resp.getPaginationMeta()));
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public CashierResponse createCashier(@Argument("input") CreateCashier input) {
        var resp = cashierCommandStub.createCashier(
                com.sanedge.pointofsale.pb.cashier.CreateCashierRequest.newBuilder()
                        .setMerchantId(Gql.i(input.merchantId()))
                        .setUserId(Gql.i(input.userId()))
                        .setName(Gql.s(input.name()))
                        .build());
        return ProtoMapper.cashier(resp.getData());
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public CashierResponse updateCashier(@Argument("input") UpdateCashier input) {
        var resp = cashierCommandStub.updateCashier(
                com.sanedge.pointofsale.pb.cashier.UpdateCashierRequest.newBuilder()
                        .setCashierId(Gql.i(input.cashierId()))
                        .setName(Gql.s(input.name()))
                        .build());
        return ProtoMapper.cashier(resp.getData());
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public CashierResponseDeleteAt trashCashier(@Argument Long id) {
        var resp = cashierCommandStub.trashedCashier(
                com.sanedge.pointofsale.pb.cashier.FindByIdCashierRequest.newBuilder()
                        .setCashierId(Gql.i(id)).build());
        return ProtoMapper.cashierDeleteAt(resp.getData());
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public CashierResponseDeleteAt restoreCashier(@Argument Long id) {
        var resp = cashierCommandStub.restoreCashier(
                com.sanedge.pointofsale.pb.cashier.FindByIdCashierRequest.newBuilder()
                        .setCashierId(Gql.i(id)).build());
        return ProtoMapper.cashierDeleteAt(resp.getData());
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public Boolean deleteCashierPermanent(@Argument Long id) {
        var resp = cashierCommandStub.deleteCashierPermanent(
                com.sanedge.pointofsale.pb.cashier.FindByIdCashierRequest.newBuilder()
                        .setCashierId(Gql.i(id)).build());
        return resp.getSuccess();
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public Boolean restoreAllCashiers() {
        var resp = cashierCommandStub.restoreAllCashier(Empty.getDefaultInstance());
        return resp.getSuccess();
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public Boolean deleteAllCashiersPermanent() {
        var resp = cashierCommandStub.deleteAllCashierPermanent(Empty.getDefaultInstance());
        return resp.getSuccess();
    }

    public record CreateCashier(Long merchantId, Long userId, String name) {
    }

    public record UpdateCashier(Long cashierId, String name) {
    }
}
