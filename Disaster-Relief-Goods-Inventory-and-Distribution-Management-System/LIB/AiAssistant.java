package LIB;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Optional LLM adapter (Ollama locally, or Groq over HTTPS). It turns messages into text or tool
 * calls. It has no inventory write methods, and it never stores an API key.
 */
public final class AiAssistant {
    public static final String PROVIDER_OLLAMA = "Ollama";
    public static final String PROVIDER_GROQ = "Groq";
    public static final String OLLAMA_ENDPOINT = "http://localhost:11434/api/generate";
    public static final String OLLAMA_CHAT = "http://localhost:11434/api/chat";
    public static final String OLLAMA_MODEL = "barangay-qwen";
    public static final String GROQ_ENDPOINT = "https://api.groq.com/openai/v1/chat/completions";
    public static final String GROQ_MODEL = "openai/gpt-oss-120b";
    public static final int MAX_QUESTION_LENGTH = 2000;

    private static final Path SETTINGS = Paths.get(System.getProperty("user.home"), ".barangaycare-ai.properties");
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(90);

    public static final class Config {
        public String provider = PROVIDER_OLLAMA;
        public String model = OLLAMA_MODEL;
        public String endpoint = OLLAMA_ENDPOINT;
    }

    public static final class ToolCall {
        public final String id;
        public final String name;
        public final String arguments;

        public ToolCall(String id, String name, String arguments) {
            this.id = id;
            this.name = name;
            this.arguments = arguments == null ? "{}" : arguments;
        }
    }

    public static final class Turn {
        public final String content;
        public final List<ToolCall> toolCalls;
        public final long promptTokens;
        public final long completionTokens;
        public final long elapsedMillis;
        public final String provider;
        public final String model;

        public Turn(String content, List<ToolCall> toolCalls) {
            this(content, toolCalls, 0, 0, 0, "", "");
        }

        private Turn(String content, List<ToolCall> toolCalls, long promptTokens, long completionTokens,
                long elapsedMillis, String provider, String model) {
            this.content = content == null ? "" : content.trim();
            this.toolCalls = toolCalls == null ? List.of() : toolCalls;
            this.promptTokens = promptTokens;
            this.completionTokens = completionTokens;
            this.elapsedMillis = elapsedMillis;
            this.provider = provider;
            this.model = model;
        }

        public boolean wantsTools() {
            return !toolCalls.isEmpty();
        }

        private Turn measured(long elapsedMillis, String provider, String model) {
            return new Turn(content, toolCalls, promptTokens, completionTokens, elapsedMillis, provider, model);
        }
    }

    public static final class Message {
        public final String role;
        public final String content;
        public final String toolCallId;
        public final String name;
        public final List<ToolCall> toolCalls;

        public Message(String role, String content) {
            this(role, content, null, null, null);
        }

        public Message(String role, String content, String toolCallId, String name, List<ToolCall> toolCalls) {
            this.role = role;
            this.content = content;
            this.toolCallId = toolCallId;
            this.name = name;
            this.toolCalls = toolCalls;
        }

        public static Message system(String content) {
            return new Message("system", content);
        }

        public static Message user(String content) {
            return new Message("user", content);
        }

        public static Message assistant(String content, List<ToolCall> toolCalls) {
            return new Message("assistant", content, null, null, toolCalls);
        }

        public static Message tool(ToolCall call, String result) {
            return new Message("tool", result, call.id, call.name, null);
        }
    }

    private AiAssistant() {
    }

    public static Config loadConfig() {
        Properties properties = new Properties();
        if (Files.isReadable(SETTINGS)) {
            try (InputStream in = Files.newInputStream(SETTINGS)) {
                properties.load(in);
            } catch (IOException unreadable) {
                // keep the defaults
            }
        }
        Config config = new Config();
        String saved = properties.getProperty("provider", "").trim();
        if (PROVIDER_GROQ.equals(saved)) {
            config.provider = PROVIDER_GROQ;
            config.model = orDefault(properties.getProperty("model"), GROQ_MODEL);
            config.endpoint = orDefault(properties.getProperty("endpoint"), GROQ_ENDPOINT);
        } else if (PROVIDER_OLLAMA.equals(saved)) {
            config.model = orDefault(properties.getProperty("model"), OLLAMA_MODEL);
            config.endpoint = orDefault(properties.getProperty("endpoint"), OLLAMA_ENDPOINT);
        }
        return config;
    }

    public static void saveConfig(String provider, String model, String endpoint) throws IOException {
        if (!PROVIDER_OLLAMA.equals(provider) && !PROVIDER_GROQ.equals(provider)) {
            throw new IllegalArgumentException("Choose Ollama or Groq.");
        }
        String cleanModel = model == null ? "" : model.trim();
        if (cleanModel.isEmpty()) {
            throw new IllegalArgumentException("Enter a model name.");
        }
        if (!isWebAddress(endpoint)) {
            throw new IllegalArgumentException("Enter a valid HTTP or HTTPS endpoint.");
        }
        Properties properties = new Properties();
        properties.setProperty("provider", provider);
        properties.setProperty("model", cleanModel);
        properties.setProperty("endpoint", endpoint.trim());
        try (OutputStream out = Files.newOutputStream(SETTINGS)) {
            properties.store(out, "BarangayCare AI provider settings; API keys are never stored here.");
        }
    }

