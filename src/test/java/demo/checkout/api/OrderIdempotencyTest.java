package demo.checkout.api;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@QuarkusTest
class OrderIdempotencyTest {

    private static final String KEY_HEADER = "Idempotency-Key";
    private static final String REPLAYED_HEADER = "Idempotent-Replayed";

    @Test
    @DisplayName("retry with the same key replays the first response and charges once")
    void testRetryWithSameKeyChargesOnce() {
        final long before = charges();
        final String key = newKey();

        final Response first = postOrder("/orders", key, orderJson("laptop", "999.00"));
        final Response retry = postOrder("/orders", key, orderJson("laptop", "999.00"));

        assertThat(first.statusCode()).isEqualTo(201);
        assertThat(first.header(REPLAYED_HEADER)).isNull();
        assertThat(retry.statusCode()).isEqualTo(201);
        assertThat(retry.header(REPLAYED_HEADER)).isEqualTo("true");
        assertThat(retry.header("Location")).isEqualTo(first.header("Location"));
        assertThat(retry.jsonPath().getString("id")).isEqualTo(first.jsonPath().getString("id"));
        assertThat(charges() - before).isEqualTo(1);
    }

    @Test
    @DisplayName("without the guard the same retry charges the customer twice")
    void testUnsafeEndpointChargesTwice() {
        final long before = charges();
        final String key = newKey();

        postOrder("/orders/unsafe", key, orderJson("laptop", "999.00"));
        postOrder("/orders/unsafe", key, orderJson("laptop", "999.00"));

        assertThat(charges() - before).isEqualTo(2);
    }

    @Test
    @DisplayName("same key with a different body is rejected with 422 and does not charge")
    void testKeyReuseWithDifferentBodyIsRejected() {
        final long before = charges();
        final String key = newKey();
        postOrder("/orders", key, orderJson("laptop", "999.00"));

        final Response mismatch = postOrder("/orders", key, orderJson("laptop", "1.00"));

        assertThat(mismatch.statusCode()).isEqualTo(422);
        assertThat(mismatch.contentType()).contains("problem+json");
        assertThat(charges() - before).isEqualTo(1);
    }

    @Test
    @DisplayName("a guarded POST without a key is rejected with 400")
    void testMissingKeyIsRejected() {
        final long before = charges();

        given().contentType(ContentType.JSON).body(orderJson("laptop", "999.00"))
                .when().post("/orders")
                .then().statusCode(400);

        assertThat(charges() - before).isZero();
    }

    @Test
    @DisplayName("parallel duplicates with one key run the handler once")
    void testParallelDuplicatesChargeOnce() throws Exception {
        final long before = charges();
        final String key = newKey();

        final List<Integer> statuses = fireInParallel(8, key);

        assertThat(statuses).contains(201).allMatch(status -> status == 201 || status == 409);
        assertThat(charges() - before).isEqualTo(1);
        assertThat(postOrder("/orders", key, orderJson("laptop", "999.00")).header(REPLAYED_HEADER))
                .isEqualTo("true");
    }

    @Test
    @DisplayName("an invalid body is a client error and is not stored for replay")
    void testInvalidBodyIsNotCharged() {
        final long before = charges();

        given().contentType(ContentType.JSON).header(KEY_HEADER, newKey()).body("{\"item\":\"\",\"amount\":-5}")
                .when().post("/orders")
                .then().statusCode(400);

        assertThat(charges() - before).isZero();
    }

    private List<Integer> fireInParallel(final int requests, final String key) throws Exception {
        final ExecutorService pool = Executors.newFixedThreadPool(requests);
        try {
            final List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < requests; i++) {
                futures.add(pool.submit(postStatus(key)));
            }
            final List<Integer> statuses = new ArrayList<>();
            for (final Future<Integer> future : futures) {
                statuses.add(future.get());
            }
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }

    private Callable<Integer> postStatus(final String key) {
        return () -> postOrder("/orders", key, orderJson("laptop", "999.00")).statusCode();
    }

    private long charges() {
        return given().when().get("/orders/charges/count")
                .then().statusCode(200)
                .extract().jsonPath().getLong("charges");
    }

    private Response postOrder(final String path, final String key, final String body) {
        return given().contentType(ContentType.JSON).header(KEY_HEADER, key).body(body)
                .when().post(path);
    }

    private String newKey() {
        return UUID.randomUUID().toString();
    }

    private String orderJson(final String item, final String amount) {
        return "{\"item\":\"" + item + "\",\"amount\":" + amount + "}";
    }
}
