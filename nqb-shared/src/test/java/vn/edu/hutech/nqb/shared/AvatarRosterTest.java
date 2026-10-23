package vn.edu.hutech.nqb.shared;

import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class AvatarRosterTest {
    @Test void catalogHasUniqueStableIdsAndNames() {
        assertEquals(24, AvatarCatalog.ALL.size());
        assertEquals(24, AvatarCatalog.ALL.stream().map(AvatarCatalog.Avatar::id).distinct().count());
        assertEquals(24, AvatarCatalog.ALL.stream().map(AvatarCatalog.Avatar::name).distinct().count());
    }
    @Test void changingToOccupiedKeepsOldReservation() {
        var room = new AvatarRoster(); String a = room.join("a"), b = room.join("b");
        assertEquals("AVATAR_TAKEN", assertThrows(IllegalStateException.class, () -> room.select("a", b)).getMessage());
        assertEquals(a, room.snapshot().get("a"));
        room.select("a", "quizling-10"); room.select("b", a);
        assertEquals(a, room.snapshot().get("b"));
    }
    @Test void leaveReleasesAndRoomsAreIndependent() {
        var room = new AvatarRoster(); var another = new AvatarRoster();
        String a = room.join("a"); assertEquals(a, another.join("b"));
        room.leave("a"); assertEquals(a, room.join("c")); room.clear(); assertTrue(room.snapshot().isEmpty());
    }
    @Test void invalidAndNonMemberClaimsCannotMutateRoster() {
        var room = new AvatarRoster(); room.join("a"); var before = room.snapshot();
        assertThrows(IllegalArgumentException.class, () -> room.select("a", "bad-id"));
        assertThrows(IllegalStateException.class, () -> room.select("intruder", "quizling-20"));
        assertEquals(before, room.snapshot()); assertThrows(UnsupportedOperationException.class, () -> before.clear());
    }
    @Test void fullRoomRejectsWithoutDuplicatingAvatar() {
        var room = new AvatarRoster(); for (int i=0;i<24;i++) room.join("p"+i);
        assertEquals("ROOM_FULL", assertThrows(IllegalStateException.class, () -> room.join("overflow")).getMessage());
        assertEquals(24, room.snapshot().values().stream().distinct().count());
    }
    @Test void simultaneousClaimsHaveExactlyOneWinner() throws Exception {
        var room = new AvatarRoster(); for (int i=0;i<12;i++) room.join("p"+i);
        var gate = new CountDownLatch(1); var wins = new AtomicInteger();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new java.util.ArrayList<Future<?>>();
            for (int i=0;i<12;i++) { String id = "p"+i; futures.add(executor.submit(() -> { gate.await(); try { room.select(id,"quizling-24"); wins.incrementAndGet(); } catch (IllegalStateException taken) { assertEquals("AVATAR_TAKEN",taken.getMessage()); } return null; })); }
            gate.countDown(); for (var f : futures) f.get(3,TimeUnit.SECONDS);
        }
        assertEquals(1,wins.get()); assertEquals(12,room.snapshot().values().stream().distinct().count());
    }
}
