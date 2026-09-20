package com.dealership.shared.api;

public record PageQuery(int page, int size, String q) {

  public static PageQuery bind(
      Integer page, Integer size, String q, int defaultSize, int maxSize, int qMax) {
    int resolvedPage = page == null ? 0 : page;
    int resolvedSize = size == null ? defaultSize : size;
    if (resolvedPage < 0) {
      throw ApiException.of(ApiErrorCode.INVALID_PAGE, "page must be >= 0");
    }
    if (resolvedSize < 1 || resolvedSize > maxSize) {
      throw ApiException.of(ApiErrorCode.INVALID_SIZE, "size must be between 1 and " + maxSize);
    }
    String query = Inputs.sanitize(q);
    if (query != null && query.isEmpty()) {
      query = null;
    }
    if (query != null && query.length() > qMax) {
      throw ApiException.of(ApiErrorCode.INVALID_Q, "q must be at most " + qMax + " characters");
    }
    return new PageQuery(resolvedPage, resolvedSize, query);
  }

  public String like() {
    return q == null ? null : "%" + escapeLike(q.toLowerCase()) + "%";
  }

  private static String escapeLike(String raw) {
    return raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }
}
