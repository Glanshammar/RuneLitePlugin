package com.main.utils;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import net.runelite.api.GameState;
import net.runelite.api.Client;

import java.time.Duration;
import java.time.Instant;
import java.util.function.BooleanSupplier;

@Slf4j
public class Waiter implements AutoCloseable {
    private static volatile Client client;
    private static volatile ClientThread clientThread;
    private final BooleanSupplier condition;
    private final Instant deadline;
    private final Runnable onSuccess;
    private final Runnable onTimeout;
    private final String label;

    private volatile boolean done = false;

    public Waiter(ClientThread clientThread, String label,
                  BooleanSupplier condition, Duration timeout,
                  Runnable onSuccess, Runnable onTimeout) {
        this.label = label;
        this.condition = condition;
        this.deadline = Instant.now().plus(timeout);
        this.onSuccess = onSuccess;
        this.onTimeout = onTimeout;

        clientThread.invokeLater(() -> {
            if (done) return true;
            if (condition.getAsBoolean()) {
                done = true;
                if (onSuccess != null) onSuccess.run();
                return true;
            }
            if (Instant.now().isAfter(deadline)) {
                done = true;
                log.warn("Waiter '{}' timed out after {}", label, timeout);
                if (onTimeout != null) onTimeout.run();
                return true;
            }
            return false;
        });
    }

    public static void waitFor(String label,
                               BooleanSupplier condition,
                               Runnable onSuccess,
                               Runnable onFailure,
                               Duration timeoutSeconds) {
        new Waiter(clientThread, label, condition, Duration.ofSeconds(timeoutSeconds.getSeconds()),
                onSuccess, onFailure);
    }

    public static void waitFor(String label,
                               boolean condition,
                               Runnable onSuccess,
                               Runnable onFailure,
                               Duration timeoutSeconds) {
        waitFor(label, () -> condition, onSuccess, onFailure, timeoutSeconds);
    }

    public boolean isDone() { return done; }

    public void cancel() { done = true; }

    @Override
    public void close() { cancel(); }
}