// Online Java Compiler (Editor)
// Write and run Java online using this editor.

import java.util.*;
import java.time.*;

class Node<K, V> {
    final K key;
    V value;
    Node<K, V> next;
    Node<K, V> prev;

    Node(K key, V value) {
        this.key = key;
        this.value = value;
    }
}

interface EvictionPolicy<K> {
    void keyAdded(K key);
    void keyAccessed(K key);
    K evictKey();
}

class FIFOPolicy<K> implements EvictionPolicy<K> {
    private final Deque<K> queue = new ArrayDeque<>();

    public void keyAdded(K key) {
        queue.addLast(key);
    }

    public void keyAccessed(K key) {
        // intentionally empty: FIFO ignores reads, which is the whole difference from LRU
    }

    public K evictKey() {
        return queue.pollFirst();
    }
}

class LRUPolicy<K> implements EvictionPolicy<K> {
    private final Node<K, Void> head = new Node<>(null, null);
    private final Node<K, Void> tail = new Node<>(null, null);
    private final Map<K, Node<K, Void>> map = new HashMap<>();

    LRUPolicy() {
        head.next = tail;
        tail.prev = head;
    }

    private void remove(Node<K, Void> node) {
        Node<K, Void> prevNode = node.prev;
        Node<K, Void> nextNode = node.next;
        prevNode.next = nextNode;
        nextNode.prev = prevNode;
    }

    private void addToFront(Node<K, Void> node) {
        Node<K, Void> first = head.next;
        head.next = node;
        node.prev = head;
        node.next = first;
        first.prev = node;
    }

    public void keyAdded(K key) {
        Node<K, Void> newNode = new Node<>(key, null);
        map.put(key, newNode);
        addToFront(newNode);
    }

    public void keyAccessed(K key) {
        Node<K, Void> node = map.get(key);
        remove(node);
        addToFront(node);
    }

    public K evictKey() {
        Node<K, Void> victim = tail.prev;
        map.remove(victim.key);
        remove(victim);
        return victim.key;
    }
}

class Cache<K, V> {
    private final int capacity;
    private final Map<K, V> map = new HashMap<>();
    private final EvictionPolicy<K> policy;

    Cache(int capacity, EvictionPolicy<K> policy) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity should be positive");
        }
        this.capacity = capacity;
        this.policy = policy;
    }

    public Optional<V> get(K key) {
        if (map.containsKey(key)) {
            V value = map.get(key);
            policy.keyAccessed(key);
            return Optional.of(value);
        } else {
            return Optional.empty();
        }
    }

    public void put(K key, V value) {
        if (map.containsKey(key)) {
            map.put(key, value);
            policy.keyAccessed(key);
        } else {
            int size = map.size();
            if (size == capacity) {
                K evictedKey = policy.evictKey();
                map.remove(evictedKey);
            }
            map.put(key, value);
            policy.keyAdded(key);
        }
    }
}

class Main {
    public static void main(String[] args) {
        Cache<Integer, Integer> lru = new Cache<>(2, new LRUPolicy<>());
        Cache<Integer, Integer> fifo = new Cache<>(2, new FIFOPolicy<>());

        for (Cache<Integer, Integer> c : List.of(lru, fifo)) {
            c.put(1, 1);
            c.put(2, 2);
            c.get(1);
            c.put(3, 3);
            System.out.println(c.get(1) + " " + c.get(2) + " " + c.get(3));
        }
    }
}
