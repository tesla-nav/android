/*
 * Tesla Fleet API login: browser auth-code flow + push client_id/client_secret/tokens
 * to the device via adb.
 *
 * No dependency beyond the JDK (already required to build this app) and `adb` on PATH.
 * Run directly, no separate compile step (JDK 11+). Reads TESLA_CLIENT_ID/TESLA_CLIENT_SECRET
 * from the environment (e.g. `set -a && source .env && set +a`, see .env.example) or from
 * --client-id/--client-secret. access_token/refresh_token always come from a fresh login —
 * Tesla rotates the refresh_token on every use, so a cached one goes stale fast and isn't
 * safe to replay.
 *
 *   java scripts/TeslaLogin.java                 # browser login, then push
 *   java scripts/TeslaLogin.java --no-push       # browser login, print tokens instead
 */
import java.awt.Desktop;
import java.io.*;
import java.math.BigInteger;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class TeslaLogin {
    static final int PORT = 8765;
    static final String REDIRECT_URI = "http://localhost:" + PORT + "/callback";
    static final String AUTHORIZE_URL = "https://auth.tesla.com/oauth2/v3/authorize";
    static final String TOKEN_URL = "https://auth.tesla.com/oauth2/v3/token";
    static final String AUDIENCE = "https://fleet-api.prd.eu.vn.cloud.tesla.com";
    static final String SCOPE = "openid email offline_access vehicle_device_data vehicle_location";
    static final String PREFS_FILE = "shared_prefs/TeslaNavSettings.xml";

    public static void main(String[] args) throws Exception {
        Map<String, String> flags = parseArgs(args);
        String clientId = flags.getOrDefault("client-id", System.getenv("TESLA_CLIENT_ID"));
        String clientSecret = flags.getOrDefault("client-secret", System.getenv("TESLA_CLIENT_SECRET"));
        String pkg = flags.getOrDefault("package", "io.github.teslanav.app");
        boolean push = !flags.containsKey("no-push");

        if (clientId == null || clientSecret == null) {
            System.err.println("Set TESLA_CLIENT_ID / TESLA_CLIENT_SECRET (env or --client-id/--client-secret).");
            System.exit(1);
        }

        String state = new BigInteger(130, new SecureRandom()).toString(32);
        String url = AUTHORIZE_URL + "?"
                + "client_id=" + enc(clientId)
                + "&redirect_uri=" + enc(REDIRECT_URI)
                + "&response_type=code"
                + "&scope=" + enc(SCOPE)
                + "&state=" + enc(state)
                + "&prompt=consent"; // forces the full permissions screen every time, see README §5b

        System.out.println("Opening browser for Tesla login:\n" + url + "\n");
        try {
            Desktop.getDesktop().browse(URI.create(url));
        } catch (Exception e) {
            System.out.println("Couldn't open a browser automatically, open the URL above manually.");
        }

        Map<String, String> params = awaitCallback(PORT);
        if (params.containsKey("error")) {
            fail("Tesla refused the login: " + params.get("error"));
        }
        if (!state.equals(params.get("state"))) {
            fail("State mismatch: expected " + state + ", got " + params.get("state"));
        }
        String code = params.get("code");
        if (code == null) {
            fail("No 'code' in callback: " + params);
        }

        Map<String, String> tokens = exchangeCode(clientId, clientSecret, code);
        List<String> scope = jwtScope(tokens.get("access_token"));
        System.out.println("Granted scope: " + scope);
        if (!tokens.containsKey("refresh_token")) {
            fail("No refresh_token in the response - shouldn't happen with prompt=consent, check the account's Tesla app permissions.");
        }
        if (!scope.contains("vehicle_device_data")) {
            fail("Scope is missing vehicle_device_data - check the app's scopes on developer.tesla.com.");
        }

        if (push) {
            pushSettings(clientId, clientSecret, tokens.get("access_token"), tokens.get("refresh_token"), pkg);
            System.out.println("Pushed client_id/client_secret/tokens to " + pkg + " and restarted the app.");
        } else {
            tokens.forEach((k, v) -> System.out.println(k + "=" + v));
        }
    }

    static Map<String, String> parseArgs(String[] args) {
        Map<String, String> flags = new HashMap<>();
        for (String a : args) {
            if (!a.startsWith("--")) continue;
            String[] kv = a.substring(2).split("=", 2);
            flags.put(kv[0], kv.length > 1 ? kv[1] : "true");
        }
        return flags;
    }

    static void fail(String message) {
        System.err.println(message);
        System.exit(1);
    }

    static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    static Map<String, String> awaitCallback(int port) throws IOException {
        System.out.println("Listening on " + REDIRECT_URI + " ...");
        try (ServerSocket server = new ServerSocket(port)) {
            try (Socket client = server.accept()) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
                String requestLine = reader.readLine();
                PrintWriter writer = new PrintWriter(client.getOutputStream(), true);
                writer.print("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nConnection: close\r\n\r\n"
                        + "<html><body><p>Login captured, you can close this tab.</p></body></html>");
                writer.flush();
                return parseQuery(requestLine);
            }
        }
    }

    static Map<String, String> parseQuery(String requestLine) {
        Map<String, String> params = new HashMap<>();
        if (requestLine == null) return params;
        String path = requestLine.split(" ").length > 1 ? requestLine.split(" ")[1] : "";
        int q = path.indexOf('?');
        if (q < 0) return params;
        for (String pair : path.substring(q + 1).split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                params.put(kv[0], URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
            }
        }
        return params;
    }

    static Map<String, String> exchangeCode(String clientId, String clientSecret, String code) throws IOException, InterruptedException {
        String body = "{"
                + "\"grant_type\":\"authorization_code\","
                + "\"client_id\":\"" + jsonEsc(clientId) + "\","
                + "\"client_secret\":\"" + jsonEsc(clientSecret) + "\","
                + "\"code\":\"" + jsonEsc(code) + "\","
                + "\"redirect_uri\":\"" + jsonEsc(REDIRECT_URI) + "\","
                + "\"audience\":\"" + jsonEsc(AUDIENCE) + "\""
                + "}";
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create(TOKEN_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            fail("Token exchange failed (HTTP " + response.statusCode() + "): " + response.body());
        }
        Map<String, String> tokens = new HashMap<>();
        for (String key : List.of("access_token", "refresh_token", "token_type")) {
            String v = jsonString(response.body(), key);
            if (v != null) tokens.put(key, v);
        }
        return tokens;
    }

    static String jsonEsc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    static String jsonString(String json, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").matcher(json);
        return m.find() ? m.group(1).replace("\\\"", "\"").replace("\\\\", "\\") : null;
    }

    static List<String> jwtScope(String accessToken) {
        String payload = accessToken.split("\\.")[1];
        payload += "=".repeat((4 - payload.length() % 4) % 4);
        String json = new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("\"scp\"\\s*:\\s*\\[([^]]*)]").matcher(json);
        if (!m.find()) return List.of();
        List<String> scopes = new ArrayList<>();
        Matcher item = Pattern.compile("\"([^\"]*)\"").matcher(m.group(1));
        while (item.find()) scopes.add(item.group(1));
        return scopes;
    }

    static String adb(boolean check, String... args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("adb");
        command.addAll(Arrays.asList(args));
        Process process = new ProcessBuilder(command).redirectErrorStream(false).start();
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        int code = process.waitFor();
        if (check && code != 0) {
            throw new RuntimeException("adb " + String.join(" ", args) + " failed: " + stderr.trim());
        }
        return stdout;
    }

    static void pushSettings(String clientId, String clientSecret, String accessToken, String refreshToken, String pkg) throws Exception {
        adb(false, "shell", "run-as", pkg, "mkdir", "-p", "shared_prefs");
        String raw = adb(false, "shell", "run-as", pkg, "cat", PREFS_FILE);

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        Document doc;
        if (raw.isBlank()) {
            doc = dbf.newDocumentBuilder().newDocument();
            doc.appendChild(doc.createElement("map"));
        } else {
            doc = dbf.newDocumentBuilder().parse(new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)));
        }
        Element root = doc.getDocumentElement();
        setPrefString(doc, root, "tesla_client_id", clientId);
        setPrefString(doc, root, "tesla_client_secret", clientSecret);
        setPrefString(doc, root, "tesla_token", accessToken);
        setPrefString(doc, root, "tesla_refresh_token", refreshToken);

        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        StringWriter sw = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(sw));
        String xmlDoc = "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n" + sw + "\n";

        File local = File.createTempFile("TeslaNavSettings", ".xml");
        Files.writeString(local.toPath(), xmlDoc, StandardCharsets.UTF_8);
        try {
            String remoteTmp = "/data/local/tmp/TeslaNavSettings.xml";
            adb(true, "push", local.getAbsolutePath(), remoteTmp);
            adb(true, "shell", "run-as", pkg, "cp", remoteTmp, PREFS_FILE);
            adb(true, "shell", "rm", remoteTmp);
            adb(true, "shell", "am", "force-stop", pkg);
            adb(true, "shell", "am", "start", "-n", pkg + "/.MainActivity");
        } finally {
            local.delete();
        }
    }

    static void setPrefString(Document doc, Element root, String name, String value) {
        NodeList strings = root.getElementsByTagName("string");
        for (int i = 0; i < strings.getLength(); i++) {
            Element el = (Element) strings.item(i);
            if (name.equals(el.getAttribute("name"))) {
                el.setTextContent(value);
                return;
            }
        }
        Element el = doc.createElement("string");
        el.setAttribute("name", name);
        el.setTextContent(value);
        root.appendChild(el);
    }
}
