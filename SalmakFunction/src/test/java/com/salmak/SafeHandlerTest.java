package com.salmak;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import org.junit.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class SafeHandlerTest {

    // -----------------------------------------------------------------------
    // Stubs
    // -----------------------------------------------------------------------

    /** User exists in the table. */
    private static DynamoDbClient foundStub(AtomicBoolean updateCalled,
                                             AtomicReference<String> capturedExpression) {
        return new DynamoDbClient() {
            @Override
            public GetItemResponse getItem(GetItemRequest req) {
                return GetItemResponse.builder()
                        .item(Map.of(
                                "phoneNumber",  AttributeValue.builder().s("+96170000000").build(),
                                "activeAlert",  AttributeValue.builder().s("{\"lat\":33.8}").build()
                        ))
                        .build();
            }
            @Override
            public UpdateItemResponse updateItem(UpdateItemRequest req) {
                updateCalled.set(true);
                capturedExpression.set(req.updateExpression());
                return UpdateItemResponse.builder().build();
            }
            @Override public String serviceName() { return "stub"; }
            @Override public void close() {}
        };
    }

    /** User does not exist. */
    private static DynamoDbClient notFoundStub() {
        return new DynamoDbClient() {
            @Override
            public GetItemResponse getItem(GetItemRequest req) {
                return GetItemResponse.builder().build(); // empty
            }
            @Override public String serviceName() { return "stub"; }
            @Override public void close() {}
        };
    }

    /** Throws on every call. */
    private static DynamoDbClient errorStub() {
        return new DynamoDbClient() {
            @Override
            public GetItemResponse getItem(GetItemRequest req) {
                throw new RuntimeException("DynamoDB unavailable");
            }
            @Override public String serviceName() { return "stub"; }
            @Override public void close() {}
        };
    }

    /** GetItem succeeds but UpdateItem throws. */
    private static DynamoDbClient updateErrorStub() {
        return new DynamoDbClient() {
            @Override
            public GetItemResponse getItem(GetItemRequest req) {
                return GetItemResponse.builder()
                        .item(Map.of("phoneNumber", AttributeValue.builder().s("+96170000000").build()))
                        .build();
            }
            @Override
            public UpdateItemResponse updateItem(UpdateItemRequest req) {
                throw new RuntimeException("Update failed");
            }
            @Override public String serviceName() { return "stub"; }
            @Override public void close() {}
        };
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    @Test
    public void missingPhoneNumber_returns400() {
        SafeHandler handler = new SafeHandler(notFoundStub());
        APIGatewayProxyResponseEvent res = postSafe(handler, "{}");
        assertEquals(400, res.getStatusCode().intValue());
        assertTrue(res.getBody().contains("phoneNumber"));
    }

    @Test
    public void invalidJson_returns400() {
        SafeHandler handler = new SafeHandler(notFoundStub());
        APIGatewayProxyResponseEvent res = postSafe(handler, "not-json");
        assertEquals(400, res.getStatusCode().intValue());
        assertTrue(res.getBody().contains("Invalid JSON"));
    }

    @Test
    public void userNotFound_returns404() {
        SafeHandler handler = new SafeHandler(notFoundStub());
        APIGatewayProxyResponseEvent res = postSafe(handler, "{\"phoneNumber\":\"+96170000000\"}");
        assertEquals(404, res.getStatusCode().intValue());
        assertTrue(res.getBody().contains("User not found"));
    }

    @Test
    public void dynamoGetError_returns500() {
        SafeHandler handler = new SafeHandler(errorStub());
        APIGatewayProxyResponseEvent res = postSafe(handler, "{\"phoneNumber\":\"+96170000000\"}");
        assertEquals(500, res.getStatusCode().intValue());
    }

    @Test
    public void dynamoUpdateError_returns500() {
        SafeHandler handler = new SafeHandler(updateErrorStub());
        APIGatewayProxyResponseEvent res = postSafe(handler, "{\"phoneNumber\":\"+96170000000\"}");
        assertEquals(500, res.getStatusCode().intValue());
    }

    @Test
    public void userFound_returns200AndConfirmsEvacuation() {
        AtomicBoolean updateCalled = new AtomicBoolean(false);
        AtomicReference<String> capturedExpr = new AtomicReference<>();
        SafeHandler handler = new SafeHandler(foundStub(updateCalled, capturedExpr));

        APIGatewayProxyResponseEvent res = postSafe(handler, "{\"phoneNumber\":\"+96170000000\"}");

        assertEquals(200, res.getStatusCode().intValue());
        assertTrue(res.getBody().contains("Evacuation confirmed"));
        assertTrue("UpdateItem should have been called", updateCalled.get());
    }

    @Test
    public void updateExpression_usesREMOVE() {
        AtomicBoolean updateCalled = new AtomicBoolean(false);
        AtomicReference<String> capturedExpr = new AtomicReference<>();
        SafeHandler handler = new SafeHandler(foundStub(updateCalled, capturedExpr));

        postSafe(handler, "{\"phoneNumber\":\"+96170000000\"}");

        assertNotNull(capturedExpr.get());
        assertTrue("UpdateExpression must use REMOVE",
                capturedExpr.get().toUpperCase().startsWith("REMOVE"));
        assertTrue("UpdateExpression must target activeAlert",
                capturedExpr.get().contains("activeAlert"));
    }

    @Test
    public void corsHeaders_presentOnSuccess() {
        AtomicBoolean updateCalled = new AtomicBoolean(false);
        AtomicReference<String> capturedExpr = new AtomicReference<>();
        SafeHandler handler = new SafeHandler(foundStub(updateCalled, capturedExpr));

        APIGatewayProxyResponseEvent res = postSafe(handler, "{\"phoneNumber\":\"+96170000000\"}");

        assertNotNull(res.getHeaders());
        assertEquals("*", res.getHeaders().get("Access-Control-Allow-Origin"));
    }

    @Test
    public void corsHeaders_presentOn404() {
        SafeHandler handler = new SafeHandler(notFoundStub());
        APIGatewayProxyResponseEvent res = postSafe(handler, "{\"phoneNumber\":\"+96170000000\"}");
        assertNotNull(res.getHeaders());
        assertEquals("*", res.getHeaders().get("Access-Control-Allow-Origin"));
    }

    @Test
    public void routingViaApp_postSafe_success() {
        AtomicBoolean updateCalled = new AtomicBoolean(false);
        AtomicReference<String> capturedExpr = new AtomicReference<>();

        App app = new App(
                new RegisterHandler(noOpDynamo()),
                new UserHandler(noOpDynamo()),
                new AlertHandler(noOpDynamo()),
                new GetAlertHandler(noOpDynamo()),
                new SafeHandler(foundStub(updateCalled, capturedExpr))
        );

        APIGatewayProxyRequestEvent req = new APIGatewayProxyRequestEvent()
                .withHttpMethod("POST")
                .withPath("/safe")
                .withBody("{\"phoneNumber\":\"+96170000000\"}");

        APIGatewayProxyResponseEvent res = app.handleRequest(req, null);
        assertEquals(200, res.getStatusCode().intValue());
        assertTrue(res.getBody().contains("Evacuation confirmed"));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private APIGatewayProxyResponseEvent postSafe(SafeHandler handler, String body) {
        APIGatewayProxyRequestEvent req = new APIGatewayProxyRequestEvent()
                .withHttpMethod("POST")
                .withPath("/safe")
                .withBody(body);
        return handler.handle(req, null);
    }

    private static DynamoDbClient noOpDynamo() {
        return new DynamoDbClient() {
            @Override public String serviceName() { return "noop"; }
            @Override public void close() {}
        };
    }
}
