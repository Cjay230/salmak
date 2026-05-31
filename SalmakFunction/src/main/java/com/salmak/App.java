package com.salmak;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import java.util.Map;

/**
 * Main Lambda entry point. Routes requests to the appropriate handler
 * based on HTTP method and path.
 */
public class App implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final RegisterHandler registerHandler;

    /** Production constructor. */
    public App() {
        this.registerHandler = new RegisterHandler();
    }

    /** Test constructor — accepts a pre-configured handler (e.g. with a stub DynamoDB client). */
    App(RegisterHandler registerHandler) {
        this.registerHandler = registerHandler;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        String method = input.getHttpMethod();
        String path   = input.getPath();

        if ("POST".equalsIgnoreCase(method) && "/register".equals(path)) {
            return registerHandler.handle(input, context);
        }

        return response(404, "{\"message\":\"Not found\"}");
    }

    static APIGatewayProxyResponseEvent response(int statusCode, String body) {
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withHeaders(Map.of("Content-Type", "application/json"))
                .withBody(body);
    }
}
