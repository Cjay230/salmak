package com.salmak;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;

import java.util.Map;

/**
 * Handles GET /user/{phoneNumber} — looks up a user in the Salmak-Users table
 * and returns their registration status and data.
 */
public class UserHandler {

    private static final String TABLE_NAME = System.getenv("TABLE_NAME");

    // Lazy holder — client only created on first real invocation
    private static final class DynamoHolder {
        static final DynamoDbClient INSTANCE = DynamoDbClient.create();
    }

    private final DynamoDbClient dynamo;

    /** Production constructor — uses the lazy singleton. */
    public UserHandler() {
        this.dynamo = null;
    }

    /** Test constructor — accepts an injected client (e.g. a mock). */
    UserHandler(DynamoDbClient dynamo) {
        this.dynamo = dynamo;
    }

    private DynamoDbClient getDynamo() {
        return dynamo != null ? dynamo : DynamoHolder.INSTANCE;
    }

    public APIGatewayProxyResponseEvent handle(APIGatewayProxyRequestEvent input, Context context) {
        LambdaLogger logger = context != null ? context.getLogger() : new LambdaLogger() {
            @Override public void log(String message) { System.out.println(message); }
            @Override public void log(byte[] message) { System.out.println(new String(message)); }
        };

        // --- Extract path parameter ---
        Map<String, String> pathParams = input.getPathParameters();
        String phoneNumber = pathParams != null ? pathParams.get("phoneNumber") : null;

        if (phoneNumber == null || phoneNumber.isBlank()) {
            return App.response(400, "{\"message\":\"Missing or invalid phoneNumber\"}");
        }

        logger.log("Looking up user: " + phoneNumber);

        // --- Query DynamoDB ---
        try {
            GetItemResponse result = getDynamo().getItem(GetItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .key(Map.of("phoneNumber", AttributeValue.builder().s(phoneNumber).build()))
                    .build());

            if (!result.hasItem() || result.item().isEmpty()) {
                return App.response(200, "{\"registered\":false}");
            }

            Map<String, AttributeValue> item = result.item();
            String body = buildUserJson(item);
            return App.response(200, body);

        } catch (Exception e) {
            logger.log("DynamoDB lookup failed: " + e.getMessage());
            return App.response(500, "{\"message\":\"Failed to look up user\"}");
        }
    }

    /**
     * Builds a JSON string from a DynamoDB item map.
     * Includes all stored fields plus "registered": true.
     */
    private String buildUserJson(Map<String, AttributeValue> item) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"registered\":true");

        appendStr(sb, item, "phoneNumber");
        appendStr(sb, item, "name");
        appendStr(sb, item, "emergencyContact");
        appendNum(sb, item, "peopleInHouse");
        appendNum(sb, item, "lat");
        appendNum(sb, item, "lng");

        if (item.containsKey("idPhoto")) {
            appendStr(sb, item, "idPhoto");
        }

        sb.append("}");
        return sb.toString();
    }

    private void appendStr(StringBuilder sb, Map<String, AttributeValue> item, String key) {
        if (item.containsKey(key)) {
            sb.append(",\"").append(key).append("\":\"")
              .append(item.get(key).s().replace("\"", "\\\""))
              .append("\"");
        }
    }

    private void appendNum(StringBuilder sb, Map<String, AttributeValue> item, String key) {
        if (item.containsKey(key)) {
            sb.append(",\"").append(key).append("\":")
              .append(item.get(key).n());
        }
    }
}
