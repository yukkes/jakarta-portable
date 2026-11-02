package com.example.conformance;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.specification.RequestSpecification;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * The HTTP behavior all 6 runtimes must share. The same tests run against a running server.
 *
 * <pre>
 *   mvn -pl conformance test -Dapp.base.url=http://localhost:8080 \
 *       -Dapp.health.url=http://localhost:8080/q/health
 * </pre>
 *
 * <p>Requires the demo users (admin/admin, user/user) to be enabled, or the passwords to be passed with
 * {@code -Dapp.password} and friends. The products and customers used are fetched from the API, so it works with the
 * demo data as well as the benchmark data, and can run against the same instance any number of times.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApiConformanceTest {

    private static final String REFRESH_COOKIE = "APP_REFRESH";
    private static final String CSRF_COOKIE = "XSRF-TOKEN";
    private static final String CSRF_HEADER = "X-XSRF-TOKEN";
    /** The double-submit check only compares the header with the cookie, so the tests use a fixed value. */
    private static final String TEST_CSRF = "conformance-csrf";

    private static String token;
    private static Long createdOrderId;
    private static String customerCode;
    private static String productCode;
    private static BigDecimal unitPrice;

    @BeforeAll
    static void login() {
        // Without a target the whole class is skipped, so `mvn test` passes without a server.
        String baseUrl = System.getProperty("app.base.url", "");
        assumeTrue(!baseUrl.isBlank(), "set -Dapp.base.url=http://host:port (or use make conformance)");
        RestAssured.baseURI = baseUrl;
        token = bearerLogin(admin()).getString("token");

        // Use the product with the most stock, since the confirm test takes 1 from it every run.
        Map<String, Object> best = products().stream()
                .max(Comparator.comparingInt(ApiConformanceTest::stock))
                .orElseThrow();
        productCode = (String) best.get("code");
        unitPrice = new BigDecimal(best.get("unitPrice").toString());
        customerCode = api().get("/api/customers?page=0&size=1&sort=code,asc")
                .then()
                .statusCode(200)
                .extract()
                .path("content[0].code");
    }

    @Test
    @Order(1)
    void appHealthReportsUp() {
        given().get("/api/health")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"))
                .body("database", equalTo("UP"));
    }

    @Test
    @Order(2)
    void loginIssuesAccessAndRefreshTokens() {
        JsonPath login = bearerLogin(admin());
        assertTrue(login.getString("token") != null && login.getString("refreshToken") != null, login.prettify());
        assertTrue(login.getList("roles") != null && login.getString("expiresAt") != null, login.prettify());
    }

    @Test
    @Order(3)
    void deniesAnonymousAccessToTheBusinessApi() {
        given().get("/api/orders").then().statusCode(401);
        given().get("/api/auth/status").then().statusCode(200).body("authenticated", equalTo(false));
    }

    @Test
    @Order(4)
    void listsProductsAsAPageWithTheSampleShape() {
        api().get("/api/products?page=0&size=2&sort=code,asc")
                .then()
                .statusCode(200)
                .body("content", notNullValue())
                .body("page", equalTo(0))
                .body("size", equalTo(2))
                .body("totalElements", greaterThan(0))
                .body("totalPages", greaterThan(0))
                .body("first", equalTo(true))
                .body("hasNext", notNullValue())
                .body("hasPrevious", equalTo(false));
    }

    @Test
    @Order(5)
    void clampsThePageSizeToOneHundred() {
        api().get("/api/products?page=0&size=1000").then().statusCode(200).body("size", equalTo(100));
    }

    @Test
    @Order(6)
    void rejectsASortFieldOutsideTheWhitelist() {
        api().get("/api/products?page=0&size=5&sort=password,asc")
                .then()
                .statusCode(400)
                .body("status", equalTo(400))
                .body("error", equalTo("BUSINESS_ERROR"));
    }

    @Test
    @Order(7)
    void listsCustomers() {
        api().get("/api/customers?page=0&size=5")
                .then()
                .statusCode(200)
                .body("content", notNullValue())
                .body("totalElements", greaterThan(0));
    }

    @Test
    @Order(8)
    void createsAnOrderAndComputesTheTotal() {
        JsonPath created = api().contentType(ContentType.JSON)
                .body(orderOf(productCode, 1))
                .post("/api/orders")
                .then()
                .statusCode(201)
                .body("status", equalTo("DRAFT"))
                .body("orderNo", notNullValue())
                .body("lines", notNullValue())
                .extract()
                .jsonPath();
        createdOrderId = created.getLong("id");
        assertEquals(0, unitPrice.compareTo(new BigDecimal(created.getString("totalAmount"))));
    }

    @Test
    @Order(9)
    void confirmingAnOrderMovesStock() {
        int before = stockOf(productCode);
        api().post("/api/orders/" + createdOrderId + "/confirm")
                .then()
                .statusCode(200)
                .body("status", equalTo("CONFIRMED"));
        assertEquals(before - 1, stockOf(productCode));
    }

    @Test
    @Order(10)
    void rejectsAnUnknownCustomer() {
        Map<String, Object> order = new HashMap<>(orderOf(productCode, 1));
        order.put("customerCode", "C-UNKNOWN");
        api().contentType(ContentType.JSON)
                .body(order)
                .post("/api/orders")
                .then()
                .statusCode(404)
                .body("status", equalTo(404))
                .body("error", equalTo("BUSINESS_ERROR"));
    }

    @Test
    @Order(11)
    void reportsValidationErrorsWithFieldNames() {
        api().contentType(ContentType.JSON)
                .body(orderOf(productCode, 0))
                .post("/api/orders")
                .then()
                .statusCode(400)
                .body("status", equalTo(400))
                .body("error", equalTo("VALIDATION_ERROR"))
                .body("message", containsString("quantity"));
    }

    @Test
    @Order(12)
    void returnsProblemDetailsWhenExplicitlyRequested() {
        given().header("Authorization", "Bearer " + token)
                .accept("application/problem+json")
                .get("/api/orders/999999")
                .then()
                .statusCode(404)
                .body("status", equalTo(404))
                .body("type", notNullValue())
                .body("title", notNullValue())
                .body("instance", notNullValue());
    }

    @Test
    @Order(13)
    void listsInvoices() {
        api().get("/api/invoices?page=0&size=5")
                .then()
                .statusCode(200)
                .body("content", notNullValue())
                .body("page", equalTo(0));
    }

    /** Jobs start asynchronously, so at the time of the POST response the status is STARTING or STARTED. */
    @Test
    @Order(14)
    void runsTheInvoiceJobThroughJakartaBatch() {
        assumeBatch();
        JsonPath started = api().post("/api/batch/invoices?month=" + lastMonth())
                .then()
                .statusCode(202)
                .extract()
                .jsonPath();
        assertTrue(List.of("STARTED", "STARTING").contains(started.getString("status")), started.prettify());
        assertEquals(
                "COMPLETED", waitForExecution(started.getLong("executionId")).get("status"));
    }

    @Test
    @Order(15)
    void refreshNeedsTheOpaqueCookie() {
        given().post("/api/auth/refresh").then().statusCode(401);
    }

    @Test
    @Order(16)
    void returnsTheCurrentUser() {
        api().get("/api/auth/me")
                .then()
                .statusCode(200)
                .body("username", equalTo(admin().get("username")))
                .body("roles", notNullValue());
    }

    /** Cookie (browser) writes require X-XSRF-TOKEN. */
    @Test
    @Order(17)
    void requiresACsrfTokenForCookieWrites() {
        Map<String, String> cookies = sessionLogin();
        String csrf = cookies.get(CSRF_COOKIE);
        assertTrue(csrf != null && !csrf.isBlank(), "no XSRF-TOKEN cookie");

        given().cookies(cookies)
                .contentType(ContentType.JSON)
                .body(orderOf(productCode, 1))
                .post("/api/orders")
                .then()
                .statusCode(403);
        given().cookies(cookies)
                .header(CSRF_HEADER, csrf)
                .contentType(ContentType.JSON)
                .body(orderOf(productCode, 1))
                .post("/api/orders")
                .then()
                .statusCode(201);
    }

    @Test
    @Order(18)
    void keepsBatchBehindTheAdminRole() {
        String userToken = bearerLogin(Map.of(
                        "username", System.getProperty("app.user", "user"),
                        "password", System.getProperty("app.user.password", "user")))
                .getString("token");
        given().header("Authorization", "Bearer " + userToken)
                .get("/api/orders")
                .then()
                .statusCode(200);
        given().header("Authorization", "Bearer " + userToken)
                .post("/api/batch/invoices?month="
                        + LocalDate.now().minusMonths(2).toString().substring(0, 7))
                .then()
                .statusCode(403);
    }

    @Test
    @Order(19)
    void rejectsAnUnsupportedMethod() {
        api().contentType(ContentType.JSON)
                .body(Map.of("code", "X"))
                .put("/api/products")
                .then()
                .statusCode(405);
    }

    @Test
    @Order(20)
    void rotatesTheRefreshTokenAndRejectsTheOldOne() {
        String refreshToken = sessionLogin().get(REFRESH_COOKIE);
        String rotated = refresh(refreshToken)
                .post("/api/auth/refresh")
                .then()
                .statusCode(200)
                .extract()
                .cookie(REFRESH_COOKIE);
        assertTrue(rotated != null && !rotated.isBlank(), "no cookie after rotation");

        refresh(refreshToken).post("/api/auth/refresh").then().statusCode(401);
        refresh(rotated).post("/api/auth/refresh").then().statusCode(200);
    }

    /** Concurrent refreshes with the same refresh token: only one gets a new session. */
    @Test
    @Order(21)
    void allowsExactlyOneConcurrentRefresh() throws Exception {
        String refreshToken = sessionLogin().get(REFRESH_COOKIE);
        List<Integer> statuses = concurrently(
                6, () -> refresh(refreshToken).post("/api/auth/refresh").statusCode());
        assertEquals(1, Collections.frequency(statuses, 200), statuses.toString());
        assertEquals(5, Collections.frequency(statuses, 401), statuses.toString());
    }

    /**
     * The runtime's own MicroProfile Health (optional). Its path differs per runtime, so it is checked only when given.
     */
    @Test
    @Order(22)
    void runtimeHealthEndpointAnswersWhenConfigured() {
        String healthUrl = System.getProperty("app.health.url");
        assumeTrue(healthUrl != null && !healthUrl.isBlank(), "no runtime health endpoint configured");
        given().get(healthUrl).then().statusCode(200);
    }

    /** Import files are given by file name only, and nothing outside the import directory can be read. */
    @Test
    @Order(23)
    void rejectsImportFileNamesOutsideTheDataDirectory() {
        assumeBatch();
        for (String name : List.of("/etc/passwd", "../orders.csv", "orders.txt", "missing.csv")) {
            api().post("/api/batch/imports?inputFile=" + name).then().statusCode(400);
        }
    }

    /**
     * Importing the same CSV twice never creates duplicate orders.
     *
     * <p>The bundled CSV has 4 lines, 2 of them identical. Both are checked to be imported (or skipped as already
     * imported). The first import may already have happened in an earlier run, so the check uses the counts.
     */
    @Test
    @Order(24)
    void importingTheSameCsvTwiceCreatesNoDuplicateOrders() {
        assumeBatch();
        assumeTrue(
                api().get("/api/customers?page=0&size=100")
                                .path("content.code")
                                .toString()
                                .contains("C001")
                        && products().stream().anyMatch(row -> "P002".equals(row.get("code"))),
                "the sample CSV needs the demo rows C001..C003 / P001..P004");

        long before = total("/api/orders");
        ImportCounts first = runImportJob();
        assertEquals(0, first.failed(), first.toString());
        assertEquals(4, first.created() + first.skipped(), first.toString());
        assertEquals(before + first.created(), total("/api/orders"), first.toString());

        ImportCounts second = runImportJob();
        assertEquals(new ImportCounts(0, 4, 0), second);
        assertEquals(before + first.created(), total("/api/orders"));
    }

    /** refresh is a cookie write too, so it needs the CSRF header. A rejected request does not consume the token. */
    @Test
    @Order(25)
    void refreshRequiresTheCsrfHeader() {
        String refreshToken = sessionLogin().get(REFRESH_COOKIE);
        given().cookie(REFRESH_COOKIE, refreshToken)
                .cookie(CSRF_COOKIE, TEST_CSRF)
                .post("/api/auth/refresh")
                .then()
                .statusCode(403);
        refresh(refreshToken).post("/api/auth/refresh").then().statusCode(200);
    }

    /** Logout revokes the refresh token. Bearer clients can revoke it without a cookie. */
    @Test
    @Order(26)
    void logoutRevokesTheRefreshToken() {
        Map<String, String> cookies = sessionLogin();
        given().cookies(cookies)
                .header(CSRF_HEADER, cookies.get(CSRF_COOKIE))
                .post("/api/auth/logout")
                .then()
                .statusCode(204);
        refresh(cookies.get(REFRESH_COOKIE)).post("/api/auth/refresh").then().statusCode(401);

        JsonPath login = bearerLogin(admin());
        given().header("Authorization", "Bearer " + login.getString("token"))
                .post("/api/auth/logout")
                .then()
                .statusCode(204);
        refresh(login.getString("refreshToken"))
                .post("/api/auth/refresh")
                .then()
                .statusCode(401);
    }

    @Test
    @Order(27)
    void cancellingAConfirmedOrderReturnsTheStock() {
        long id = createOrder(productCode, 1);
        int before = stockOf(productCode);
        api().post("/api/orders/" + id + "/confirm").then().statusCode(200);
        assertEquals(before - 1, stockOf(productCode));

        api().post("/api/orders/" + id + "/cancel").then().statusCode(200).body("status", equalTo("CANCELLED"));
        assertEquals(before, stockOf(productCode));
        // A second cancel is 409 and the stock is not returned twice.
        api().post("/api/orders/" + id + "/cancel").then().statusCode(409);
        assertEquals(before, stockOf(productCode));
    }

    @Test
    @Order(28)
    void confirmingMoreThanTheStockIsAConflict() {
        // Use the product with the least stock, to stay under the order quantity limit (10,000).
        Map<String, Object> scarce = products().stream()
                .min(Comparator.comparingInt(ApiConformanceTest::stock))
                .orElseThrow();
        String code = (String) scarce.get("code");
        int stock = stock(scarce);
        assumeTrue(stock < 10_000, "every product has more stock than one order line may ask for");

        long id = createOrder(code, stock + 1);
        api().post("/api/orders/" + id + "/confirm").then().statusCode(409);
        assertEquals(stock, stockOf(code));
        api().get("/api/orders/" + id).then().statusCode(200).body("status", equalTo("DRAFT"));
    }

    /** Optimistic locking: of concurrent confirmations of the same order, only one moves the stock. */
    @Test
    @Order(29)
    void confirmsAnOrderOnceUnderConcurrentRequests() throws Exception {
        long id = createOrder(productCode, 1);
        int before = stockOf(productCode);
        List<Integer> statuses = concurrently(
                4, () -> api().post("/api/orders/" + id + "/confirm").statusCode());
        assertEquals(1, Collections.frequency(statuses, 200), statuses.toString());
        assertEquals(3, Collections.frequency(statuses, 409), statuses.toString());
        assertEquals(before - 1, stockOf(productCode));
    }

    /** Sorting by a column with many equal values never shows the same row on two pages. */
    @Test
    @Order(30)
    void pagesStablyWhenTheSortColumnHasTies() {
        int size = 3;
        long total = Math.min(total("/api/orders"), 30);
        List<Long> seen = new ArrayList<>();
        for (int page = 0; (long) page * size < total; page++) {
            seen.addAll(api().get("/api/orders?page=" + page + "&size=" + size + "&sort=status,asc")
                    .then()
                    .statusCode(200)
                    .extract()
                    .jsonPath()
                    .getList("content.id", Long.class));
        }
        assertEquals(seen.size(), new HashSet<>(seen).size(), seen.toString());
    }

    @Test
    @Order(31)
    void reportsUnknownOrFinishedBatchExecutionsAsClientErrors() {
        assumeBatch();
        api().get("/api/batch/executions/987654321").then().statusCode(404);
        api().post("/api/batch/executions/987654321/restart").then().statusCode(404);

        // The invoice job completed in #14 cannot be restarted.
        Number completed = api().get("/api/batch/executions?job=invoiceJob&count=1")
                .then()
                .statusCode(200)
                .extract()
                .path("[0].executionId");
        assumeTrue(completed != null, "no invoice job execution to restart");
        api().post("/api/batch/executions/" + completed + "/restart").then().statusCode(409);
    }

    @Test
    @Order(32)
    void runningTheInvoiceJobTwiceBillsEachOrderOnce() {
        assumeBatch();
        runInvoiceJob();
        long invoices = total("/api/invoices");
        runInvoiceJob();
        assertEquals(invoices, total("/api/invoices"));
    }

    // ---------------------------------------------------------------- helpers

    private static RequestSpecification api() {
        return given().header("Authorization", "Bearer " + token).accept(ContentType.JSON);
    }

    /**
     * Skipped with {@code -Dapp.batch=false} on setups where batch is disabled, such as some Liberty configurations.
     */
    private static void assumeBatch() {
        assumeTrue(
                Boolean.parseBoolean(System.getProperty("app.batch", "true")),
                "batch endpoints not available on this runtime configuration");
    }

    private static Map<String, String> admin() {
        return Map.of(
                "username", System.getProperty("app.username", "admin"),
                "password", System.getProperty("app.password", "admin"));
    }

    /** Login for Bearer clients. The tokens come back in the body. */
    private static JsonPath bearerLogin(Map<String, String> credentials) {
        return given().contentType(ContentType.JSON)
                .body(credentials)
                .post("/api/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath();
    }

    /** Login for browsers. The tokens come back in cookies. */
    private static Map<String, String> sessionLogin() {
        return given().contentType(ContentType.JSON)
                .body(admin())
                .post("/api/auth/session")
                .then()
                .statusCode(200)
                .extract()
                .cookies();
    }

    /** A refresh request with the CSRF double submit (cookie and header match). */
    private static RequestSpecification refresh(String refreshToken) {
        return given().cookie(REFRESH_COOKIE, refreshToken)
                .cookie(CSRF_COOKIE, TEST_CSRF)
                .header(CSRF_HEADER, TEST_CSRF)
                .accept(ContentType.JSON);
    }

    private static Map<String, Object> orderOf(String product, int quantity) {
        return Map.of(
                "customerCode", customerCode,
                "orderDate", LocalDate.now().toString(),
                "lines", List.of(Map.of("productCode", product, "quantity", quantity)));
    }

    private static long createOrder(String product, int quantity) {
        Number id = api().contentType(ContentType.JSON)
                .body(orderOf(product, quantity))
                .post("/api/orders")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
        return id.longValue();
    }

    private static List<Map<String, Object>> products() {
        return api().get("/api/products?page=0&size=100&sort=code,asc")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList("content");
    }

    private static int stock(Map<String, Object> product) {
        return ((Number) product.get("stock")).intValue();
    }

    private static int stockOf(String code) {
        return products().stream()
                .filter(row -> code.equals(row.get("code")))
                .mapToInt(ApiConformanceTest::stock)
                .findFirst()
                .orElseThrow();
    }

    /** {@code totalElements} of a list. */
    private static long total(String path) {
        Number total = api().get(path + "?page=0&size=1")
                .then()
                .statusCode(200)
                .extract()
                .path("totalElements");
        return total.longValue();
    }

    private static String lastMonth() {
        return LocalDate.now().minusMonths(1).toString().substring(0, 7);
    }

    /** Sends the same request concurrently and returns the status of each response. */
    private static List<Integer> concurrently(int attempts, Callable<Integer> request) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < attempts; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return request.call();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get(30, TimeUnit.SECONDS));
            }
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }

    /** Waits until the job execution ends and returns it. FAILED, STOPPED or 30 seconds elapsed is a failure. */
    private static Map<String, Object> waitForExecution(long executionId) {
        for (int attempt = 0; attempt < 60; attempt++) {
            Map<String, Object> execution = api().get("/api/batch/executions/" + executionId)
                    .then()
                    .statusCode(200)
                    .extract()
                    .path("$");
            Object status = execution.get("status");
            if ("COMPLETED".equals(status)) {
                return execution;
            }
            if ("FAILED".equals(status) || "STOPPED".equals(status)) {
                throw new AssertionError("job execution " + executionId + " ended " + status + ": " + execution);
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new AssertionError("job execution " + executionId + " did not finish within 30 s");
    }

    private static long startJob(String path) {
        Number id = api().post(path).then().statusCode(202).extract().path("executionId");
        return id.longValue();
    }

    private static void runInvoiceJob() {
        waitForExecution(startJob("/api/batch/invoices?month=" + lastMonth()));
    }

    private static ImportCounts runImportJob() {
        return ImportCounts.parse(
                String.valueOf(waitForExecution(startJob("/api/batch/imports")).get("exitStatus")));
    }

    /** The counts in the import job's exit status (for example {@code COMPLETED created=3 skipped=0 failed=0}). */
    private record ImportCounts(int created, int skipped, int failed) {

        static ImportCounts parse(String exitStatus) {
            Map<String, Integer> counts = new HashMap<>();
            for (String part : exitStatus.split(" ")) {
                String[] pair = part.split("=", 2);
                if (pair.length == 2) {
                    counts.put(pair[0], Integer.parseInt(pair[1]));
                }
            }
            assertTrue(counts.keySet().containsAll(List.of("created", "skipped", "failed")), exitStatus);
            return new ImportCounts(counts.get("created"), counts.get("skipped"), counts.get("failed"));
        }
    }
}
