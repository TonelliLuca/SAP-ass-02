package application;

import application.ports.UserServiceAPI;
import application.ports.UserRepository;

import domain.model.User;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class UserServiceImpl implements UserServiceAPI {

    private final UserRepository repository;

    public UserServiceImpl(UserRepository repository) {
        this.repository = repository;
    }

    @Override
    public CompletableFuture<JsonObject> signIn(String username) {
        return repository.findByUsername(username).thenApply(user -> {
            if (user.isPresent()) {
                return user.get();
            } else {
                System.out.println("User not found");
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<JsonObject> signUp(String username, User.UserType type) {
        int credit = 100;
        JsonObject user = new JsonObject()
                .put("username", username)
                .put("type", type.toString())
                .put("credit", credit);

        return repository.save(user).thenApply(v -> user);
    }

    @Override
    public CompletableFuture<Optional<JsonObject>> getUserByUsername(String username) {
        return repository.findByUsername(username);
    }

    @Override
    public CompletableFuture<JsonObject> updateUser(JsonObject user) {
        String username = user.getString("username");
        return repository.findByUsername(username).thenCompose(optionalUser -> {
            if (optionalUser.isPresent()) {
                JsonObject existingUser = optionalUser.get();
                if (user.containsKey("credit")) {
                    int newCredit = user.getInteger("credit");
                    existingUser.put("credit", newCredit);
                }
                return repository.update(existingUser).thenApply(v -> existingUser);
            } else {
                throw new RuntimeException("User not found");
            }
        });
    }

    @Override
    public CompletableFuture<JsonObject> rechargeCredit(String username, int creditToAdd) {
        return repository.findByUsername(username).thenCompose(optionalUser -> {
            if (optionalUser.isPresent()) {
                JsonObject user = optionalUser.get();
                int currentCredit = user.getInteger("credit");
                user.put("credit", currentCredit + creditToAdd);
                return repository.update(user).thenApply(v -> user);
            }
            return CompletableFuture.completedFuture(null);
        });
    }

    @Override
    public CompletableFuture<JsonObject> decreaseCredit(String username, int creditToDecrease) {
        return repository.findByUsername(username).thenCompose(optionalUser -> {
            if (optionalUser.isPresent()) {
                JsonObject user = optionalUser.get();
                int newCredit = Math.max(user.getInteger("credit") - creditToDecrease, 0);
                user.put("credit", newCredit);
                return repository.update(user).thenApply(v -> user);
            }
            return CompletableFuture.completedFuture(null);
        });
    }

    @Override
    public CompletableFuture<JsonArray> getAllUsers() {
        return repository.findAll();
    }
}