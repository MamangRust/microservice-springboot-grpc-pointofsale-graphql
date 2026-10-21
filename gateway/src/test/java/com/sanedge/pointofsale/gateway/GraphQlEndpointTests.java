package com.sanedge.pointofsale.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.tester.AutoConfigureGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifies the GraphQL edge without any downstream gRPC service:
 * <ul>
 * <li>a public field resolves,</li>
 * <li>a role-protected field is rejected with a GraphQL error (not a 500).</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureGraphQlTester
class GraphQlEndpointTests {

    @Autowired
    private GraphQlTester graphQlTester;

    @Test
    void healthcheckerIsPublic() {
        graphQlTester.document("query { healthchecker }")
                .execute()
                .path("healthchecker")
                .entity(String.class)
                .isEqualTo("✅ API is up and running!");
    }

    @Test
    void protectedFieldRequiresAuthentication() {
        graphQlTester.document("query { users(page: 1, pageSize: 10) { data { id } } }")
                .execute()
                .errors()
                .satisfy(errors -> assertThat(errors).isNotEmpty());
    }
}
