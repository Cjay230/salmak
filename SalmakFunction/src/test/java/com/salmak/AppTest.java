package com.salmak;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import org.junit.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AppTest {

    // -----------------------------------------------------------------------
    // Minimal no-op DynamoDB stub — never touches AWS
    // -----------------------------------------------------------------------
    private static final DynamoDbClient STUB_DYNAMO = new DynamoDbClient() {
        @Override
        public PutItemResponse putItem(PutItemRequest req) {
            return PutItemResponse.builder().build();
        }
        @Override
        public String serviceName() { return "dynamodb-stub"; }
        @Override
        public void close() {}
    };

    private App appWithStub() {
        return new App(new RegisterHandler(STUB_DYNAMO));
    }

    // -----------------------------------------------------------------------
    // Routing
    // -----------------------------------------------------------------------

    @Test
    public void unknownRoute_returns404() {
        APIGatewayProxyRequestEvent req = new APIGatewayProxyRequestEvent()
                .withHttpMethod("GET")
                .withPath("/unknown");

        APIGatewayProxyResponseEvent res = appWithStub().handleRequest(req, null);

        assertEquals(404, res.getStatusCode().intValue());
    }

    // -----------------------------------------------------------------------
    // POST /register — validation failures (400)
    // -----------------------------------------------------------------------

    @Test
    public void register_missingPhoneNumber_returns400() {
        String body = """
                {"name":"Ali","coordinates":{"lat":33.8,"lng":35.5},
                 "emergencyContact":"+9611234","peopleInHouse":2}
                """;
        APIGatewayProxyResponseEvent res = postRegister(body);

        assertEquals(400, res.getStatusCode().intValue());
        assertTrue(res.getBody().contains("phoneNumber"));
    }

    @Test
    public void register_missingName_returns400() {
        String body = """
                {"phoneNumber":"+96170000000","coordinates":{"lat":33.8,"lng":35.5},
                 "emergencyContact":"+9611234","peopleInHouse":2}
                """;
        APIGatewayProxyResponseEvent res = postRegister(body);

        assertEquals(400, res.getStatusCode().intValue());
        assertTrue(res.getBody().contains("name"));
    }

    @Test
    public void register_missingCoordinates_returns400() {
        String body = """
                {"phoneNumber":"+96170000000","name":"Ali",
                 "emergencyContact":"+9611234","peopleInHouse":2}
                """;
        APIGatewayProxyResponseEvent res = postRegister(body);

        assertEquals(400, res.getStatusCode().intValue());
        assertTrue(res.getBody().contains("coordinates"));
    }

    @Test
    public void register_missingEmergencyContact_returns400() {
        String body = """
                {"phoneNumber":"+96170000000","name":"Ali",
                 "coordinates":{"lat":33.8,"lng":35.5},"peopleInHouse":2}
                """;
        APIGatewayProxyResponseEvent res = postRegister(body);

        assertEquals(400, res.getStatusCode().intValue());
        assertTrue(res.getBody().contains("emergencyContact"));
    }

    @Test
    public void register_missingPeopleInHouse_returns400() {
        String body = """
                {"phoneNumber":"+96170000000","name":"Ali",
                 "coordinates":{"lat":33.8,"lng":35.5},"emergencyContact":"+9611234"}
                """;
        APIGatewayProxyResponseEvent res = postRegister(body);

        assertEquals(400, res.getStatusCode().intValue());
        assertTrue(res.getBody().contains("peopleInHouse"));
    }

    @Test
    public void register_invalidJson_returns400() {
        APIGatewayProxyResponseEvent res = postRegister("not-json");

        assertEquals(400, res.getStatusCode().intValue());
        assertTrue(res.getBody().contains("Invalid JSON"));
    }

    // -----------------------------------------------------------------------
    // POST /register — success (200)
    // -----------------------------------------------------------------------

    @Test
    public void register_allRequiredFields_returns200() {
        String body = """
                {"phoneNumber":"+96170000000","name":"Ali",
                 "coordinates":{"lat":33.8938,"lng":35.5018},
                 "emergencyContact":"+9611234567","peopleInHouse":3}
                """;
        APIGatewayProxyResponseEvent res = postRegister(body);

        assertEquals(200, res.getStatusCode().intValue());
        assertTrue(res.getBody().contains("registered successfully"));
    }

    @Test
    public void register_withOptionalIdPhoto_returns200() {
        String body = """
                {"phoneNumber":"+96170000001","name":"Sara",
                 "coordinates":{"lat":33.8938,"lng":35.5018},
                 "emergencyContact":"+9611234567","peopleInHouse":1,
                 "idPhoto":"data:image/png;base64,abc123"}
                """;
        APIGatewayProxyResponseEvent res = postRegister(body);

        assertEquals(200, res.getStatusCode().intValue());
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private APIGatewayProxyResponseEvent postRegister(String body) {
        APIGatewayProxyRequestEvent req = new APIGatewayProxyRequestEvent()
                .withHttpMethod("POST")
                .withPath("/register")
                .withBody(body);
        return appWithStub().handleRequest(req, null);
    }
}
