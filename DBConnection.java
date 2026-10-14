import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection {
    // JDBC details can be set via env vars for cloud deployment
    private static final String DEFAULT_URL = "jdbc:oracle:thin:@172.16.8.54:1521:orcl";

    public static Connection getConnection() throws SQLException {
        try {
            Class.forName("oracle.jdbc.OracleDriver");
        } catch (ClassNotFoundException e) {
            System.err.println("Driver Error: " + e.getMessage());
        }

        String url = getEnvOrDefault("HEMS_DB_URL", DEFAULT_URL);
        String user = getRequiredEnv("HEMS_DB_USER");
        String password = getRequiredEnv("HEMS_DB_PASSWORD");

        return DriverManager.getConnection(url, user, password);
    }

    private static String getEnvOrDefault(String key, String defaultValue) {
        String val = System.getenv(key);
        if (val == null || val.isBlank()) return defaultValue;
        return val;
    }

    private static String getRequiredEnv(String key) {
        String val = System.getenv(key);
        if (val == null || val.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + key);
        }
        return val;
    }
}
