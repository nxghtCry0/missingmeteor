/*
 * This file is part of the MissingMeteor distribution (https://github.com/nxghtCry0/missingmeteor).
 * Copyright (c) nxghtCry0.
 */

package meteordevelopment.meteorclient.systems.modules.misc.swarm;

import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.pathing.PathManagers;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;

import java.io.*;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

/**
 * Swarm worker client. Connects to a host and receives commands,
 * while sending position updates, inventory data, and block claims back.
 */
public class SwarmWorker extends Thread {
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;

    public int workerId = -1;
    public String group = "default";
    public Block target;

    // Settings references (set by Swarm module)
    public boolean ussEnabled = true;
    public int ussSyncRate = 10;
    public boolean invSyncEnabled = false;
    public int invSyncRate = 100;
    public boolean invHotbarOnly = true;

    private int tickCounter = 0;
    public boolean connected = false;

    // Local claim table (received from host)
    public final Map<Long, Integer> claimTable = new HashMap<>();

    public SwarmWorker(String ip, int port) {
        try {
            socket = new Socket(ip, port);
            in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        } catch (Exception e) {
            socket = null;
            ChatUtils.warningPrefix("Swarm", "Server not found at %s on port %s.", ip, port);
            e.printStackTrace();
        }
        if (socket != null) start();
    }

    @Override
    public void run() {
        connected = true;
        ChatUtils.infoPrefix("Swarm", "Connected to HiveMind host at %s:%s.", getIp(), socket.getPort());

        try {
            // Step 1: Send handshake with protocol version
            SwarmProtocol.writeFrame(out, SwarmProtocol.HANDSHAKE, SwarmProtocol.serializeHandshake(SwarmProtocol.PROTOCOL_VERSION));
            out.flush();

            // Step 2: Receive worker ID assignment
            byte type = SwarmProtocol.readType(in);
            byte[] payload = SwarmProtocol.readPayload(in);
            if (type == SwarmProtocol.HANDSHAKE) {
                workerId = SwarmProtocol.deserializeHandshake(payload);
                ChatUtils.infoPrefix("Swarm", "Assigned worker ID: (highlight)#%d", workerId);
            } else {
                ChatUtils.errorPrefix("Swarm", "Expected handshake response, got type %d.", type);
                disconnect();
                return;
            }

            // Step 3: Main loop — read messages from host
            while (connected && !isInterrupted()) {
                if (in.available() > 0) {
                    byte msgType = SwarmProtocol.readType(in);
                    byte[] msgPayload = SwarmProtocol.readPayload(in);
                    handleHostMessage(msgType, msgPayload);
                } else {
                    Thread.sleep(5);
                }
            }
        } catch (IOException | InterruptedException e) {
            if (connected) {
                ChatUtils.errorPrefix("Swarm", "Connection to host lost: %s", e.getMessage());
            }
        } finally {
            connected = false;
            try { in.close(); } catch (IOException ignored) {}
            try { out.close(); } catch (IOException ignored) {}
            ChatUtils.infoPrefix("Swarm", "Disconnected from host.");
        }
    }

