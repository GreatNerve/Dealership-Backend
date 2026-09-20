package com.dealership.shared.time;

import java.time.Instant;
import java.time.ZoneOffset;

public record BookingInstant(Instant utc, ZoneOffset displayOffset) {}
