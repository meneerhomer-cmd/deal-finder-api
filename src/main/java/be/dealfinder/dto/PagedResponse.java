package be.dealfinder.dto;

import java.util.List;

public record PagedResponse<T>(
    List<T> items,
    long totalItems,
    int page,
    int pageSize,
    int totalPages
) {
    public static <T> PagedResponse<T> of(List<T> items, long totalItems, int page, int pageSize) {
        int totalPages = pageSize > 0 ? (int) Math.ceil((double) totalItems / pageSize) : 1;
        return new PagedResponse<>(items, totalItems, page, pageSize, totalPages);
    }
}
