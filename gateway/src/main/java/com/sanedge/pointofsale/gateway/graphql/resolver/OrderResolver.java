package com.sanedge.pointofsale.gateway.graphql.resolver;

import java.util.List;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import com.google.protobuf.Empty;
import com.sanedge.pointofsale.common.grpc.ProtoMappers;
import com.sanedge.pointofsale.gateway.dto.responses.OrderResponse;
import com.sanedge.pointofsale.gateway.dto.responses.OrderResponseDeleteAt;
import com.sanedge.pointofsale.gateway.graphql.Gql;
import com.sanedge.pointofsale.gateway.graphql.dto.Page;
import com.sanedge.pointofsale.gateway.mapper.ProtoMapper;
import com.sanedge.pointofsale.pb.order.OrderCommandServiceGrpc;
import com.sanedge.pointofsale.pb.order.OrderQueryServiceGrpc;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class OrderResolver {

    private final OrderQueryServiceGrpc.OrderQueryServiceBlockingStub orderQueryStub;
    private final OrderCommandServiceGrpc.OrderCommandServiceBlockingStub orderCommandStub;

    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public Page<OrderResponse> orders(@Argument Integer page, @Argument Integer pageSize,
            @Argument String search) {
        var resp = orderQueryStub.findAll(com.sanedge.pointofsale.pb.order.FindAllOrderRequest.newBuilder()
                .setPage(Gql.i(page)).setPageSize(Gql.i(pageSize)).setSearch(Gql.s(search)).build());
        return Page.of(ProtoMapper.orders(resp.getDataList()),
                ProtoMappers.fromProtoPagination(resp.getPaginationMeta()));
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public OrderResponse order(@Argument Long id) {
        var resp = orderQueryStub.findById(com.sanedge.pointofsale.pb.order.FindByIdOrderRequest.newBuilder()
                .setOrderId(Gql.i(id)).build());
        return ProtoMapper.order(resp.getData());
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public Page<OrderResponseDeleteAt> activeOrders(@Argument Integer page, @Argument Integer pageSize,
            @Argument String search) {
        var resp = orderQueryStub.findByActive(com.sanedge.pointofsale.pb.order.FindAllOrderRequest.newBuilder()
                .setPage(Gql.i(page)).setPageSize(Gql.i(pageSize)).setSearch(Gql.s(search)).build());
        return Page.of(ProtoMapper.ordersDeleteAt(resp.getDataList()),
                ProtoMappers.fromProtoPagination(resp.getPaginationMeta()));
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public Page<OrderResponseDeleteAt> trashedOrders(@Argument Integer page, @Argument Integer pageSize,
            @Argument String search) {
        var resp = orderQueryStub.findByTrashed(com.sanedge.pointofsale.pb.order.FindAllOrderRequest.newBuilder()
                .setPage(Gql.i(page)).setPageSize(Gql.i(pageSize)).setSearch(Gql.s(search)).build());
        return Page.of(ProtoMapper.ordersDeleteAt(resp.getDataList()),
                ProtoMappers.fromProtoPagination(resp.getPaginationMeta()));
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public Page<OrderResponse> ordersByMerchant(@Argument Long merchantId, @Argument Integer page,
            @Argument Integer pageSize, @Argument String search) {
        var resp = orderQueryStub.findByMerchant(
                com.sanedge.pointofsale.pb.order.FindByMerchantOrderRequest.newBuilder()
                        .setMerchantId(Gql.i(merchantId))
                        .setPage(Gql.i(page)).setPageSize(Gql.i(pageSize)).setSearch(Gql.s(search))
                        .build());
        return Page.of(ProtoMapper.orders(resp.getDataList()),
                ProtoMappers.fromProtoPagination(resp.getPaginationMeta()));
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public OrderResponse createOrder(@Argument("input") CreateOrder input) {
        var builder = com.sanedge.pointofsale.pb.order.CreateOrderRequest.newBuilder()
                .setMerchantId(Gql.i(input.merchantId()))
                .setCashierId(Gql.i(input.cashierId()));
        if (input.items() != null) {
            for (OrderItem item : input.items()) {
                builder.addItems(com.sanedge.pointofsale.pb.order.CreateOrderItemRequest.newBuilder()
                        .setProductId(Gql.i(item.productId()))
                        .setQuantity(Gql.i(item.quantity()))
                        .setPrice(Gql.i(item.price()))
                        .build());
            }
        }
        var resp = orderCommandStub.createOrder(builder.build());
        return ProtoMapper.order(resp.getData());
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public OrderResponse updateOrder(@Argument("input") UpdateOrder input) {
        var builder = com.sanedge.pointofsale.pb.order.UpdateOrderRequest.newBuilder()
                .setOrderId(Gql.i(input.orderId()))
                .setCashierId(Gql.i(input.cashierId()));
        if (input.items() != null) {
            for (UpdateOrderItem item : input.items()) {
                builder.addItems(com.sanedge.pointofsale.pb.order.UpdateOrderItemRequest.newBuilder()
                        .setOrderItemId(Gql.i(item.orderItemId()))
                        .setProductId(Gql.i(item.productId()))
                        .setQuantity(Gql.i(item.quantity()))
                        .setPrice(Gql.i(item.price()))
                        .build());
            }
        }
        var resp = orderCommandStub.updateOrder(builder.build());
        return ProtoMapper.order(resp.getData());
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public OrderResponseDeleteAt trashOrder(@Argument Long id) {
        var resp = orderCommandStub.trashedOrder(com.sanedge.pointofsale.pb.order.FindByIdOrderRequest.newBuilder()
                .setOrderId(Gql.i(id)).build());
        return ProtoMapper.orderDeleteAt(resp.getData());
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public OrderResponseDeleteAt restoreOrder(@Argument Long id) {
        var resp = orderCommandStub.restoreOrder(com.sanedge.pointofsale.pb.order.FindByIdOrderRequest.newBuilder()
                .setOrderId(Gql.i(id)).build());
        return ProtoMapper.orderDeleteAt(resp.getData());
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public Boolean deleteOrderPermanent(@Argument Long id) {
        var resp = orderCommandStub.deleteOrderPermanent(
                com.sanedge.pointofsale.pb.order.FindByIdOrderRequest.newBuilder()
                        .setOrderId(Gql.i(id)).build());
        return resp.getSuccess();
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public Boolean restoreAllOrders() {
        var resp = orderCommandStub.restoreAllOrder(Empty.getDefaultInstance());
        return resp.getSuccess();
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public Boolean deleteAllOrdersPermanent() {
        var resp = orderCommandStub.deleteAllOrderPermanent(Empty.getDefaultInstance());
        return resp.getSuccess();
    }

    public record OrderItem(Long productId, Integer quantity, Integer price) {
    }

    public record UpdateOrderItem(Long orderItemId, Long productId, Integer quantity, Integer price) {
    }

    public record CreateOrder(Long merchantId, Long cashierId, List<OrderItem> items) {
    }

    public record UpdateOrder(Long orderId, Long cashierId, List<UpdateOrderItem> items) {
    }
}