    /** One-shot completion used by the legacy generate path. */
    public static String generate(String systemContext, String question) {
        String asked = question == null ? "" : question.trim();
        if (asked.length() > MAX_QUESTION_LENGTH) {
            throw new IllegalArgumentException(String.format("Keep the question under %,d characters.", MAX_QUESTION_LENGTH));
        }
        List<Message> messages = new ArrayList<>();
        messages.add(Message.system("You are BarangayCare's disaster-relief decision-support assistant. "
                + "Use only verified application context. Never invent data. Do not approve or execute transactions."));
        messages.add(Message.user("VERIFIED APPLICATION CONTEXT:\n" + systemContext + "\n\nUSER REQUEST:\n"
                + (asked.isEmpty()
                        ? "Provide a concise situation assessment, priority concerns, recommended checks, and missing information."
                        : asked)));
        return chat(messages, List.of()).content;
    }

    public static Turn chat(List<Message> messages, List<Map<String, Object>> tools) {
        Config config = loadConfig();
        if (PROVIDER_GROQ.equals(config.provider) && AiPrivacyGuard.messagesContainPersonalData(messages)) {
            throw new IllegalStateException("This request may contain household or contact details. "
                    + "For privacy, use the local Ollama provider; no request was sent to Groq.");
        }
        long started = System.nanoTime();
        Turn turn = PROVIDER_GROQ.equals(config.provider)
                ? groq(config, messages, tools) : ollama(config, messages, tools);
        return turn.measured((System.nanoTime() - started) / 1_000_000L, config.provider, config.model);
    }