    private void handleHostMessage(byte type, byte[] payload) {
        switch (type) {
            case SwarmProtocol.COMMAND -> {
                String command = SwarmProtocol.deserializeCommand(payload);
                ChatUtils.infoPrefix("Swarm", "Received command: (highlight)%s", command);
                try {
                    Commands.dispatch(command);
                } catch (Exception e) {
                    ChatUtils.errorPrefix("Swarm", "Error executing command: %s", e.getMessage());
                }
            }
            case SwarmProtocol.GROUP_ASSIGN -> {
                group = SwarmProtocol.deserializeGroupAssign(payload);
                ChatUtils.infoPrefix("Swarm", "Assigned to group: (highlight)%s", group);
            }
            case SwarmProtocol.CLAIM_TABLE_SYNC -> {
                claimTable.clear();
                claimTable.putAll(SwarmProtocol.deserializeClaimTable(payload));
            }
            case SwarmProtocol.WORKER_LIST -> {
                // Workers can optionally process the full worker list for awareness
                // For now, this is mainly used by the host
            }
            case SwarmProtocol.DROP_ITEM -> {
                // payload = [slotIndex:int]
                int slot = SwarmProtocol.readInt(payload, 0);
                if (MeteorClient.mc.player != null) {
                    ItemStack stack = MeteorClient.mc.player.getInventory().getStack(slot);
                    if (!stack.isEmpty()) {
                        MeteorClient.mc.player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket(
                            net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action.DROP_ITEM,
                            net.minecraft.util.math.BlockPos.ORIGIN,
                            net.minecraft.util.math.Direction.DOWN
                        ));
                    }
                }
            }
            case SwarmProtocol.INVENTORY_REQUEST -> {
                // payload = [itemNameLen:int][itemName:bytes]
                String itemName = SwarmProtocol.readString(payload, 0);
                ChatUtils.infoPrefix("Swarm", "Host requests item: (highlight)%s", itemName);
                // Find and drop the item (simplified — drops first match from hotbar)
                if (MeteorClient.mc.player != null) {
                    for (int i = 0; i < 9; i++) {
                        ItemStack stack = MeteorClient.mc.player.getInventory().getStack(i);
                        if (!stack.isEmpty() && stack.getName().getString().contains(itemName)) {
                            InvUtils.drop().fromId(i);
                            break;
                        }
                    }
                }
            }
            case SwarmProtocol.PATH_CONFLICT -> {
                ChatUtils.warningPrefix("Swarm", "Path conflict detected! Adjusting target...");
                PathManagers.get().stop();
            }
            case SwarmProtocol.DISCONNECT -> {
                String reason = SwarmProtocol.deserializeDisconnect(payload);
                ChatUtils.warningPrefix("Swarm", "Kicked by host: %s", reason);
                disconnect();
            }
            default -> ChatUtils.warningPrefix("Swarm", "Unknown message type: %d", type);
        }
    }

    /**
     * Called every tick from Swarm module.
     * Sends periodic updates (position, inventory) to host.
     */
    public void tick() {
        if (!connected || MeteorClient.mc.player == null) return;

        tickCounter++;

        // Handle mining target (legacy support)
        if (target != null) {
            PathManagers.get().stop();
            PathManagers.get().mine(target);
            // Claim the block
            sendBlockClaim(target);
            target = null;
        }

        // Send position update every tick (it's small — 32 bytes)
        if (ussEnabled) {
            try {
                SwarmProtocol.writeFrame(out, SwarmProtocol.POSITION_UPDATE,
                    SwarmProtocol.serializePosition(
                        MeteorClient.mc.player.getX(), MeteorClient.mc.player.getY(), MeteorClient.mc.player.getZ(),
                        MeteorClient.mc.player.getYaw(), MeteorClient.mc.player.getPitch()
                    )
                );
                out.flush();
            } catch (IOException e) {
                connected = false;
            }
        }

        // Send inventory update periodically
        if (invSyncEnabled && tickCounter % invSyncRate == 0) {
            sendInventoryUpdate();
        }

        // Send group assignment if it changed
        // (group is set by host message, so no outgoing needed)
    }

    private void sendBlockClaim(Block block) {
        if (MeteorClient.mc.player == null) return;
        try {
            // Find the target block position from Baritone target or current look
            // For simplicity, claim the block the player is looking at
            net.minecraft.util.hit.BlockHitResult hit = (net.minecraft.util.hit.BlockHitResult) MeteorClient.mc.player.raycast(5, 0, false);
            if (hit != null && MeteorClient.mc.world.getBlockState(hit.getBlockPos()).getBlock() == block) {
                SwarmProtocol.writeFrame(out, SwarmProtocol.BLOCK_CLAIM,
                    SwarmProtocol.serializeBlockPos(hit.getBlockPos().getX(), hit.getBlockPos().getY(), hit.getBlockPos().getZ())
                );
                out.flush();
            }
        } catch (IOException ignored) {}
    }

    private void sendInventoryUpdate() {
        if (MeteorClient.mc.player == null) return;
        try {
            int maxSlot = invHotbarOnly ? 9 : 41; // 9 hotbar, 41 full inv
            java.util.List<int[]> entries = new java.util.ArrayList<>();
            for (int i = 0; i < maxSlot; i++) {
                ItemStack stack = MeteorClient.mc.player.getInventory().getStack(i);
                if (!stack.isEmpty()) {
                    int rawId = net.minecraft.item.Item.getRawId(stack.getItem());
                    entries.add(new int[] { i, rawId, stack.getCount() });
                }
            }
            int[] slots = new int[entries.size()];
            int[] itemIds = new int[entries.size()];
            int[] counts = new int[entries.size()];
            for (int i = 0; i < entries.size(); i++) {
                slots[i] = entries.get(i)[0];
                itemIds[i] = entries.get(i)[1];
                counts[i] = entries.get(i)[2];
            }
            SwarmProtocol.writeFrame(out, SwarmProtocol.INVENTORY_UPDATE,
                SwarmProtocol.serializeInventory(slots, itemIds, counts)
            );
            out.flush();
        } catch (IOException ignored) {}
    }

    public void sendGroupRequest(String groupName) {
        try {
            SwarmProtocol.writeFrame(out, SwarmProtocol.GROUP_ASSIGN, SwarmProtocol.serializeGroupAssign(groupName));
            out.flush();
        } catch (IOException e) {
            connected = false;
        }
    }

    public void disconnect() {
        connected = false;
        try {
            if (socket != null && !socket.isClosed()) {
                SwarmProtocol.writeFrame(out, SwarmProtocol.DISCONNECT, SwarmProtocol.serializeDisconnect("Worker disconnecting"));
                out.flush();
            }
        } catch (IOException ignored) {}
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
        PathManagers.get().stop();
        interrupt();
    }

    public String getConnection() {
        return getIp() + ":" + (socket != null ? socket.getPort() : "?");
    }

    private String getIp() {
        if (socket == null) return "unknown";
        String ip = socket.getInetAddress().getHostAddress();
        return ip.equals("127.0.0.1") ? "localhost" : ip;
    }

    /**
     * Check if a block position is claimed by another worker.
     */
    public boolean isBlockClaimed(int x, int z) {
        if (!ussEnabled) return false;
        long key = SwarmProtocol.blockPosToKey(x, 0, z);
        return claimTable.containsKey(key) && SwarmProtocol.getWorkerIdFromValue(claimTable.get(key)) != workerId;
    }
}
