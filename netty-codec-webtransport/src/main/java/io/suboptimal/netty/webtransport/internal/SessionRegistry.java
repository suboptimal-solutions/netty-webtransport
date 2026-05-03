package io.suboptimal.netty.webtransport.internal;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps session IDs to active {@link DefaultWebTransportSession} instances on a single QUIC
 * connection.
 *
 * <p>Lookups are on the hot path (every datagram, every new stream), so this is a simple
 * ConcurrentHashMap. All mutations happen on the connection's event loop, but concurrent reads are
 * safe.
 */
public final class SessionRegistry {

    private final Map<Long, DefaultWebTransportSession> sessions = new ConcurrentHashMap<>();

    public void register(DefaultWebTransportSession session) {
        sessions.put(session.sessionId(), session);
    }

    public DefaultWebTransportSession get(long sessionId) {
        return sessions.get(sessionId);
    }

    public DefaultWebTransportSession remove(long sessionId) {
        return sessions.remove(sessionId);
    }

    public Collection<DefaultWebTransportSession> all() {
        return sessions.values();
    }

    public boolean isEmpty() {
        return sessions.isEmpty();
    }

    public int size() {
        return sessions.size();
    }
}
