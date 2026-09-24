package com.dealership.shared.db;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Date;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class SqlValuesTest {

  @Test
  void localDateFromLocalDateSqlDateAndString() {
    LocalDate day = LocalDate.of(2026, 9, 24);
    assertEquals(day, SqlValues.localDate(day));
    assertEquals(day, SqlValues.localDate(Date.valueOf(day)));
    assertEquals(day, SqlValues.localDate("2026-09-24"));
  }
}
