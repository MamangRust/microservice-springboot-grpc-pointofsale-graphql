package com.sanedge.pointofsale.gateway.graphql;

import java.util.Map;

import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindException;

import com.sanedge.pointofsale.common.exception.ResourceNotFoundException;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.validation.ConstraintViolationException;

/**
 * Translates downstream gRPC failures and local validation/security errors onto
 * GraphQL errors. The former HTTP status is preserved in
 * {@code extensions.httpStatus} so existing clients can keep their mapping.
 */
@Component
public class GraphQlExceptionHandler extends DataFetcherExceptionResolverAdapter {

    @Override
    protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
        Throwable cause = unwrap(ex);

        if (cause instanceof StatusRuntimeException sre) {
            Status.Code code = sre.getStatus().getCode();
            return error(statusFor(code), sre.getStatus().getDescription() != null
                    ? sre.getStatus().getDescription()
                    : "gRPC error: " + code, Map.of("grpcCode", code.name()));
        }
        if (cause instanceof ResourceNotFoundException rnfe) {
            return error(404, rnfe.getMessage(), Map.of());
        }
        if (cause instanceof AccessDeniedException ade) {
            boolean authenticated = SecurityContextHolder.getContext().getAuthentication() != null
                    && SecurityContextHolder.getContext().getAuthentication().isAuthenticated()
                    && !(SecurityContextHolder.getContext().getAuthentication() instanceof
                            org.springframework.security.authentication.AnonymousAuthenticationToken);
            if (!authenticated) {
                return error(401, "Authentication required", Map.of());
            }
            return error(403, ade.getMessage() == null ? "Access denied" : ade.getMessage(), Map.of());
        }
        if (cause instanceof AuthenticationException ae) {
            return error(401, ae.getMessage() == null ? "Unauthenticated" : ae.getMessage(), Map.of());
        }
        if (cause instanceof ConstraintViolationException
                || cause instanceof BindException
                || cause instanceof IllegalArgumentException) {
            return error(400, cause.getMessage(), Map.of());
        }
        if (cause instanceof UnsupportedOperationException uoe) {
            return error(501, uoe.getMessage(), Map.of());
        }
        return null;
    }

    private static Throwable unwrap(Throwable ex) {
        Throwable current = ex;
        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static int statusFor(Status.Code code) {
        return switch (code) {
            case NOT_FOUND -> 404;
            case INVALID_ARGUMENT, FAILED_PRECONDITION -> 400;
            case ALREADY_EXISTS -> 409;
            case UNAUTHENTICATED -> 401;
            case PERMISSION_DENIED -> 403;
            default -> 500;
        };
    }

    private static GraphQLError error(int httpStatus, String message, Map<String, Object> extra) {
        var extensions = new java.util.HashMap<String, Object>();
        extensions.put("httpStatus", httpStatus);
        extensions.putAll(extra);
        return GraphqlErrorBuilder.newError()
                .message(message == null ? "Unexpected error" : message)
                .extensions(extensions)
                .build();
    }
}
