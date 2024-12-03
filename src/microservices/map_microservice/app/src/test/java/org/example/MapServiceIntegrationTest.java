package org.example;


import application.RestMapServiceAPIImpl;
import application.ports.EventPublisher;
import application.ports.RestMapServiceAPI;
import domain.model.EBike;
import domain.model.EBikeState;
import domain.model.P2d;
import infrastructure.EBikeRepositoryImpl;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import infrastructure.adapter.BikeUpdateAdapter;
import infrastructure.adapter.MapServiceVerticle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(VertxExtension.class)
public class MapServiceIntegrationTest {

    private Vertx vertx;
    private WebClient client;
    private RestMapServiceAPI mapService;
    private static final int PORT = 8088;

    @BeforeEach
    void setUp(VertxTestContext testContext) {
        vertx = Vertx.vertx();
        client = WebClient.create(vertx);

        // Initialize components
        EBikeRepositoryImpl repository = new EBikeRepositoryImpl();
        EventPublisher eventPublisher = new TestEventPublisher();
        mapService = new RestMapServiceAPIImpl(repository, eventPublisher);

        // Deploy verticles
        vertx.deployVerticle(new MapServiceVerticle(mapService, "map-microservice"))
                .compose(id -> vertx.deployVerticle(new BikeUpdateAdapter(mapService)))
                .onComplete(ar -> {
                    if (ar.succeeded()) {
                        testContext.completeNow();
                    } else {
                        testContext.failNow(ar.cause());
                    }
                });
    }

    @AfterEach
    void tearDown(VertxTestContext testContext) {
        vertx.close().onComplete(testContext.succeeding(v -> testContext.completeNow()));
    }

    @Test
    void testUpdateEBike(VertxTestContext testContext) {
        JsonObject bikeJson = new JsonObject()
                .put("id", "bike1")
                .put("location", new JsonObject()
                        .put("x", 10.0)
                        .put("y", 20.0))
                .put("state", "AVAILABLE")
                .put("batteryLevel", 100);

        client.put(PORT, "localhost", "/updateEBike")
                .sendJsonObject(bikeJson)
                .onComplete(testContext.succeeding(response -> {
                    testContext.verify(() -> {
                        assertEquals(200, response.statusCode());
                        testContext.completeNow();
                    });
                }));
    }

    @Test
    void testGetHealth(VertxTestContext testContext) {
        client.get(PORT, "localhost", "/health")
                .send()
                .onComplete(testContext.succeeding(response -> {
                    testContext.verify(() -> {
                        assertEquals(200, response.statusCode());
                        assertEquals("OK", response.bodyAsString());
                        testContext.completeNow();
                    });
                }));
    }

//    @Test
//    void testNotifyStartRide(VertxTestContext testContext) {
//        // First, create a bike
//        EBike bike = new EBike("testBike", new P2d(0, 0), EBikeState.AVAILABLE, 100);
//        mapService.updateEBike(bike)
//                .thenCompose(v -> {
//                    JsonObject startRideJson = new JsonObject()
//                            .put("username", "testUser")
//                            .put("bikeName", "testBike");
//
//                    return vertx.executeBlocking(promise ->
//                            client.post(PORT, "localhost", "/notifyStartRide")
//                                    .sendJsonObject(startRideJson)
//                                    .onComplete(ar -> {
//                                        if (ar.succeeded()) {
//                                            promise.complete(ar.result());
//                                        } else {
//                                            promise.fail(ar.cause());
//                                        }
//                                    })
//                    );
//                })
//                .whenComplete((response, throwable) -> {
//                    if (throwable == null) {
//                        testContext.verify(() -> {
//                            assertEquals(200, response.statusCode());
//                            testContext.completeNow();
//                        });
//                    } else {
//                        testContext.failNow(throwable);
//                    }
//                });
//    }

    // Test implementation of EventPublisher for integration tests
    private static class TestEventPublisher implements EventPublisher {
        @Override
        public void publishBikesUpdate(List<EBike> bikes) {
            // No-op for testing
        }

        @Override
        public void publishUserBikesUpdate(List<EBike> bikes, String username) {
            // No-op for testing
        }

        @Override
        public void publishUserAvailableBikesUpdate(List<EBike> bikes) {
            // No-op for testing
        }

        @Override
        public void publishStopRide(String username) {

        }
    }
}