package vn.edu.hutech.nqb.shared;

import java.util.LinkedHashMap;
import java.util.Map;

/** One instance per room, owned by the room worker (not a client authority).
 * Atomic check-and-swap also protects simultaneous requests in tests/adapters.
 * Authentication and room phase checks belong to the request handler. */
public final class AvatarRoster {
    private final Map<String, String> byPlayer = new LinkedHashMap<>();
    public synchronized String join(String playerId) {
        if (playerId == null || playerId.isBlank()) throw new IllegalArgumentException("INVALID_PLAYER");
        if (byPlayer.containsKey(playerId)) return byPlayer.get(playerId);
        String free = AvatarCatalog.ALL.stream().map(AvatarCatalog.Avatar::id)
            .filter(id -> !byPlayer.containsValue(id)).findFirst().orElseThrow(() -> new IllegalStateException("ROOM_FULL"));
        byPlayer.put(playerId, free); return free;
    }
    public synchronized void select(String playerId, String avatarId) {
        AvatarCatalog.find(avatarId);
        if (!byPlayer.containsKey(playerId)) throw new IllegalStateException("NOT_JOINED");
        if (byPlayer.entrySet().stream().anyMatch(e -> !e.getKey().equals(playerId) && e.getValue().equals(avatarId)))
            throw new IllegalStateException("AVATAR_TAKEN");
        byPlayer.put(playerId, avatarId); // Release old only after successful claim.
    }
    public synchronized void leave(String playerId) { byPlayer.remove(playerId); }
    public synchronized Map<String, String> snapshot() { return Map.copyOf(byPlayer); }
    public synchronized void clear() { byPlayer.clear(); }
}
