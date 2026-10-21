package com.sanedge.pointofsale.gateway.graphql.resolver;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import com.google.protobuf.Empty;
import com.sanedge.pointofsale.common.grpc.ProtoMappers;
import com.sanedge.pointofsale.gateway.dto.responses.ProductResponse;
import com.sanedge.pointofsale.gateway.dto.responses.ProductResponseDeleteAt;
import com.sanedge.pointofsale.gateway.graphql.Gql;
import com.sanedge.pointofsale.gateway.graphql.dto.Page;
import com.sanedge.pointofsale.gateway.mapper.ProtoMapper;
import com.sanedge.pointofsale.pb.product.ProductCommandServiceGrpc;
import com.sanedge.pointofsale.pb.product.ProductQueryServiceGrpc;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class ProductResolver {

    private final ProductQueryServiceGrpc.ProductQueryServiceBlockingStub productQueryStub;
    private final ProductCommandServiceGrpc.ProductCommandServiceBlockingStub productCommandStub;

    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public Page<ProductResponse> products(@Argument Integer page, @Argument Integer pageSize,
            @Argument String search) {
        var resp = productQueryStub.findAll(com.sanedge.pointofsale.pb.product.FindAllProductRequest.newBuilder()
                .setPage(Gql.i(page)).setPageSize(Gql.i(pageSize)).setSearch(Gql.s(search)).build());
        return Page.of(ProtoMapper.products(resp.getDataList()),
                ProtoMappers.fromProtoPagination(resp.getPaginationMeta()));
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ProductResponse product(@Argument Long id) {
        var resp = productQueryStub.findById(com.sanedge.pointofsale.pb.product.FindByIdProductRequest.newBuilder()
                .setProductId(Gql.i(id)).build());
        return ProtoMapper.product(resp.getData());
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public Page<ProductResponse> productsByMerchant(@Argument Long merchantId, @Argument Long categoryId,
            @Argument Integer minPrice, @Argument Integer maxPrice, @Argument Integer page,
            @Argument Integer pageSize, @Argument String search) {
        var resp = productQueryStub.findByMerchant(
                com.sanedge.pointofsale.pb.product.FindByMerchantProductRequest.newBuilder()
                        .setMerchantId(Gql.i(merchantId))
                        .setSearch(Gql.s(search))
                        .setCategoryId(Gql.i(categoryId))
                        .setMinPrice(Gql.i(minPrice))
                        .setMaxPrice(Gql.i(maxPrice))
                        .setPage(Gql.i(page))
                        .setPageSize(Gql.i(pageSize))
                        .build());
        return Page.of(ProtoMapper.products(resp.getDataList()),
                ProtoMappers.fromProtoPagination(resp.getPaginationMeta()));
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public Page<ProductResponse> productsByCategory(@Argument String categoryName, @Argument Integer minPrice,
            @Argument Integer maxPrice, @Argument Integer page, @Argument Integer pageSize,
            @Argument String search) {
        var resp = productQueryStub.findByCategory(
                com.sanedge.pointofsale.pb.product.FindByCategoryProductRequest.newBuilder()
                        .setCategoryName(Gql.s(categoryName))
                        .setSearch(Gql.s(search))
                        .setMinPrice(Gql.i(minPrice))
                        .setMaxPrice(Gql.i(maxPrice))
                        .setPage(Gql.i(page))
                        .setPageSize(Gql.i(pageSize))
                        .build());
        return Page.of(ProtoMapper.products(resp.getDataList()),
                ProtoMappers.fromProtoPagination(resp.getPaginationMeta()));
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public Page<ProductResponseDeleteAt> activeProducts(@Argument Integer page, @Argument Integer pageSize,
            @Argument String search) {
        var resp = productQueryStub.findByActive(com.sanedge.pointofsale.pb.product.FindAllProductRequest.newBuilder()
                .setPage(Gql.i(page)).setPageSize(Gql.i(pageSize)).setSearch(Gql.s(search)).build());
        return Page.of(ProtoMapper.productsDeleteAt(resp.getDataList()),
                ProtoMappers.fromProtoPagination(resp.getPaginationMeta()));
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public Page<ProductResponseDeleteAt> trashedProducts(@Argument Integer page, @Argument Integer pageSize,
            @Argument String search) {
        var resp = productQueryStub.findByTrashed(com.sanedge.pointofsale.pb.product.FindAllProductRequest.newBuilder()
                .setPage(Gql.i(page)).setPageSize(Gql.i(pageSize)).setSearch(Gql.s(search)).build());
        return Page.of(ProtoMapper.productsDeleteAt(resp.getDataList()),
                ProtoMappers.fromProtoPagination(resp.getPaginationMeta()));
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ProductResponse createProduct(@Argument("input") CreateProduct input) {
        var resp = productCommandStub.createProduct(
                com.sanedge.pointofsale.pb.product.CreateProductRequest.newBuilder()
                        .setMerchantId(Gql.i(input.merchantId()))
                        .setCategoryId(Gql.i(input.categoryId()))
                        .setName(Gql.s(input.name()))
                        .setDescription(Gql.s(input.description()))
                        .setPrice(Gql.i(input.price()))
                        .setCountInStock(Gql.i(input.countInStock()))
                        .setBrand(Gql.s(input.brand()))
                        .setWeight(String.valueOf(Gql.i(input.weight())))
                        .setRating(Gql.f(input.rating()))
                        .setSlugProduct(Gql.s(input.slugProduct()))
                        .setImageProduct(Gql.s(input.imageProduct()))
                        .build());
        return ProtoMapper.product(resp.getData());
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ProductResponse updateProduct(@Argument("input") UpdateProduct input) {
        var resp = productCommandStub.updateProduct(
                com.sanedge.pointofsale.pb.product.UpdateProductRequest.newBuilder()
                        .setProductId(Gql.i(input.productId()))
                        .setMerchantId(Gql.i(input.merchantId()))
                        .setCategoryId(Gql.i(input.categoryId()))
                        .setName(Gql.s(input.name()))
                        .setDescription(Gql.s(input.description()))
                        .setPrice(Gql.i(input.price()))
                        .setCountInStock(Gql.i(input.countInStock()))
                        .setBrand(Gql.s(input.brand()))
                        .setWeight(String.valueOf(Gql.i(input.weight())))
                        .setRating(Gql.f(input.rating()))
                        .setSlugProduct(Gql.s(input.slugProduct()))
                        .setImageProduct(Gql.s(input.imageProduct()))
                        .build());
        return ProtoMapper.product(resp.getData());
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ProductResponseDeleteAt trashProduct(@Argument Long id) {
        var resp = productCommandStub.trashedProduct(
                com.sanedge.pointofsale.pb.product.FindByIdProductRequest.newBuilder()
                        .setProductId(Gql.i(id)).build());
        return ProtoMapper.productDeleteAt(resp.getData());
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ProductResponseDeleteAt restoreProduct(@Argument Long id) {
        var resp = productCommandStub.restoreProduct(
                com.sanedge.pointofsale.pb.product.FindByIdProductRequest.newBuilder()
                        .setProductId(Gql.i(id)).build());
        return ProtoMapper.productDeleteAt(resp.getData());
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public Boolean deleteProductPermanent(@Argument Long id) {
        var resp = productCommandStub.deleteProductPermanent(
                com.sanedge.pointofsale.pb.product.FindByIdProductRequest.newBuilder()
                        .setProductId(Gql.i(id)).build());
        return resp.getSuccess();
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public Boolean restoreAllProducts() {
        var resp = productCommandStub.restoreAllProduct(Empty.getDefaultInstance());
        return resp.getSuccess();
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public Boolean deleteAllProductsPermanent() {
        var resp = productCommandStub.deleteAllProductPermanent(Empty.getDefaultInstance());
        return resp.getSuccess();
    }

    public record CreateProduct(Long merchantId, Long categoryId, String name, String description, Integer price,
            Integer countInStock, String brand, Integer weight, Float rating, String slugProduct,
            String imageProduct) {
    }

    public record UpdateProduct(Long productId, Long merchantId, Long categoryId, String name, String description,
            Integer price, Integer countInStock, String brand, Integer weight, Float rating, String slugProduct,
            String imageProduct) {
    }
}
