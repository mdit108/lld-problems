import java.util.*;
import java.time.*;

enum VehicleType { BIKE, CAR }

class ParkingLotFullException extends RuntimeException {
    public ParkingLotFullException(String msg) {
        super(msg);
    }
}

class InvalidTicketException extends RuntimeException {
    public InvalidTicketException(String msg) {
        super(msg);
    }
}

class Vehicle {
    private final String regNum;
    private final VehicleType type;

    Vehicle(String r, VehicleType type) {
        this.regNum = r;
        this.type = type;
    }

    VehicleType getVehicleType() {
        return this.type;
    }
}

class Slot {
    private final String id;
    private final VehicleType type;
    boolean parked;

    Slot(String id, VehicleType type) {
        this.id = id;
        this.type = type;
        this.parked = false;
    }

    boolean isFree() {
        return !parked;
    }

    void park() {
        parked = true;
    }

    void free() {
        parked = false;
    }
}

interface PricingStrategy {
    public int calculatePrice(VehicleType vehicleType, int hours);
}

class WeekdayPricingStrategy implements PricingStrategy {
    private int baseHourlyRate = 5;

    public int calculatePrice(VehicleType vehicleType, int hours) {
        int carModifier = vehicleType == VehicleType.CAR ? 2 : 1;
        return baseHourlyRate * carModifier * hours;
    }
}

class WeekendPricingStrategy implements PricingStrategy {
    private int baseHourlyRate = 10;

    public int calculatePrice(VehicleType vehicleType, int hours) {
        int carModifier = vehicleType == VehicleType.CAR ? 2 : 1;
        return baseHourlyRate * carModifier * hours;
    }
}

class Ticket {
    private final String ticketId = UUID.randomUUID().toString();
    private final Vehicle vehicle;
    private final Slot slot;
    private LocalDateTime entryTime = LocalDateTime.now();

    Ticket(Vehicle v, Slot s) {
        this.vehicle = v;
        this.slot = s;
    }

    String getId() {
        return this.ticketId;
    }

    Vehicle getVehicle() {
        return this.vehicle;
    }

    Slot getSlot() {
        return this.slot;
    }

    LocalDateTime getEntryTime() {
        return this.entryTime;
    }
}

class Floor {
    private int level;
    private final Map<VehicleType, List<Slot>> slotMap;

    public Floor(int level, Map<VehicleType, List<Slot>> slotMap) {
        this.level = level;
        this.slotMap = slotMap;
    }

    Slot findFreeSlot(VehicleType type) {
        if (slotMap.containsKey(type)) {
            List<Slot> slots = slotMap.get(type);
            for (Slot s : slots) {
                if (s.isFree()) {
                    s.park();
                    return s;
                }
            }
            return null;
        }
        return null;
    }
}

class ParkingLot {
    private final List<Floor> floors;
    private Map<String, Ticket> tickets = new HashMap<String, Ticket>();

    public ParkingLot(List<Floor> floors) {
        this.floors = floors;
    }

    public Ticket park(Vehicle vehicle) {
        for (Floor f : floors) {
            Slot slot = f.findFreeSlot(vehicle.getVehicleType());
            if (slot != null) {
                Ticket ticket = new Ticket(vehicle, slot);
                tickets.put(ticket.getId(), ticket);
                return ticket;
            }
        }
        throw new ParkingLotFullException("No Parking Available");
    }

    public void unpark(String ticketId) {
        Ticket ticket = tickets.get(ticketId);
        if (ticket != null) {
            Slot slot = ticket.getSlot();
            Vehicle vehicle = ticket.getVehicle();
            LocalDateTime entryTime = ticket.getEntryTime();
            long minutes = Duration.between(entryTime, LocalDateTime.now()).toMinutes();
            int hours = Math.max(1, (int) (minutes + 59) / 60);
            PricingStrategy pricing = new WeekdayPricingStrategy();
            int amt = pricing.calculatePrice(vehicle.getVehicleType(), hours);
            System.out.println("You have been charged Rs: " + amt);
            slot.free();
            tickets.remove(ticketId);
        } else {
            throw new InvalidTicketException("Ticket does not exist");
        }
    }
}

class Main {
    public static void main(String[] args) {
        // Floor 0: 1 car slot, 2 bike slots
        Map<VehicleType, List<Slot>> f0Slots = new HashMap<>();
        f0Slots.put(VehicleType.CAR, List.of(new Slot("F0-C1", VehicleType.CAR)));
        f0Slots.put(VehicleType.BIKE, List.of(
            new Slot("F0-B1", VehicleType.BIKE),
            new Slot("F0-B2", VehicleType.BIKE)));

        // Floor 1: 1 car slot
        Map<VehicleType, List<Slot>> f1Slots = new HashMap<>();
        f1Slots.put(VehicleType.CAR, List.of(new Slot("F1-C1", VehicleType.CAR)));

        ParkingLot lot = new ParkingLot(List.of(
            new Floor(0, f0Slots), new Floor(1, f1Slots)));

        // 1. Two cars: second one should land on floor 1
        Ticket t1 = lot.park(new Vehicle("KA01-1111", VehicleType.CAR));
        Ticket t2 = lot.park(new Vehicle("KA01-2222", VehicleType.CAR));
        System.out.println("Parked: " + t1.getId() + ", " + t2.getId());

        // 2. Third car: no car slots left on any floor
        try {
            lot.park(new Vehicle("KA01-3333", VehicleType.CAR));
        } catch (ParkingLotFullException e) {
            System.out.println("Expected full: " + e.getMessage());
        }

        // 3. Unpark first car: should charge Rs 10 (5 * 2 * 1 hour)
        lot.unpark(t1.getId());

        // 4. The freed slot should be usable again
        Ticket t3 = lot.park(new Vehicle("KA01-3333", VehicleType.CAR));
        System.out.println("Parked again: " + t3.getId());

        // 5. Bike: should charge Rs 5 (5 * 1 * 1 hour)
        Ticket t4 = lot.park(new Vehicle("KA02-4444", VehicleType.BIKE));
        lot.unpark(t4.getId());

        // 6. Bad ticket id
        lot.unpark("bad-id");
        System.out.println("Done");
    }
}
