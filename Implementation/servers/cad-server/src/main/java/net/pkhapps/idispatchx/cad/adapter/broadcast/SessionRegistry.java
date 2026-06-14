package net.pkhapps.idispatchx.cad.adapter.broadcast;

import net.pkhapps.idispatchx.cad.adapter.primary.websocket.dispatcher.DispatcherSession;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry of active WebSocket sessions.
 * <p>
 * Sessions are registered on WebSocket connect and unregistered on close or error.
 * The registry is used by {@link DispatcherBroadcastService} to obtain the set of
 * active dispatcher sessions for event broadcasting.
 * <p>
 * A secondary index maps OIDC session IDs to connection IDs so that all connections
 * belonging to a revoked OIDC session can be closed immediately via
 * {@link #revokeOidcSession(String)}.
 */
public final class SessionRegistry {

    private final ConcurrentHashMap<String, DispatcherSession> dispatcherSessions = new ConcurrentHashMap<>();
    // oidcSessionId → set of connectionIds
    private final ConcurrentHashMap<String, Set<String>> oidcSessionIndex = new ConcurrentHashMap<>();

    /**
     * Registers a dispatcher WebSocket session.
     *
     * @param session the session to register
     */
    public void registerDispatcherSession(DispatcherSession session) {
        dispatcherSessions.put(session.sessionId(), session);
        if (session.oidcSessionId() != null) {
            oidcSessionIndex
                    .computeIfAbsent(session.oidcSessionId(), k -> ConcurrentHashMap.newKeySet())
                    .add(session.sessionId());
        }
    }

    /**
     * Removes the dispatcher session with the given connection ID.
     *
     * @param sessionId the connection ID to remove
     */
    public void unregisterDispatcherSession(String sessionId) {
        var session = dispatcherSessions.remove(sessionId);
        if (session != null && session.oidcSessionId() != null) {
            var connectionIds = oidcSessionIndex.get(session.oidcSessionId());
            if (connectionIds != null) {
                connectionIds.remove(sessionId);
            }
        }
    }

    /**
     * Closes and unregisters all WebSocket connections that belong to the given OIDC session.
     * <p>
     * Called on back-channel logout or administrative session termination to immediately
     * invalidate active connections — satisfying the immediate-revocation security requirement.
     *
     * @param oidcSessionId the OIDC {@code sid} claim identifying the session to revoke
     */
    public void revokeOidcSession(String oidcSessionId) {
        var connectionIds = oidcSessionIndex.remove(oidcSessionId);
        if (connectionIds == null) {
            return;
        }
        for (var connectionId : new ArrayList<>(connectionIds)) {
            var session = dispatcherSessions.remove(connectionId);
            if (session != null) {
                session.close(4001, "Session revoked");
            }
        }
    }

    /**
     * Returns an unmodifiable snapshot of all currently active dispatcher sessions.
     */
    public Collection<DispatcherSession> getDispatcherSessions() {
        return Collections.unmodifiableCollection(dispatcherSessions.values());
    }
}
