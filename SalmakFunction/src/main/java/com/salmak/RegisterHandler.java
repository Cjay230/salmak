package com.salmak;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.util.HashMap;
import java.util.Map;

/**
 * Handles POST /register — validates the request body and saves the user
 * to the Salmak-Users DynamoDB table.
 */
public class RegisterHandler {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TABLE_NAME = System.getenv("TABLE_NAME");

    // Lazy holder — DynamoDbClient is only created on first real invocation,
    // not at class-load time. This prevents failures in unit tests where no
    // AWS region is configured.
    private static final class DynamoHolder {
        static final DynamoDbClient INSTANCE = DynamoDbClient.create();
    }

    private final DynamoDbClient dynamo;

    /** Production constructor — uses the lazy singleton. */
    public RegisterHandler() {
        this.dynamo = null; // resolved lazily via getDynamo()
    }

    /** Test constructor — accepts an injected client (e.g. a mock). */
    RegisterHandler(DynamoDbClient dynamo) {
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

        // --- Parse body ---
        RegisterRequest req;
        try {
            String body = input.getBody();
            if (body == null || body.isBlank()) {
                return App.response(400, "{\"message\":\"Request body is empty\"}");
            }
            req = MAPPER.readValue(body, RegisterRequest.class);
        } catch (Exception e) {
            logger.log("Failed to parse request body: " + e.getMessage());
            return App.response(400, "{\"message\":\"Invalid JSON body\"}");
        }

        // --- Validate required fields ---
        String missing = findMissingFields(req);
        if (missing != null) {
            logger.log("Validation failed — missing fields: " + missing);
            return App.response(400, "{\"message\":\"Missing required fields: " + missing + "\"}");
        }

        // --- Build DynamoDB item ---
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("phoneNumber",     str(req.getPhoneNumber()));
        item.put("name",            str(req.getName()));
        item.put("lat",             num(req.getCoordinates().getLat()));
        item.put("lng",             num(req.getCoordinates().getLng()));
        item.put("emergencyContact",str(req.getEmergencyContact()));
        item.put("peopleInHouse",   num(req.getPeopleInHouse()));

        if (req.getIdPhoto() != null && !req.getIdPhoto().isBlank()) {
            item.put("idPhoto", str(req.getIdPhoto()));
        }

        // --- Write to DynamoDB ---
        try {
            getDynamo().putItem(PutItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .item(item)
                    .build());
        } catch (Exception e) {
            logger.log("DynamoDB write failed: " + e.getMessage());
            return App.response(500, "{\"message\":\"Failed to save user\"}");
        }

        logger.log("Registered user: " + req.getPhoneNumber());
        return App.response(200, "{\"message\":\"User registered successfully\"}");
    }

    /**
     * Returns a comma-separated list of missing required field names,
     * or null if all required fields are present.
     */
    private String findMissingFields(RegisterRequest req) {
        StringBuilder missing = new StringBuilder();

        if (isBlank(req.getPhoneNumber()))    append(missing, "phoneNumber");
        if (isBlank(req.getName()))           append(missing, "name");
        if (req.getCoordinates() == null
                || req.getCoordinates().getLat() == null
                || req.getCoordinates().getLng() == null) append(missing, "coordinates");
        if (isBlank(req.getEmergencyContact())) append(missing, "emergencyContact");
        if (req.getPeopleInHouse() == null)   append(missing, "peopleInHouse");

        return missing.isEmpty() ? null : missing.toString();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static void append(StringBuilder sb, String field) {
        if (!sb.isEmpty()) sb.append(", ");
        sb.append(field);
    }

    private static AttributeValue str(String v) {
        return AttributeValue.builder().s(v).build();
    }

    private static AttributeValue num(Number v) {
        return AttributeValue.builder().n(String.valueOf(v)).build();
    }
}
