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

    private final RegisterHandler  registerHandler;
    private final UserHandler      userHandler;
    private final AlertHandler     alertHandler;
    private final GetAlertHandler  getAlertHandler;
    private final SafeHandler      safeHandler;

    /** Production constructor. */
    public App() {
        this.registerHandler  = new RegisterHandler();
        this.userHandler      = new UserHandler();
        this.alertHandler     = new AlertHandler();
        this.getAlertHandler  = new GetAlertHandler();
        this.safeHandler      = new SafeHandler();
    }

    /** Full test constructor — accepts pre-configured handlers. */
    App(RegisterHandler registerHandler, UserHandler userHandler,
        AlertHandler alertHandler, GetAlertHandler getAlertHandler,
        SafeHandler safeHandler) {
        this.registerHandler  = registerHandler;
        this.userHandler      = userHandler;
        this.alertHandler     = alertHandler;
        this.getAlertHandler  = getAlertHandler;
        this.safeHandler      = safeHandler;
    }

    /** Convenience test constructor for register + user + alert + getAlert tests. */
    App(RegisterHandler registerHandler, UserHandler userHandler,
        AlertHandler alertHandler, GetAlertHandler getAlertHandler) {
        this(registerHandler, userHandler, alertHandler, getAlertHandler, new SafeHandler());
    }

    /** Convenience test constructor for register + user + alert tests. */
    App(RegisterHandler registerHandler, UserHandler userHandler, AlertHandler alertHandler) {
        this(registerHandler, userHandler, alertHandler, new GetAlertHandler(), new SafeHandler());
    }

    /** Convenience test constructor for register + user tests. */
    App(RegisterHandler registerHandler, UserHandler userHandler) {
        this(registerHandler, userHandler, new AlertHandler(), new GetAlertHandler(), new SafeHandler());
    }

    /** Convenience test constructor for register-only tests. */
    App(RegisterHandler registerHandler) {
        this(registerHandler, new UserHandler(), new AlertHandler(), new GetAlertHandler(), new SafeHandler());
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        String method = input.getHttpMethod();
        String path   = input.getPath();

        if ("POST".equalsIgnoreCase(method) && "/register".equals(path)) {
            return registerHandler.handle(input, context);
        }

        if ("GET".equalsIgnoreCase(method) && path != null && path.matches("/user/.*")) {
            return userHandler.handle(input, context);
        }

        if ("POST".equalsIgnoreCase(method) && "/alert".equals(path)) {
            return alertHandler.handle(input, context);
        }

        if ("GET".equalsIgnoreCase(method) && path != null && path.matches("/alert/.*")) {
            return getAlertHandler.handle(input, context);
        }

        if ("POST".equalsIgnoreCase(method) && "/safe".equals(path)) {
            return safeHandler.handle(input, context);
        }

        // Handle CORS preflight for all routes
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return response(200, "");
        }

        return response(404, "{\"message\":\"Not found\"}");
    }

    static APIGatewayProxyResponseEvent response(int statusCode, String body) {
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withHeaders(Map.of(
                        "Content-Type", "application/json",
                        "Access-Control-Allow-Origin", "*",
                        "Access-Control-Allow-Methods", "GET, POST, OPTIONS",
                        "Access-Control-Allow-Headers", "Content-Type"
                ))
                .withBody(body);
    }
}
