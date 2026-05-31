package com.salmak;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Handles POST /alert — scans all registered users, finds those within
 * 500 metres of the given coordinates using the Haversine formula, and
 * stamps an "activeAlert" field on each matching DynamoDB record.
 */
public class AlertHandler {

    private static final ObjectMapper MAPPER        = new ObjectMapper();
    private static final String       TABLE_NAME    = System.getenv("TABLE_NAME");
    private static final double       RADIUS_METRES = 500.0;
    private static final double       EARTH_RADIUS  = 6_371_000.0; // metres

    // Lazy singleton — only created on first real invocation
    private static final class DynamoHolder {
        static final DynamoDbClient INSTANCE = DynamoDbClient.create();
    }

    private final DynamoDbClient dynamo;

    /** Production constructor. */
    public AlertHandler() {
        this.dynamo = null;
    }

    /** Test constructor — accepts an injected client. */
    AlertHandler(DynamoDbClient dynamo) {
        this.dynamo = dynamo;
    }

    private DynamoDbClient getDynamo() {
        return dynamo != null ? dynamo : DynamoHolder.INSTANCE;
    }

    // -----------------------------------------------------------------------
    // Request body POJO
    // -----------------------------------------------------------------------

    static class AlertRequest {
        private Double lat;
        private Double lng;

        public Double getLat() { return lat; }
        public void setLat(Double lat) { this.lat = lat; }
        public Double getLng() { return lng; }
        public void setLng(Double lng) { this.lng = lng; }
    }

    // -----------------------------------------------------------------------
    // Handler
    // -----------------------------------------------------------------------

    public APIGatewayProxyResponseEvent handle(APIGatewayProxyRequestEvent input, Context context) {
        LambdaLogger logger = context != null ? context.getLogger() : new LambdaLogger() {
            @Override public void log(String message) { System.out.println(message); }
            @Override public void log(byte[] message) { System.out.println(new String(message)); }
        };

        // --- Parse body ---
        AlertRequest req;
        try {
            req = MAPPER.readValue(input.getBody(), AlertRequest.class);
        } catch (Exception e) {
            logger.log("Failed to parse alert body: " + e.getMessage());
            return App.response(400, "{\"message\":\"Invalid JSON body\"}");
        }

        if (req.getLat() == null || req.getLng() == null) {
            return App.response(400, "{\"message\":\"Missing required fields: lat, lng\"}");
        }

        double alertLat = req.getLat();
        double alertLng = req.getLng();
        String timestamp = Instant.now().toString();

        logger.log(String.format("Alert triggered at lat=%.6f lng=%.6f", alertLat, alertLng));

        // --- Scan all users (paginated) ---
        List<Map<String, AttributeValue>> allUsers = new ArrayList<>();
        try {
            String lastKey = null;
            do {
                ScanRequest.Builder scanBuilder = ScanRequest.builder().tableName(TABLE_NAME);
                if (lastKey != null) {
                    scanBuilder.exclusiveStartKey(
                            Map.of("phoneNumber", AttributeValue.builder().s(lastKey).build()));
                }
                ScanResponse page = getDynamo().scan(scanBuilder.build());
                allUsers.addAll(page.items());
                lastKey = page.hasLastEvaluatedKey()
                        ? page.lastEvaluatedKey().get("phoneNumber").s()
                        : null;
            } while (lastKey != null);
        } catch (Exception e) {
            logger.log("DynamoDB scan failed: " + e.getMessage());
            return App.response(500, "{\"message\":\"Failed to scan users\"}");
        }

        // --- Find users within 500 m and stamp activeAlert ---
        int alertedCount = 0;
        for (Map<String, AttributeValue> user : allUsers) {
            AttributeValue latAttr = user.get("lat");
            AttributeValue lngAttr = user.get("lng");
            if (latAttr == null || lngAttr == null) continue;

            double userLat = Double.parseDouble(latAttr.n());
            double userLng = Double.parseDouble(lngAttr.n());

            if (haversineMetres(alertLat, alertLng, userLat, userLng) <= RADIUS_METRES) {
                String phone = user.get("phoneNumber").s();
                try {
                    stampActiveAlert(phone, alertLat, alertLng, timestamp);
                    alertedCount++;
                    logger.log("Alerted user: " + phone);
                } catch (Exception e) {
                    logger.log("Failed to update user " + phone + ": " + e.getMessage());
                }
            }
        }

        logger.log("Alert complete — users alerted: " + alertedCount);
        return App.response(200,
                String.format("{\"message\":\"Alert sent\",\"usersAlerted\":%d}", alertedCount));
    }

    // -----------------------------------------------------------------------
    // DynamoDB update
    // -----------------------------------------------------------------------

    private void stampActiveAlert(String phoneNumber, double lat, double lng, String timestamp) {
        String alertJson = String.format(
                "{\\\"lat\\\":%.6f,\\\"lng\\\":%.6f,\\\"timestamp\\\":\\\"%s\\\"}",
                lat, lng, timestamp);

        getDynamo().updateItem(UpdateItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(Map.of("phoneNumber", AttributeValue.builder().s(phoneNumber).build()))
                .updateExpression("SET activeAlert = :a")
                .expressionAttributeValues(Map.of(
                        ":a", AttributeValue.builder().s(alertJson).build()))
                .build());
    }

    // -----------------------------------------------------------------------
    // Haversine formula — returns distance in metres between two lat/lng points
    // -----------------------------------------------------------------------

    static double haversineMetres(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return EARTH_RADIUS * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
