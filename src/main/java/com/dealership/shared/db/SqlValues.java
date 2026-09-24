package com.dealership.shared.db;

import java.sql.Date;
import java.time.LocalDate;

public final class SqlValues {

  private SqlValues() {}

  public static LocalDate localDate(Object value) {
    if (value instanceof LocalDate day) {
      return day;
    }
    if (value instanceof Date day) {
      return day.toLocalDate();
    }
    return LocalDate.parse(String.valueOf(value));
  }
}
