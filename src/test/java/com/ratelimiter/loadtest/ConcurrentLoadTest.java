package com.ratelimiter.loadtest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * NOT a JUnit test - this is a standalone runnable load test.
 *
 * How to run it:
 *   1. Start the app: mvn spring-boot:run
 *   2. Register a test client with a KNOWN capacity, e.g. capacity=50:
 *      curl -X POST http://localhost:8080/api/v1/admin/limits \
 *        -H "Content-Type: application/json" \
 *        -d '{"clientKey":"loadtest-client","capacity":50,"refillRate":5,"mode":"TOKEN_BUCKET"}'
 *   3. Run this class's main() method from your IDE (or: mvn test-compile
 *      exec:java -Dexec.mainClass=com.ratelimiter.loadtest.ConcurrentLoadTest
 *      -Dexec.classpathScope=test)
 *
 * What it proves:
 *   Requirement 07 needs 500+ concurrent req/s AND correctness under that load.
 *   This test fires CONCURRENCY requests at the SAME clientKey at the exact
 *   same instant. If the pessimistic locking in TokenBucketAlgorithm is
 *   working, the number of ALLOW responses must be EXACTLY equal to the
 *   bucket's starting capacity (50) - not one more, not one less - even
 *   though hundreds of threads raced for the same row.
 */
public class ConcurrentLoadTest {

    private static final String BASE_URL = "http://localhost:8080";
    private static final String CLIENT_KEY = "loadtest-client";
    private static final int CONCURRENCY = 600; // > 500 required by spec
    private static final int EXPECTED_CAPACITY = 50; // must match the capacity you registered above

    public static void main(String[] args) throws InterruptedException {
        HttpClient httpClient = HttpClient.newHttpClient();
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENCY);
        CountDownLatch readyLatch = new CountDownLatch(CONCURRENCY);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(CONCURRENCY);

        AtomicInteger allowCount = new AtomicInteger(0);
        AtomicInteger denyCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        for (int i = 0; i < CONCURRENCY; i++) {
            executor.submit(() -> {
                try {
                    readyLatch.countDown();
                    startLatch.await(); // every thread blocks here until we release them all together
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(BASE_URL + "/api/v1/rate-limit/check?clientKey=" + CLIENT_KEY))
                            .POST(HttpRequest.BodyPublishers.noBody())
                            .build();
                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() == 200) {
                        allowCount.incrementAndGet();
                    } else if (response.statusCode() == 429) {
                        denyCount.incrementAndGet();
                    } else {
                        errorCount.incrementAndGet();
                        System.out.println("Unexpected status: " + response.statusCode());
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    System.out.println("Request failed: " + e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();          // wait until all threads are queued up and ready
        long startTime = System.nanoTime();
        startLatch.countDown();      // release all threads at once - true concurrent burst
        doneLatch.await();
        long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;

        executor.shutdown();

        System.out.println("=== Load Test Results ===");
        System.out.println("Concurrent requests fired : " + CONCURRENCY);
        System.out.println("Elapsed time               : " + elapsedMs + " ms");
        System.out.println("ALLOW count                : " + allowCount.get());
        System.out.println("DENY count                 : " + denyCount.get());
        System.out.println("Errors                     : " + errorCount.get());
        System.out.println("Expected ALLOW (capacity)  : " + EXPECTED_CAPACITY);

        if (allowCount.get() == EXPECTED_CAPACITY) {
            System.out.println("PASS: exactly " + EXPECTED_CAPACITY + " requests allowed - no double-spend under concurrency.");
        } else {
            System.out.println("FAIL: expected exactly " + EXPECTED_CAPACITY
                    + " ALLOWs but got " + allowCount.get()
                    + " - the locking is NOT preventing double-spend.");
        }
    }
}
