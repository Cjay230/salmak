package com.salmak;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import org.junit.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class AlertHandlerTest {

    // Beirut city centre — used as the alert origin in most tests
    private static final double ALERT_LAT = 33.8938;
    private static final double ALERT_LNG = 35.5018;

    // -----------------------------------------------------------------------
    // Haversine unit tests (no DynamoDB needed)
    // -----------------------------------------------------------------------

    @Test
    public void haversine_samePoint_isZero() {
        assertEquals(0.0, AlertHandler.haversineMetres(33.8938, 35.5018, 33.8938, 35.5018), 0.001);
    }

    @Test
    public void haversine_within500m_detected() {
        // ~200 m north of alert origin
        double nearLat = 33.8956;
        double nearLng = 35.5018;
        double dist = AlertHandler.haversineMetres(ALERT_LAT, ALERT_LNG, nearLat, nearLng);
        assertTrue("Expected < 500 m, got " + dist, dist < 500.0);
    }

    @Test
    public void haversine_beyond500m_notDetected() {
        // ~5 km away
        double farLat = 33.9400;
        double farLng = 35.5018;
        double dist = AlertHandler.haversineMetres(ALERT_LAT, ALERT_LNG, farLat, farLng);
        assertTrue("Expected > 500 m, got " + dist, dist > 500.0);
    }

    // -----------------------------------------------------------------------
    // Validation tests
    // -----------------------------------------------------------------------

    @Test
    public void missingBody_returns400() {
        AlertHandler handler = new AlertHandler(emptyTableStub(new AtomicInteger()));
        APIGatewayProxyResponseEvent res = postAlert(handler, "not-json");
        assertEquals(400, res.getStatusCode().intValue());
    }

    @Test
    public void missingLat_returns400() {
        AlertHandler handler = new AlertHandler(emptyTableStub(new AtomicInteger()));
        APIGatewayProxyResponseEvent res = postAlert(handler, "{\"lng\":35.5018}");
        assertEquals(400, res.getStatusCode().intValue());
        assertTrue(res.getBody().contains("lat"));
    }

    @Test
    public void missingLng_returns400() {
        AlertHandler handler = new AlertHandler(emptyTableStub(new AtomicInteger()));
        APIGatewayProxyResponseEvent res = postAlert(handler, "{\"lat\":33.8938}");
        assertEquals(400, res.getStatusCode().intValue());
    }

    // -----------------------------------------------------------------------
    // Proximity + update tests
    // -----------------------------------------------------------------------

    @Test
    public void noUsersInTable_returns200WithZeroAlerted() {
        AlertHandler handler = new AlertHandler(emptyTableStub(new AtomicInteger()));
        APIGatewayProxyResponseEvent res = postAlert(handler,
                String.format("{\"lat\":%f,\"lng\":%f}", ALERT_LAT, ALERT_LNG));

        assertEquals(200, res.getStatusCode().intValue());
        assertTrue(res.getBody().contains("\"usersAlerted\":0"));
    }

    @Test
    public void oneUserWithin500m_isAlerted() {
        AtomicInteger updateCount = new AtomicInteger();
        // User ~200 m north of alert origin
        DynamoDbClient stub = tableStub(
                List.of(userItem("+96170000001", 33.8956, 35.5018)),
                updateCount);

        AlertHandler handler = new AlertHandler(stub);
        APIGatewayProxyResponseEvent res = postAlert(handler,
                String.format("{\"lat\":%f,\"lng\":%f}", ALERT_LAT, ALERT_LNG));

        assertEquals(200, res.getStatusCode().intValue());
        assertTrue(res.getBody().contains("\"usersAlerted\":1"));
        assertEquals(1, updateCount.get());
    }

    @Test
    public void oneUserBeyond500m_isNotAlerted() {
        AtomicInteger updateCount = new AtomicInteger();
        // User ~5 km away
        DynamoDbClient stub = tableStub(
                List.of(userItem("+96170000002", 33.9400, 35.5018)),
                updateCount);

        AlertHandler handler = new AlertHandler(stub);
        APIGatewayProxyResponseEvent res = postAlert(handler,
                String.format("{\"lat\":%f,\"lng\":%f}", ALERT_LAT, ALERT_LNG));

        assertEquals(200, res.getStatusCode().intValue());
        assertTrue(res.getBody().contains("\"usersAlerted\":0"));
        assertEquals(0, updateCount.get());
    }

    @Test
    public void mixedUsers_onlyNearbyAlerted() {
        AtomicInteger updateCount = new AtomicInteger();
        DynamoDbClient stub = tableStub(
                List.of(
                        userItem("+96170000003", 33.8956, 35.5018),  // ~200 m — IN
                        userItem("+96170000004", 33.9400, 35.5018),  // ~5 km  — OUT
                        userItem("+96170000005", 33.8940, 35.5020)   // ~30 m  — IN
                ),
                updateCount);

        AlertHandler handler = new AlertHandler(stub);
        APIGatewayProxyResponseEvent res = postAlert(handler,
                String.format("{\"lat\":%f,\"lng\":%f}", ALERT_LAT, ALERT_LNG));

        assertEquals(200, res.getStatusCode().intValue());
        assertTrue(res.getBody().contains("\"usersAlerted\":2"));
        assertEquals(2, updateCount.get());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private APIGatewayProxyResponseEvent postAlert(AlertHandler handler, String body) {
        APIGatewayProxyRequestEvent req = new APIGatewayProxyRequestEvent()
                .withHttpMethod("POST")
                .withPath("/alert")
                .withBody(body);
        return handler.handle(req, null);
    }

    private static Map<String, AttributeValue> userItem(String phone, double lat, double lng) {
        return Map.of(
                "phoneNumber", AttributeValue.builder().s(phone).build(),
                "lat",         AttributeValue.builder().n(String.valueOf(lat)).build(),
                "lng",         AttributeValue.builder().n(String.valueOf(lng)).build()
        );
    }

    /** Stub that returns an empty table and counts UpdateItem calls. */
    private static DynamoDbClient emptyTableStub(AtomicInteger updateCount) {
        return tableStub(List.of(), updateCount);
    }

    /** Stub that returns the given items on Scan and counts UpdateItem calls. */
    private static DynamoDbClient tableStub(
            List<Map<String, AttributeValue>> items, AtomicInteger updateCount) {
        return new DynamoDbClient() {
            @Override
            public ScanResponse scan(ScanRequest req) {
                return ScanResponse.builder().items(items).build();
            }
            @Override
            public UpdateItemResponse updateItem(UpdateItemRequest req) {
                updateCount.incrementAndGet();
                return UpdateItemResponse.builder().build();
            }
            @Override public String serviceName() { return "stub"; }
            @Override public void close() {}
        };
    }
}
