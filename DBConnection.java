import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection {
    // Lab Oracle Database Connection Details
    private static final String URL = "jdbc:oracle:thin:@172.16.8.54:1521:orcl";
    private static final String USER = "25bce5654";
    private static final String PASSWORD = "25bce5654";

    public static Connection getConnection() throws SQLException {
        try {
            Class.forName("oracle.jdbc.OracleDriver");
        } catch (ClassNotFoundException e) {
            System.err.println("Driver Error: " + e.getMessage());
        }
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}
