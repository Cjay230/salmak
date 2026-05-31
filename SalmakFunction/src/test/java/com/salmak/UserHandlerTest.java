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

public class UserHandlerTest {

    // -----------------------------------------------------------------------
    // Stubs
    // -----------------------------------------------------------------------

    /** Returns a found user item. */
    private static DynamoDbClient foundStub() {
        return new DynamoDbClient() {
            @Override
            public GetItemResponse getItem(GetItemRequest req) {
                return GetItemResponse.builder()
                        .item(Map.of(
                                "phoneNumber",     AttributeValue.builder().s("+96170000000").build(),
                                "name",            AttributeValue.builder().s("Ali").build(),
                                "emergencyContact",AttributeValue.builder().s("+9611234567").build(),
                                "peopleInHouse",   AttributeValue.builder().n("3").build(),
                                "lat",             AttributeValue.builder().n("33.8938").build(),
                                "lng",             AttributeValue.builder().n("35.5018").build()
                        ))
                        .build();
            }
            @Override public String serviceName() { return "stub"; }
            @Override public void close() {}
        };
    }

    /** Returns an empty (not found) response. */
    private static DynamoDbClient notFoundStub() {
        return new DynamoDbClient() {
            @Override
            public GetItemResponse getItem(GetItemRequest req) {
                return GetItemResponse.builder().build(); // no item
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

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    @Test
    public void missingPhoneNumber_returns400() {
        UserHandler handler = new UserHandler(notFoundStub());
        APIGatewayProxyRequestEvent req = new APIGatewayProxyRequestEvent()
                .withHttpMethod("GET")
                .withPath("/user/");
        // no path parameters map

        APIGatewayProxyResponseEvent res = handler.handle(req, null);

        assertEquals(400, res.getStatusCode().intValue());
        assertTrue(res.getBody().contains("Missing or invalid phoneNumber"));
    }

    @Test
    public void blankPhoneNumber_returns400() {
        UserHandler handler = new UserHandler(notFoundStub());
        APIGatewayProxyRequestEvent req = new APIGatewayProxyRequestEvent()
                .withHttpMethod("GET")
                .withPath("/user/ ")
                .withPathParameters(Map.of("phoneNumber", "  "));

        APIGatewayProxyResponseEvent res = handler.handle(req, null);

        assertEquals(400, res.getStatusCode().intValue());
    }

    @Test
    public void userNotFound_returns200WithRegisteredFalse() {
        UserHandler handler = new UserHandler(notFoundStub());
        APIGatewayProxyRequestEvent req = new APIGatewayProxyRequestEvent()
                .withHttpMethod("GET")
                .withPath("/user/+96170000000")
                .withPathParameters(Map.of("phoneNumber", "+96170000000"));

        APIGatewayProxyResponseEvent res = handler.handle(req, null);

        assertEquals(200, res.getStatusCode().intValue());
        assertTrue(res.getBody().contains("\"registered\":false"));
    }

    @Test
    public void userFound_returns200WithRegisteredTrueAndData() {
        UserHandler handler = new UserHandler(foundStub());
        APIGatewayProxyRequestEvent req = new APIGatewayProxyRequestEvent()
                .withHttpMethod("GET")
                .withPath("/user/+96170000000")
                .withPathParameters(Map.of("phoneNumber", "+96170000000"));

        APIGatewayProxyResponseEvent res = handler.handle(req, null);

        assertEquals(200, res.getStatusCode().intValue());
        String body = res.getBody();
        assertTrue(body.contains("\"registered\":true"));
        assertTrue(body.contains("\"phoneNumber\":\"+96170000000\""));
        assertTrue(body.contains("\"name\":\"Ali\""));
        assertTrue(body.contains("\"lat\":33.8938"));
        assertTrue(body.contains("\"lng\":35.5018"));
    }

    @Test
    public void dynamoError_returns500() {
        UserHandler handler = new UserHandler(errorStub());
        APIGatewayProxyRequestEvent req = new APIGatewayProxyRequestEvent()
                .withHttpMethod("GET")
                .withPath("/user/+96170000000")
                .withPathParameters(Map.of("phoneNumber", "+96170000000"));

        APIGatewayProxyResponseEvent res = handler.handle(req, null);

        assertEquals(500, res.getStatusCode().intValue());
    }

    @Test
    public void routingViaApp_getUser_found() {
        App app = new App(
                new RegisterHandler(new DynamoDbClient() {
                    @Override public String serviceName() { return "stub"; }
                    @Override public void close() {}
                }),
                new UserHandler(foundStub())
        );

        APIGatewayProxyRequestEvent req = new APIGatewayProxyRequestEvent()
                .withHttpMethod("GET")
                .withPath("/user/+96170000000")
                .withPathParameters(Map.of("phoneNumber", "+96170000000"));

        APIGatewayProxyResponseEvent res = app.handleRequest(req, null);

        assertEquals(200, res.getStatusCode().intValue());
        assertTrue(res.getBody().contains("\"registered\":true"));
    }
}
