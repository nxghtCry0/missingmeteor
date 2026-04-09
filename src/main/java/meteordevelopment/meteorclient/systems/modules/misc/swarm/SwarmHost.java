/*
 * This file is part of the MissingMeteor distribution (https://github.com/nxghtCry0/missingmeteor).
 * Copyright (c) nxghtCry0.
 */

package meteordevelopment.meteorclient.systems.modules.misc.swarm;

import meteordevelopment.meteorclient.utils.player.ChatUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Swarm host server. Manages all worker connections, routes messages,
 * maintains claim tables, worker states, group assignments, and hive inventory.
 */
public class SwarmHost extends Thread {
    private ServerSocket socket;
    private final Swarm swarm;

    private final SwarmConnection[] connections = new SwarmConnection[50];
    private final Map<Integer, SwarmWorkerInfo> workers = new ConcurrentHashMap<>();
    private final Map<Long, Integer> claimTable = new ConcurrentHashMap<>();
    private final SwarmHiveInventory hiveInventory = new SwarmHiveInventory();

    private int nextWorkerId = 0;
    private int tickCounter = 0;

    // Settings references
    public boolean ussEnabled = true;
    public int ussSyncRate = 10;
    public double ussProximityAvoid = 5.0;
    public boolean invSyncEnabled = false;
    public int invSyncRate = 100;

    public SwarmHost(int port, Swarm swarm) {
        this.swarm = swarm;
        try {
            socket = new ServerSocket(port);
        } catch (IOException e) {
            socket = null;
            ChatUtils.errorPrefix("Swarm", "Couldn't start HiveMind server on port %s.", port);
            e.printStackTrace();
        }
        if (socket != null) start();
    }

    @Override
    public void run() {
        ChatUtils.infoPrefix("Swarm", "(highlight)HiveMind Host(listening) on port %s.", socket.getLocalPort());

        while (!isInterrupted()) {
            try {
                Socket connection = socket.accept();
                assignConnection(connection);
            } catch (IOException e) {
                if (!isInterrupted()) {
                    ChatUtils.errorPrefix("Swarm", "Error accepting worker connection.");
                    e.printStackTrace();
                }
            }
        }
    }

