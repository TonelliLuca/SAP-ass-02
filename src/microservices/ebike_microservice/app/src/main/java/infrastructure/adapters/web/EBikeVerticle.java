package infrastructure.adapters.web;

import application.ports.EurekaRegistrationPort;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.UUID;

public class EBikeVerticle extends AbstractVerticle {
    private static final Logger logger = LoggerFactory.getLogger(EBikeVerticle.class);

    private final RESTEBikeController controller;
    private final EurekaRegistrationPort eurekaRegistration;
    private final String applicationName;
    private final int port;
    private final String instanceId;

    public EBikeVerticle(
            RESTEBikeController controller,
            EurekaRegistrationPort eurekaRegistration,
            String applicationName,
            int port
    ) {
        this.controller = controller;
        this.eurekaRegistration = eurekaRegistration;
        this.applicationName = applicationName;
        this.port = port;
        this.instanceId = applicationName + ":" + UUID.randomUUID().toString();
    }

    @Override
    public void start(Promise<Void> startPromise) {
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());

        // Configure routes
        controller.configureRoutes(router);

        // Start HTTP server
        vertx.createHttpServer()
                .requestHandler(router)
                .listen(port)
                .onSuccess(server -> {
                    registerWithEureka();
                    startHeartbeat();
                    logger.info("HTTP server started on port {}", port);
                    startPromise.complete();
                })
                .onFailure(startPromise::fail);
    }

    private void registerWithEureka() {
        try {
            String hostName = InetAddress.getLocalHost().getHostName();
            eurekaRegistration.register(applicationName, hostName, port)
                    .onSuccess(v -> logger.info("Successfully registered with Eureka"))
                    .onFailure(err -> logger.error("Failed to register with Eureka", err));
        } catch (Exception e) {
            logger.error("Failed to get hostname", e);
        }
    }

    private void startHeartbeat() {
        vertx.setPeriodic(30000, id -> {
            eurekaRegistration.sendHeartbeat(applicationName, instanceId)
                    .onFailure(err -> logger.warn("Failed to send heartbeat to Eureka: {}", err.getMessage()));
        });
    }

    @Override
    public void stop(Promise<Void> stopPromise) {
        eurekaRegistration.deregister(applicationName, instanceId)
                .onComplete(ar -> stopPromise.complete());
    }
}
