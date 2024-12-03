package application;

import application.ports.RestMapServiceAPI;

import domain.model.EBike;
import application.ports.EventPublisher;
import application.ports.EBikeRepository;
import domain.model.EBikeState;
import domain.model.User;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

public class RestMapServiceAPIImpl implements RestMapServiceAPI {

    private final EBikeRepository bikeRepository;
    private final EventPublisher eventPublisher;
    private final List<String> registeredUsers = new CopyOnWriteArrayList<>();

    public RestMapServiceAPIImpl(EBikeRepository bikeRepository, EventPublisher eventPublisher) {
        this.bikeRepository = bikeRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public CompletableFuture<Void> updateEBikes(List<EBike> bikes) {

        return CompletableFuture.allOf(bikes.stream()
                .map(bikeRepository::saveBike)
                .toArray(CompletableFuture[]::new))
                .thenAccept(v -> {
                    //Publish the update on the global endpoint
                    var bikesInRepo = bikeRepository.getAllBikes().join();
                    eventPublisher.publishBikesUpdate(bikesInRepo);
                    var availableBikes = bikesInRepo.stream()
                            .filter(bike -> bike.getState().equals(EBikeState.AVAILABLE))
                            .toList();
                    //List<EBike> availableBikes = bikeRepository.getAvailableBikes().join();
                    var usersWithAssignedBikes = bikeRepository.getAllUsersWithAssignedBikes().join();
                    if(!usersWithAssignedBikes.isEmpty()){
                        usersWithAssignedBikes.forEach(username -> {
                            List<EBike> userBikes = new ArrayList<>(bikesInRepo.stream()
                                    .filter(bike -> {
                                        String assignedUser = bikeRepository.isBikeAssigned(bike).join();
                                        return assignedUser != null && assignedUser.equals(username);
                                    }).toList());
                            userBikes.addAll(availableBikes);
                            eventPublisher.publishUserBikesUpdate(userBikes, username);
                        });
                    }
                    else{
                        eventPublisher.publishUserAvailableBikesUpdate(availableBikes);
                    }

                });
    }

    //TODO: make a private method for the two methods
    private void publishBikeForUser(){

    }

    @Override
    public CompletableFuture<Void> updateEBike(EBike bike) {

        return bikeRepository.saveBike(bike)
                .thenAccept(v -> {
                    var bikesInRepo = bikeRepository.getAllBikes().join();
                    eventPublisher.publishBikesUpdate(bikesInRepo);
                    System.out.println("Bikes in repo: " + bikesInRepo);
                    List<EBike> availableBikes = bikeRepository.getAvailableBikes().join();
                    var usersWithAssignedBikes = bikeRepository.getAllUsersWithAssignedBikes().join();
                    if (!usersWithAssignedBikes.isEmpty()) {
                        usersWithAssignedBikes.forEach(username -> {
                            List<EBike> userBikes = new ArrayList<>(bikesInRepo.stream()
                                    .filter(b -> {
                                        String assignedUser = bikeRepository.isBikeAssigned(b).join();
                                        return assignedUser != null && assignedUser.equals(username);
                                    }).toList());
                            userBikes.addAll(availableBikes);
                            eventPublisher.publishUserBikesUpdate(userBikes, username);
                        });
                        registeredUsers.stream()
                                .filter(user -> !usersWithAssignedBikes.contains(user))
                                .forEach(user -> eventPublisher.publishUserBikesUpdate(availableBikes, user));
                    } else {
                        eventPublisher.publishUserAvailableBikesUpdate(availableBikes);
                    }
                });
    }

    @Override
    public CompletableFuture<Void> notifyStartRide(String username, String bikeName) {
         return bikeRepository.getBike(bikeName)
                 .thenCompose(bike -> bikeRepository.assignBikeToUser(username, bike))
                 .thenAccept(v -> {
                     List<EBike> availableBikes = bikeRepository.getAvailableBikes().join();
                     eventPublisher.publishUserAvailableBikesUpdate(availableBikes);
                 });
    }


    @Override
    public CompletableFuture<Void> notifyStopRide(String username, String bikeName) {
        return bikeRepository.getBike(bikeName)
                .thenCompose(bike -> bikeRepository.unassignBikeFromUser(username, bike))
                .thenAccept(v -> {
                    List<EBike> availableBikes = bikeRepository.getAvailableBikes().join();
                    eventPublisher.publishUserAvailableBikesUpdate(availableBikes);
                    eventPublisher.publishStopRide(username);
                });
    }

    @Override
    public void getAllBikes() {
        bikeRepository.getAllBikes().thenAccept(eventPublisher::publishBikesUpdate);
    }

    @Override
    public void getAllBikes(String username) {
        List<EBike> availableBikes = bikeRepository.getAvailableBikes().join();
        List<EBike> userBikes = bikeRepository.getAllBikes(username).join();
        if(!userBikes.isEmpty()){
            availableBikes.addAll(userBikes);
            eventPublisher.publishUserBikesUpdate(availableBikes, username);
        }
        else{
            System.out.println("No bikes assigned to user: " + username);
            System.out.println("Available bikes: " + availableBikes);
            eventPublisher.publishUserAvailableBikesUpdate(availableBikes);
        }

    }

    @Override
    public void registerUser(String username) {
        registeredUsers.add(username);
    }

}