    private void assignConnection(Socket connection) {
        try {
            SwarmConnection conn = new SwarmConnection(connection, this);

            // Handshake: read worker's protocol version
            // (The worker sends HANDSHAKE immediately on connect)
            // We read it in the connection's first read cycle, but we need to assign the ID first
            // So we do the handshake synchronously here before the connection thread takes over

            DataInputStream in = new DataInputStream(new BufferedInputStream(connection.getInputStream()));
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(connection.getOutputStream()));

            // Read handshake
            byte type = SwarmProtocol.readType(in);
            if (type != SwarmProtocol.HANDSHAKE) {
                ChatUtils.errorPrefix("Swarm", "Worker sent unexpected type %d, expected HANDSHAKE.", type);
                connection.close();
                return;
            }
            byte[] payload = SwarmProtocol.readPayload(in);
            int version = SwarmProtocol.deserializeHandshake(payload);

            if (version != SwarmProtocol.PROTOCOL_VERSION) {
                ChatUtils.errorPrefix("Swarm", "Worker has incompatible protocol version %d (expected %d).", version, SwarmProtocol.PROTOCOL_VERSION);
                connection.close();
                return;
            }

            // Assign worker ID
            int workerId = nextWorkerId++;
            conn.workerId = workerId;

            // Send back the worker ID
            SwarmProtocol.writeFrame(out, SwarmProtocol.HANDSHAKE, SwarmProtocol.serializeHandshake(workerId));
            out.flush();

            // Store connection
            for (int i = 0; i < connections.length; i++) {
                if (connections[i] == null) {
                    connections[i] = conn;
                    break;
                }
            }

            // Create worker info
            SwarmWorkerInfo info = new SwarmWorkerInfo(workerId);
            workers.put(workerId, info);
            conn.info = info;

            // Push the buffered input stream to the connection
            // (We already read the handshake, the connection will continue from here)
            ChatUtils.infoPrefix("Swarm", "Worker (highlight)#%d(default) connected with protocol v%d.", workerId, version);

        } catch (IOException e) {
            ChatUtils.errorPrefix("Swarm", "Error during worker handshake.");
            e.printStackTrace();
            try { connection.close(); } catch (IOException ignored) {}
        }
    }

    /**
     * Handle an incoming message from a worker.
     */
    public void handleWorkerMessage(int workerId, byte type, byte[] payload) {
        SwarmWorkerInfo info = workers.get(workerId);
        if (info == null) return;

        switch (type) {
            case SwarmProtocol.POSITION_UPDATE -> {
                double[] posData = SwarmProtocol.deserializePosition(payload);
                info.updatePosition(posData[0], posData[1], posData[2], (float) posData[3], (float) posData[4]);
            }
            case SwarmProtocol.BLOCK_CLAIM -> {
                int[] bp = SwarmProtocol.deserializeBlockPos(payload);
                long key = SwarmProtocol.blockPosToKey(bp[0], bp[1], bp[2]);
                int value = SwarmProtocol.yPosAndWorkerToValue(bp[1], workerId);
                claimTable.put(key, value);
            }
            case SwarmProtocol.BLOCK_RELEASE -> {
                int[] bp = SwarmProtocol.deserializeBlockPos(payload);
                long key = SwarmProtocol.blockPosToKey(bp[0], bp[1], bp[2]);
                Integer existing = claimTable.get(key);
                if (existing != null && SwarmProtocol.getWorkerIdFromValue(existing) == workerId) {
                    claimTable.remove(key);
                }
            }
            case SwarmProtocol.INVENTORY_UPDATE -> {
                int[][] invData = SwarmProtocol.deserializeInventory(payload);
                int[] slots = invData[0];
                int[] itemIds = invData[1];
                int[] counts = invData[2];
                Map<Integer, Integer> invMap = new HashMap<>();
                for (int i = 0; i < slots.length; i++) {
                    invMap.put(itemIds[i], counts[i]);
                }
                info.inventory.clear();
                info.inventory.putAll(invMap);
                hiveInventory.updateWorkerInventory(workerId, invMap);
            }
            case SwarmProtocol.PING -> {
                // Respond with pong
                sendToWorker(workerId, SwarmProtocol.PONG, payload);
            }
            case SwarmProtocol.PONG -> {
                long sentTime = SwarmProtocol.readLong(payload, 0);
                info.updatePing(System.currentTimeMillis() - sentTime);
            }
            case SwarmProtocol.GROUP_ASSIGN -> {
                info.group = SwarmProtocol.deserializeGroupAssign(payload);
                ChatUtils.infoPrefix("Swarm", "Worker #%d joined group (highlight)%s", workerId, info.group);
            }
            case SwarmProtocol.DISCONNECT -> {
                String reason = SwarmProtocol.deserializeDisconnect(payload);
                ChatUtils.infoPrefix("Swarm", "Worker #%d disconnecting: %s", workerId, reason);
                removeWorker(workerId);
            }
            default -> ChatUtils.warningPrefix("Swarm", "Unknown message type %d from worker #%d.", type, workerId);
        }
    }

    // --- Messaging ---

    /** Broadcast a command to ALL workers. */
    public void sendMessage(String command) {
        byte[] payload = SwarmProtocol.serializeCommand(command);
        for (SwarmConnection conn : connections) {
            if (conn != null && conn.active) {
                conn.sendFrame(SwarmProtocol.COMMAND, payload);
            }
        }
    }

    /** Send a command to a specific worker by ID. */
    public void sendToWorker(int workerId, String command) {
        SwarmConnection conn = getConnectionByWorkerId(workerId);
        if (conn != null && conn.active) {
            conn.sendCommand(command);
        } else {
            ChatUtils.errorPrefix("Swarm", "Worker #%d not found or disconnected.", workerId);
        }
    }

    /** Send a raw binary frame to a specific worker. */
    public void sendToWorker(int workerId, byte type, byte[] payload) {
        SwarmConnection conn = getConnectionByWorkerId(workerId);
        if (conn != null && conn.active) {
            conn.sendFrame(type, payload);
        }
    }

    /** Send a command to all workers in a specific group. */
    public void sendToGroup(String group, String command) {
        byte[] payload = SwarmProtocol.serializeCommand(command);
        int sent = 0;
        for (SwarmWorkerInfo info : workers.values()) {
            if (info.group.equalsIgnoreCase(group) && info.connected) {
                sendToWorker(info.id, SwarmProtocol.COMMAND, payload);
                sent++;
            }
        }
        if (sent == 0) {
            ChatUtils.warningPrefix("Swarm", "No workers found in group '%s'.", group);
        } else {
            ChatUtils.infoPrefix("Swarm", "Sent command to (highlight)%d(default) workers in group '%s'.", sent, group);
        }
    }

    /** Assign a worker to a group. */
    public void assignGroup(int workerId, String group) {
        SwarmWorkerInfo info = workers.get(workerId);
        if (info == null) {
            ChatUtils.errorPrefix("Swarm", "Worker #%d not found.", workerId);
            return;
        }
        info.group = group;
        sendToWorker(workerId, SwarmProtocol.GROUP_ASSIGN, SwarmProtocol.serializeGroupAssign(group));
        ChatUtils.infoPrefix("Swarm", "Worker #%d assigned to group (highlight)%s", workerId, group);
    }

    /** Assign all workers to a group. */
    public void assignAllGroups(String group) {
        byte[] payload = SwarmProtocol.serializeGroupAssign(group);
        for (SwarmWorkerInfo info : workers.values()) {
            info.group = group;
            sendToWorker(info.id, SwarmProtocol.GROUP_ASSIGN, payload);
        }
        ChatUtils.infoPrefix("Swarm", "All workers assigned to group (highlight)%s", group);
    }

    /** Request a worker to drop an item. */
    public void requestDrop(int workerId, int slot) {
        byte[] payload = new byte[4];
        SwarmProtocol.putInt(payload, 0, slot);
        sendToWorker(workerId, SwarmProtocol.DROP_ITEM, payload);
    }

    /** Request a worker to find and drop an item for another worker. */
    public void requestItem(int fromWorkerId, String itemName) {
        sendToWorker(fromWorkerId, SwarmProtocol.INVENTORY_REQUEST, SwarmProtocol.serializeGroupAssign(itemName));
    }

    /** Ping all workers. */
    public void pingAll() {
        long now = System.currentTimeMillis();
        byte[] payload = new byte[8];
        SwarmProtocol.putLong(payload, 0, now);
        for (SwarmConnection conn : connections) {
            if (conn != null && conn.active) {
                conn.sendFrame(SwarmProtocol.PING, payload);
            }
        }
    }

    // --- Tick (periodic sync) ---

    /** Called from Swarm.onTick() on the host side. */
    public void tick() {
        tickCounter++;

        // Periodically broadcast claim table to all workers
        if (ussEnabled && tickCounter % ussSyncRate == 0) {
            broadcastClaimTable();
        }
    }

    private void broadcastClaimTable() {
        if (claimTable.isEmpty()) return;
        byte[] payload = SwarmProtocol.serializeClaimTable(claimTable);
        for (SwarmConnection conn : connections) {
            if (conn != null && conn.active) {
                conn.sendFrame(SwarmProtocol.CLAIM_TABLE_SYNC, payload);
            }
        }
    }

    // --- Cleanup ---

    public void removeWorker(int workerId) {
        // Clean claims
        claimTable.entrySet().removeIf(e -> SwarmProtocol.getWorkerIdFromValue(e.getValue()) == workerId);

        // Clean inventory
        hiveInventory.removeWorker(workerId);

        // Clean worker info
        workers.remove(workerId);

        // Clean connection
        for (int i = 0; i < connections.length; i++) {
            if (connections[i] != null && connections[i].workerId == workerId) {
                connections[i].active = false;
                connections[i] = null;
                break;
            }
        }

        ChatUtils.infoPrefix("Swarm", "Worker #%d removed.", workerId);
    }

    public void disconnect() {
        // Notify all workers
        byte[] payload = SwarmProtocol.serializeDisconnect("Host shutting down");
        for (SwarmConnection conn : connections) {
            if (conn != null) {
                try {
                    conn.sendFrame(SwarmProtocol.DISCONNECT, payload);
                } catch (Exception ignored) {}
                conn.disconnect();
            }
        }

        workers.clear();
        claimTable.clear();
        hiveInventory.clear();

        try {
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        ChatUtils.infoPrefix("Swarm", "HiveMind Host closed.");
        interrupt();
    }

    // --- Accessors ---

    public SwarmConnection[] getConnections() {
        return connections;
    }

    public SwarmConnection getConnectionByWorkerId(int workerId) {
        for (SwarmConnection conn : connections) {
            if (conn != null && conn.workerId == workerId) return conn;
        }
        return null;
    }

    public Collection<SwarmWorkerInfo> getWorkers() {
        return workers.values();
    }

    public SwarmWorkerInfo getWorker(int workerId) {
        return workers.get(workerId);
    }

    public SwarmHiveInventory getHiveInventory() {
        return hiveInventory;
    }

    public Map<Long, Integer> getClaimTable() {
        return claimTable;
    }

    public int getConnectionCount() {
        int count = 0;
        for (SwarmConnection conn : connections) {
            if (conn != null && conn.active) count++;
        }
        return count;
    }

    public boolean isInterrupted() {
        return Thread.currentThread().isInterrupted() || (socket != null && socket.isClosed());
    }
}
