import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class BizAnalysisServer {
    private static final Set<String> TYPE_KEYS = new LinkedHashSet<>(Arrays.asList("분양", "임대", "예정", "미분류"));
    private static final String DEFAULT_TYPE = "미분류";
    private static final Set<String> ALLOWED_EXT = Set.of(".pdf", ".xlsx", ".xls");
    private static final long MAX_UPLOAD_SIZE = 20L * 1024L * 1024L;
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Path rootDir;
    private final Path publicDir;
    private final Path uploadDir;
    private final DataStore store;

    public static void main(String[] args) throws Exception {
        int port = intEnv("PORT", 8080);
        Path root = Path.of("").toAbsolutePath().normalize();
        BizAnalysisServer app = new BizAnalysisServer(root);
        app.start(port);
    }

    private static int intEnv(String key, int fallback) {
        try {
            String value = System.getenv(key);
            return value == null || value.isBlank() ? fallback : Integer.parseInt(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    BizAnalysisServer(Path rootDir) throws IOException {
        this.rootDir = rootDir;
        this.publicDir = rootDir.resolve("public").normalize();
        this.uploadDir = rootDir.resolve("uploads").normalize();
        Files.createDirectories(uploadDir);
        this.store = new DataStore(rootDir.resolve("data").resolve("sites.json"));
    }

    void start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newFixedThreadPool(Math.max(4, Runtime.getRuntime().availableProcessors())));
        server.start();
        System.out.println("Java version: http://localhost:" + port);
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod();
            String path = cleanPath(exchange.getRequestURI().getPath());

            if (path.equals("/api/health") && method.equals("GET")) {
                sendJson(exchange, 200, mapOf("success", true, "app", "biz-analysis-hub-java"));
                return;
            }

            if (path.equals("/api/sites") && method.equals("GET")) {
                handleListSites(exchange);
                return;
            }

            if (path.equals("/api/upload") && method.equals("POST")) {
                handleUpload(exchange);
                return;
            }

            if (path.equals("/api/save") && method.equals("POST")) {
                handleSave(exchange);
                return;
            }

            if (path.equals("/api/site-order") && method.equals("GET")) {
                handleGetOrder(exchange);
                return;
            }

            if (path.equals("/api/site-order") && method.equals("POST")) {
                handleSaveOrder(exchange);
                return;
            }

            if (path.startsWith("/api/sites/")) {
                handleSite(exchange, path, method);
                return;
            }

            serveStatic(exchange, path);
        } catch (ClientError e) {
            sendJson(exchange, e.status, mapOf("success", false, "error", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            sendJson(exchange, 500, mapOf("success", false, "error", e.getMessage()));
        }
    }

    private void handleListSites(HttpExchange exchange) throws IOException {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> site : store.sortedSites()) {
            Map<String, Object> values = asMap(site.get("values"));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", string(site.get("id")));
            row.put("name", string(site.get("name")));
            row.put("rev", number(values.get("rev")));
            row.put("cost", number(values.get("cost")));
            row.put("profit", number(values.get("profit")));
            row.put("roi", number(values.get("roi")));
            row.put("land", number(values.get("land")));
            row.put("constr", number(values.get("constr")));
            row.put("sales", number(values.get("sales")));
            row.put("levy", number(values.get("levy")));
            row.put("finance", number(values.get("finance")));
            row.put("incid", number(values.get("incid")));
            row.put("type", normalizeType(site.get("type")));
            row.put("orderIdx", intNumber(site.get("orderIdx"), 9999));
            result.add(row);
        }
        sendJson(exchange, 200, result);
    }

    private void handleSite(HttpExchange exchange, String path, String method) throws IOException {
        String suffix = path.substring("/api/sites/".length());
        boolean namePatch = suffix.endsWith("/name");
        String id = safeId(namePatch ? suffix.substring(0, suffix.length() - "/name".length()) : suffix);

        if (namePatch && method.equals("PATCH")) {
            Map<String, Object> body = parseObject(readUtf8(exchange, 1024 * 1024));
            String name = string(body.get("name")).trim();
            if (name.isEmpty()) throw new ClientError(400, "현장명을 입력하세요.");
            if (!store.rename(id, name)) throw new ClientError(404, "현장을 찾을 수 없습니다.");
            sendJson(exchange, 200, mapOf("success", true));
            return;
        }

        if (method.equals("GET")) {
            Map<String, Object> site = store.find(id);
            if (site == null) throw new ClientError(404, "현장을 찾을 수 없습니다.");
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", string(site.get("id")));
            result.put("name", string(site.get("name")));
            result.put("type", normalizeType(site.get("type")));
            result.put("values", fallback(site.get("values"), new LinkedHashMap<>()));
            result.put("tree", fallback(site.get("tree"), new ArrayList<>()));
            result.put("line_items", fallback(site.get("line_items"), new ArrayList<>()));
            result.put("traces", fallback(site.get("traces"), new LinkedHashMap<>()));
            sendJson(exchange, 200, result);
            return;
        }

        if (method.equals("DELETE")) {
            if (!store.delete(id)) throw new ClientError(404, "현장을 찾을 수 없습니다.");
            sendJson(exchange, 200, mapOf("success", true));
            return;
        }

        throw new ClientError(405, "지원하지 않는 메서드입니다.");
    }

    private void handleSave(HttpExchange exchange) throws IOException {
        Map<String, Object> body = parseObject(readUtf8(exchange, 10 * 1024 * 1024));
        String id = safeId(body.get("id"));
        Map<String, Object> data = asMap(body.get("data"));
        if (id.isEmpty() || data.isEmpty()) throw new ClientError(400, "데이터 부족");

        String requestedType = typeOrNull(body.get("type"));
        Map<String, Object> saved = store.upsert(id, data, requestedType);
        sendJson(exchange, 200, mapOf(
                "success", true,
                "id", string(saved.get("id")),
                "type", normalizeType(saved.get("type")),
                "orderIdx", intNumber(saved.get("orderIdx"), 9999)
        ));
    }

    private void handleGetOrder(HttpExchange exchange) throws IOException {
        Map<String, Object> order = emptyOrder();
        for (Map<String, Object> site : store.sortedSites()) {
            String type = normalizeType(site.get("type"));
            asList(order.get(type)).add(string(site.get("id")));
        }
        sendJson(exchange, 200, order);
    }

    private void handleSaveOrder(HttpExchange exchange) throws IOException {
        Map<String, Object> order = parseObject(readUtf8(exchange, 2 * 1024 * 1024));
        store.saveOrder(order);
        sendJson(exchange, 200, mapOf("success", true));
    }

    private void handleUpload(HttpExchange exchange) throws Exception {
        Headers headers = exchange.getRequestHeaders();
        String contentType = firstHeader(headers, "Content-Type");
        if (contentType == null || !contentType.contains("multipart/form-data")) {
            throw new ClientError(400, "multipart/form-data 요청이 필요합니다.");
        }
        String boundary = multipartBoundary(contentType);
        if (boundary == null) throw new ClientError(400, "업로드 boundary가 없습니다.");

        byte[] body = readBytes(exchange, MAX_UPLOAD_SIZE + 1024);
        UploadedFile file = parseMultipart(body, boundary);
        if (file == null || file.bytes.length == 0) throw new ClientError(400, "파일이 없습니다.");

        String ext = extension(file.filename);
        if (!ALLOWED_EXT.contains(ext)) {
            throw new ClientError(400, "PDF/Excel(.pdf,.xlsx,.xls) 파일만 업로드 가능합니다.");
        }

        String savedName = System.currentTimeMillis() + "-" + Math.round(Math.random() * 1_000_000_000L) + ext;
        Path savedPath = uploadDir.resolve(savedName);
        Files.write(savedPath, file.bytes);

        String extractorResult = runExtractor(savedPath, file.filename);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        byte[] out = extractorResult.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, out.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(out);
        }
    }

    private String runExtractor(Path filePath, String originalName) throws Exception {
        PythonCommand python = resolvePython();
        List<String> command = new ArrayList<>();
        command.add(python.bin);
        command.addAll(python.prefixArgs);
        command.add("file_to_json.py");
        command.add(filePath.toString());
        command.add(originalName == null ? filePath.getFileName().toString() : originalName);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(rootDir.toFile());
        pb.environment().put("PYTHONIOENCODING", "utf-8");
        Process process = pb.start();
        boolean done = process.waitFor(120, TimeUnit.SECONDS);
        if (!done) {
            process.destroyForcibly();
            throw new ClientError(500, "추출기 실행 시간이 초과되었습니다.");
        }

        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.exitValue() != 0) {
            throw new ClientError(500, "추출기 실행 실패: " + stderr.trim());
        }
        return stdout.trim();
    }

    private PythonCommand resolvePython() {
        String env = System.getenv("PYTHON");
        if (env != null && !env.isBlank()) return new PythonCommand(env, List.of());

        Path bundled = Path.of(System.getProperty("user.home"), ".cache", "codex-runtimes",
                "codex-primary-runtime", "dependencies", "python", "python.exe");
        if (Files.exists(bundled)) return new PythonCommand(bundled.toString(), List.of());

        return new PythonCommand("python", List.of());
    }

    private void serveStatic(HttpExchange exchange, String path) throws IOException {
        if (!exchange.getRequestMethod().equals("GET") && !exchange.getRequestMethod().equals("HEAD")) {
            throw new ClientError(405, "지원하지 않는 메서드입니다.");
        }
        String target = path.equals("/") ? "/index.html" : path;
        Path file = publicDir.resolve(target.substring(1)).normalize();
        if (!file.startsWith(publicDir) || !Files.exists(file) || Files.isDirectory(file)) {
            sendText(exchange, 404, "Not Found");
            return;
        }
        byte[] bytes = Files.readAllBytes(file);
        exchange.getResponseHeaders().set("Content-Type", contentType(file));
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(200, exchange.getRequestMethod().equals("HEAD") ? -1 : bytes.length);
        if (!exchange.getRequestMethod().equals("HEAD")) {
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    private static String contentType(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        if (name.endsWith(".json")) return "application/json; charset=utf-8";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        return "application/octet-stream";
    }

    private UploadedFile parseMultipart(byte[] body, String boundary) {
        String raw = new String(body, StandardCharsets.ISO_8859_1);
        String marker = "--" + boundary;
        int pos = raw.indexOf(marker);
        while (pos >= 0) {
            int headerStart = pos + marker.length();
            if (raw.startsWith("--", headerStart)) return null;
            if (raw.startsWith("\r\n", headerStart)) headerStart += 2;
            int headerEnd = raw.indexOf("\r\n\r\n", headerStart);
            if (headerEnd < 0) return null;

            String partHeaders = raw.substring(headerStart, headerEnd);
            int dataStart = headerEnd + 4;
            int next = raw.indexOf("\r\n" + marker, dataStart);
            if (next < 0) return null;

            String disposition = Arrays.stream(partHeaders.split("\r\n"))
                    .filter(h -> h.toLowerCase().startsWith("content-disposition:"))
                    .findFirst().orElse("");
            String filename = headerParam(disposition, "filename");
            if (filename != null) {
                byte[] bytes = raw.substring(dataStart, next).getBytes(StandardCharsets.ISO_8859_1);
                return new UploadedFile(filename, bytes);
            }
            pos = raw.indexOf(marker, next + marker.length());
        }
        return null;
    }

    private static String multipartBoundary(String contentType) {
        for (String part : contentType.split(";")) {
            String p = part.trim();
            if (p.startsWith("boundary=")) {
                String value = p.substring("boundary=".length()).trim();
                if (value.startsWith("\"") && value.endsWith("\"")) return value.substring(1, value.length() - 1);
                return value;
            }
        }
        return null;
    }

    private static String headerParam(String header, String key) {
        String prefix = key + "=\"";
        int start = header.indexOf(prefix);
        if (start < 0) return null;
        start += prefix.length();
        int end = header.indexOf('"', start);
        if (end < 0) return null;
        return header.substring(start, end);
    }

    private static String readUtf8(HttpExchange exchange, long maxBytes) throws IOException {
        return new String(readBytes(exchange, maxBytes), StandardCharsets.UTF_8);
    }

    private static byte[] readBytes(HttpExchange exchange, long maxBytes) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            long total = 0;
            int n;
            while ((n = in.read(buf)) >= 0) {
                total += n;
                if (total > maxBytes) throw new ClientError(413, "요청 크기가 너무 큽니다.");
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }

    private static void sendJson(HttpExchange exchange, int status, Object data) throws IOException {
        byte[] bytes = Json.stringify(data).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void sendText(HttpExchange exchange, int status, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static Map<String, Object> parseObject(String json) {
        Object parsed = Json.parse(json);
        if (!(parsed instanceof Map)) return new LinkedHashMap<>();
        return asMap(parsed);
    }

    private static Map<String, Object> emptyOrder() {
        Map<String, Object> order = new LinkedHashMap<>();
        for (String type : TYPE_KEYS) order.put(type, new ArrayList<>());
        return order;
    }

    private static String cleanPath(String path) {
        String decoded = URLDecoder.decode(path == null || path.isBlank() ? "/" : path, StandardCharsets.UTF_8);
        return decoded.replace('\\', '/');
    }

    private static String safeId(Object raw) {
        return string(raw).replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    private static String normalizeType(Object raw) {
        String type = string(raw);
        return TYPE_KEYS.contains(type) ? type : DEFAULT_TYPE;
    }

    private static String typeOrNull(Object raw) {
        String type = string(raw);
        return TYPE_KEYS.contains(type) ? type : null;
    }

    private static String extension(String filename) {
        String name = filename == null ? "" : filename.toLowerCase();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot) : "";
    }

    private static String firstHeader(Headers headers, String name) {
        List<String> values = headers.get(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> raw) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }
        return new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object value) {
        if (value instanceof List<?>) return (List<Object>) value;
        return new ArrayList<>();
    }

    private static Object fallback(Object value, Object fallback) {
        return value == null ? fallback : value;
    }

    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static Number number(Object value) {
        if (value instanceof Number n) return n;
        try {
            String s = string(value);
            return s.contains(".") ? Double.parseDouble(s) : Long.parseLong(s);
        } catch (Exception e) {
            return 0;
        }
    }

    private static int intNumber(Object value, int fallback) {
        if (value instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(string(value));
        } catch (Exception e) {
            return fallback;
        }
    }

    private static Map<String, Object> mapOf(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return map;
    }

    private record UploadedFile(String filename, byte[] bytes) {}
    private record PythonCommand(String bin, List<String> prefixArgs) {}

    private static class ClientError extends RuntimeException {
        final int status;
        ClientError(int status, String message) {
            super(message);
            this.status = status;
        }
    }

    private static class DataStore {
        private final Path file;
        private final List<Map<String, Object>> sites = new ArrayList<>();

        DataStore(Path file) throws IOException {
            this.file = file;
            Files.createDirectories(file.getParent());
            load();
        }

        synchronized List<Map<String, Object>> sortedSites() {
            List<Map<String, Object>> result = new ArrayList<>(sites);
            result.sort(Comparator
                    .comparing((Map<String, Object> s) -> normalizeType(s.get("type")))
                    .thenComparingInt(s -> intNumber(s.get("orderIdx"), 9999))
                    .thenComparing(s -> string(s.get("createdAt"))));
            return result;
        }

        synchronized Map<String, Object> find(String id) {
            return sites.stream().filter(s -> Objects.equals(string(s.get("id")), id)).findFirst().orElse(null);
        }

        synchronized Map<String, Object> upsert(String id, Map<String, Object> data, String requestedType) throws IOException {
            Map<String, Object> existing = find(id);
            String currentType = existing == null ? null : typeOrNull(existing.get("type"));
            String type = requestedType != null ? requestedType : (currentType != null ? currentType : DEFAULT_TYPE);
            boolean movedType = existing != null && requestedType != null && !Objects.equals(currentType, requestedType);
            int orderIdx = existing != null && existing.get("orderIdx") != null && !movedType
                    ? intNumber(existing.get("orderIdx"), 9999)
                    : nextOrderIdx(type);

            Map<String, Object> site = existing == null ? new LinkedHashMap<>() : existing;
            if (existing == null) {
                site.put("id", id);
                site.put("createdAt", now());
                sites.add(site);
            }
            site.put("name", string(data.getOrDefault("name", id)).trim());
            site.put("values", fallback(data.get("values"), new LinkedHashMap<>()));
            site.put("tree", fallback(data.get("tree"), new ArrayList<>()));
            site.put("line_items", fallback(data.get("line_items"), new ArrayList<>()));
            site.put("traces", fallback(data.get("traces"), new LinkedHashMap<>()));
            site.put("type", type);
            site.put("orderIdx", orderIdx);
            site.put("updatedAt", now());
            persist();
            return site;
        }

        synchronized boolean rename(String id, String name) throws IOException {
            Map<String, Object> site = find(id);
            if (site == null) return false;
            site.put("name", name);
            site.put("updatedAt", now());
            persist();
            return true;
        }

        synchronized boolean delete(String id) throws IOException {
            boolean removed = sites.removeIf(s -> Objects.equals(string(s.get("id")), id));
            if (removed) persist();
            return removed;
        }

        synchronized void saveOrder(Map<String, Object> order) throws IOException {
            for (String type : TYPE_KEYS) {
                List<Object> ids = asList(order.get(type));
                for (int idx = 0; idx < ids.size(); idx++) {
                    Map<String, Object> site = find(safeId(ids.get(idx)));
                    if (site != null) {
                        site.put("type", type);
                        site.put("orderIdx", idx);
                        site.put("updatedAt", now());
                    }
                }
            }
            persist();
        }

        private int nextOrderIdx(String type) {
            int max = -1;
            for (Map<String, Object> site : sites) {
                if (Objects.equals(normalizeType(site.get("type")), type)) {
                    max = Math.max(max, intNumber(site.get("orderIdx"), -1));
                }
            }
            return max + 1;
        }

        private void load() throws IOException {
            if (!Files.exists(file)) {
                persist();
                return;
            }
            String json = Files.readString(file, StandardCharsets.UTF_8);
            Object parsed = json.isBlank() ? new ArrayList<>() : Json.parse(json);
            if (parsed instanceof List<?> list) {
                for (Object item : list) {
                    Map<String, Object> site = asMap(item);
                    if (!site.isEmpty()) sites.add(site);
                }
            }
        }

        private void persist() throws IOException {
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, Json.stringifyPretty(sites), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }

        private static String now() {
            return LocalDateTime.now().format(TS);
        }
    }

    private static class Json {
        static Object parse(String input) {
            return new Parser(input).parse();
        }

        static String stringify(Object value) {
            StringBuilder sb = new StringBuilder();
            write(value, sb, 0, false);
            return sb.toString();
        }

        static String stringifyPretty(Object value) {
            StringBuilder sb = new StringBuilder();
            write(value, sb, 0, true);
            return sb.toString();
        }

        private static void write(Object value, StringBuilder sb, int depth, boolean pretty) {
            if (value == null) {
                sb.append("null");
            } else if (value instanceof String s) {
                writeString(s, sb);
            } else if (value instanceof Number || value instanceof Boolean) {
                sb.append(value);
            } else if (value instanceof Map<?, ?> map) {
                sb.append('{');
                boolean first = true;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (!first) sb.append(',');
                    newline(sb, depth + 1, pretty);
                    writeString(String.valueOf(entry.getKey()), sb);
                    sb.append(pretty ? ": " : ":");
                    write(entry.getValue(), sb, depth + 1, pretty);
                    first = false;
                }
                newline(sb, depth, pretty && !map.isEmpty());
                sb.append('}');
            } else if (value instanceof Iterable<?> list) {
                sb.append('[');
                boolean first = true;
                for (Object item : list) {
                    if (!first) sb.append(',');
                    newline(sb, depth + 1, pretty);
                    write(item, sb, depth + 1, pretty);
                    first = false;
                }
                newline(sb, depth, pretty && list.iterator().hasNext());
                sb.append(']');
            } else {
                writeString(String.valueOf(value), sb);
            }
        }

        private static void newline(StringBuilder sb, int depth, boolean pretty) {
            if (!pretty) return;
            sb.append('\n');
            sb.append("  ".repeat(Math.max(0, depth)));
        }

        private static void writeString(String value, StringBuilder sb) {
            sb.append('"');
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                switch (c) {
                    case '"' -> sb.append("\\\"");
                    case '\\' -> sb.append("\\\\");
                    case '\b' -> sb.append("\\b");
                    case '\f' -> sb.append("\\f");
                    case '\n' -> sb.append("\\n");
                    case '\r' -> sb.append("\\r");
                    case '\t' -> sb.append("\\t");
                    default -> {
                        if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                        else sb.append(c);
                    }
                }
            }
            sb.append('"');
        }

        private static class Parser {
            private final String s;
            private int i;

            Parser(String s) {
                this.s = s == null ? "" : s;
            }

            Object parse() {
                skipWs();
                Object value = parseValue();
                skipWs();
                return value;
            }

            private Object parseValue() {
                skipWs();
                if (i >= s.length()) return null;
                char c = s.charAt(i);
                if (c == '"') return parseString();
                if (c == '{') return parseObject();
                if (c == '[') return parseArray();
                if (c == 't' && s.startsWith("true", i)) { i += 4; return true; }
                if (c == 'f' && s.startsWith("false", i)) { i += 5; return false; }
                if (c == 'n' && s.startsWith("null", i)) { i += 4; return null; }
                return parseNumber();
            }

            private Map<String, Object> parseObject() {
                Map<String, Object> map = new LinkedHashMap<>();
                i++;
                skipWs();
                if (peek('}')) { i++; return map; }
                while (i < s.length()) {
                    String key = parseString();
                    skipWs();
                    expect(':');
                    Object value = parseValue();
                    map.put(key, value);
                    skipWs();
                    if (peek('}')) { i++; break; }
                    expect(',');
                    skipWs();
                }
                return map;
            }

            private List<Object> parseArray() {
                List<Object> list = new ArrayList<>();
                i++;
                skipWs();
                if (peek(']')) { i++; return list; }
                while (i < s.length()) {
                    list.add(parseValue());
                    skipWs();
                    if (peek(']')) { i++; break; }
                    expect(',');
                }
                return list;
            }

            private String parseString() {
                expect('"');
                StringBuilder out = new StringBuilder();
                while (i < s.length()) {
                    char c = s.charAt(i++);
                    if (c == '"') break;
                    if (c == '\\' && i < s.length()) {
                        char e = s.charAt(i++);
                        switch (e) {
                            case '"' -> out.append('"');
                            case '\\' -> out.append('\\');
                            case '/' -> out.append('/');
                            case 'b' -> out.append('\b');
                            case 'f' -> out.append('\f');
                            case 'n' -> out.append('\n');
                            case 'r' -> out.append('\r');
                            case 't' -> out.append('\t');
                            case 'u' -> {
                                String hex = s.substring(i, Math.min(i + 4, s.length()));
                                out.append((char) Integer.parseInt(hex, 16));
                                i += 4;
                            }
                            default -> out.append(e);
                        }
                    } else {
                        out.append(c);
                    }
                }
                return out.toString();
            }

            private Number parseNumber() {
                int start = i;
                while (i < s.length()) {
                    char c = s.charAt(i);
                    if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') i++;
                    else break;
                }
                String token = s.substring(start, i);
                if (token.contains(".") || token.contains("e") || token.contains("E")) return Double.parseDouble(token);
                try {
                    return Long.parseLong(token);
                } catch (NumberFormatException e) {
                    return Double.parseDouble(token);
                }
            }

            private void skipWs() {
                while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
            }

            private boolean peek(char c) {
                return i < s.length() && s.charAt(i) == c;
            }

            private void expect(char c) {
                if (i >= s.length() || s.charAt(i) != c) {
                    throw new IllegalArgumentException("Invalid JSON near position " + i);
                }
                i++;
            }
        }
    }
}
