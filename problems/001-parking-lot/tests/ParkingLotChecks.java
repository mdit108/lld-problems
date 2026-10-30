import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;

/** Dependency-free behavioral checks for the preserved initial solution. */
class ParkingLotChecks {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void expect(Class<? extends RuntimeException> type, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException error) {
            if (type.isInstance(error)) return;
            throw error;
        }
        throw new AssertionError("Expected " + type.getSimpleName());
    }

    private static void expectCharge(ParkingLot lot, Ticket ticket, int charge) {
        PrintStream original = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PrintStream capture = new PrintStream(output)) {
            System.setOut(capture);
            lot.unpark(ticket.getId());
        } finally {
            System.setOut(original);
        }
        check(output.toString().trim().equals("You have been charged Rs: " + charge),
            "Unexpected checkout charge: " + output);
    }

    public static void main(String[] args) {
        Slot car0 = new Slot("F0-C1", VehicleType.CAR);
        Slot bike0 = new Slot("F0-B1", VehicleType.BIKE);
        Slot car1 = new Slot("F1-C1", VehicleType.CAR);
        ParkingLot lot = new ParkingLot(List.of(
            new Floor(0, Map.of(VehicleType.CAR, List.of(car0), VehicleType.BIKE, List.of(bike0))),
            new Floor(1, Map.of(VehicleType.CAR, List.of(car1)))));

        Ticket first = lot.park(new Vehicle("CAR-1", VehicleType.CAR));
        Ticket second = lot.park(new Vehicle("CAR-2", VehicleType.CAR));
        check(first.getSlot() == car0, "First car should use floor 0");
        check(second.getSlot() == car1, "Second car should use floor 1");
        check(!car0.isFree() && !car1.isFree(), "Allocated spots must be occupied");
        check(bike0.isFree(), "Cars must not occupy bike spots");
        expect(ParkingLotFullException.class,
            () -> lot.park(new Vehicle("CAR-3", VehicleType.CAR)));

        expectCharge(lot, first, 10);
        check(car0.isFree(), "Checkout should release the spot");
        expect(InvalidTicketException.class, () -> lot.unpark(first.getId()));
        Ticket replacement = lot.park(new Vehicle("CAR-3", VehicleType.CAR));
        check(replacement.getSlot() == car0, "Freed spot should be reused");

        Ticket bike = lot.park(new Vehicle("BIKE-1", VehicleType.BIKE));
        check(bike.getSlot() == bike0, "Bike should use the bike spot");
        expectCharge(lot, bike, 5);
        expect(InvalidTicketException.class, () -> lot.unpark("bad-id"));
        check(!car0.isFree() && !car1.isFree(), "Invalid checkout must preserve other occupancy");
        System.out.println("Parking lot behavioral checks passed");
    }
}
