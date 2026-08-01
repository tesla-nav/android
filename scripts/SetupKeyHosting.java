/*
 * Generates the EC key pair for Tesla Fleet API partner registration (README step 1.2)
 * and publishes the public half on a GitHub Pages site you own, at the exact path Tesla
 * expects: https://<domain>/.well-known/appspecific/com.tesla.3p.public-key.pem
 *
 * Each person running this app registers their OWN Tesla developer app against their OWN
 * domain, so this publishes to a repo YOU control, not tesla-nav's. Default target is your
 * GitHub user page (<github-username>.github.io) since Tesla requires the file at the
 * domain root, not under a project path (e.g. github.io/<repo>/... won't work) - GitHub only
 * serves a repo at the bare account root when it's named exactly <username>.github.io.
 *
 * No dependency beyond the JDK (already required to build this app) - talks to the GitHub
 * REST API directly, no git/gh CLI needed. Needs a GitHub personal access token with repo
 * creation + contents + pages write access (classic PAT: "repo" scope is enough).
 *
 *   export GITHUB_TOKEN=ghp_...
 *   java scripts/SetupKeyHosting.java                        # publish to <you>.github.io
 *   java scripts/SetupKeyHosting.java myorg/myorg.github.io  # publish to a specific repo instead
 *   java scripts/SetupKeyHosting.java --yes                  # skip the confirmation prompt
 */
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SetupKeyHosting {
    static final String API = "https://api.github.com";
    static final HttpClient HTTP = HttpClient.newHttpClient();
    static String token;

    public static void main(String[] args) throws Exception {
        Map<String, String> flags = new LinkedHashMap<>();
        String repoArg = null;
        for (String a : args) {
            if (a.equals("--yes") || a.equals("-y")) flags.put("yes", "true");
            else if (a.startsWith("--token=")) token = a.substring("--token=".length());
            else repoArg = a;
        }
        boolean assumeYes = flags.containsKey("yes");

        token = token != null ? token : System.getenv("GITHUB_TOKEN");
        if (token == null || token.isBlank()) {
            fail("Set GITHUB_TOKEN (a personal access token with 'repo' scope) or pass --token=...");
        }

        String username = jsonString(api("GET", "/user", null), "login");
        if (username == null) fail("Couldn't authenticate to GitHub - check the token.");

        String owner, name;
        if (repoArg != null && repoArg.contains("/")) {
            String[] parts = repoArg.split("/", 2);
            owner = parts[0];
            name = parts[1];
        } else {
            owner = username;
            name = username + ".github.io";
        }
        String domain = name;

        System.out.println("Target: https://" + domain + "/.well-known/appspecific/com.tesla.3p.public-key.pem (repo: " + owner + "/" + name + ")");
        if (!assumeYes) {
            System.out.print("This will create/push to a PUBLIC GitHub repo and enable Pages. Continue? [y/N] ");
            System.out.flush();
            String reply = new BufferedReader(new InputStreamReader(System.in)).readLine();
            if (reply == null || !reply.trim().toLowerCase(Locale.ROOT).startsWith("y")) {
                System.out.println("Aborted.");
                return;
            }
        }

        Path privateKeyPath = Path.of("private-key.pem");
        Path publicKeyPath = Path.of("public-key.pem");
        String publicKeyPem;
        if (Files.exists(publicKeyPath)) {
            publicKeyPem = Files.readString(publicKeyPath, StandardCharsets.UTF_8);
        } else {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
            kpg.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair pair = kpg.generateKeyPair();
            String privatePem = toPem("PRIVATE KEY", pair.getPrivate().getEncoded());
            publicKeyPem = toPem("PUBLIC KEY", pair.getPublic().getEncoded());
            Files.writeString(privateKeyPath, privatePem, StandardCharsets.UTF_8);
            Files.writeString(publicKeyPath, publicKeyPem, StandardCharsets.UTF_8);
            System.out.println("Generated private-key.pem / public-key.pem (gitignored - keep private-key.pem off any server).");
        }

        HttpResponse<String> repoCheck = apiResponse("GET", "/repos/" + owner + "/" + name, null);
        if (repoCheck.statusCode() == 404) {
            System.out.println("Creating repo " + owner + "/" + name + " ...");
            String body = "{\"name\":\"" + jsonEsc(name) + "\","
                    + "\"description\":\"Public key hosting for Tesla Fleet API\","
                    + "\"private\":false,\"auto_init\":true}";
            String createPath = owner.equals(username) ? "/user/repos" : "/orgs/" + owner + "/repos";
            api("POST", createPath, body);
        } else if (repoCheck.statusCode() != 200) {
            fail("Couldn't check repo " + owner + "/" + name + " (HTTP " + repoCheck.statusCode() + "): " + repoCheck.body());
        }

        String branch = jsonString(api("GET", "/repos/" + owner + "/" + name, null), "default_branch");
        if (branch == null) branch = "main";

        putFile(owner, name, branch, ".nojekyll", "");
        putFile(owner, name, branch, ".well-known/appspecific/com.tesla.3p.public-key.pem", publicKeyPem);

        HttpResponse<String> pagesCheck = apiResponse("GET", "/repos/" + owner + "/" + name + "/pages", null);
        if (pagesCheck.statusCode() == 404) {
            String body = "{\"source\":{\"branch\":\"" + jsonEsc(branch) + "\",\"path\":\"/\"}}";
            HttpResponse<String> enabled = apiResponse("POST", "/repos/" + owner + "/" + name + "/pages", body);
            if (enabled.statusCode() / 100 != 2) {
                System.err.println("Warning: couldn't enable Pages automatically (HTTP " + enabled.statusCode() + "): " + enabled.body());
                System.err.println("Enable it by hand: repo Settings -> Pages -> Deploy from branch " + branch + " / (root)");
            }
        }

        System.out.println();
        System.out.println("Domain for developer.tesla.com and the partner_accounts registration (README step 1.4): " + domain);
        System.out.println("Verify (can take a minute to build): curl https://" + domain + "/.well-known/appspecific/com.tesla.3p.public-key.pem");
    }

    static void putFile(String owner, String repo, String branch, String path, String content) throws Exception {
        String getResp = null;
        HttpResponse<String> get = apiResponse("GET", "/repos/" + owner + "/" + repo + "/contents/" + path + "?ref=" + branch, null);
        String sha = get.statusCode() == 200 ? jsonString(get.body(), "sha") : null;

        String b64 = Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8));
        StringBuilder body = new StringBuilder("{");
        body.append("\"message\":\"Publish Tesla Fleet API public key\",");
        body.append("\"content\":\"").append(b64).append("\",");
        body.append("\"branch\":\"").append(jsonEsc(branch)).append("\"");
        if (sha != null) body.append(",\"sha\":\"").append(sha).append("\"");
        body.append("}");

        HttpResponse<String> put = apiResponse("PUT", "/repos/" + owner + "/" + repo + "/contents/" + path, body.toString());
        if (put.statusCode() / 100 != 2) {
            fail("Couldn't write " + path + " (HTTP " + put.statusCode() + "): " + put.body());
        }
    }

    static String api(String method, String path, String body) throws Exception {
        HttpResponse<String> resp = apiResponse(method, path, body);
        if (resp.statusCode() / 100 != 2) {
            fail(method + " " + path + " failed (HTTP " + resp.statusCode() + "): " + resp.body());
        }
        return resp.body();
    }

    static HttpResponse<String> apiResponse(String method, String path, String body) throws Exception {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(API + path))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28");
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8);
        req.method(method, publisher);
        if (body != null) req.header("Content-Type", "application/json");
        return HTTP.send(req.build(), HttpResponse.BodyHandlers.ofString());
    }

    static String toPem(String label, byte[] der) {
        String b64 = Base64.getEncoder().encodeToString(der);
        StringBuilder sb = new StringBuilder();
        sb.append("-----BEGIN ").append(label).append("-----\n");
        for (int i = 0; i < b64.length(); i += 64) {
            sb.append(b64, i, Math.min(i + 64, b64.length())).append("\n");
        }
        sb.append("-----END ").append(label).append("-----\n");
        return sb.toString();
    }

    static void fail(String message) {
        System.err.println(message);
        System.exit(1);
    }

    static String jsonEsc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    static String jsonString(String json, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").matcher(json);
        return m.find() ? m.group(1).replace("\\\"", "\"").replace("\\\\", "\\") : null;
    }
}
