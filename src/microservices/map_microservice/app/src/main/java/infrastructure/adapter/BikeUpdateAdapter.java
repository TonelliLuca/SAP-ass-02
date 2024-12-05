package infrastructure.adapter;

import application.ports.RestMapServiceAPI;
import domain.model.EBike;
import domain.model.EBikeFactory;
import domain.model.EBikeState;
import infrastructure.MetricsManager;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

import java.util.List;
import java.util.stream.Collectors;

public class BikeUpdateAdapter extends AbstractVerticle {

    private final RestMapServiceAPI mapService;
    private final int port;
    private final MetricsManager metricsManager;

    public BikeUpdateAdapter(RestMapServiceAPI mapService) {
        this.mapService = mapService;
        this.port = EnvUtils.getEnvOrDefaultInt("ADAPTER_EBIKE_PORT", 8082);
        this.metricsManager = MetricsManager.getInstance();
    }

    @Override
    public void start() {
        HttpServer server = vertx.createHttpServer();
        Router router = Router.router(vertx);

        // Enable request body handling for PUT/POST requests
        router.route().handler(BodyHandler.create());

        router.get("/health").handler(ctx -> ctx.response().setStatusCode(200).end("OK"));

        // Expose Prometheus metrics
        router.get("/metrics").handler(ctx -> ctx.response()
                .putHeader("Content-Type", "text/plain")
                .end(metricsManager.getMetrics()));

        router.put("/updateEBike").handler(ctx -> {
            metricsManager.incrementMethodCounter("updateEBike");
            var timer = metricsManager.startTimer();

            JsonObject body = ctx.body().asJsonObject();
            try {
                EBike bike = createEBikeFromJson(body);
                // Process the update request
                mapService.updateEBike(bike)
                        .thenAccept(v -> {
                                    ctx.response().setStatusCode(200).end("EBike updated successfully");
                                    metricsManager.recordTimer(timer, "updateEBike");
                                }
                        )
                        .exceptionally(ex -> {
                            ctx.response().setStatusCode(500).end("Failed to update EBike: " + ex.getMessage());
                            metricsManager.recordError(timer, "updateEBike", ex);
                            return null;
                        });
            } catch (Exception e) {
                System.err.println("Invalid input data: " + e.getMessage());
                ctx.response().setStatusCode(400).end("Invalid input data: " + e.getMessage());
                metricsManager.recordError(timer, "updateEBike", e);
            }
        });

        router.put("/updateEBikes").handler(ctx -> {
            metricsManager.incrementMethodCounter("updateEBikes");
            var timer = metricsManager.startTimer();

            JsonArray body = ctx.body().asJsonArray();
            System.out.println("Received update request for " + body.size() + " EBikes");
            System.out.println(body.encodePrettily());
            try {
                List<EBike> bikes = body.stream()
                        .map(obj -> (JsonObject) obj)
                        .map(this::createEBikeFromJson)
                        .collect(Collectors.toList());

                // Process the update request
                mapService.updateEBikes(bikes)
                        .thenAccept(v -> {
                            ctx.response().setStatusCode(200).end("EBikes updated successfully");
                            metricsManager.recordTimer(timer, "updateEBikes");
                        })
                        .exceptionally(ex -> {
                            ctx.response().setStatusCode(500).end("Failed to update EBikes: " + ex.getMessage());
                            metricsManager.recordError(timer, "updateEBikes", ex);
                            return null;
                        });
            } catch (Exception e) {
                System.err.println("Invalid input data: " + e.getMessage());
                ctx.response().setStatusCode(400).end("Invalid input data: " + e.getMessage());
                metricsManager.recordError(timer, "updateEBikes", e);
            }
        });

        // Start the server on the specified port
        server.requestHandler(router).listen(port, result -> {
            if (result.succeeded()) {
                System.out.println("BikeUpdateAdapter is running on port " + port);
            } else {
                System.err.println("Failed to start BikeUpdateAdapter: " + result.cause().getMessage());
            }
        });
    }

    private EBike createEBikeFromJson(JsonObject body) {
        String bikeName = body.getString("id");
        JsonObject location = body.getJsonObject("location");
        double x = location.getDouble("x");
        double y = location.getDouble("y");
        EBikeState state = EBikeState.valueOf(body.getString("state"));
        int batteryLevel = body.getInteger("batteryLevel");

        EBikeFactory factory = EBikeFactory.getInstance();
        return factory.createEBike(bikeName, (float) x, (float) y, state, batteryLevel);
    }

}
