package com.sanedge.pointofsale.gateway.graphql.dto;

import java.util.List;

import com.sanedge.pointofsale.common.domain.responses.api.PaginationMeta;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Generic paginated payload. Its GraphQL type name is decided by the schema
 * (e.g. {@code ProductPage}); graphql-java only reads the {@code data} and
 * {@code pagination} properties, so a single Java type serves every domain.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Page<T> {
    private List<T> data;
    private PaginationMeta pagination;

    public static <T> Page<T> of(List<T> data, PaginationMeta pagination) {
        return new Page<>(data, pagination);
    }
}
