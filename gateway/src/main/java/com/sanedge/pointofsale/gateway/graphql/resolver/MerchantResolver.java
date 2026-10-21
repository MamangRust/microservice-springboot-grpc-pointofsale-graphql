package com.sanedge.pointofsale.gateway.graphql.resolver;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import com.google.protobuf.Empty;
import com.sanedge.pointofsale.common.grpc.ProtoMappers;
import com.sanedge.pointofsale.gateway.dto.responses.MerchantResponse;
import com.sanedge.pointofsale.gateway.dto.responses.MerchantResponseDeleteAt;
import com.sanedge.pointofsale.gateway.graphql.Gql;
import com.sanedge.pointofsale.gateway.graphql.dto.Page;
import com.sanedge.pointofsale.gateway.mapper.ProtoMapper;
import com.sanedge.pointofsale.pb.merchant.MerchantCommandServiceGrpc;
import com.sanedge.pointofsale.pb.merchant.MerchantQueryServiceGrpc;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class MerchantResolver {

    private final MerchantQueryServiceGrpc.MerchantQueryServiceBlockingStub merchantQueryStub;
    private final MerchantCommandServiceGrpc.MerchantCommandServiceBlockingStub merchantCommandStub;

    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public Page<MerchantResponse> merchants(@Argument Integer page, @Argument Integer pageSize,
            @Argument String search) {
        var resp = merchantQueryStub.findAll(com.sanedge.pointofsale.pb.merchant.FindAllMerchantRequest.newBuilder()
                .setPage(Gql.i(page)).setPageSize(Gql.i(pageSize)).setSearch(Gql.s(search)).build());
        return Page.of(ProtoMapper.merchants(resp.getDataList()),
                ProtoMappers.fromProtoPagination(resp.getPaginationMeta()));
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public MerchantResponse merchant(@Argument Long id) {
        var resp = merchantQueryStub.findById(com.sanedge.pointofsale.pb.merchant.FindByIdMerchantRequest.newBuilder()
                .setMerchantId(Gql.i(id)).build());
        return ProtoMapper.merchant(resp.getData());
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public Page<MerchantResponseDeleteAt> activeMerchants(@Argument Integer page, @Argument Integer pageSize,
            @Argument String search) {
        var resp = merchantQueryStub.findByActive(com.sanedge.pointofsale.pb.merchant.FindAllMerchantRequest.newBuilder()
                .setPage(Gql.i(page)).setPageSize(Gql.i(pageSize)).setSearch(Gql.s(search)).build());
        return Page.of(ProtoMapper.merchantsDeleteAt(resp.getDataList()),
                ProtoMappers.fromProtoPagination(resp.getPaginationMeta()));
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public Page<MerchantResponseDeleteAt> trashedMerchants(@Argument Integer page, @Argument Integer pageSize,
            @Argument String search) {
        var resp = merchantQueryStub.findByTrashed(com.sanedge.pointofsale.pb.merchant.FindAllMerchantRequest.newBuilder()
                .setPage(Gql.i(page)).setPageSize(Gql.i(pageSize)).setSearch(Gql.s(search)).build());
        return Page.of(ProtoMapper.merchantsDeleteAt(resp.getDataList()),
                ProtoMappers.fromProtoPagination(resp.getPaginationMeta()));
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public MerchantResponse createMerchant(@Argument("input") CreateMerchant input) {
        var resp = merchantCommandStub.createMerchant(
                com.sanedge.pointofsale.pb.merchant.CreateMerchantRequest.newBuilder()
                        .setUserId(Gql.i(input.userId()))
                        .setName(Gql.s(input.name()))
                        .setDescription(Gql.s(input.description()))
                        .setAddress(Gql.s(input.address()))
                        .setContactEmail(Gql.s(input.contactEmail()))
                        .setContactPhone(Gql.s(input.contactPhone()))
                        .setStatus(Gql.s(input.status()))
                        .build());
        return ProtoMapper.merchant(resp.getData());
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public MerchantResponse updateMerchant(@Argument("input") UpdateMerchant input) {
        var resp = merchantCommandStub.updateMerchant(
                com.sanedge.pointofsale.pb.merchant.UpdateMerchantRequest.newBuilder()
                        .setMerchantId(Gql.i(input.merchantId()))
                        .setUserId(Gql.i(input.userId()))
                        .setName(Gql.s(input.name()))
                        .setDescription(Gql.s(input.description()))
                        .setAddress(Gql.s(input.address()))
                        .setContactEmail(Gql.s(input.contactEmail()))
                        .setContactPhone(Gql.s(input.contactPhone()))
                        .setStatus(Gql.s(input.status()))
                        .build());
        return ProtoMapper.merchant(resp.getData());
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public MerchantResponseDeleteAt trashMerchant(@Argument Long id) {
        var resp = merchantCommandStub.trashedMerchant(
                com.sanedge.pointofsale.pb.merchant.FindByIdMerchantRequest.newBuilder()
                        .setMerchantId(Gql.i(id)).build());
        return ProtoMapper.merchantDeleteAt(resp.getData());
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public MerchantResponseDeleteAt restoreMerchant(@Argument Long id) {
        var resp = merchantCommandStub.restoreMerchant(
                com.sanedge.pointofsale.pb.merchant.FindByIdMerchantRequest.newBuilder()
                        .setMerchantId(Gql.i(id)).build());
        return ProtoMapper.merchantDeleteAt(resp.getData());
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public Boolean deleteMerchantPermanent(@Argument Long id) {
        var resp = merchantCommandStub.deleteMerchantPermanent(
                com.sanedge.pointofsale.pb.merchant.FindByIdMerchantRequest.newBuilder()
                        .setMerchantId(Gql.i(id)).build());
        return resp.getSuccess();
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public Boolean restoreAllMerchants() {
        var resp = merchantCommandStub.restoreAllMerchant(Empty.getDefaultInstance());
        return resp.getSuccess();
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public Boolean deleteAllMerchantsPermanent() {
        var resp = merchantCommandStub.deleteAllMerchantPermanent(Empty.getDefaultInstance());
        return resp.getSuccess();
    }

    public record CreateMerchant(Long userId, String name, String description, String address,
            String contactEmail, String contactPhone, String status) {
    }

    public record UpdateMerchant(Long merchantId, Long userId, String name, String description, String address,
            String contactEmail, String contactPhone, String status) {
    }
}
