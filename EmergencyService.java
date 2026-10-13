import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class EmergencyService {

    // Atomic Transaction: Insert Patient AND Assign Bed together
    public boolean admitAndAllocateBed(int patientId, String name, int age, String gender, String triage, int bedId) {
        String insertPatientSql = "INSERT INTO EMERGENCY_PATIENT (PATIENT_ID, NAME, AGE, GENDER, TRIAGE_LEVEL) VALUES (?, ?, ?, ?, ?)";
        String updateBedSql = "UPDATE BED_ALLOCATION SET IS_OCCUPIED = 'Y', PATIENT_ID = ? WHERE BED_ID = ? AND IS_OCCUPIED = 'N'";

        Connection con = null;
        try {
            con = DBConnection.getConnection();

            // 1. Begin Transaction
            con.setAutoCommit(false);

            // 2. Insert Patient Record
            try (PreparedStatement ps1 = con.prepareStatement(insertPatientSql)) {
                ps1.setInt(1, patientId);
                ps1.setString(2, name);
                ps1.setInt(3, age);
                ps1.setString(4, gender);
                ps1.setString(5, triage);
                ps1.executeUpdate();
            }

            // 3. Assign Bed (Only if bed is currently NOT occupied: IS_OCCUPIED = 'N')
            int bedUpdated;
            try (PreparedStatement ps2 = con.prepareStatement(updateBedSql)) {
                ps2.setInt(1, patientId);
                ps2.setInt(2, bedId);
                bedUpdated = ps2.executeUpdate();
            }

            // 4. Validate Bed Assignment
            if (bedUpdated == 0) {
                // Bed was already occupied or invalid -> trigger rollback!
                throw new SQLException("Bed " + bedId + " is unavailable or already occupied.");
            }

            // 5. Commit both operations permanently
            con.commit();
            System.out.println("[TRANSACTION COMMIT] Patient " + patientId + " admitted & Bed " + bedId + " allocated!");
            return true;

        } catch (SQLException e) {
            // 6. Rollback if any part fails
            if (con != null) {
                try {
                    con.rollback();
                    System.err.println("[TRANSACTION ROLLBACK] Rolled back: " + e.getMessage());
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
            return false;
        } finally {
            // 7. Safe close
            if (con != null) {
                try {
                    con.setAutoCommit(true);
                    con.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // Atomic Discharge: Update patient status to DISCHARGED and free the bed
    public boolean dischargePatient(int patientId) {
        String dischargeSql = "UPDATE EMERGENCY_PATIENT SET STATUS = 'DISCHARGED' WHERE PATIENT_ID = ?";
        String freeBedSql = "UPDATE BED_ALLOCATION SET IS_OCCUPIED = 'N', PATIENT_ID = NULL WHERE PATIENT_ID = ?";

        Connection con = null;
        try {
            con = DBConnection.getConnection();
            con.setAutoCommit(false);

            try (PreparedStatement ps1 = con.prepareStatement(dischargeSql)) {
                ps1.setInt(1, patientId);
                ps1.executeUpdate();
            }

            try (PreparedStatement ps2 = con.prepareStatement(freeBedSql)) {
                ps2.setInt(1, patientId);
                ps2.executeUpdate();
            }

            con.commit();
            System.out.println("[TRANSACTION COMMIT] Patient " + patientId + " discharged and bed vacated.");
            return true;
        } catch (SQLException e) {
            if (con != null) {
                try { con.rollback(); } catch (SQLException ex) {}
            }
            return false;
        } finally {
            if (con != null) {
                try { con.close(); } catch (SQLException e) {}
            }
        }
    }
}
