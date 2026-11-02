// Online Java Compiler (Editor)
// Write and run Java online using this editor.

import java.util.*;
import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

enum SeatStatus { BOOKED, AVAILABLE, LOCKED }
enum SeatType { REGULAR, PREMIUM }
enum BookingStatus { CONFIRMED, PENDING, CANCELLED, EXPIRED }

class SeatUnavailableException extends RuntimeException {
    public SeatUnavailableException(String msg) { super(msg); }
}
class LockExpiredException extends RuntimeException {
    public LockExpiredException(String msg) { super(msg); }
}
class BookingNotFoundException extends RuntimeException {
    public BookingNotFoundException(String msg) { super(msg); }
}

class Seat {
    private final String id;
    private final SeatType type;

    public Seat(String id, SeatType type) {
        this.id = id;
        this.type = type;
    }
    public SeatType getSeatType() { return this.type; }
    public String getId() { return id; }
}

class Movie {
    private final String id;
    private final String name;

    public Movie(String id, String name) {
        this.id = id;
        this.name = name;
    }
    public String getMovieName() { return this.name; }
}

class Screen {
    private final String id;
    public List<Seat> seats = new ArrayList<>();

    public Screen(String id, List<Seat> seats) {
        this.id = id;
        this.seats = seats;
    }
    public List<Seat> getSeats() { return seats; }
}

class Theatre {
    private final String id;
    private final String name;
    public List<Screen> screens = new ArrayList<>();

    public Theatre(String id, String name, List<Screen> screens) {
        this.id = id;
        this.name = name;
        this.screens = screens;
    }
    public String getTheatreName() { return this.name; }
}

class ShowSeat {
    private final Seat seat;
    public SeatStatus seatStatus = SeatStatus.AVAILABLE;
    public String lockedBy;
    public LocalDateTime expireBy;

    public ShowSeat(Seat seat) { this.seat = seat; }

    public boolean isAvailable(LocalDateTime now) {
        if (seatStatus == SeatStatus.AVAILABLE) return true;
        if (seatStatus == SeatStatus.LOCKED) {
            if (now.isAfter(expireBy)) return true;
            else return false;
        }
        return false;
    }

    public void book() { seatStatus = SeatStatus.BOOKED; }
    public void free() { seatStatus = SeatStatus.AVAILABLE; }

    public void lock(String userId, LocalDateTime expireBy) {
        seatStatus = SeatStatus.LOCKED;
        lockedBy = userId;
        this.expireBy = expireBy;
    }

    public boolean isLockedBy(String userId, LocalDateTime now) {
        return seatStatus == SeatStatus.LOCKED
            && userId.equals(lockedBy)
            && !now.isAfter(expireBy);
    }
}

class Show {
    private final String id;
    public final Movie movie;
    public final Screen screen;
    public final LocalDateTime startTime;
    ReentrantLock lock = new ReentrantLock();
    private final Map<String, ShowSeat> showSeats = new HashMap<>();

    public Show(String id, Movie movie, Screen screen, LocalDateTime startTime) {
        this.id = id;
        this.movie = movie;
        this.screen = screen;
        this.startTime = startTime;
        for (Seat x : screen.getSeats()) {
            showSeats.put(x.getId(), new ShowSeat(x));
        }
    }

    private ShowSeat getShowSeat(String seatId) {
        ShowSeat ss = showSeats.get(seatId);
        if (ss == null) throw new IllegalArgumentException("Unknown seat: " + seatId);
        return ss;
    }

    void lockSeats(List<String> seatIds, String userId, Duration holdFor) {
        Set<String> ids = new HashSet<>(seatIds);
        lock.lock();
        try {
            LocalDateTime now = LocalDateTime.now();
            for (String id : ids) {
                ShowSeat ss = getShowSeat(id);
                if (!ss.isAvailable(now)) {
                    throw new SeatUnavailableException("Seat not available: " + id);
                }
            }
            LocalDateTime expiry = now.plus(holdFor);
            for (String id : ids) {
                ShowSeat ss = getShowSeat(id);
                ss.lock(userId, expiry);
            }
        } finally {
            lock.unlock();
        }
    }

    void confirmSeats(List<String> seatIds, String userId) {
        Set<String> ids = new HashSet<>(seatIds);
        lock.lock();
        try {
            LocalDateTime now = LocalDateTime.now();
            for (String id : ids) {
                ShowSeat ss = getShowSeat(id);
                if (!ss.isLockedBy(userId, now)) {
                    throw new LockExpiredException("Lock expire");
                }
            }
            for (String id : ids) getShowSeat(id).book();
        } finally {
            lock.unlock();
        }
    }

    void releaseSeat(List<String> seatIds, String userId) {
        Set<String> ids = new HashSet<>(seatIds);
        lock.lock();
        try {
            LocalDateTime now = LocalDateTime.now();
            for (String id : ids) {
                ShowSeat ss = getShowSeat(id);
                if (ss.isLockedBy(userId, now)) ss.free();
            }
        } finally {
            lock.unlock();
        }
    }
}

class Main {
    public static void main(String[] args) throws Exception {
        Seat seat1 = new Seat("S1", SeatType.REGULAR);
        Seat seat2 = new Seat("S2", SeatType.REGULAR);
        Seat seat3 = new Seat("S3", SeatType.REGULAR);
        Seat seat4 = new Seat("S4", SeatType.PREMIUM);
        List<Seat> seats = new ArrayList<>();
        seats.add(seat1);
        seats.add(seat2);
        seats.add(seat3);
        seats.add(seat4);
        Screen screen1 = new Screen("SC1", seats);
        List<Screen> screens = new ArrayList<>();
        screens.add(screen1);
        Theatre theatre1 = new Theatre("T1", "Theatre 1", screens);
        // Theatre theatre2 = new Theatre("T2", "Theatre 2");
        Movie movie = new Movie("M1", "Inception");
        Show show = new Show("SH1", movie, screen1, LocalDateTime.now().plusHours(2));
        Duration hold = Duration.ofMinutes(5);

        show.lockSeats(List.of("S1", "S2"), "u1", hold); // ok
        try { show.lockSeats(List.of("S2", "S3"), "u2", hold); } // S2 held by u1
        catch (SeatUnavailableException e) { System.out.println("1: " + e.getMessage()); }
        show.lockSeats(List.of("S3"), "u2", hold); // ok: proves S3 wasn't locked above
        show.confirmSeats(List.of("S1", "S2"), "u1"); // ok
        try { show.lockSeats(List.of("S1"), "u3", hold); } // S1 is booked
        catch (SeatUnavailableException e) { System.out.println("2: " + e.getMessage()); }
        show.lockSeats(List.of("S4"), "u4", Duration.ofSeconds(1));
        Thread.sleep(1500);
        try { show.confirmSeats(List.of("S4"), "u4"); } // lock expired
        catch (LockExpiredException e) { System.out.println("3: " + e.getMessage()); }
        show.lockSeats(List.of("S4"), "u5", hold); // ok: expired lock is reusable
        System.out.println("4: done");
    }
}
