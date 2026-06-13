package net.pkhapps.idispatchx.cad.adapter.broadcast;

import net.pkhapps.idispatchx.cad.adapter.primary.websocket.dispatcher.DispatcherSession;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry of active WebSocket sessions.
 * <p>
 * Sessions are registered on WebSocket connect and unregistered on close or error.
 * The registry is used by {@link DispatcherBroadcastService} to obtain the set of
 * active dispatcher sessions for event broadcasting.
 */
public final class SessionRegistry {

    private final ConcurrentHashMap<String, DispatcherSession> dispatcherSessions = new ConcurrentHashMap<>();

    /**
     * Registers a dispatcher WebSocket session.
     *
     * @param session the session to register
     */
    public void registerDispatcherSession(DispatcherSession session) {
        dispatcherSessions.put(session.sessionId(), session);
    }

    /**
     * Removes the dispatcher session with the given ID.
     *
     * @param sessionId the session ID to remove
     */
    public void unregisterDispatcherSession(String sessionId) {
        dispatcherSessions.remove(sessionId);
    }

    /**
     * Returns an unmodifiable snapshot of all currently active dispatcher sessions.
     */
    public Collection<DispatcherSession> getDispatcherSessions() {
        return Collections.unmodifiableCollection(dispatcherSessions.values());
    }
}
