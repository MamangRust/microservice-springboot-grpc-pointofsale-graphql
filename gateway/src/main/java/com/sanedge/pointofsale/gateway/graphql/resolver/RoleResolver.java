package com.sanedge.pointofsale.gateway.graphql.resolver;

import java.util.List;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import com.google.protobuf.Empty;
import com.sanedge.pointofsale.common.grpc.ProtoMappers;
import com.sanedge.pointofsale.gateway.dto.responses.RoleResponse;
import com.sanedge.pointofsale.gateway.dto.responses.RoleResponseDeleteAt;
import com.sanedge.pointofsale.gateway.graphql.Gql;
import com.sanedge.pointofsale.gateway.graphql.dto.Page;
import com.sanedge.pointofsale.gateway.mapper.ProtoMapper;
import com.sanedge.pointofsale.pb.role.RoleCommandServiceGrpc;
import com.sanedge.pointofsale.pb.role.RoleQueryServiceGrpc;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class RoleResolver {

    private final RoleQueryServiceGrpc.RoleQueryServiceBlockingStub roleQueryStub;
    private final RoleCommandServiceGrpc.RoleCommandServiceBlockingStub roleCommandStub;

    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public Page<RoleResponse> roles(@Argument Integer page, @Argument Integer pageSize, @Argument String search) {
        var resp = roleQueryStub.findAll(com.sanedge.pointofsale.pb.role.FindAllRoleRequest.newBuilder()
                .setPage(Gql.i(page)).setPageSize(Gql.i(pageSize)).setSearch(Gql.s(search)).build());
        return Page.of(ProtoMapper.roles(resp.getDataList()),
                ProtoMappers.fromProtoPagination(resp.getPaginationMeta()));
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public RoleResponse role(@Argument Long id) {
        var resp = roleQueryStub.findById(com.sanedge.pointofsale.pb.role.FindByIdRoleRequest.newBuilder()
                .setRoleId(Gql.i(id)).build());
        return ProtoMapper.role(resp.getData());
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public Page<RoleResponseDeleteAt> activeRoles(@Argument Integer page, @Argument Integer pageSize,
            @Argument String search) {
        var resp = roleQueryStub.findByActive(com.sanedge.pointofsale.pb.role.FindAllRoleRequest.newBuilder()
                .setPage(Gql.i(page)).setPageSize(Gql.i(pageSize)).setSearch(Gql.s(search)).build());
        return Page.of(ProtoMapper.rolesDeleteAt(resp.getDataList()),
                ProtoMappers.fromProtoPagination(resp.getPaginationMeta()));
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public Page<RoleResponseDeleteAt> trashedRoles(@Argument Integer page, @Argument Integer pageSize,
            @Argument String search) {
        var resp = roleQueryStub.findByTrashed(com.sanedge.pointofsale.pb.role.FindAllRoleRequest.newBuilder()
                .setPage(Gql.i(page)).setPageSize(Gql.i(pageSize)).setSearch(Gql.s(search)).build());
        return Page.of(ProtoMapper.rolesDeleteAt(resp.getDataList()),
                ProtoMappers.fromProtoPagination(resp.getPaginationMeta()));
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public List<RoleResponse> rolesByUser(@Argument Long userId) {
        var resp = roleQueryStub.findByUserId(com.sanedge.pointofsale.pb.role.FindByUserIdRoleRequest.newBuilder()
                .setUserId(Gql.i(userId)).build());
        return ProtoMapper.roles(resp.getDataList());
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public RoleResponse roleByName(@Argument String name) {
        var resp = roleQueryStub.findByName(com.sanedge.pointofsale.pb.role.FindByNameRoleRequest.newBuilder()
                .setName(Gql.s(name)).build());
        return ProtoMapper.role(resp.getData());
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public RoleResponse createRole(@Argument("input") CreateRole input) {
        var resp = roleCommandStub.createRole(com.sanedge.pointofsale.pb.role.CreateRoleRequest.newBuilder()
                .setName(Gql.s(input.name())).build());
        return ProtoMapper.role(resp.getData());
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public RoleResponse updateRole(@Argument("input") UpdateRole input) {
        var resp = roleCommandStub.updateRole(com.sanedge.pointofsale.pb.role.UpdateRoleRequest.newBuilder()
                .setRoleId(Gql.i(input.id()))
                .setName(Gql.s(input.name())).build());
        return ProtoMapper.role(resp.getData());
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public RoleResponseDeleteAt trashRole(@Argument Long id) {
        var resp = roleCommandStub.trashedRole(com.sanedge.pointofsale.pb.role.FindByIdRoleRequest.newBuilder()
                .setRoleId(Gql.i(id)).build());
        return ProtoMapper.roleDeleteAt(resp.getData());
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public RoleResponseDeleteAt restoreRole(@Argument Long id) {
        var resp = roleCommandStub.restoreRole(com.sanedge.pointofsale.pb.role.FindByIdRoleRequest.newBuilder()
                .setRoleId(Gql.i(id)).build());
        return ProtoMapper.roleDeleteAt(resp.getData());
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public Boolean deleteRolePermanent(@Argument Long id) {
        var resp = roleCommandStub.deleteRolePermanent(com.sanedge.pointofsale.pb.role.FindByIdRoleRequest.newBuilder()
                .setRoleId(Gql.i(id)).build());
        return resp.getSuccess();
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public Boolean restoreAllRoles() {
        var resp = roleCommandStub.restoreAllRole(Empty.getDefaultInstance());
        return resp.getSuccess();
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public Boolean deleteAllRolesPermanent() {
        var resp = roleCommandStub.deleteAllRolePermanent(Empty.getDefaultInstance());
        return resp.getSuccess();
    }

    public record CreateRole(String name) {
    }

    public record UpdateRole(Long id, String name) {
    }
}
