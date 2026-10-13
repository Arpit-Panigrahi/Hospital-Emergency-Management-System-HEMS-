import java.util.Scanner;

public class MainApp {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        PatientDAO patientDAO = new PatientDAO();
        EmergencyService emergencyService = new EmergencyService();

        System.out.println("=================================================================");
        System.out.println("           HOSPITAL EMERGENCY MANAGEMENT SYSTEM (HEMS)           ");
        System.out.println("              Architecture: Login -> JDBC -> Database           ");
        System.out.println("=================================================================");

        // Step 1: Application Credential Login
        System.out.println("\n--- APPLICATION LOGIN ---");
        System.out.println("(Accounts in Oracle DB: admin / admin123, doctor / doctor123, 25bce5654 / 25bce5654)");

        boolean authenticated = false;
        while (!authenticated) {
            System.out.print("Enter Username: ");
            String username = scanner.nextLine().trim();

            System.out.print("Enter Password: ");
            String password = scanner.nextLine().trim();

            authenticated = patientDAO.authenticateUser(username, password);
            if (!authenticated) {
                System.out.println("Invalid credentials. Please try again.\n");
            }
        }

        // Step 2: Main Menu Loop
        while (true) {
            System.out.println("\n======================== HEMS MAIN MENU ========================");
            System.out.println("1. View Active Emergency Patients");
            System.out.println("2. Emergency Admission & Bed Allocation (Atomic Transaction)");
            System.out.println("3. View Bed Availability");
            System.out.println("4. Update Patient Status");
            System.out.println("5. Discharge Patient & Free Bed");
            System.out.println("6. Exit");
            System.out.print("Enter your choice (1-6): ");

            String choiceStr = scanner.nextLine().trim();
            if (choiceStr.isEmpty()) continue;

            int choice;
            try {
                choice = Integer.parseInt(choiceStr);
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid number (1-6).");
                continue;
            }

            switch (choice) {
                case 1:
                    patientDAO.displayAllPatients();
                    break;

                case 2:
                    System.out.println("\n--- EMERGENCY ADMISSION & BED ALLOCATION ---");
                    try {
                        System.out.print("Enter Patient ID (e.g. 201): ");
                        int id = Integer.parseInt(scanner.nextLine().trim());

                        System.out.print("Enter Patient Name: ");
                        String name = scanner.nextLine().trim();

                        System.out.print("Enter Age: ");
                        int age = Integer.parseInt(scanner.nextLine().trim());

                        System.out.print("Enter Gender (M/F): ");
                        String gender = scanner.nextLine().trim().toUpperCase();

                        System.out.print("Enter Triage Level (CRITICAL / URGENT / NON-URGENT): ");
                        String triage = scanner.nextLine().trim().toUpperCase();

                        patientDAO.displayAllBeds();
                        System.out.print("Enter Bed ID to Assign (e.g. 101, 102): ");
                        int bedId = Integer.parseInt(scanner.nextLine().trim());

                        // Validation
                        if (name.isEmpty() || age <= 0) {
                            System.out.println("Validation Error: Name cannot be empty and Age must be > 0.");
                            break;
                        }

                        if (!triage.equals("CRITICAL") && !triage.equals("URGENT") && !triage.equals("NON-URGENT")) {
                            System.out.println("Validation Error: Triage must be CRITICAL, URGENT, or NON-URGENT.");
                            break;
                        }

                        boolean ok = emergencyService.admitAndAllocateBed(id, name, age, gender, triage, bedId);
                        if (ok) {
                            System.out.println("Success: Patient registered and bed allocated.");
                        } else {
                            System.out.println("Failed: Transaction was rolled back.");
                        }
                    } catch (NumberFormatException e) {
                        System.out.println("Invalid numeric input.");
                    }
                    break;

                case 3:
                    patientDAO.displayAllBeds();
                    break;

                case 4:
                    System.out.println("\n--- UPDATE PATIENT STATUS ---");
                    try {
                        System.out.print("Enter Patient ID: ");
                        int updateId = Integer.parseInt(scanner.nextLine().trim());

                        System.out.print("Enter New Status (ADMITTED / DISCHARGED / TRANSFERRED): ");
                        String status = scanner.nextLine().trim().toUpperCase();

                        boolean updated = patientDAO.updatePatientStatus(updateId, status);
                        if (updated) {
                            System.out.println("Patient status updated successfully.");
                        } else {
                            System.out.println("Update failed. Patient ID not found.");
                        }
                    } catch (NumberFormatException e) {
                        System.out.println("Invalid numeric input.");
                    }
                    break;

                case 5:
                    System.out.println("\n--- DISCHARGE PATIENT & FREE BED ---");
                    try {
                        System.out.print("Enter Patient ID to discharge: ");
                        int dischargeId = Integer.parseInt(scanner.nextLine().trim());
                        emergencyService.dischargePatient(dischargeId);
                    } catch (NumberFormatException e) {
                        System.out.println("Invalid numeric input.");
                    }
                    break;

                case 6:
                    System.out.println("Exiting Application. Goodbye!");
                    scanner.close();
                    System.exit(0);
                    break;

                default:
                    System.out.println("Invalid option. Please choose 1 to 6.");
            }
        }
    }
}
