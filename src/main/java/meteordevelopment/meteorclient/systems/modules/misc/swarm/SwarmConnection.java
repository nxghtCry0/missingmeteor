/*
 * This file is part of the MissingMeteor distribution (https://github.com/nxghtCry0/missingmeteor).
 * Copyright (c) nxghtCry0.
 */

package meteordevelopment.meteorclient.systems.modules.misc.swarm;

import meteordevelopment.meteorclient.utils.player.ChatUtils;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Handles bidirectional binary communication with a single swarm worker.
 * Runs on the host side — one SwarmConnection per connected worker.
 */
public class SwarmConnection extends Thread {
    public final Socket socket;
    private final SwarmHost host;
    public final DataInputStream in;
    public final DataOutputStream out;

    public int workerId = -1;
    public SwarmWorkerInfo info;
    public boolean active = false;

    private final ConcurrentLinkedQueue<byte[]> sendQueue = new ConcurrentLinkedQueue<>();

    public SwarmConnection(Socket socket, SwarmHost host) throws IOException {
        this.socket = socket;
        this.host = host;
        this.in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        this.out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        // Do NOT start the thread here — the host does the handshake first, then calls startReading()
    }

    /**
     * Start the read loop. Called after the host completes the handshake.
     */
    public void startReading() {
        start();
    }

    @Override
    public void run() {
        active = true;

        try {
            while (active && !isInterrupted()) {
                // Send queued messages
                while (!sendQueue.isEmpty()) {
                    byte[] frame = sendQueue.poll();
                    if (frame != null) {
                        out.write(frame);
                    }
                }
                out.flush();

                // Check for incoming data (non-blocking via available)
                if (in.available() > 0) {
                    byte type = in.readByte();
                    byte[] payload = SwarmProtocol.readPayload(in);
                    host.handleWorkerMessage(workerId, type, payload);
                } else {
                    Thread.sleep(5); // Small sleep to avoid busy loop
                }
            }
        } catch (IOException | InterruptedException e) {
            if (active) {
                ChatUtils.warningPrefix("Swarm", "Worker #%d (%s) disconnected: %s", workerId,
                    info != null ? info.playerName : "?", e.getMessage());
            }
        } finally {
            active = false;
            host.removeWorker(workerId);
            try { in.close(); } catch (IOException ignored) {}
            try { out.close(); } catch (IOException ignored) {}
        }
    }

    /**
     * Queue a binary frame to send to this worker.
     */
    public void sendFrame(byte type, byte[] payload) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream tempOut = new DataOutputStream(baos);
            SwarmProtocol.writeFrame(tempOut, type, payload);
            sendQueue.add(baos.toByteArray());
        } catch (IOException e) {
            ChatUtils.errorPrefix("Swarm", "Error queuing message to worker #%d.", workerId);
        }
    }

    /**
     * Send a command string to this worker.
     */
    public void sendCommand(String command) {
        sendFrame(SwarmProtocol.COMMAND, SwarmProtocol.serializeCommand(command));
    }

    public void disconnect() {
        active = false;
        try { socket.close(); } catch (IOException ignored) {}
        interrupt();
    }

    public String getConnection() {
        return getIp(socket.getInetAddress().getHostAddress()) + ":" + socket.getPort();
    }

    private String getIp(String ip) {
        return ip.equals("127.0.0.1") ? "localhost" : ip;
    }
}
