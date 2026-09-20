package com.dealership.shared.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.data.domain.Page;

public record PageResponse<T>(
    List<T> items,
    @Schema(example = "0") int page,
    @Schema(example = "100") int size,
    @Schema(example = "1") long totalElements,
    @Schema(example = "1") int totalPages) {

  public static <T> PageResponse<T> of(Page<T> page) {
    return new PageResponse<>(
        page.getContent(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages());
  }

  public static <T> PageResponse<T> of(List<T> items, PageQuery query, long total) {
    int pages = query.size() == 0 ? 0 : (int) Math.ceil(total / (double) query.size());
    return new PageResponse<>(items, query.page(), query.size(), total, pages);
  }
}
