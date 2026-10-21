package com.sanedge.pointofsale.gateway.graphql.resolver;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import com.google.protobuf.Empty;
import com.sanedge.pointofsale.common.grpc.ProtoMappers;
import com.sanedge.pointofsale.gateway.dto.responses.CategoryResponse;
import com.sanedge.pointofsale.gateway.dto.responses.CategoryResponseDeleteAt;
import com.sanedge.pointofsale.gateway.graphql.Gql;
import com.sanedge.pointofsale.gateway.graphql.dto.Page;
import com.sanedge.pointofsale.gateway.mapper.ProtoMapper;
import com.sanedge.pointofsale.pb.category.CategoryCommandServiceGrpc;
import com.sanedge.pointofsale.pb.category.CategoryQueryServiceGrpc;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class CategoryResolver {

    private final CategoryQueryServiceGrpc.CategoryQueryServiceBlockingStub categoryQueryStub;
    private final CategoryCommandServiceGrpc.CategoryCommandServiceBlockingStub categoryCommandStub;

    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public Page<CategoryResponse> categories(@Argument Integer page, @Argument Integer pageSize,
            @Argument String search) {
        var resp = categoryQueryStub.findAll(com.sanedge.pointofsale.pb.category.FindAllCategoryRequest.newBuilder()
                .setPage(Gql.i(page)).setPageSize(Gql.i(pageSize)).setSearch(Gql.s(search)).build());
        return Page.of(ProtoMapper.categories(resp.getDataList()),
                ProtoMappers.fromProtoPagination(resp.getPaginationMeta()));
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public CategoryResponse category(@Argument Long id) {
        var resp = categoryQueryStub.findById(com.sanedge.pointofsale.pb.category.FindByIdCategoryRequest.newBuilder()
                .setCategoryId(Gql.i(id)).build());
        return ProtoMapper.category(resp.getData());
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public Page<CategoryResponseDeleteAt> activeCategories(@Argument Integer page, @Argument Integer pageSize,
            @Argument String search) {
        var resp = categoryQueryStub.findByActive(com.sanedge.pointofsale.pb.category.FindAllCategoryRequest.newBuilder()
                .setPage(Gql.i(page)).setPageSize(Gql.i(pageSize)).setSearch(Gql.s(search)).build());
        return Page.of(ProtoMapper.categoriesDeleteAt(resp.getDataList()),
                ProtoMappers.fromProtoPagination(resp.getPaginationMeta()));
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public Page<CategoryResponseDeleteAt> trashedCategories(@Argument Integer page, @Argument Integer pageSize,
            @Argument String search) {
        var resp = categoryQueryStub.findByTrashed(com.sanedge.pointofsale.pb.category.FindAllCategoryRequest.newBuilder()
                .setPage(Gql.i(page)).setPageSize(Gql.i(pageSize)).setSearch(Gql.s(search)).build());
        return Page.of(ProtoMapper.categoriesDeleteAt(resp.getDataList()),
                ProtoMappers.fromProtoPagination(resp.getPaginationMeta()));
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public CategoryResponse createCategory(@Argument("input") CreateCategory input) {
        var resp = categoryCommandStub.createCategory(
                com.sanedge.pointofsale.pb.category.CreateCategoryRequest.newBuilder()
                        .setName(Gql.s(input.name()))
                        .setDescription(Gql.s(input.description()))
                        .setSlugCategory(Gql.s(input.slugCategory()))
                        .build());
        return ProtoMapper.category(resp.getData());
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public CategoryResponse updateCategory(@Argument("input") UpdateCategory input) {
        var resp = categoryCommandStub.updateCategory(
                com.sanedge.pointofsale.pb.category.UpdateCategoryRequest.newBuilder()
                        .setCategoryId(Gql.i(input.categoryId()))
                        .setName(Gql.s(input.name()))
                        .setDescription(Gql.s(input.description()))
                        .setSlugCategory(Gql.s(input.slugCategory()))
                        .build());
        return ProtoMapper.category(resp.getData());
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public CategoryResponseDeleteAt trashCategory(@Argument Long id) {
        var resp = categoryCommandStub.trashedCategory(
                com.sanedge.pointofsale.pb.category.FindByIdCategoryRequest.newBuilder()
                        .setCategoryId(Gql.i(id)).build());
        return ProtoMapper.categoryDeleteAt(resp.getData());
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public CategoryResponseDeleteAt restoreCategory(@Argument Long id) {
        var resp = categoryCommandStub.restoreCategory(
                com.sanedge.pointofsale.pb.category.FindByIdCategoryRequest.newBuilder()
                        .setCategoryId(Gql.i(id)).build());
        return ProtoMapper.categoryDeleteAt(resp.getData());
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public Boolean deleteCategoryPermanent(@Argument Long id) {
        var resp = categoryCommandStub.deleteCategoryPermanent(
                com.sanedge.pointofsale.pb.category.FindByIdCategoryRequest.newBuilder()
                        .setCategoryId(Gql.i(id)).build());
        return resp.getSuccess();
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public Boolean restoreAllCategories() {
        var resp = categoryCommandStub.restoreAllCategory(Empty.getDefaultInstance());
        return resp.getSuccess();
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public Boolean deleteAllCategoriesPermanent() {
        var resp = categoryCommandStub.deleteAllCategoryPermanent(Empty.getDefaultInstance());
        return resp.getSuccess();
    }

    public record CreateCategory(String name, String description, String slugCategory) {
    }

    public record UpdateCategory(Long categoryId, String name, String description, String slugCategory) {
    }
}
