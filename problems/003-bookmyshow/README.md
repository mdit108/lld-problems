# BookMyShow

Problem 003 · Java · Initial solution preserved, reviewed

## Requirements

- Theatres have screens, and screens have seats.
- A show has one movie, one screen, and one start time. Seat state belongs to the show.
- A user chooses a show and several seats, which are locked for five minutes.
- Successful payment confirms the booking and changes locked seats to booked. Expiry or failed payment makes seats available again.
- Two users must never get the same seat. A multi-seat reservation must be all or nothing.
- Errors: `SeatUnavailableException`, `LockExpiredException`, `BookingNotFoundException`.

## Submitted implementation

[src/Main.java](src/Main.java) preserves the submitted behavior and design. Chat escaping was removed and formatting normalized.

`Show` owns a separate seat-state map and a `ReentrantLock`. Reservation and confirmation validate every requested seat before mutating any of them. Duplicate seat IDs are deduplicated. `releaseSeat` frees unexpired locks owned by the specified user; it does not cancel booked seats. Expired locks are reusable on demand without a cleanup worker.

## Run

Use JDK 11 or later, with no external dependencies. Compile this problem separately from other problems because its default-package class names overlap.

```sh
cd problems/003-bookmyshow
mkdir -p out
javac -d out src/Main.java
java -cp out Main
```

The submitted demo waits 1.5 seconds to exercise expiration and prints:

```text
1: Seat not available: S2
2: Seat not available: S1
3: Lock expire
4: done
```

Behavioral checks:

```sh
javac -d out src/Main.java tests/BookMyShowChecks.java
java -cp out BookMyShowChecks
```

## Design review

### Strengths

- Physical seats and per-show seat state are separate, so two shows on the same screen can book the same physical seat independently.
- The show-level lock serializes availability checks and state transitions. The `try/finally` blocks release it on errors.
- Reservation and confirmation both use a validate-all, then mutate-all structure, protecting those operations from partial updates on ordinary validation failures.
- Expiration is checked inside the critical section, using one captured time for the request.
- An expired hold can be replaced without needing a scheduler for correctness of subsequent reservation attempts.

### Changes to tackle next

1. **Introduce booking/hold identity and lifecycle.** `BookingStatus` and `BookingNotFoundException` exist but are unused. There is no booking record or booking-ID lookup, so the required unknown-booking error cannot occur. Create a booking with an immutable ID, owner, show, complete seat selection, expiry, and status. Confirm/release by that identity and validate state transitions; define retry behavior for payment callbacks.
2. **Bind callbacks to the specific hold.** User ID alone is insufficient. Suppose a user's old hold expires and they acquire a new hold on the same seats: a delayed confirmation or failed-payment release from the old attempt can operate on the new hold. Validate a unique hold/booking token on each seat as well as ownership and expiry. The current API also allows confirming a subset of a larger hold; decide whether a booking must always be confirmed as a complete unit.
3. **Connect payment outcomes to the lifecycle.** The code exposes confirmation and release primitives but contains no payment orchestration or callback handling. A failed-payment handler must release the matching hold and update its booking status; successful payment must confirm it safely. Specify what happens when payment succeeds after expiry, and keep slow external payment calls outside the seat-state lock.
4. **Make the five-minute policy and expiry boundary explicit.** Callers can supply any duration, including zero or negative values. Enforce the five-minute hold in the booking service, or clearly expose a configurable policy with positive-duration validation. At exactly `expireBy`, this implementation still considers the lock valid; choose and test whether expiration means `now >= expireBy` instead.

### Further design improvements

- Inject a `Clock` and use `Instant` for elapsed-time deadlines. This enables deterministic end-to-end expiry checks and avoids local-time daylight-saving ambiguity. The current checks test `ShowSeat` boundaries directly; the submitted demo uses a real wait.
- Lazy expiration provides effective availability, but leaves the stored status as `LOCKED` until another transition. Any future seat-map query must compute effective availability or normalize expired state under the same lock. A scheduler is optional cleanup, not a substitute for checking expiry during confirmation.
- Hide mutable state: make the show lock private/final, make `ShowSeat` state private, and defensively copy screen/theatre collections. Current package access allows code to replace the show lock, and public fields allow bypassing invariants where instances are exposed.
- Validate nonempty seat selections, non-null user IDs/durations, and unique seat IDs within a screen. Empty selections currently succeed; duplicate physical IDs silently overwrite entries in a show's map.
- `releaseSeat` validates and mutates in one loop. A request mixing a valid held seat and an unknown ID can release some seats before throwing, depending on set iteration order. If release also needs all-or-nothing behavior, validate the complete selection before mutation.
- The lock guarantees coordination only for callers sharing this exact `Show` instance in one JVM. Separate instances or application processes need a shared authoritative store with transactional or conditional seat updates to prevent double booking.
- A single lock per show is a reasonable initial tradeoff: simple invariants, but all seat requests for that show serialize. Optimize only if measured contention requires it.

## Complexity

For a request with K distinct seats, reservation, confirmation, and release take expected O(K) time and O(K) temporary space for deduplication. Each show stores O(S) seat states for S seats. Lock acquisition can add unbounded waiting under contention; the operation cost excludes that wait.

## Verification

Compiled and run on JDK 24. The original demo completed with the expected output. Dependency-free checks cover:

- Rejection of overlapping reservations without partially claiming free seats.
- Confirmation validation without partial booking.
- Booked-seat protection and release restricted to the current owner.
- Seat reuse after release, duplicate request IDs, and unknown seat rejection.
- Independent seat state for shows sharing the same physical screen.
- Expiry just before, at, and after the deadline, and replacement by a different owner.
- Fifty synchronized two-user races, each producing exactly one successful multi-seat booking.

Passing checks establish the tested behavior within one in-memory show instance. They do not establish a completed booking/payment workflow, stale-callback safety, distributed correctness, or production load behavior. Review gaps remain open; the submitted code has not been corrected silently.
