package org.opennms.poc.hs1384.client;

import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class SimpleReconnectStrategy implements Runnable, ReconnectStrategy {

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final ManagedChannel channel;
    private final Runnable onConnect;
    private final Runnable onDisconnect;
    private final int rate;
    private ScheduledFuture<?> reconnectTask;

    public SimpleReconnectStrategy(ManagedChannel channel, Runnable onConnect, Runnable onDisconnect, int rate) {
        this.channel = channel;
        this.onConnect = onConnect;
        this.onDisconnect = onDisconnect;
        this.rate = rate;
    }

    @Override
    public void activate() {
        onDisconnect.run();
        reconnectTask = executor.scheduleAtFixedRate(this, rate, rate, TimeUnit.MILLISECONDS);
    }

    @Override
    public void run() {
        ConnectivityState state = channel.getState(true);
        if (state == ConnectivityState.READY) {
            if (reconnectTask != null) {
                reconnectTask.cancel(false);
                onConnect.run();
                reconnectTask = null;
            }
        }
    }
}
