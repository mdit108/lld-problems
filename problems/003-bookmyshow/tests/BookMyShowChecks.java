import java.time.*;
import java.util.*;
import java.util.concurrent.*;

class BookMyShowChecks {
    private static final Duration HOLD = Duration.ofMinutes(5);

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void expect(Class<? extends RuntimeException> type, Runnable action) {
        try { action.run(); }
        catch (RuntimeException error) {
            if (type.isInstance(error)) return;
            throw error;
        }
        throw new AssertionError("Expected " + type.getSimpleName());
    }

    private static Show show() {
        return new Show("SH", new Movie("M", "Inception"),
            new Screen("SC", List.of(new Seat("S1", SeatType.REGULAR),
                new Seat("S2", SeatType.PREMIUM), new Seat("S3", SeatType.REGULAR))),
            LocalDateTime.now().plusHours(2));
    }

    private static void allocation() {
        Show show = show();
        show.lockSeats(List.of("S1"), "u1", HOLD);
        expect(SeatUnavailableException.class,
            () -> show.lockSeats(List.of("S1", "S2"), "u2", HOLD));
        show.lockSeats(List.of("S2"), "u2", HOLD); // Failed request did not claim S2.
        expect(LockExpiredException.class,
            () -> show.confirmSeats(List.of("S1", "S2"), "u1"));
        show.confirmSeats(List.of("S1"), "u1"); // Failed confirmation did not book S1.
        expect(SeatUnavailableException.class,
            () -> show.lockSeats(List.of("S1"), "u3", HOLD));
        show.releaseSeat(List.of("S1"), "u1"); // Release cannot undo a booked seat.
        expect(SeatUnavailableException.class,
            () -> show.lockSeats(List.of("S1"), "u3", HOLD));
        show.releaseSeat(List.of("S2"), "wrong-user");
        expect(SeatUnavailableException.class,
            () -> show.lockSeats(List.of("S2"), "u3", HOLD));
        show.releaseSeat(List.of("S2"), "u2");
        show.lockSeats(List.of("S2", "S2"), "u3", HOLD); // Duplicate IDs deduplicated.
        show.confirmSeats(List.of("S2"), "u3");
        expect(IllegalArgumentException.class,
            () -> show.lockSeats(List.of("S3", "unknown"), "u1", HOLD));
        show.lockSeats(List.of("S3"), "u1", HOLD); // Unknown ID did not partially lock.
    }

    private static void showIsolation() {
        Screen screen = new Screen("SC", List.of(new Seat("S1", SeatType.REGULAR)));
        Movie movie = new Movie("M", "Inception");
        Show a = new Show("A", movie, screen, LocalDateTime.now().plusHours(2));
        Show b = new Show("B", movie, screen, LocalDateTime.now().plusHours(4));
        a.lockSeats(List.of("S1"), "u1", HOLD);
        a.confirmSeats(List.of("S1"), "u1");
        b.lockSeats(List.of("S1"), "u2", HOLD);
        b.confirmSeats(List.of("S1"), "u2");
    }

    private static void expiryBoundary() {
        ShowSeat seat = new ShowSeat(new Seat("S", SeatType.REGULAR));
        LocalDateTime expiry = LocalDateTime.of(2026, 1, 1, 12, 5);
        seat.lock("u1", expiry);
        check(!seat.isAvailable(expiry.minusNanos(1)), "Premature availability");
        check(seat.isLockedBy("u1", expiry), "Current implementation includes expiry instant");
        check(!seat.isAvailable(expiry), "Expiry instant should match current implementation");
        check(seat.isAvailable(expiry.plusNanos(1)), "Expired hold must be reusable");
        check(!seat.isLockedBy("u1", expiry.plusNanos(1)), "Expired owner still valid");
        seat.lock("u2", expiry.plusMinutes(5));
        check(!seat.isLockedBy("u1", expiry.plusSeconds(1)), "Old owner retained access");
    }

    private static void contenders() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int iteration = 0; iteration < 50; iteration++) {
                Show show = show();
                CountDownLatch ready = new CountDownLatch(2);
                CountDownLatch start = new CountDownLatch(1);
                List<Future<Boolean>> results = new ArrayList<>();
                for (String user : List.of("u1", "u2")) {
                    results.add(pool.submit(() -> {
                        ready.countDown();
                        if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("Start timeout");
                        try {
                            show.lockSeats(List.of("S1", "S2"), user, HOLD);
                            show.confirmSeats(List.of("S1", "S2"), user);
                            return true;
                        } catch (SeatUnavailableException expected) { return false; }
                    }));
                }
                try {
                    check(ready.await(5, TimeUnit.SECONDS), "Workers not ready");
                } finally { start.countDown(); }
                int winners = 0;
                for (Future<Boolean> result : results) {
                    if (result.get(5, TimeUnit.SECONDS)) winners++;
                }
                check(winners == 1, "Exactly one contender must book the requested seats");
            }
        } finally { pool.shutdownNow(); }
    }

    public static void main(String[] args) throws Exception {
        allocation();
        showIsolation();
        expiryBoundary();
        contenders();
        System.out.println("BookMyShow checks passed, including 50 two-user races");
    }
}
