import java.util.Optional;
import java.util.Random;
import java.util.LinkedHashMap;
import java.util.Map;

/** Dependency-free checks against externally observable cache behavior. */
class CacheChecks {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void basic(boolean lru) {
        Cache<Integer, String> cache = new Cache<>(2,
            lru ? new LRUPolicy<>() : new FIFOPolicy<>());
        check(cache.get(99).isEmpty(), "Empty cache must miss");
        cache.put(1, "one");
        cache.put(2, "two");
        check(cache.get(1).equals(Optional.of("one")), "Hit must return value");
        cache.put(3, "three");
        check(cache.get(lru ? 2 : 1).isEmpty(), "Wrong eviction after read");
        check(cache.get(lru ? 1 : 2).isPresent(), "Survivor missing");

        Cache<Integer, String> updates = new Cache<>(2,
            lru ? new LRUPolicy<>() : new FIFOPolicy<>());
        updates.put(1, "old");
        updates.put(2, "two");
        updates.put(1, "new");
        updates.put(3, "three");
        check(updates.get(lru ? 2 : 1).isEmpty(), "Wrong eviction after update");
        if (lru) check(updates.get(1).equals(Optional.of("new")), "Update lost");

        Cache<Integer, String> single = new Cache<>(1,
            lru ? new LRUPolicy<>() : new FIFOPolicy<>());
        single.put(1, "a");
        single.put(1, "b");
        check(single.get(1).equals(Optional.of("b")), "Capacity-one update failed");
        single.put(2, "c");
        check(single.get(1).isEmpty(), "Capacity-one eviction failed");
        single.put(1, "d");
        check(single.get(2).isEmpty(), "Reinsert must evict current key");
        check(single.get(1).equals(Optional.of("d")), "Reinsert failed");
    }

    private static void randomized(boolean lru) {
        // Independent reference: JDK insertion-order/access-order LinkedHashMap.
        Map<Integer, Integer> reference = new LinkedHashMap<>(16, 0.75f, lru);
        Cache<Integer, Integer> actual = new Cache<>(4,
            lru ? new LRUPolicy<>() : new FIFOPolicy<>());
        Random random = new Random(108);
        for (int i = 0; i < 5000; i++) {
            int key = random.nextInt(10);
            if (random.nextBoolean()) {
                int value = random.nextInt();
                if (!reference.containsKey(key) && reference.size() == 4) {
                    reference.remove(reference.keySet().iterator().next());
                }
                reference.put(key, value);
                actual.put(key, value);
            } else {
                check(actual.get(key).equals(Optional.ofNullable(reference.get(key))),
                    "Reference mismatch at operation " + i + ", LRU=" + lru);
            }
        }
        // Drain through fresh inserts and reads to exercise final eviction order.
        for (int key = 10; key < 20; key++) {
            if (reference.size() == 4) reference.remove(reference.keySet().iterator().next());
            reference.put(key, key);
            actual.put(key, key);
            for (int probe = 0; probe < 20; probe++) {
                check(actual.get(probe).equals(Optional.ofNullable(reference.get(probe))),
                    "Mismatch while draining, LRU=" + lru);
            }
        }
    }

    public static void main(String[] args) {
        for (int capacity : new int[] {0, -1}) {
            try {
                new Cache<Integer, Integer>(capacity, new LRUPolicy<>());
                throw new AssertionError("Invalid capacity accepted");
            } catch (IllegalArgumentException expected) {
                // Expected constructor contract.
            }
        }
        basic(true);
        basic(false);
        randomized(true);
        randomized(false);
        System.out.println("Cache behavioral checks passed (LRU and FIFO)");
    }
}
