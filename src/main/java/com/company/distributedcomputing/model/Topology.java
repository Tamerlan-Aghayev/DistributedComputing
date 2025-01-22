package com.company.distributedcomputing.model;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class Topology {
    private final ConcurrentHashMap<String, Node> neighbors = new ConcurrentHashMap<>();
    private final AtomicReference<String> parentId = new AtomicReference<>();

    public Map<String, Node> getNeighbors() {
        return neighbors;
    }

    public String getParentId() {
        return parentId.get();
    }

    public void setParentId(String id) {
        parentId.set(id);
    }

    public void addNeighbor(String id, Node node) {
        neighbors.put(id, node);
    }

    public void removeNeighbor(String id) {
        neighbors.remove(id);
    }

    public Node getNeighbor(String id) {
        return neighbors.get(id);
    }

    public void clear() {
        neighbors.clear();
        parentId.set(null);
    }
}