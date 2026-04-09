/*
 * This file is part of the MissingMeteor distribution (https://github.com/nxghtCry0/missingmeteor).
 * Copyright (c) nxghtCry0.
 */

package meteordevelopment.meteorclient.systems.modules.misc.swarm;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the state of a single swarm worker instance.
 */
public class SwarmWorkerInfo {
    public final int id;
    public String playerName = "unknown";
    public String group = "default";
    public double x, y, z;
    public float yaw, pitch;
    public long lastUpdate;
    public long ping;
    public boolean connected = true;

    // Path goal
    public double goalX, goalY, goalZ;
    public boolean hasGoal = false;

    // Inventory (itemRawId -> count)
    public final Map<Integer, Integer> inventory = new ConcurrentHashMap<>();

    public SwarmWorkerInfo(int id, String playerName) {
        this.id = id;
        this.playerName = playerName;
        this.lastUpdate = System.currentTimeMillis();
    }

    public void updatePosition(double x, double y, double z, float yaw, float pitch) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.lastUpdate = System.currentTimeMillis();
    }

    public void updatePing(long ping) {
        this.ping = ping;
    }

    public boolean isAlive() {
        return connected && (System.currentTimeMillis() - lastUpdate) < 10000;
    }

    @Override
    public String toString() {
        return String.format("Worker #%d [%s] (%s) (%.0f, %.0f, %.0f) ping=%dms", id, playerName, group, x, y, z, ping);
    }
}
