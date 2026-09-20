# 0010 No Lombok, record DTOs

The assignment requires defending every line. Lombok-generated accessors fail that test. HTTP request/response types are Java records. JPA entities are explicit classes (records remain a poor fit for mutable Hibernate entities).

**Status:** accepted
