package com.sanedge.pointofsale.gateway.graphql.resolver;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import com.google.protobuf.Empty;
import com.sanedge.pointofsale.common.grpc.ProtoMappers;
import com.sanedge.pointofsale.gateway.dto.responses.TransactionResponse;
import com.sanedge.pointofsale.gateway.dto.responses.TransactionResponseDeleteAt;
import com.sanedge.pointofsale.gateway.graphql.Gql;
import com.sanedge.pointofsale.gateway.graphql.dto.Page;
import com.sanedge.pointofsale.gateway.mapper.ProtoMapper;
import com.sanedge.pointofsale.pb.transaction.TransactionCommandServiceGrpc;
import com.sanedge.pointofsale.pb.transaction.TransactionQueryServiceGrpc;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class TransactionResolver {

    private final TransactionQueryServiceGrpc.TransactionQueryServiceBlockingStub transactionQueryStub;
    private final TransactionCommandServiceGrpc.TransactionCommandServiceBlockingStub transactionCommandStub;

    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public Page<TransactionResponse> transactions(@Argument Integer page, @Argument Integer pageSize,
            @Argument String search) {
        var resp = transactionQueryStub.findAll(
                com.sanedge.pointofsale.pb.transaction.FindAllTransactionRequest.newBuilder()
                        .setPage(Gql.i(page)).setPageSize(Gql.i(pageSize)).setSearch(Gql.s(search)).build());
        return Page.of(ProtoMapper.transactions(resp.getDataList()),
                ProtoMappers.fromProtoPagination(resp.getPaginationMeta()));
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public TransactionResponse transaction(@Argument Long id) {
        var resp = transactionQueryStub.findById(
                com.sanedge.pointofsale.pb.transaction.FindByIdTransactionRequest.newBuilder()
                        .setTransactionId(Gql.i(id)).build());
        return ProtoMapper.transaction(resp.getData());
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public Page<TransactionResponse> transactionsByMerchant(@Argument Long merchantId, @Argument Integer page,
            @Argument Integer pageSize, @Argument String search) {
        var resp = transactionQueryStub.findByMerchant(
                com.sanedge.pointofsale.pb.transaction.FindByMerchantTransactionRequest.newBuilder()
                        .setMerchantId(Gql.i(merchantId))
                        .setPage(Gql.i(page)).setPageSize(Gql.i(pageSize)).setSearch(Gql.s(search))
                        .build());
        return Page.of(ProtoMapper.transactions(resp.getDataList()),
                ProtoMappers.fromProtoPagination(resp.getPaginationMeta()));
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public Page<TransactionResponseDeleteAt> activeTransactions(@Argument Integer page, @Argument Integer pageSize,
            @Argument String search) {
        var resp = transactionQueryStub.findByActive(
                com.sanedge.pointofsale.pb.transaction.FindAllTransactionRequest.newBuilder()
                        .setPage(Gql.i(page)).setPageSize(Gql.i(pageSize)).setSearch(Gql.s(search)).build());
        return Page.of(ProtoMapper.transactionsDeleteAt(resp.getDataList()),
                ProtoMappers.fromProtoPagination(resp.getPaginationMeta()));
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public Page<TransactionResponseDeleteAt> trashedTransactions(@Argument Integer page, @Argument Integer pageSize,
            @Argument String search) {
        var resp = transactionQueryStub.findByTrashed(
                com.sanedge.pointofsale.pb.transaction.FindAllTransactionRequest.newBuilder()
                        .setPage(Gql.i(page)).setPageSize(Gql.i(pageSize)).setSearch(Gql.s(search)).build());
        return Page.of(ProtoMapper.transactionsDeleteAt(resp.getDataList()),
                ProtoMappers.fromProtoPagination(resp.getPaginationMeta()));
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public TransactionResponse createTransaction(@Argument("input") CreateTransaction input) {
        var resp = transactionCommandStub.createTransaction(
                com.sanedge.pointofsale.pb.transaction.CreateTransactionRequest.newBuilder()
                        .setOrderId(Gql.i(input.orderId()))
                        .setMerchantId(Gql.i(input.merchantId()))
                        .setPaymentMethod(Gql.s(input.paymentMethod()))
                        .setAmount(Gql.i(input.amount()))
                        .build());
        return ProtoMapper.transaction(resp.getData());
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public TransactionResponse updateTransaction(@Argument("input") UpdateTransaction input) {
        var resp = transactionCommandStub.updateTransaction(
                com.sanedge.pointofsale.pb.transaction.UpdateTransactionRequest.newBuilder()
                        .setTransactionId(Gql.i(input.transactionId()))
                        .setOrderId(Gql.i(input.orderId()))
                        .setMerchantId(Gql.i(input.merchantId()))
                        .setPaymentMethod(Gql.s(input.paymentMethod()))
                        .setAmount(Gql.i(input.amount()))
                        .setPaymentStatus(Gql.s(input.paymentStatus()))
                        .build());
        return ProtoMapper.transaction(resp.getData());
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public TransactionResponseDeleteAt trashTransaction(@Argument Long id) {
        var resp = transactionCommandStub.trashedTransaction(
                com.sanedge.pointofsale.pb.transaction.FindByIdTransactionRequest.newBuilder()
                        .setTransactionId(Gql.i(id)).build());
        return ProtoMapper.transactionDeleteAt(resp.getData());
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public TransactionResponseDeleteAt restoreTransaction(@Argument Long id) {
        var resp = transactionCommandStub.restoreTransaction(
                com.sanedge.pointofsale.pb.transaction.FindByIdTransactionRequest.newBuilder()
                        .setTransactionId(Gql.i(id)).build());
        return ProtoMapper.transactionDeleteAt(resp.getData());
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public Boolean deleteTransactionPermanent(@Argument Long id) {
        var resp = transactionCommandStub.deleteTransactionPermanent(
                com.sanedge.pointofsale.pb.transaction.FindByIdTransactionRequest.newBuilder()
                        .setTransactionId(Gql.i(id)).build());
        return resp.getSuccess();
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public Boolean restoreAllTransactions() {
        var resp = transactionCommandStub.restoreAllTransaction(Empty.getDefaultInstance());
        return resp.getSuccess();
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public Boolean deleteAllTransactionsPermanent() {
        var resp = transactionCommandStub.deleteAllTransactionPermanent(Empty.getDefaultInstance());
        return resp.getSuccess();
    }

    public record CreateTransaction(Long orderId, Long merchantId, String paymentMethod, Integer amount) {
    }

    public record UpdateTransaction(Long transactionId, Long orderId, Long merchantId, String paymentMethod,
            Integer amount, String paymentStatus) {
    }
}
