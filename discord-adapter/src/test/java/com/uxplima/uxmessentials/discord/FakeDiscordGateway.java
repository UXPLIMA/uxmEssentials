package com.uxplima.uxmessentials.discord;

import java.util.ArrayList;
import java.util.List;

/**
 * In-memory {@link DiscordGateway} for tests. Records every send instead of talking to Discord, so the
 * {@link NotificationForwarder} rules are verified with no live JDA connection (CLAUDE.md: "tests use a fake
 * gateway").
 */
final class FakeDiscordGateway implements DiscordGateway {

    record Sent(String channelId, String message) {}

    private final List<Sent> sent = new ArrayList<>();
    private boolean connected;

    @Override
    public void connect(String token) {
        connected = true;
    }

    @Override
    public void sendToChannel(String channelId, String message) {
        sent.add(new Sent(channelId, message));
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public void shutdown() {
        connected = false;
    }

    void setConnected(boolean value) {
        this.connected = value;
    }

    List<Sent> sent() {
        return List.copyOf(sent);
    }
}
