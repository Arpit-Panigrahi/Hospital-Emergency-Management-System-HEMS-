import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WebServer {

    private static final int PORT = 8080;
    private static final PatientDAO patientDAO = new PatientDAO();
    private static final EmergencyService emergencyService = new EmergencyService();

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        // Static Web Page
        server.createContext("/", new StaticFileHandler());

        // API Endpoints
        server.createContext("/api/login", new LoginHandler());
        server.createContext("/api/patients", new PatientsHandler());
        server.createContext("/api/beds", new BedsHandler());
        server.createContext("/api/admit", new AdmitHandler());
        server.createContext("/api/discharge", new DischargeHandler());

        server.setExecutor(null); // Default single/multi-thread executor
        server.start();

        System.out.println("=================================================================");
        System.out.println("           HEMS WEB APPLICATION SERVER STARTED SUCCESSFULLY      ");
        System.out.println("   Architecture: Web Interface -> HTTP API -> JDBC -> Oracle     ");
        System.out.println("   Server URL  : http://localhost:" + PORT + "                          ");
        System.out.println("=================================================================");
        System.out.println("Opening your web browser automatically...");

        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI("http://localhost:" + PORT));
            }
        } catch (Exception ignored) {}
    }

    // Serve HTML Web Interface
    static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            File htmlFile = new File("index.html");
            if (!htmlFile.exists()) {
                sendResponse(ex, 404, "text/plain", "index.html not found.");
                return;
            }
            byte[] bytes = Files.readAllBytes(htmlFile.toPath());
            sendResponseBytes(ex, 200, "text/html; charset=utf-8", bytes);
        }
    }

    // Authenticate Credentials via JDBC
    static class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                sendResponse(ex, 405, "application/json", "{\"error\":\"Method Not Allowed\"}");
                return;
            }
            String body = readBody(ex.getRequestBody());
            Map<String, String> json = parseJson(body);
            String user = json.get("username");
            String pass = json.get("password");

            String[] info = patientDAO.getUserInfo(user, pass);
            if (info != null) {
                String res = String.format("{\"success\":true,\"name\":\"%s\",\"role\":\"%s\"}",
                        info[0], info[1]);
                sendResponse(ex, 200, "application/json", res);
            } else {
                sendResponse(ex, 401, "application/json", "{\"success\":false,\"message\":\"Invalid username or password\"}");
            }
        }
    }

    // Get Emergency Patients JSON
    static class PatientsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            String json = patientDAO.getPatientsJson();
            sendResponse(ex, 200, "application/json", json);
        }
    }

    // Get Beds JSON
    static class BedsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            String json = patientDAO.getBedsJson();
            sendResponse(ex, 200, "application/json", json);
        }
    }

    // Admit Patient & Allocate Bed (Atomic Transaction)
    static class AdmitHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                sendResponse(ex, 405, "application/json", "{\"error\":\"Method Not Allowed\"}");
                return;
            }
            try {
                String body = readBody(ex.getRequestBody());
                Map<String, String> json = parseJson(body);

                int patientId = Integer.parseInt(json.get("patientId"));
                String name = json.get("name");
                int age = Integer.parseInt(json.get("age"));
                String gender = json.get("gender");
                String triage = json.get("triage");
                int bedId = Integer.parseInt(json.get("bedId"));

                boolean ok = emergencyService.admitAndAllocateBed(patientId, name, age, gender, triage, bedId);
                if (ok) {
                    sendResponse(ex, 200, "application/json", "{\"success\":true,\"message\":\"Patient admitted & bed allocated successfully.\"}");
                } else {
                    sendResponse(ex, 400, "application/json", "{\"success\":false,\"message\":\"Transaction rolled back. Bed unavailable or invalid input.\"}");
                }
            } catch (Exception e) {
                sendResponse(ex, 400, "application/json", "{\"success\":false,\"message\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    // Discharge Patient & Free Bed
    static class DischargeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                sendResponse(ex, 405, "application/json", "{\"error\":\"Method Not Allowed\"}");
                return;
            }
            try {
                String body = readBody(ex.getRequestBody());
                Map<String, String> json = parseJson(body);
                int patientId = Integer.parseInt(json.get("patientId"));

                boolean ok = emergencyService.dischargePatient(patientId);
                if (ok) {
                    sendResponse(ex, 200, "application/json", "{\"success\":true,\"message\":\"Patient discharged and bed freed.\"}");
                } else {
                    sendResponse(ex, 400, "application/json", "{\"success\":false,\"message\":\"Discharge failed. Patient not found.\"}");
                }
            } catch (Exception e) {
                sendResponse(ex, 400, "application/json", "{\"success\":false,\"message\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    // Utility Methods
    private static String readBody(InputStream in) throws IOException {
        byte[] bytes = in.readAllBytes();
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void sendResponse(HttpExchange ex, int status, String contentType, String content) throws IOException {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        sendResponseBytes(ex, status, contentType, bytes);
    }

    private static void sendResponseBytes(HttpExchange ex, int status, String contentType, byte[] bytes) throws IOException {
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static Map<String, String> parseJson(String json) {
        Map<String, String> map = new HashMap<>();
        if (json == null) return map;
        Pattern pattern = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(?:\"([^\"]*)\"|([0-9]+|true|false|null))");
        Matcher matcher = pattern.matcher(json);
        while (matcher.find()) {
            String key = matcher.group(1);
            String val = matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
            map.put(key, val);
        }
        return map;
    }
}
