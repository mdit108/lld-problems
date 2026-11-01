# Cache

Problem 002 · Java · Initial solution preserved, reviewed

## Requirements

1. Support `get` and `put` operations.
2. Use a strategy-based eviction policy, with LRU and FIFO implementations.

## Submitted implementation

[src/Main.java](src/Main.java) preserves the submitted design and logic. Chat escaping was removed and formatting was normalized.

- `Cache<K, V>` owns storage and delegates eviction decisions to an injected `EvictionPolicy<K>`.
- `get` returns `Optional<V>`; a hit notifies the policy.
- Updating an existing key replaces its value and counts as an access.
- New entries evict one key when capacity is reached.
- LRU uses a key-to-node map and a doubly linked list; the head side is most recently used.
- FIFO uses a queue; reads and updates leave insertion order unchanged.
- Capacity must be positive.

These are implementation choices beyond the two stated requirements. Null handling, concurrency, policy reuse, deletion, and expiration were not specified.

## Run

Use JDK 11 or later; no external dependencies are required. Compile each problem separately because both this problem and Parking Lot use a default-package `Main` class.

```sh
cd problems/002-cache
mkdir -p out
javac -d out src/Main.java
java -cp out Main
```

Expected output, LRU followed by FIFO:

```text
Optional[1] Optional.empty Optional[3]
Optional.empty Optional[2] Optional[3]
```

Behavioral checks:

```sh
javac -d out src/Main.java tests/CacheChecks.java
java -cp out CacheChecks
```

## Design review

### Strengths

- The policy is injected into the constructor. Adding another implementation of the existing callback contract does not require changing `Cache`.
- Storage and eviction ordering have separate responsibilities.
- LRU maintains direct node lookup, allowing constant-time list updates without scanning for a key.
- FIFO correctly ignores access notifications, including updates, preserving insertion order.
- Existing-key updates avoid capacity eviction and duplicate policy insertion.
- `Optional` makes the cache-miss result explicit for non-null values.

### Changes to consider next

1. **Define and enforce the null contract before mutation.** `put(key, null)` is accepted, but a later `get` throws because `Optional.of` rejects null. FIFO also rejects null keys in `ArrayDeque`, after the cache has already inserted the key and potentially evicted an entry. A simple contract is to reject null keys and values at the start of `get`/`put`, before any eviction or state change. Reject a null policy in the constructor as well.
2. **Make policy ownership explicit.** One fresh policy instance must belong to one cache. Sharing an LRU or FIFO policy between caches mixes their eviction state, so it can return a victim absent from the requesting cache. The current code can then exceed capacity. Document exclusive ownership; if construction needs to enforce it, accept a factory that creates a fresh policy per cache. External callers should not manipulate that policy after handing it to the cache.
3. **Document the policy callback contract.** `keyAdded` assumes a new key, `keyAccessed` assumes a tracked key, and `evictKey` assumes a nonempty policy and must remove the victim from its own state. LRU throws on an empty eviction or unknown access; FIFO returns null on empty eviction. These cases are unreachable through a correctly used cache with a fresh, exclusive policy. Choose consistent fail-fast behavior if policies are intended for standalone use, and clarify expectations for third-party policies.
4. **Define failure behavior for custom policies.** Storage is updated before `keyAdded` or `keyAccessed` finishes. A throwing policy can leave storage and eviction state inconsistent. Either constrain callbacks to be nonthrowing for valid input and document that contract, or design recovery if stronger failure guarantees become a requirement.

### Further improvements if scope grows

- Keep this implementation explicitly single-threaded. A concurrent map alone does not make storage changes and policy updates atomic; concurrency requires coordination across the entire operation.
- For deletion or expiration, extend the callback lifecycle so policy metadata is removed when storage removes a key. The current interface fits the stated get/put scope.
- `Node<K, Void>` does not use its value field. A key-only node would express the LRU data structure more directly. The `java.time` import is unused.
- Describe whether a write refreshes recency: the current LRU does, while FIFO retains original insertion order. Both are coherent choices and deserve explicit tests.

## Complexity

For capacity C, both policies use O(C) storage. Cache lookups and LRU map operations are expected O(1); linked-list operations are O(1). FIFO queue operations are amortized O(1). Overall get/put operations are expected/amortized O(1), assuming stable, well-behaved key hashing and equality.

## Verification

Compiled and run on JDK 24. Checks cover empty misses, hits, read-driven eviction, existing-key updates, capacity one, reinsertion, and invalid capacity. Each policy also runs through 5,000 seeded operations against an independent JDK `LinkedHashMap` reference configured for access or insertion order, followed by further inserts and comparisons.

These checks exercise non-null data with fresh, exclusively owned policies in a single thread. The review concerns above remain open; the submitted implementation has not been silently corrected.
