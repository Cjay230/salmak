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
 * Handles GET /alert/{phoneNumber} — checks whether a user has an active alert
 * stamped on their DynamoDB record.
 *
 * Responses:
 *   400 — phoneNumber missing or blank
 *   404 — user not found in the table
 *   200 + hasAlert:false — user exists but no activeAlert field
 *   200 + hasAlert:true  — user exists and has an activeAlert field
 *   500 — DynamoDB error
 */
public class GetAlertHandler {

    private static final String TABLE_NAME = System.getenv("TABLE_NAME");

    // Lazy singleton — only created on first real invocation
    private static final class DynamoHolder {
        static final DynamoDbClient INSTANCE = DynamoDbClient.create();
    }

    private final DynamoDbClient dynamo;

    /** Production constructor. */
    public GetAlertHandler() {
        this.dynamo = null;
    }

    /** Test constructor — accepts an injected client. */
    GetAlertHandler(DynamoDbClient dynamo) {
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

        logger.log("Checking alert for user: " + phoneNumber);

        // --- Fetch user from DynamoDB ---
        GetItemResponse result;
        try {
            result = getDynamo().getItem(GetItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .key(Map.of("phoneNumber", AttributeValue.builder().s(phoneNumber).build()))
                    .build());
        } catch (Exception e) {
            logger.log("DynamoDB lookup failed: " + e.getMessage());
            return App.response(500, "{\"message\":\"Failed to look up user\"}");
        }

        // --- User not found ---
        if (!result.hasItem() || result.item().isEmpty()) {
            return App.response(404, "{\"message\":\"User not found\"}");
        }

        Map<String, AttributeValue> item = result.item();

        // --- No active alert ---
        if (!item.containsKey("activeAlert")) {
            return App.response(200, "{\"hasAlert\":false}");
        }

        // --- Active alert present ---
        String alertJson = item.get("activeAlert").s();
        // alertJson is already a JSON string stored as a DynamoDB String attribute;
        // embed it directly into the response body.
        String body = String.format("{\"hasAlert\":true,\"alert\":%s}", alertJson);
        return App.response(200, body);
    }
}
