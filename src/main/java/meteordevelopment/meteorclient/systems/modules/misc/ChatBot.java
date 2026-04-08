/*
 * This file is part of the MissingMeteor distribution (https://github.com/nxghtCry0/missingmeteor).
 * Copyright (c) nxghtCry0.
 */

package meteordevelopment.meteorclient.systems.modules.misc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.game.SendMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatBot extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgApi = settings.createGroup("API");
    private final SettingGroup sgBehavior = settings.createGroup("Behavior");
    private final SettingGroup sgMath = settings.createGroup("Math");

    // General

    private final Setting<Boolean> enabled = sgGeneral.add(new BoolSetting.Builder()
        .name("enabled")
        .description("Master toggle for chat responses.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> cooldown = sgGeneral.add(new DoubleSetting.Builder()
        .name("cooldown")
        .description("Minimum seconds between responses to avoid spam.")
        .defaultValue(3.0)
        .min(0)
        .sliderMax(30)
        .build()
    );

    private final Setting<Boolean> logResponses = sgGeneral.add(new BoolSetting.Builder()
        .name("log-responses")
        .description("Log AI responses in chat with a prefix.")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> responsePrefix = sgGeneral.add(new StringSetting.Builder()
        .name("response-prefix")
        .description("Prefix added before AI responses in chat.")
        .defaultValue("[ChatBot] ")
        .visible(logResponses::get)
        .build()
    );

    // API

    private final Setting<String> apiKey = sgApi.add(new StringSetting.Builder()
        .name("api-key")
        .description("Your NVIDIA NIM API key.")
        .defaultValue("")
        .build()
    );

    private final Setting<String> apiUrl = sgApi.add(new StringSetting.Builder()
        .name("api-url")
        .description("The NVIDIA NIM API endpoint URL.")
        .defaultValue("https://integrate.api.nvidia.com/v1/chat/completions")
        .build()
    );

    private final Setting<String> model = sgApi.add(new StringSetting.Builder()
        .name("model")
        .description("The model to use (e.g., meta/llama-3.1-8b-instruct).")
        .defaultValue("meta/llama-3.1-8b-instruct")
        .build()
    );

    private final Setting<Double> temperature = sgApi.add(new DoubleSetting.Builder()
        .name("temperature")
        .description("Controls randomness. Lower = more deterministic, higher = more creative.")
        .defaultValue(0.7)
        .min(0)
        .sliderMax(2.0)
        .build()
    );

    private final Setting<Integer> maxTokens = sgApi.add(new IntSetting.Builder()
        .name("max-tokens")
        .description("Maximum number of tokens in the AI response.")
        .defaultValue(256)
        .min(1)
        .sliderRange(1, 2048)
        .build()
    );

    private final Setting<Integer> timeout = sgApi.add(new IntSetting.Builder()
        .name("timeout")
        .description("HTTP request timeout in seconds.")
        .defaultValue(15)
        .min(1)
        .sliderRange(1, 60)
        .build()
    );

    // Behavior

    private final Setting<TriggerMode> triggerMode = sgBehavior.add(new EnumSetting.Builder<TriggerMode>()
        .name("trigger-mode")
        .description("When the bot should respond to chat messages.")
        .defaultValue(TriggerMode.WhenMentioned)
        .build()
    );

    private final Setting<String> triggerKeyword = sgBehavior.add(new StringSetting.Builder()
        .name("trigger-keyword")
        .description("Keyword that triggers a response when in Keyword mode.")
        .defaultValue("!bot")
        .visible(() -> triggerMode.get() == TriggerMode.Keyword)
        .build()
    );

    private final Setting<String> playerName = sgBehavior.add(new StringSetting.Builder()
        .name("player-name")
        .description("Your player name used for mention detection.")
        .defaultValue("")
        .visible(() -> triggerMode.get() == TriggerMode.WhenMentioned)
        .build()
    );

    private final Setting<Boolean> ignoreSelf = sgBehavior.add(new BoolSetting.Builder()
        .name("ignore-self")
        .description("Ignore messages sent by yourself.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> ignoreServerMessages = sgBehavior.add(new BoolSetting.Builder()
        .name("ignore-server-messages")
        .description("Ignore messages that appear to be server/system messages (no player prefix).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> respondToDirectMessages = sgBehavior.add(new BoolSetting.Builder()
        .name("respond-to-dms")
        .description("Respond to private/direct messages (e.g., from /msg or /tell).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> chatHistory = sgBehavior.add(new BoolSetting.Builder()
        .name("chat-history")
        .description("Include recent conversation history for context.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> historySize = sgBehavior.add(new IntSetting.Builder()
        .name("history-size")
        .description("Number of recent messages to include as context.")
        .defaultValue(10)
        .min(1)
        .sliderRange(1, 30)
        .visible(chatHistory::get)
        .build()
    );

    private final Setting<String> systemPrompt = sgBehavior.add(new StringSetting.Builder()
        .name("system-prompt")
        .description("System prompt that defines the bot's behavior and personality.")
        .defaultValue("You are a helpful and friendly Minecraft chat assistant. Keep responses concise (under 200 characters). Be witty and fun. You are playing Minecraft on a server.")
        .build()
    );

    // Math

    private final Setting<Boolean> mathMode = sgMath.add(new BoolSetting.Builder()
        .name("math-mode")
        .description("Automatically detect and solve math problems in chat.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> mathProbability = sgMath.add(new DoubleSetting.Builder()
        .name("math-probability")
        .description("Chance of responding to a detected math problem as a percentage.")
        .defaultValue(100)
        .min(0)
        .sliderMax(100)
        .visible(mathMode::get)
        .build()
    );

    private final Setting<String> mathPrompt = sgMath.add(new StringSetting.Builder()
        .name("math-prompt")
        .description("System prompt override when solving math problems.")
        .defaultValue("You are a math expert. Solve the given math problem. Only output the final answer, nothing else. Keep it very short.")
        .visible(mathMode::get)
        .build()
    );

    private final Pattern mathPattern = Pattern.compile(".*\\b\\d+\\s*[+\\-*/^%]\\s*\\d+.*");

    private final Queue<String> sendQueue = new ArrayDeque<>();
    private final List<String[]> messageHistory = new ArrayList<>();
    private long lastResponseTime = 0;
    private boolean isRequesting = false;
    private String lastSender = "";

    public ChatBot() {
        super(Categories.Misc, "chat-bot", "Uses NVIDIA NIM AI to respond to chat messages and solve math problems.");
    }

    @Override
    public void onDeactivate() {
        sendQueue.clear();
        messageHistory.clear();
        isRequesting = false;
        lastResponseTime = 0;
    }

    @EventHandler
    private void onSendMessage(SendMessageEvent event) {
        if (!isActive() || !ignoreSelf.get()) return;

        String msg = event.message;
        if (msg.startsWith("/")) return;

        // Track own messages for context
        String cleanMsg = msg;
        if (playerName.get().isEmpty() && mc.player != null) {
            cleanMsg = "[" + mc.player.getName().getString() + "] " + msg;
        } else if (!playerName.get().isEmpty()) {
            cleanMsg = "[" + playerName.get() + "] " + msg;
        }
        messageHistory.add(new String[]{"user", cleanMsg});
        trimHistory();
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (!isActive() || !enabled.get() || mc.player == null) return;

        String messageString = event.getMessage().getString();
        if (messageString == null || messageString.isEmpty()) return;

        // Extract sender and content
        String sender = extractSender(messageString);
        String content = extractContent(messageString);

        if (content.isEmpty()) return;

        // Track incoming message for context
        messageHistory.add(new String[]{"other", messageString});
        trimHistory();

        // Ignore self
        if (ignoreSelf.get()) {
            String myName = playerName.get().isEmpty() ? mc.player.getName().getString() : playerName.get();
            if (sender.equalsIgnoreCase(myName)) return;
        }

        // Ignore server messages (messages without a clear player sender prefix)
        if (ignoreServerMessages.get() && sender.isEmpty()) return;

        lastSender = sender;

        // Check if this is a DM
        boolean isDM = content.toLowerCase().startsWith("[dm]") || content.toLowerCase().startsWith("(dm)")
            || messageString.toLowerCase().contains("whispers:") || messageString.toLowerCase().contains("whispers to you");

        if (isDM && !respondToDirectMessages.get()) return;

        // Math mode check
        if (mathMode.get() && containsMath(content)) {
            if (mathProbability.get() >= 100 || Math.random() * 100 < mathProbability.get()) {
                requestAI(content, true);
                return;
            }
        }

        // Trigger mode check
        boolean shouldRespond = false;
        switch (triggerMode.get()) {
            case AllMessages -> shouldRespond = true;
            case WhenMentioned -> {
                String name = playerName.get().isEmpty() ? mc.player.getName().getString() : playerName.get();
                shouldRespond = content.toLowerCase().contains(name.toLowerCase());
            }
            case Keyword -> shouldRespond = content.toLowerCase().contains(triggerKeyword.get().toLowerCase());
        }

        if (shouldRespond) {
            requestAI(content, false);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!isActive() || mc.player == null) return;

        // Send queued messages with cooldown
        if (!sendQueue.isEmpty()) {
            long now = System.currentTimeMillis();
            if (now - lastResponseTime >= cooldown.get() * 1000) {
                String msg = sendQueue.poll();
                ChatUtils.sendPlayerMsg(msg);
                lastResponseTime = now;
            }
        }
    }

    private void requestAI(String userMessage, boolean isMath) {
        if (isRequesting) return;
        if (apiKey.get().isEmpty()) {
            error("No API key set. Configure your NVIDIA NIM API key first.");
            return;
        }

        isRequesting = true;

        try {
            Thread thread = new Thread(() -> {
                try {
                    String response = callNimApi(userMessage, isMath);

                    if (response != null && !response.isEmpty()) {
                        messageHistory.add(new String[]{"assistant", response});
                        trimHistory();

                        if (logResponses.get()) {
                            sendQueue.add(responsePrefix.get() + response);
                        } else {
                            sendQueue.add(response);
                        }
                    }
                } catch (Exception e) {
                    error("ChatBot API error: %s", e.getMessage());
                } finally {
                    isRequesting = false;
                }
            });
            thread.setDaemon(true);
            thread.start();
        } catch (Exception e) {
            error("Failed to start API request thread: %s", e.getMessage());
            isRequesting = false;
        }
    }

    private String callNimApi(String userMessage, boolean isMath) throws Exception {
        URI uri = new URI(apiUrl.get());
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();

        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey.get());
        conn.setConnectTimeout(timeout.get() * 1000);
        conn.setReadTimeout(timeout.get() * 1000);
        conn.setDoOutput(true);

        // Build messages array
        JsonObject body = new JsonObject();
        body.addProperty("model", model.get());
        body.addProperty("temperature", temperature.get());
        body.addProperty("max_tokens", maxTokens.get());
        body.addProperty("stream", false);

        JsonArray messages = new JsonArray();

        // System prompt
        JsonObject sysMsg = new JsonObject();
        sysMsg.addProperty("role", "system");
        sysMsg.addProperty("content", isMath ? mathPrompt.get() : systemPrompt.get());
        messages.add(sysMsg);

        // Chat history
        if (chatHistory.get()) {
            int start = Math.max(0, messageHistory.size() - historySize.get());
            for (int i = start; i < messageHistory.size(); i++) {
                String[] entry = messageHistory.get(i);
                JsonObject histMsg = new JsonObject();
                histMsg.addProperty("role", "user");
                histMsg.addProperty("content", entry[1]);
                messages.add(histMsg);
            }
        }

        // Current user message
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        String fullMessage = userMessage;
        if (!lastSender.isEmpty()) {
            fullMessage = "<" + lastSender + "> " + userMessage;
        }
        userMsg.addProperty("content", fullMessage);
        messages.add(userMsg);

        body.add("messages", messages);

        // Send request
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }

        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            BufferedReader errReader = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8));
            StringBuilder errResponse = new StringBuilder();
            String line;
            while ((line = errReader.readLine()) != null) {
                errResponse.append(line);
            }
            errReader.close();
            throw new RuntimeException("API returned " + responseCode + ": " + errResponse);
        }

        // Read response
        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();

        // Parse JSON response
        JsonObject json = JsonParser.parseString(response.toString()).getAsJsonObject();
        JsonArray choices = json.getAsJsonArray("choices");

        if (choices != null && !choices.isEmpty()) {
            JsonObject firstChoice = choices.get(0).getAsJsonObject();
            JsonObject messageObj = firstChoice.getAsJsonObject("message");
            String content = messageObj.get("content").getAsString();
            // Clean up the response - strip newlines for chat
            return content.trim().replace("\n", " ").replaceAll("\\s+", " ");
        }

        return null;
    }

    private String extractSender(String message) {
        // Common formats: <Player>, [Player], Player:, etc.
        Matcher matcher = Pattern.compile("^[<\\[]([^>\\]]+)[>\\]]\\s*").matcher(message);
        if (matcher.find()) return matcher.group(1);

        matcher = Pattern.compile("^([^:]+):\\s*").matcher(message);
        if (matcher.find()) return matcher.group(1).trim();

        return "";
    }

    private String extractContent(String message) {
        // Remove common sender prefixes
        String content = message.replaceFirst("^[<\\[]([^>\\]]+)[>\\]]\\s*", "");
        content = content.replaceFirst("^[^:]+:\\s*", "");
        return content.trim();
    }

    private boolean containsMath(String message) {
        return mathPattern.matcher(message).matches();
    }

    private void trimHistory() {
        int maxSize = chatHistory.get() ? historySize.get() * 2 : 0;
        while (messageHistory.size() > maxSize) {
            messageHistory.removeFirst();
        }
    }

    @Override
    public String getInfoString() {
        return isRequesting ? "Thinking..." : (sendQueue.isEmpty() ? null : "Queued");
    }

    public enum TriggerMode {
        AllMessages,
        WhenMentioned,
        Keyword
    }
}
