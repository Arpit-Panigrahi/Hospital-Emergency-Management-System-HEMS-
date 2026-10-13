import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class PatientDAO {

    // 1. Authenticate Application User Login (Login -> JDBC -> Database)
    public boolean authenticateUser(String username, String password) {
        String[] info = getUserInfo(username, password);
        if (info != null) {
            System.out.println("Access Granted: " + info[0] + " [" + info[1] + "]");
            return true;
        }
        return false;
    }

    // Get User Full Name and Role upon successful credentials check
    public String[] getUserInfo(String username, String password) {
        String sql = "SELECT FULL_NAME, ROLE FROM APP_USERS WHERE USERNAME = ? AND PASSWORD_HASH = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, username);
            ps.setString(2, password);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new String[]{ rs.getString("FULL_NAME"), rs.getString("ROLE") };
                }
            }
        } catch (SQLException e) {
            System.err.println("Database Login Error: " + e.getMessage());
        }
        return null;
    }

    // 2. View Active Emergency Patients (SELECT & ResultSet)
    public void displayAllPatients() {
        String sql = "SELECT PATIENT_ID, NAME, AGE, GENDER, TRIAGE_LEVEL, STATUS FROM EMERGENCY_PATIENT ORDER BY PATIENT_ID ASC";
        try (Connection con = DBConnection.getConnection();
             Statement stmt = con.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            System.out.println("\n----------------------------- EMERGENCY PATIENT LIST -----------------------------");
            System.out.printf("%-10s | %-20s | %-5s | %-8s | %-12s | %-12s\n",
                    "ID", "Name", "Age", "Gender", "Triage", "Status");
            System.out.println("----------------------------------------------------------------------------------");

            boolean found = false;
            while (rs.next()) {
                found = true;
                int id = rs.getInt("PATIENT_ID");
                String name = rs.getString("NAME");
                int age = rs.getInt("AGE");
                String gender = rs.getString("GENDER");
                String triage = rs.getString("TRIAGE_LEVEL");
                String status = rs.getString("STATUS");

                System.out.printf("%-10d | %-20s | %-5d | %-8s | %-12s | %-12s\n",
                        id, name, age, gender != null ? gender : "-", triage, status);
            }
            if (!found) {
                System.out.println("No emergency patients registered yet.");
            }
            System.out.println("----------------------------------------------------------------------------------");

        } catch (SQLException e) {
            System.err.println("Query Error: " + e.getMessage());
        }
    }

    // Get Patients as JSON array for Web Interface
    public String getPatientsJson() {
        StringBuilder sb = new StringBuilder("[");
        String sql = "SELECT PATIENT_ID, NAME, AGE, GENDER, TRIAGE_LEVEL, STATUS, TO_CHAR(ADMISSION_TIME, 'YYYY-MM-DD HH24:MI:SS') AS ADM_TIME FROM EMERGENCY_PATIENT ORDER BY PATIENT_ID ASC";

        try (Connection con = DBConnection.getConnection();
             Statement stmt = con.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            boolean first = true;
            while (rs.next()) {
                if (!first) sb.append(",");
                first = false;

                sb.append("{")
                  .append("\"patientId\":").append(rs.getInt("PATIENT_ID")).append(",")
                  .append("\"name\":\"").append(escape(rs.getString("NAME"))).append("\",")
                  .append("\"age\":").append(rs.getInt("AGE")).append(",")
                  .append("\"gender\":\"").append(escape(rs.getString("GENDER"))).append("\",")
                  .append("\"triage\":\"").append(escape(rs.getString("TRIAGE_LEVEL"))).append("\",")
                  .append("\"status\":\"").append(escape(rs.getString("STATUS"))).append("\",")
                  .append("\"time\":\"").append(escape(rs.getString("ADM_TIME"))).append("\"")
                  .append("}");
            }
        } catch (SQLException e) {
            System.err.println("Patients JSON Error: " + e.getMessage());
        }
        sb.append("]");
        return sb.toString();
    }

    // 3. View Bed Availability
    public void displayAllBeds() {
        String sql = "SELECT BED_ID, BED_TYPE, IS_OCCUPIED, PATIENT_ID FROM BED_ALLOCATION ORDER BY BED_ID ASC";
        try (Connection con = DBConnection.getConnection();
             Statement stmt = con.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            System.out.println("\n----------------------- BED & ICU ALLOCATION STATUS -----------------------");
            System.out.printf("%-10s | %-15s | %-12s | %-15s\n", "Bed ID", "Bed Type", "Occupied", "Patient ID");
            System.out.println("---------------------------------------------------------------------------");

            while (rs.next()) {
                int bedId = rs.getInt("BED_ID");
                String type = rs.getString("BED_TYPE");
                String occ = rs.getString("IS_OCCUPIED");
                int pid = rs.getInt("PATIENT_ID");
                String pidStr = rs.wasNull() || pid == 0 ? "-" : String.valueOf(pid);
                String occStr = "Y".equalsIgnoreCase(occ) ? "YES (Occupied)" : "NO (Available)";

                System.out.printf("%-10d | %-15s | %-12s | %-15s\n", bedId, type, occStr, pidStr);
            }
            System.out.println("---------------------------------------------------------------------------");

        } catch (SQLException e) {
            System.err.println("Bed Query Error: " + e.getMessage());
        }
    }

    // Get Beds as JSON array for Web Interface
    public String getBedsJson() {
        StringBuilder sb = new StringBuilder("[");
        String sql = "SELECT BED_ID, BED_TYPE, IS_OCCUPIED, PATIENT_ID FROM BED_ALLOCATION ORDER BY BED_ID ASC";

        try (Connection con = DBConnection.getConnection();
             Statement stmt = con.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            boolean first = true;
            while (rs.next()) {
                if (!first) sb.append(",");
                first = false;

                int pid = rs.getInt("PATIENT_ID");
                String pidVal = rs.wasNull() ? "null" : String.valueOf(pid);

                sb.append("{")
                  .append("\"bedId\":").append(rs.getInt("BED_ID")).append(",")
                  .append("\"bedType\":\"").append(escape(rs.getString("BED_TYPE"))).append("\",")
                  .append("\"isOccupied\":\"").append(escape(rs.getString("IS_OCCUPIED"))).append("\",")
                  .append("\"patientId\":").append(pidVal)
                  .append("}");
            }
        } catch (SQLException e) {
            System.err.println("Beds JSON Error: " + e.getMessage());
        }
        sb.append("]");
        return sb.toString();
    }

    // 4. Update Patient Status (UPDATE)
    public boolean updatePatientStatus(int patientId, String newStatus) {
        String sql = "UPDATE EMERGENCY_PATIENT SET STATUS = ? WHERE PATIENT_ID = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, newStatus);
            ps.setInt(2, patientId);

            int rows = ps.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            System.err.println("Update Error: " + e.getMessage());
            return false;
        }
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\"", "\\\"");
    }
}
