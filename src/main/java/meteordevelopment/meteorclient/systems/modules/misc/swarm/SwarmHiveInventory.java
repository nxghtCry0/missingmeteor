/*
 * This file is part of the MissingMeteor distribution (https://github.com/nxghtCry0/missingmeteor).
 * Copyright (c) nxghtCry0.
 */

package meteordevelopment.meteorclient.systems.modules.misc.swarm;

import java.util.*;

/**
 * Manages the aggregate inventory view across all swarm workers.
 * Each worker periodically reports its inventory, and this class
 * merges them into a single view for the host.
 */
public class SwarmHiveInventory {

    // workerId -> (rawItemId -> count)
    private final Map<Integer, Map<Integer, Integer>> workerInventories = new HashMap<>();

    public void updateWorkerInventory(int workerId, Map<Integer, Integer> items) {
        workerInventories.put(workerId, items);
    }

    public void removeWorker(int workerId) {
        workerInventories.remove(workerId);
    }

    /**
     * Get the total count of an item across all workers.
     */
    public int getTotalCount(int rawItemId) {
        int total = 0;
        for (Map<Integer, Integer> inv : workerInventories.values()) {
            total += inv.getOrDefault(rawItemId, 0);
        }
        return total;
    }

    /**
     * Find which workers have a specific item.
     */
    public List<Integer> findWorkersWithItem(int rawItemId) {
        List<Integer> workers = new ArrayList<>();
        for (Map.Entry<Integer, Map<Integer, Integer>> entry : workerInventories.entrySet()) {
            if (entry.getValue().getOrDefault(rawItemId, 0) > 0) {
                workers.add(entry.getKey());
            }
        }
        return workers;
    }

    /**
     * Get a worker's inventory map.
     */
    public Map<Integer, Integer> getWorkerInventory(int workerId) {
        return workerInventories.getOrDefault(workerId, Collections.emptyMap());
    }

    /**
     * Get all items aggregated across all workers.
     * Returns: itemId -> total count
     */
    public Map<Integer, Integer> getAggregateInventory() {
        Map<Integer, Integer> aggregate = new HashMap<>();
        for (Map<Integer, Integer> inv : workerInventories.values()) {
            for (Map.Entry<Integer, Integer> entry : inv.entrySet()) {
                aggregate.merge(entry.getKey(), entry.getValue(), Integer::sum);
            }
        }
        return aggregate;
    }

    /**
     * Get all unique items sorted by total count (descending).
     */
    public List<Map.Entry<Integer, Integer>> getSortedInventory() {
        List<Map.Entry<Integer, Integer>> entries = new ArrayList<>(getAggregateInventory().entrySet());
        entries.sort((a, b) -> b.getValue() - a.getValue());
        return entries;
    }

    public void clear() {
        workerInventories.clear();
    }

    public int getWorkerCount() {
        return workerInventories.size();
    }
}
