package com.salmak;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import org.junit.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;

import java.util.Map;

import static org.junit.Assert.*;

public class GetAlertHandlerTest {

    // -----------------------------------------------------------------------
    // Stubs
    // -----------------------------------------------------------------------

    /** User exists with an activeAlert field. */
    private static DynamoDbClient withAlertStub() {
        return stub(GetItemResponse.builder()
                .item(Map.of(
                        "phoneNumber", AttributeValue.builder().s("+96170000000").build(),
                        "activeAlert", AttributeValue.builder()
                                .s("{\"lat\":33.8547,\"lng\":35.4942,\"timestamp\":\"2026-05-31T10:00:00Z\"}")
                                .build()
                ))
                .build());
    }

    /** User exists but has no activeAlert field. */
    private static DynamoDbClient noAlertStub() {
        return stub(GetItemResponse.builder()
                .item(Map.of(
                        "phoneNumber", AttributeValue.builder().s("+96170000000").build(),
                        "name",        AttributeValue.builder().s("Ali").build()
                ))
                .build());
    }

    /** User does not exist — empty response. */
    private static DynamoDbClient notFoundStub() {
        return stub(GetItemResponse.builder().build());
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

    private static DynamoDbClient stub(GetItemResponse response) {
        return new DynamoDbClient() {
            @Override
            public GetItemResponse getItem(GetItemRequest req) { return response; }
            @Override public String serviceName() { return "stub"; }
            @Override public void close() {}
        };
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    @Test
    public void missingPhoneNumber_returns400() {
        GetAlertHandler handler = new GetAlertHandler(notFoundStub());
        APIGatewayProxyResponseEvent res = getAlert(handler, null);
        assertEquals(400, res.getStatusCode().intValue());
        assertTrue(res.getBody().contains("Missing or invalid phoneNumber"));
    }

    @Test
    public void blankPhoneNumber_returns400() {
        GetAlertHandler handler = new GetAlertHandler(notFoundStub());
        APIGatewayProxyResponseEvent res = getAlertWithParam(handler, "   ");
        assertEquals(400, res.getStatusCode().intValue());
    }

    @Test
    public void userNotFound_returns404() {
        GetAlertHandler handler = new GetAlertHandler(notFoundStub());
        APIGatewayProxyResponseEvent res = getAlert(handler, "+96170000000");
        assertEquals(404, res.getStatusCode().intValue());
        assertTrue(res.getBody().contains("User not found"));
    }

    @Test
    public void userExistsNoAlert_returns200WithHasAlertFalse() {
        GetAlertHandler handler = new GetAlertHandler(noAlertStub());
        APIGatewayProxyResponseEvent res = getAlert(handler, "+96170000000");
        assertEquals(200, res.getStatusCode().intValue());
        assertTrue(res.getBody().contains("\"hasAlert\":false"));
        assertFalse(res.getBody().contains("\"hasAlert\":true"));
    }

    @Test
    public void userExistsWithAlert_returns200WithHasAlertTrueAndDetails() {
        GetAlertHandler handler = new GetAlertHandler(withAlertStub());
        APIGatewayProxyResponseEvent res = getAlert(handler, "+96170000000");
        assertEquals(200, res.getStatusCode().intValue());
        String body = res.getBody();
        assertTrue(body.contains("\"hasAlert\":true"));
        assertTrue(body.contains("\"lat\""));
        assertTrue(body.contains("\"lng\""));
        assertTrue(body.contains("\"timestamp\""));
    }

    @Test
    public void dynamoError_returns500() {
        GetAlertHandler handler = new GetAlertHandler(errorStub());
        APIGatewayProxyResponseEvent res = getAlert(handler, "+96170000000");
        assertEquals(500, res.getStatusCode().intValue());
    }

    @Test
    public void routingViaApp_getAlert_hasAlert() {
        App app = new App(
                new RegisterHandler(noOpDynamo()),
                new UserHandler(noOpDynamo()),
                new AlertHandler(noOpDynamo()),
                new GetAlertHandler(withAlertStub())
        );

        APIGatewayProxyRequestEvent req = new APIGatewayProxyRequestEvent()
                .withHttpMethod("GET")
                .withPath("/alert/+96170000000")
                .withPathParameters(Map.of("phoneNumber", "+96170000000"));

        APIGatewayProxyResponseEvent res = app.handleRequest(req, null);
        assertEquals(200, res.getStatusCode().intValue());
        assertTrue(res.getBody().contains("\"hasAlert\":true"));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private APIGatewayProxyResponseEvent getAlert(GetAlertHandler handler, String phoneNumber) {
        return getAlertWithParam(handler, phoneNumber);
    }

    private APIGatewayProxyResponseEvent getAlertWithParam(GetAlertHandler handler, String phoneNumber) {
        APIGatewayProxyRequestEvent req = new APIGatewayProxyRequestEvent()
                .withHttpMethod("GET")
                .withPath("/alert/" + (phoneNumber != null ? phoneNumber : ""));
        if (phoneNumber != null) {
            req.withPathParameters(Map.of("phoneNumber", phoneNumber));
        }
        return handler.handle(req, null);
    }

    private static DynamoDbClient noOpDynamo() {
        return new DynamoDbClient() {
            @Override public String serviceName() { return "noop"; }
            @Override public void close() {}
        };
    }
}
