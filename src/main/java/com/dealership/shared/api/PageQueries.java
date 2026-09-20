package com.dealership.shared.api;

import com.dealership.shared.config.AppProperties;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
public class PageQueries {

  private static final Sort CREATED_DESC = Sort.by(Sort.Direction.DESC, "createdAt", "id");

  private final AppProperties.Pagination pagination;

  public PageQueries(AppProperties properties) {
    this.pagination = properties.getPagination();
  }

  public PageQuery bind(Integer page, Integer size, String q) {
    return PageQuery.bind(
        page, size, q, pagination.getDefaultSize(), pagination.getMaxSize(), pagination.getQMax());
  }

  public Pageable pageable(PageQuery query) {
    return PageRequest.of(query.page(), query.size(), CREATED_DESC);
  }
}
