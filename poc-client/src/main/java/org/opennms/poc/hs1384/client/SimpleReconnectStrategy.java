package org.opennms.poc.hs1384.client;

import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class SimpleReconnectStrategy implements Runnable, ReconnectStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(SimpleReconnectStrategy.class);

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final ManagedChannel channel;
    private final Runnable onConnect;
    private final Runnable onDisconnect;
    private final int rate;
    private final int maxReconnectAttempts;

    private ScheduledFuture<?> reconnectTask;
    private int reconnectAttemptCount;

    public SimpleReconnectStrategy(ManagedChannel channel, Runnable onConnect, Runnable onDisconnect, int rate, int maxReconnectAttempts) {
        this.channel = channel;
        this.onConnect = onConnect;
        this.onDisconnect = onDisconnect;
        this.rate = rate;
        this.maxReconnectAttempts = maxReconnectAttempts;
    }

    @Override
    public void activate() {
        onDisconnect.run();
        reconnectTask = executor.scheduleAtFixedRate(this, rate, rate, TimeUnit.MILLISECONDS);
    }

    @Override
    public void run() {
        if (reconnectAttemptCount >= maxReconnectAttempts) {
            LOG.warn("MAXIMUM RECONNECT ATTEMPTS EXCEEDED");

            if (reconnectTask != null) {
                reconnectTask.cancel(false);
                reconnectTask = null;
            }
        } else {
            reconnectAttemptCount++;
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
}
