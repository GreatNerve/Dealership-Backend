package com.dealership.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dealership.AbstractIT;
import com.dealership.appointment.AppointmentDtos;
import com.dealership.identity.Role;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("e2e")
@Tag("capacity")
class RequestCapacityTest extends AbstractIT {

  private static final Logger log = LoggerFactory.getLogger(RequestCapacityTest.class);
  private static final int READS = 200;
  private static final int WRITES = 40;
  private static final int THREADS = 16;

  @Test
  @Timeout(60)
  void handlesABurstOfReadsAndAppointmentCreates() throws Exception {
    String staffToken =
        registerAndLogin("staff-" + UUID.randomUUID() + "@ex.com", Role.DEALERSHIP_STAFF);
    UUID dealershipId = createDealership(staffToken);
    String customerToken = registerAndLogin("cust-" + UUID.randomUUID() + "@ex.com", Role.CUSTOMER);
    List<UUID> vehicles = new ArrayList<>(WRITES);
    for (int i = 0; i < WRITES; i++) {
      vehicles.add(createVehicle(customerToken, randomPlate("KA")));
    }
    OffsetDateTime when =
        OffsetDateTime.now(ZoneOffset.UTC)
            .plusDays(4)
            .withOffsetSameInstant(ZoneOffset.of("+05:30"));

    AtomicInteger readOk = new AtomicInteger();
    AtomicInteger writeOk = new AtomicInteger();
    AtomicInteger serverErrors = new AtomicInteger();
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(READS + WRITES);
    ExecutorService pool = Executors.newFixedThreadPool(THREADS);
    long began = System.nanoTime();
    try {
      for (int i = 0; i < READS; i++) {
        pool.execute(
            () -> {
              await(start);
              try {
                ResponseEntity<String> res =
                    http.exchange(
                        "/api/v1/appointments?page=0&size=20",
                        HttpMethod.GET,
                        new HttpEntity<>(bearer(customerToken)),
                        String.class);
                if (res.getStatusCode().is2xxSuccessful()) {
                  readOk.incrementAndGet();
                } else if (res.getStatusCode().is5xxServerError()) {
                  serverErrors.incrementAndGet();
                }
              } finally {
                done.countDown();
              }
            });
      }
      for (int i = 0; i < WRITES; i++) {
        UUID vehicleId = vehicles.get(i);
        pool.execute(
            () -> {
              await(start);
              try {
                HttpHeaders headers = bearer(customerToken);
                headers.add("Idempotency-Key", "cap-" + UUID.randomUUID());
                String body =
                    """
                    {"vehicleId":"%s","dealershipId":"%s","scheduledAt":"%s"}
                    """
                        .formatted(vehicleId, dealershipId, when);
                ResponseEntity<AppointmentDtos.AppointmentResponse> res =
                    http.exchange(
                        "/api/v1/appointments",
                        HttpMethod.POST,
                        new HttpEntity<>(body, headers),
                        AppointmentDtos.AppointmentResponse.class);
                if (res.getStatusCode() == HttpStatus.CREATED) {
                  writeOk.incrementAndGet();
                } else if (res.getStatusCode().is5xxServerError()) {
                  serverErrors.incrementAndGet();
                }
              } finally {
                done.countDown();
              }
            });
      }
      start.countDown();
      assertTrue(done.await(45, TimeUnit.SECONDS), "burst did not finish in 45s");
    } finally {
      pool.shutdownNow();
    }
    long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - began);
    int handled = readOk.get() + writeOk.get();
    double rps = elapsedMs == 0 ? handled : handled * 1000.0 / elapsedMs;
    log.info(
        "capacity burst handled {}/{} requests in {} ms ({} req/s); reads={} writes={} 5xx={}",
        handled,
        READS + WRITES,
        elapsedMs,
        String.format("%.1f", rps),
        readOk.get(),
        writeOk.get(),
        serverErrors.get());
    assertEquals(0, serverErrors.get(), "no 5xx during burst");
    assertEquals(READS, readOk.get());
    assertEquals(WRITES, writeOk.get());
    assertTrue(rps >= 20, "burst too slow: " + rps + " req/s");
  }

  private static void await(CountDownLatch start) {
    try {
      start.await();
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
  }
}
