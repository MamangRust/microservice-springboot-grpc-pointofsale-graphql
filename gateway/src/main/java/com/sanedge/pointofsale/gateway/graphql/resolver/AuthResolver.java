package com.sanedge.pointofsale.gateway.graphql.resolver;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;

import com.sanedge.pointofsale.gateway.dto.responses.UserResponse;
import com.sanedge.pointofsale.gateway.graphql.Gql;
import com.sanedge.pointofsale.gateway.graphql.dto.Token;
import com.sanedge.pointofsale.gateway.mapper.ProtoMapper;
import com.sanedge.pointofsale.pb.auth.AuthServiceGrpc;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class AuthResolver {

    private final AuthServiceGrpc.AuthServiceBlockingStub authStub;

    @QueryMapping
    public String healthchecker() {
        return "✅ API is up and running!";
    }

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public UserResponse me() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        var resp = authStub.getMe(com.sanedge.pointofsale.pb.auth.GetMeRequest.newBuilder()
                .setUsername(Gql.s(username))
                .build());
        return ProtoMapper.user(resp.getData());
    }

    @MutationMapping
    public UserResponse register(@Argument("input") RegisterInput input) {
        var resp = authStub.registerUser(com.sanedge.pointofsale.pb.auth.RegisterRequest.newBuilder()
                .setFirstname(Gql.s(input.firstname()))
                .setLastname(Gql.s(input.lastname()))
                .setUsername(Gql.s(input.username()))
                .setEmail(Gql.s(input.email()))
                .setPassword(Gql.s(input.password()))
                .setConfirmPassword(Gql.s(input.confirmPassword()))
                .build());
        return ProtoMapper.user(resp.getData());
    }

    @MutationMapping
    public Token login(@Argument("input") LoginInput input) {
        var resp = authStub.loginUser(com.sanedge.pointofsale.pb.auth.LoginRequest.newBuilder()
                .setUsername(Gql.s(input.username()))
                .setPassword(Gql.s(input.password()))
                .build());
        return Token.from(ProtoMapper.token(resp.getData()));
    }

    @MutationMapping
    public Token refreshToken(@Argument String refreshToken) {
        var resp = authStub.refreshToken(com.sanedge.pointofsale.pb.auth.RefreshTokenRequest.newBuilder()
                .setRefreshToken(Gql.s(refreshToken))
                .build());
        return Token.from(ProtoMapper.token(resp.getData()));
    }

    public record RegisterInput(String firstname, String lastname, String username, String email,
            String password, String confirmPassword) {
    }

    public record LoginInput(String username, String password) {
    }
}
