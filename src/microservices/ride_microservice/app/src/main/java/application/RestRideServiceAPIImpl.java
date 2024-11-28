package application;

import application.ports.EbikeCommunicationPort;
import application.ports.MapCommunicationPort;
import application.ports.RestRideServiceAPI;
import application.ports.UserCommunicationPort;
import domain.model.*;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class RestRideServiceAPIImpl implements RestRideServiceAPI {
    private final RideRepository rideRepository;
    private final Vertx vertx;
    private final EbikeCommunicationPort ebikeCommunicationAdapter;
    private final MapCommunicationPort mapCommunicationAdapter;
    private final UserCommunicationPort userCommunicationAdapter;

    public RestRideServiceAPIImpl(RideRepository rideRepository, Vertx vertx, EbikeCommunicationPort ebikeCommunicationAdapter, MapCommunicationPort mapCommunicationAdapter, UserCommunicationPort userCommunicationAdapter) {
        this.rideRepository = rideRepository;
        this.vertx = vertx;
        this.ebikeCommunicationAdapter = ebikeCommunicationAdapter;
        this.mapCommunicationAdapter = mapCommunicationAdapter;
        this.userCommunicationAdapter = userCommunicationAdapter;
        this.ebikeCommunicationAdapter.init();
        this.mapCommunicationAdapter.init();
        this.userCommunicationAdapter.init();
    }
    private CompletableFuture<EBike> checkEbike(String bikeId) {
        return ebikeCommunicationAdapter.getEbike(bikeId)
                .thenApply(ebikeJson -> {
                    if (ebikeJson == null) {
                        System.err.println("EBike not found");
                        return null;
                    }

                    JsonObject location = ebikeJson.getJsonObject("location");
                    return new EBike(
                            ebikeJson.getString("id"),
                            location.getDouble("x"),  // Get x from location object
                            location.getDouble("y"),  // Get y from location object
                            EBikeState.valueOf(ebikeJson.getString("state")),
                            ebikeJson.getInteger("batteryLevel")
                    );
                });
    }

    private CompletableFuture<User> checkUser(String userId) {
        return userCommunicationAdapter.getUser(userId)
                .thenApply(userJson -> {
                    if (userJson == null) {
                        System.err.println("User not found");
                        return null;
                    }

                    return new User(userJson.getString("id"), userJson.getInteger("balance"));
                });
    }

    @Override
    public CompletableFuture<Void> startRide(String userId, String bikeId) {
        CompletableFuture<EBike> ebikeFuture = checkEbike(bikeId);
        CompletableFuture<User> userFuture = checkUser(userId);

        return CompletableFuture.allOf(ebikeFuture, userFuture).thenCompose(v -> {
            try {
                EBike ebike = ebikeFuture.join();
                User user = userFuture.join();

                if (ebike == null || user == null) {
                    return CompletableFuture.failedFuture(new RuntimeException("EBike or User not found"));
                }

                Ride ride = new Ride("ride-" + userId + "-" + bikeId, user, ebike);
                rideRepository.addRide(ride);
                rideRepository.getRideSimulation(ride.getId()).startSimulation().whenComplete((result, throwable) -> {
                    if (throwable == null) {
                        mapCommunicationAdapter.notifyEndRide(bikeId, userId);
                    } else {
                        System.err.println("Error during ride simulation: " + throwable.getMessage());
                    }
                });
                mapCommunicationAdapter.notifyStartRide(bikeId, userId);
                return CompletableFuture.completedFuture(null);
            } catch (Exception e) {
                return CompletableFuture.failedFuture(e);
            }
        });
    }

    @Override
    public void stopRide(String userId) {
        RideSimulation rideSimulation = rideRepository.getRideSimulationByUserId(userId);
        if (rideSimulation != null) {
            rideSimulation.stopSimulationManually();
            ebikeCommunicationAdapter.sendUpdate(new JsonObject().put("id", rideSimulation.getRide().getEbike().getId()).put("state", rideSimulation.getRide().getEbike().getState().toString()));
            mapCommunicationAdapter.notifyEndRide(rideSimulation.getRide().getEbike().getId(), userId);
        }
    }
}