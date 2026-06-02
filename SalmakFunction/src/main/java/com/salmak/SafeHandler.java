package com.salmak;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.util.Map;

/**
 * Handles POST /safe — confirms a user has evacuated by removing the
 * activeAlert attribute from their DynamoDB record.
 *
 * Responses:
 *   400 — phoneNumber missing from request body
 *   404 — user not found in the table
 *   200 — activeAlert removed, evacuation confirmed
 *   500 — unexpected DynamoDB error
 */
public class SafeHandler {

    private static final ObjectMapper MAPPER     = new ObjectMapper();
    private static final String       TABLE_NAME = System.getenv("TABLE_NAME");

    // Lazy singleton — only created on first real invocation
    private static final class DynamoHolder {
        static final DynamoDbClient INSTANCE = DynamoDbClient.create();
    }

    private final DynamoDbClient dynamo;

    /** Production constructor. */
    public SafeHandler() {
        this.dynamo = null;
    }

    /** Test constructor — accepts an injected client. */
    SafeHandler(DynamoDbClient dynamo) {
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

        // --- Parse phoneNumber from request body ---
        String phoneNumber;
        try {
            JsonNode body = MAPPER.readTree(input.getBody());
            JsonNode phoneNode = body.get("phoneNumber");
            if (phoneNode == null || phoneNode.asText().isBlank()) {
                return App.response(400, "{\"message\":\"Missing required field: phoneNumber\"}");
            }
            phoneNumber = phoneNode.asText().trim();
        } catch (Exception e) {
            logger.log("Failed to parse request body: " + e.getMessage());
            return App.response(400, "{\"message\":\"Invalid JSON body\"}");
        }

        logger.log("Safe confirmation for user: " + phoneNumber);

        // --- Verify user exists ---
        try {
            GetItemResponse existing = getDynamo().getItem(GetItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .key(Map.of("phoneNumber", AttributeValue.builder().s(phoneNumber).build()))
                    .build());

            if (!existing.hasItem() || existing.item().isEmpty()) {
                return App.response(404, "{\"message\":\"User not found\"}");
            }
        } catch (Exception e) {
            logger.log("DynamoDB lookup failed: " + e.getMessage());
            return App.response(500, "{\"message\":\"Failed to look up user\"}");
        }

        // --- Remove activeAlert attribute entirely ---
        try {
            getDynamo().updateItem(UpdateItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .key(Map.of("phoneNumber", AttributeValue.builder().s(phoneNumber).build()))
                    .updateExpression("REMOVE activeAlert")
                    .build());
        } catch (ConditionalCheckFailedException e) {
            // Should not happen without a condition expression, but guard anyway
            logger.log("Conditional check failed for user " + phoneNumber + ": " + e.getMessage());
            return App.response(500, "{\"message\":\"Failed to confirm evacuation\"}");
        } catch (Exception e) {
            logger.log("DynamoDB update failed for user " + phoneNumber + ": " + e.getMessage());
            return App.response(500, "{\"message\":\"Failed to confirm evacuation\"}");
        }

        logger.log("Evacuation confirmed for user: " + phoneNumber);
        return App.response(200, "{\"message\":\"Evacuation confirmed\"}");
    }
}
