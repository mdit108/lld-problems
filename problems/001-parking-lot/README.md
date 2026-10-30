# Parking Lot

Problem 001 · Java · Initial solution preserved, reviewed

## Requirements

- A lot has multiple floors, and each floor has spots of type `BIKE` or `CAR`.
- `park(vehicle)` finds the first free compatible spot, marks it taken, and returns a ticket containing an ID, vehicle, spot, and entry time.
- `unpark(ticketId)` calculates the fee, frees the spot, and returns the fee.
- Pricing is hourly, with a different rate per vehicle type. A weekend or flat rate must be addable without editing existing classes.
- Throw `LotFullException` when no compatible spot is free and `InvalidTicketException` for an unknown ticket ID.

## Submitted implementation

[src/Main.java](src/Main.java) preserves the submitted behavior. Chat escaping was removed and whitespace was normalized; the design and logic have not been corrected. The submitted exception is named `ParkingLotFullException` rather than the specified `LotFullException`.

No build framework or external dependencies are required. Use JDK 11 or later:

```sh
cd problems/001-parking-lot
mkdir -p out
javac -d out src/Main.java
java -cp out Main
```

The demo ends with an uncaught `InvalidTicketException` for `bad-id`, so its exit code is nonzero and `Done` is never printed. This is the submitted demo's behavior.

Run the separate behavioral checks without that intentional crash:

```sh
javac -d out src/Main.java tests/ParkingLotChecks.java
java -cp out ParkingLotChecks
```

## Design review

### Strengths

- `ParkingLot`, `Floor`, `Slot`, `Vehicle`, and `Ticket` express the domain clearly.
- Ordered floor and spot lists provide a straightforward first-free search.
- Active tickets provide a direct lookup during checkout and are removed after use.
- A pricing interface is the right starting point for interchangeable pricing policies.

### Changes to tackle next

1. **Return the fee.** `unpark` currently returns `void` and prints the amount. Return the calculated fee so callers can use it; keep presentation in `Main`.
2. **Inject the pricing policy.** Constructing `WeekdayPricingStrategy` inside `unpark` fixes the lot to weekday pricing. Accept a `PricingStrategy` in the lot constructor and call it during checkout. Then a new flat-rate implementation can be supplied without editing `ParkingLot`. If weekend selection must be automatic, compose a date-aware policy and give it the relevant time context; constructor injection alone does not select weekends.
3. **Define billing boundaries and preserve time precision.** The code rounds after truncating to whole minutes. At 60 minutes and 1 second it still charges one hour. If every started hour is billable, round the full duration up rather than the truncated minutes. The minimum one-hour charge is an implementation assumption; it was not explicit in the requirements.
4. **Match the exception contract.** Rename `ParkingLotFullException` to `LotFullException` if callers or assessment code expect the stated type.

### Further design improvements

- Inject a `Clock`, use `Instant` for elapsed time, and make entry time final. This enables deterministic boundary tests and avoids local-clock daylight-saving ambiguity.
- Make occupancy private. `findFreeSlot` currently also reserves the spot; a name such as `reserveFirstCompatibleSlot` would describe that side effect.
- Validate inputs and copy collection structures. The floor currently trusts the map key even if a slot's own type disagrees, and callers can mutate the supplied collections after construction.
- The current implementation assumes one caller at a time. If concurrency becomes a requirement, reserve a slot and issue its ticket atomically, and make checkout atomic as well. A concurrent map alone will not protect the whole operation.
- Expose a spot identifier for observing allocation. Duplicate vehicle admission, currency precision, and fees across multiple pricing periods require explicit product decisions before implementation.

## Complexity

- Parking: worst-case O(F + S), where F is the number of floors and S is the number of spots scanned for the requested type.
- Unparking: expected O(1) ticket lookup/removal plus pricing calculation.
- Active ticket storage: O(T), where T is the number of parked vehicles.

## Verification

The initial version was compiled and checked on JDK 24. The behavioral checks cover ordered floor allocation, compatible spot selection, full capacity, slot reuse, weekday car/bike charges, unknown tickets, and ticket reuse rejection. The demo was also run and its expected final exception was verified. Pricing injection and billing-boundary fixes remain future work; the current checks do not claim those gaps are resolved.
