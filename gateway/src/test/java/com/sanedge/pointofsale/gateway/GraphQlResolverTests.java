package com.sanedge.pointofsale.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.tester.AutoConfigureGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.sanedge.pointofsale.pb.common.PaginationMeta;
import com.sanedge.pointofsale.pb.role.ApiResponsePaginationRole;
import com.sanedge.pointofsale.pb.role.ApiResponseRole;
import com.sanedge.pointofsale.pb.role.CreateRoleRequest;
import com.sanedge.pointofsale.pb.role.RoleCommandServiceGrpc;
import com.sanedge.pointofsale.pb.role.RoleQueryServiceGrpc;
import com.sanedge.pointofsale.pb.role.RoleResponse;

/**
 * Verifies the two mechanisms that the schema/security smoke test cannot prove:
 * input-object binding (GraphQL input → Java record) and generic Page output
 * (proto → DTO → GraphQL {@code *Page}). Downstream gRPC stubs are mocked, so no
 * running service is required.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureGraphQlTester
class GraphQlResolverTests {

    @Autowired
    private GraphQlTester graphQlTester;

    @MockitoBean
    private RoleQueryServiceGrpc.RoleQueryServiceBlockingStub roleQueryStub;

    @MockitoBean
    private RoleCommandServiceGrpc.RoleCommandServiceBlockingStub roleCommandStub;

    @BeforeEach
    void authenticateAsAdmin() {
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_ADMIN"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", null, authorities));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rolesQuerySerializesPaginatedPage() {
        when(roleQueryStub.findAll(any())).thenReturn(ApiResponsePaginationRole.newBuilder()
                .setStatus("success")
                .setMessage("ok")
                .addData(role(7, "admin"))
                .setPaginationMeta(PaginationMeta.newBuilder()
                        .setCurrentPage(1).setPageSize(10).setTotalRecords(1).setTotalPages(1).build())
                .build());

        graphQlTester.document("""
                query {
                  roles(page: 1, pageSize: 10) {
                    data { id name }
                    pagination { page pageSize totalItems totalPages }
                  }
                }
                """)
                .execute()
                .path("roles.data[0].name").entity(String.class).isEqualTo("admin")
                .path("roles.data[0].id").entity(String.class).isEqualTo("7")
                .path("roles.pagination.totalItems").entity(Integer.class).isEqualTo(1)
                .path("roles.pagination.totalPages").entity(Integer.class).isEqualTo(1);
    }

    @Test
    void createRoleBindsInputObjectToRecord() {
        when(roleCommandStub.createRole(any())).thenReturn(ApiResponseRole.newBuilder()
                .setStatus("success")
                .setMessage("ok")
                .setData(role(9, "editor"))
                .build());

        graphQlTester.document("""
                mutation {
                  createRole(input: { name: "editor" }) { id name }
                }
                """)
                .execute()
                .path("createRole.name").entity(String.class).isEqualTo("editor")
                .path("createRole.id").entity(String.class).isEqualTo("9");

        ArgumentCaptor<CreateRoleRequest> captor = ArgumentCaptor.forClass(CreateRoleRequest.class);
        verify(roleCommandStub).createRole(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("editor");
    }

    private static RoleResponse role(int id, String name) {
        return RoleResponse.newBuilder()
                .setRoleId(id)
                .setName(name)
                .setCreatedAt("2024-01-01T00:00:00Z")
                .setUpdatedAt("2024-01-01T00:00:00Z")
                .build();
    }
}