    private static Turn ollama(Config config, List<Message> messages, List<Map<String, Object>> tools) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.model);
        body.put("messages", ollamaMessages(messages));
        body.put("stream", false);
        body.put("options", Map.of("temperature", 0.2));
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools);
        }
        Map<String, Object> reply = post(chatEndpoint(config.endpoint), Json.stringify(body), Map.of(),
                PROVIDER_OLLAMA, "Check that Ollama is running and the model is installed.");
        Map<String, Object> message = Json.asObject(reply.get("message"));
        if (message.isEmpty() && reply.get("response") instanceof String text) {
            return new Turn(text, List.of(), count(reply, "prompt_eval_count"), count(reply, "eval_count"), 0,
                    PROVIDER_OLLAMA, config.model);
        }
        return new Turn(Json.text(message.get("content")), parseToolCalls(message.get("tool_calls")),
                count(reply, "prompt_eval_count"), count(reply, "eval_count"), 0, PROVIDER_OLLAMA, config.model);
    }

    private static Turn groq(Config config, List<Message> messages, List<Map<String, Object>> tools) {
        String key = System.getenv("GROQ_API_KEY");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(
                    "Groq needs GROQ_API_KEY in your environment. The key is not saved in the application.");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.model);
        body.put("messages", groqMessages(messages));
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools);
            body.put("tool_choice", "auto");
        }
        Map<String, Object> reply = post(config.endpoint, Json.stringify(body), Map.of("Authorization", "Bearer " + key),
                PROVIDER_GROQ, "Check GROQ_API_KEY, the model name, the endpoint, and your Groq account limits.");
        Map<String, Object> message = firstChoice(reply);
        if (message.isEmpty()) {
            throw new IllegalStateException("Groq returned an empty response. Check the selected model.");
        }
        Map<String, Object> usage = Json.asObject(reply.get("usage"));
        return new Turn(Json.text(message.get("content")), parseToolCalls(message.get("tool_calls")),
                count(usage, "prompt_tokens"), count(usage, "completion_tokens"), 0,
                PROVIDER_GROQ, config.model);
    }

    private static List<Map<String, Object>> groqMessages(List<Message> messages) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Message message : messages) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("role", message.role);
            if ("tool".equals(message.role)) {
                row.put("tool_call_id", message.toolCallId);
                if (message.name != null) {
                    row.put("name", message.name);
                }
                row.put("content", message.content == null ? "" : message.content);
            } else if (message.toolCalls != null && !message.toolCalls.isEmpty()) {
                if (message.content != null && !message.content.isBlank()) {
                    row.put("content", message.content);
                }
                List<Map<String, Object>> calls = new ArrayList<>();
                for (ToolCall call : message.toolCalls) {
                    Map<String, Object> fn = new LinkedHashMap<>();
                    fn.put("name", call.name);
                    fn.put("arguments", call.arguments);
                    calls.add(Map.of("id", call.id, "type", "function", "function", fn));
                }
                row.put("tool_calls", calls);
            } else {
                row.put("content", message.content == null ? "" : message.content);
            }
            out.add(row);
        }
        return out;
    }

    private static List<Map<String, Object>> ollamaMessages(List<Message> messages) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Message message : messages) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("role", "tool".equals(message.role) ? "tool" : message.role);
            row.put("content", message.content == null ? "" : message.content);
            if (message.toolCalls != null && !message.toolCalls.isEmpty()) {
                List<Map<String, Object>> calls = new ArrayList<>();
                for (ToolCall call : message.toolCalls) {
                    Map<String, Object> fn = new LinkedHashMap<>();
                    fn.put("name", call.name);
                    fn.put("arguments", Json.object(call.arguments) == null ? Map.of() : Json.object(call.arguments));
                    calls.add(Map.of("function", fn));
                }
                row.put("tool_calls", calls);
            }
            out.add(row);
        }
        return out;
    }

    private static List<ToolCall> parseToolCalls(Object raw) {
        List<ToolCall> calls = new ArrayList<>();
        int index = 0;
        for (Object entry : Json.asArray(raw)) {
            Map<String, Object> row = Json.asObject(entry);
            Map<String, Object> function = Json.asObject(row.get("function"));
            if (function.isEmpty() && row.get("name") != null) {
                function = row;
            }
            String name = Json.text(function.get("name"));
            if (name.isBlank()) {
                continue;
            }
            String id = Json.text(row.get("id"));
            if (id.isBlank()) {
                id = "call_" + (++index) + "_" + name;
            }
            Object args = function.get("arguments");
            String arguments = args instanceof String text ? text : Json.stringify(args == null ? Map.of() : args);
            calls.add(new ToolCall(id, name, arguments));
        }
        return calls;
    }

    private static Map<String, Object> firstChoice(Map<String, Object> reply) {
        List<Object> choices = Json.asArray(reply.get("choices"));
        if (choices.isEmpty()) {
            return Map.of();
        }
        return Json.asObject(Json.asObject(choices.get(0)).get("message"));
    }

    private static long count(Map<String, Object> values, String key) {
        Object value = values == null ? null : values.get(key);
        return value instanceof Number number ? Math.max(0, number.longValue()) : 0;
    }

    static String chatEndpoint(String endpoint) {
        if (endpoint == null) {
            return OLLAMA_CHAT;
        }
        String value = endpoint.trim();
        if (value.endsWith("/api/generate")) {
            return value.substring(0, value.length() - "/api/generate".length()) + "/api/chat";
        }
        return value;
    }

    private static Map<String, Object> post(String endpoint, String payload, Map<String, String> headers,
            String vendor, String hint) {
        HttpRequest.Builder builder;
        try {
            builder = HttpRequest.newBuilder(URI.create(endpoint)).timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json");
        } catch (IllegalArgumentException invalid) {
            throw new IllegalStateException("The " + vendor + " endpoint in AI settings is not a valid web address.",
                    invalid);
        }
        headers.forEach(builder::header);
        HttpRequest request = builder.POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response;
        try {
            response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException timeout) {
            throw new IllegalStateException(vendor + " did not answer within " + REQUEST_TIMEOUT.toSeconds()
                    + " seconds. " + hint, timeout);
        } catch (IOException unreachable) {
            throw new IllegalStateException("Could not reach " + vendor + " at " + endpoint + ". " + hint,
                    unreachable);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("The AI request was cancelled.", interrupted);
        }
        Map<String, Object> reply = Json.object(response.body());
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            String detail = errorDetail(reply).replaceAll("[.\\s]+$", "");
            throw new IllegalStateException(vendor + " returned HTTP " + status
                    + (detail.isEmpty() ? "" : ": " + detail) + ". " + hint);
        }
        if (reply == null) {
            throw new IllegalStateException(vendor + " returned an unreadable response. " + hint);
        }
        return reply;
    }

    private static String errorDetail(Map<String, Object> reply) {
        if (reply == null) {
            return "";
        }
        Object error = reply.get("error");
        if (error instanceof Map<?, ?> nested) {
            error = nested.get("message");
        }
        if (error instanceof String text && !text.isBlank()) {
            return text.length() > 200 ? text.substring(0, 200) + "…" : text;
        }
        return "";
    }

    static boolean isToolCapabilityError(String message) {
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("tool calling is not supported")
                || normalized.contains("does not support tool")
                || normalized.contains("tool use is not supported")
                || normalized.contains("unsupported tool calling");
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static boolean isWebAddress(String value) {
        if (value == null) {
            return false;
        }
        try {
            URI uri = new URI(value.trim());
            String scheme = uri.getScheme();
            return ("http".equals(scheme) || "https".equals(scheme)) && uri.getHost() != null;
        } catch (URISyntaxException notAUri) {
            return false;
        }
    }
}
