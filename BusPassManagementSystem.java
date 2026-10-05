import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.Scanner;
import java.util.UUID;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class BusPassManagementSystem {
    static final String DB_URL = "jdbc:sqlite:buspass.db";
    static final int BASE_FARE = 1000;
    static final Scanner input = new Scanner(System.in);

    public static void main(String[] args) {
        try {
            Class.forName("org.sqlite.JDBC");
            createTables();
            createFirstAdminIfNeeded();
            updateExpiredPasses();
            mainMenu();
        } catch (Exception e) {
            System.out.println("Could not start the system: " + e.getMessage());
            e.printStackTrace();
        }
    }

    static Connection connect() throws Exception {
        return DriverManager.getConnection(DB_URL);
    }

    static void createTables() throws Exception {
        try (Connection con = connect(); Statement st = con.createStatement()) {
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS admins (
                    username TEXT PRIMARY KEY,
                    salt BLOB NOT NULL,
                    password_hash BLOB NOT NULL
                )
                """);

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS applications (
                    application_id TEXT PRIMARY KEY,
                    pass_id TEXT UNIQUE,
                    name TEXT NOT NULL,
                    phone TEXT NOT NULL,
                    age INTEGER NOT NULL,
                    date_of_birth TEXT,
                    route TEXT NOT NULL,
                    months INTEGER NOT NULL,
                    category TEXT NOT NULL,
                    proof_id TEXT,
                    institution TEXT,
                    course TEXT,
                    parent_name TEXT,
                    student_signed INTEGER DEFAULT 0,
                    parent_signed INTEGER DEFAULT 0,
                    institution_stamped INTEGER DEFAULT 0,
                    status TEXT NOT NULL,
                    status_reason TEXT,
                    submitted_at TEXT NOT NULL,
                    decision_due TEXT,
                    issue_date TEXT,
                    expiry_date TEXT
                )
                """);

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS requests (
                    request_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    pass_id TEXT NOT NULL,
                    phone TEXT NOT NULL,
                    request_type TEXT NOT NULL,
                    extra_months INTEGER DEFAULT 0,
                    reason TEXT,
                    status TEXT NOT NULL,
                    admin_note TEXT,
                    submitted_at TEXT NOT NULL,
                    refund_amount REAL DEFAULT 0
                )
                """);

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS payments (
                    payment_id TEXT PRIMARY KEY,
                    application_id TEXT NOT NULL,
                    request_id INTEGER,
                    payment_type TEXT NOT NULL,
                    amount REAL NOT NULL,
                    method TEXT NOT NULL,
                    receipt TEXT NOT NULL,
                    paid_at TEXT NOT NULL,
                    valid_from TEXT,
                    valid_until TEXT,
                    payment_status TEXT NOT NULL DEFAULT 'Verified',
                    transaction_ref TEXT
                )
                """);
        }

        ensureColumn("requests", "refund_amount", "REAL DEFAULT 0");
        ensureColumn("payments", "payment_status", "TEXT NOT NULL DEFAULT 'Verified'");
        ensureColumn("payments", "transaction_ref", "TEXT");
    }

    static void ensureColumn(String table, String name, String definition) throws Exception {
        boolean found = false;

        try (Connection con = connect();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (name.equalsIgnoreCase(rs.getString("name"))) {
                    found = true;
                    break;
                }
            }
        }

        if (!found) {
            try (Connection con = connect(); Statement st = con.createStatement()) {
                st.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + name + " " + definition);
            }
        }
    }

    static void createFirstAdminIfNeeded() throws Exception {
        boolean noAdmins;

        try (Connection con = connect();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM admins")) {
            noAdmins = rs.next() && rs.getInt(1) == 0;
        }

        if (noAdmins) {
            System.out.println("First-time setup: create the initial admin account.");
            String username = readNonBlank("Admin username: ");
            String password = readPassword("Admin password (at least 8 characters): ");

            while (password.length() < 8) {
                System.out.println("Use at least 8 characters.");
                password = readPassword("Admin password: ");
            }

            addAdmin(username, password);
            System.out.println("Admin account created.");
        }
    }

    static void addAdmin(String username, String password) throws Exception {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        byte[] hash = passwordHash(password, salt);

        try (Connection con = connect();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO admins(username, salt, password_hash) VALUES(?, ?, ?)")) {
            ps.setString(1, username);
            ps.setBytes(2, salt);
            ps.setBytes(3, hash);
            ps.executeUpdate();
        }
    }

    static byte[] passwordHash(String password, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 120000, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    static boolean adminLogin() {
        String username = readNonBlank("Admin username: ");
        String password = readPassword("Admin password: ");

        try (Connection con = connect();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT salt, password_hash FROM admins WHERE username = ?")) {
            ps.setString(1, username);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    byte[] enteredHash = passwordHash(password, rs.getBytes("salt"));
                    if (MessageDigest.isEqual(enteredHash, rs.getBytes("password_hash"))) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Login error: " + e.getMessage());
        }

        System.out.println("Incorrect username or password.");
        return false;
    }

    static void mainMenu() throws Exception {
        while (true) {
            System.out.println("\n=== Bus Pass Management System ===");
            System.out.println("1. Admin login");
            System.out.println("2. Customer use");
            System.out.println("3. Exit");
            int choice = readInt("Choose 1 to 3: ", 1, 3);

            if (choice == 1) {
                if (adminLogin()) {
                    adminMenu();
                }
            } else if (choice == 2) {
                customerMenu();
            } else {
                System.out.println("Goodbye!");
                return;
            }
        }
    }

    static void customerMenu() throws Exception {
        while (true) {
            System.out.println("\n--- Customer Use ---");
            System.out.println("1. Apply for a pass");
            System.out.println("2. Pay for an application or approved renewal");
            System.out.println("3. Check application/pass/request status");
            System.out.println("4. Request renewal");
            System.out.println("5. Request cancellation");
            System.out.println("6. Back");

            int choice = readInt("Choose 1 to 6: ", 1, 6);
            switch (choice) {
                case 1 -> applyForPass();
                case 2 -> payDueAmount();
                case 3 -> checkStatus();
                case 4 -> requestRenewal();
                case 5 -> requestCancellation();
                case 6 -> { return; }
            }
        }
    }

    static void applyForPass() throws Exception {
        System.out.println("\nConcession type:");
        System.out.println("1. None");
        System.out.println("2. Student — 40% off");
        System.out.println("3. Senior citizen, age 60+ — 60% off");
        System.out.println("4. Ex-serviceman — 80% off");
        int type = readInt("Choose 1 to 4: ", 1, 4);

        String category = switch (type) {
            case 2 -> "Student";
            case 3 -> "Senior";
            case 4 -> "Ex-serviceman";
            default -> "None";
        };

        String name = readNonBlank("Applicant name: ");
        String phone = readNonBlank("Contact phone number: ");
        int age;
        String dob = "";
        String proofId = "";
        String institution = "";
        String course = "";
        String parentName = "";
        int studentSigned = 0;
        int parentSigned = 0;
        int institutionStamped = 0;

        if (category.equals("Senior")) {
            dob = readNonBlank("Date of birth (YYYY-MM-DD): ");
            try {
                age = Period.between(LocalDate.parse(dob), LocalDate.now()).getYears();
            } catch (Exception e) {
                System.out.println("Date must use YYYY-MM-DD. Application not submitted.");
                return;
            }

            if (age < 60) {
                System.out.println("Senior concession requires age 60 or above.");
                return;
            }

            proofId = readNonBlank("Senior-citizen card ID: ");
        } else {
            age = readInt("Age: ", 1, 120);
        }

        if (category.equals("Student")) {
            proofId = readNonBlank("Admission number: ");
            institution = readNonBlank("School/college name: ");
            course = readNonBlank("Course/class: ");
            parentName = readNonBlank("Parent/guardian name: ");
            studentSigned = readYesNo("Student signed the undertaking? (y/n): ");
            parentSigned = readYesNo("Parent/guardian signed it? (y/n): ");
            institutionStamped = readYesNo("Institution checked and stamped it? (y/n): ");
        } else if (category.equals("Ex-serviceman")) {
            proofId = readNonBlank("Ex-serviceman/service ID: ");
        }

        String route = readNonBlank("Route: ");
        int months = readInt("Pass duration in months: ", 1, 36);
        String appId = newId("APP");
        String status = category.equals("None") ? "Payment due" : "Pending review";
        String dueDate = category.equals("None")
                ? null : addWorkingDays(LocalDate.now(), 3).toString();

        String sql = """
            INSERT INTO applications (
                application_id, name, phone, age, date_of_birth, route, months,
                category, proof_id, institution, course, parent_name,
                student_signed, parent_signed, institution_stamped, status,
                submitted_at, decision_due
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection con = connect(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, appId);
            ps.setString(2, name);
            ps.setString(3, phone);
            ps.setInt(4, age);
            setOptional(ps, 5, dob);
            ps.setString(6, route);
            ps.setInt(7, months);
            ps.setString(8, category);
            setOptional(ps, 9, proofId);
            setOptional(ps, 10, institution);
            setOptional(ps, 11, course);
            setOptional(ps, 12, parentName);
            ps.setInt(13, studentSigned);
            ps.setInt(14, parentSigned);
            ps.setInt(15, institutionStamped);
            ps.setString(16, status);
            ps.setString(17, LocalDateTime.now().toString());
            setOptional(ps, 18, dueDate);
            ps.executeUpdate();
        }

        System.out.println("\nApplication ID: " + appId);
        if (category.equals("None")) {
            System.out.println("Payment is required before a pass is issued.");
            showFare(category, months);
        } else {
            System.out.println("Status: Pending review");
            System.out.println("Expected decision by: " + dueDate);
            System.out.println("After approval, pay the discounted fare to receive the pass.");
        }
    }

    static void payDueAmount() throws Exception {
        System.out.println("\n1. Pay for a new pass application");
        System.out.println("2. Pay for an approved renewal");
        int choice = readInt("Choose 1 or 2: ", 1, 2);

        if (choice == 1) {
            payApplication();
        } else {
            payRenewalRequest();
        }
    }

    static void payApplication() throws Exception {
        String appId = readNonBlank("Application ID: ");
        String phone = readNonBlank("Contact phone number: ");
        String category;
        int months;

        try (Connection con = connect();
             PreparedStatement ps = con.prepareStatement("""
                 SELECT category, months FROM applications
                 WHERE application_id = ? AND phone = ? AND status = 'Payment due'
                 """)) {
            ps.setString(1, appId);
            ps.setString(2, phone);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    System.out.println("No matching application is awaiting payment.");
                    return;
                }
                category = rs.getString("category");
                months = rs.getInt("months");
            }
        }

        int amount = monthlyFare(category) * months;
        showFare(category, months);
        String method = choosePaymentMethod();
        String reference = readNonBlank(
                "Payment reference or cash counter slip number: ");

        LocalDate from = LocalDate.now();
        LocalDate until = from.plusMonths(months);
        String receipt = newId("R");

        try (Connection con = connect()) {
            con.setAutoCommit(false);
            try (PreparedStatement payment = con.prepareStatement("""
                    INSERT INTO payments
                    (payment_id, application_id, payment_type, amount, method, receipt,
                     paid_at, valid_from, valid_until, payment_status, transaction_ref)
                    VALUES (?, ?, 'PASS', ?, ?, ?, ?, ?, ?, 'Pending', ?)
                    """);
                 PreparedStatement update = con.prepareStatement("""
                    UPDATE applications SET status = 'Payment verification pending'
                    WHERE application_id = ? AND phone = ? AND status = 'Payment due'
                    """)) {
                payment.setString(1, newId("PAY"));
                payment.setString(2, appId);
                payment.setInt(3, amount);
                payment.setString(4, method);
                payment.setString(5, receipt);
                payment.setString(6, LocalDateTime.now().toString());
                payment.setString(7, from.toString());
                payment.setString(8, until.toString());
                payment.setString(9, reference);
                payment.executeUpdate();

                update.setString(1, appId);
                update.setString(2, phone);
                update.executeUpdate();

                con.commit();
            } catch (Exception e) {
                con.rollback();
                throw e;
            }
        }

        System.out.println("Payment details submitted for admin verification.");
        System.out.println("Payment reference: " + reference);
        System.out.println("No pass has been issued yet.");
    }

    static void payRenewalRequest() throws Exception {
        int requestId = readInt("Renewal request number: ", 1, Integer.MAX_VALUE);
        String phone = readNonBlank("Contact phone number: ");

        String passId;
        int extraMonths;

        try (Connection con = connect();
             PreparedStatement ps = con.prepareStatement("""
                 SELECT pass_id, extra_months FROM requests
                 WHERE request_id = ? AND phone = ?
                   AND request_type = 'RENEW' AND status = 'Payment due'
                 """)) {
            ps.setInt(1, requestId);
            ps.setString(2, phone);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    System.out.println("No approved renewal is awaiting payment.");
                    return;
                }
                passId = rs.getString("pass_id");
                extraMonths = rs.getInt("extra_months");
            }
        }

        String appId;
        String category;
        String oldExpiry;
        try (Connection con = connect();
             PreparedStatement ps = con.prepareStatement("""
                 SELECT application_id, category, expiry_date
                 FROM applications WHERE pass_id = ? AND phone = ?
                 """)) {
            ps.setString(1, passId);
            ps.setString(2, phone);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    System.out.println("Pass record not found.");
                    return;
                }
                appId = rs.getString("application_id");
                category = rs.getString("category");
                oldExpiry = rs.getString("expiry_date");
            }
        }

        int amount = monthlyFare(category) * extraMonths;
        showFare(category, extraMonths);
        String method = choosePaymentMethod();
        String reference = readNonBlank(
                "Payment reference or cash counter slip number: ");

        LocalDate today = LocalDate.now();
        LocalDate oldEnd = LocalDate.parse(oldExpiry);
        LocalDate from = oldEnd.isAfter(today) ? oldEnd : today;
        LocalDate until = from.plusMonths(extraMonths);
        String receipt = newId("R");

        try (Connection con = connect()) {
            con.setAutoCommit(false);
            try (PreparedStatement payment = con.prepareStatement("""
                    INSERT INTO payments
                    (payment_id, application_id, request_id, payment_type, amount,
                     method, receipt, paid_at, valid_from, valid_until,
                     payment_status, transaction_ref)
                    VALUES (?, ?, ?, 'RENEWAL', ?, ?, ?, ?, ?, ?, 'Pending', ?)
                    """);
                 PreparedStatement request = con.prepareStatement("""
                    UPDATE requests SET status = 'Payment verification pending'
                    WHERE request_id = ? AND phone = ? AND status = 'Payment due'
                    """)) {
                payment.setString(1, newId("PAY"));
                payment.setString(2, appId);
                payment.setInt(3, requestId);
                payment.setInt(4, amount);
                payment.setString(5, method);
                payment.setString(6, receipt);
                payment.setString(7, LocalDateTime.now().toString());
                payment.setString(8, from.toString());
                payment.setString(9, until.toString());
                payment.setString(10, reference);
                payment.executeUpdate();

                request.setInt(1, requestId);
                request.setString(2, phone);
                request.executeUpdate();

                con.commit();
            } catch (Exception e) {
                con.rollback();
                throw e;
            }
        }

        System.out.println("Renewal payment submitted for admin verification.");
        System.out.println("Payment reference: " + reference);
        System.out.println("The pass validity has not been extended yet.");
    }

    static void requestRenewal() throws Exception {
        String passId = readNonBlank("Pass ID (case-sensitive): ");
        String phone = readNonBlank("Contact phone number: ");

        if (!validPassAndPhone(passId, phone, "Active", "Expired")) {
            System.out.println("No active or expired pass matched those details.");
            return;
        }

        int months = readInt("Additional months: ", 1, 36);
        addRequest(passId, phone, "RENEW", months, "");
        System.out.println("Renewal request submitted for admin review.");
    }

    static void requestCancellation() throws Exception {
        String passId = readNonBlank("Pass ID (case-sensitive): ");
        String phone = readNonBlank("Contact phone number: ");

        if (!validPassAndPhone(passId, phone, "Active", "Expired")) {
            System.out.println("No active or expired pass matched those details.");
            return;
        }

        String reason = readNonBlank("Reason for cancellation: ");
        addRequest(passId, phone, "CANCEL", 0, reason);
        System.out.println("Cancellation request submitted for admin review.");
    }

    static void addRequest(String passId, String phone, String type, int months, String reason)
            throws Exception {
        try (Connection con = connect();
             PreparedStatement ps = con.prepareStatement("""
                 INSERT INTO requests
                 (pass_id, phone, request_type, extra_months, reason, status, submitted_at)
                 VALUES (?, ?, ?, ?, ?, 'Pending', ?)
                 """)) {
            ps.setString(1, passId);
            ps.setString(2, phone);
            ps.setString(3, type);
            ps.setInt(4, months);
            setOptional(ps, 5, reason);
            ps.setString(6, LocalDateTime.now().toString());
            ps.executeUpdate();
        }
    }

    static boolean validPassAndPhone(String passId, String phone, String... statuses)
            throws Exception {
        try (Connection con = connect();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT status FROM applications WHERE pass_id = ? AND phone = ?")) {
            ps.setString(1, passId);
            ps.setString(2, phone);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    for (String status : statuses) {
                        if (status.equals(rs.getString("status"))) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    static void checkStatus() throws Exception {
        String id = readNonBlank("Application ID or pass ID (case-sensitive): ");
        String phone = readNonBlank("Contact phone number: ");

        try (Connection con = connect();
             PreparedStatement ps = con.prepareStatement("""
                 SELECT * FROM applications
                 WHERE (application_id = ? OR pass_id = ?) AND phone = ?
                 """)) {
            ps.setString(1, id);
            ps.setString(2, id);
            ps.setString(3, phone);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    System.out.println("No matching record. Check the ID and phone number.");
                    return;
                }

                String status = rs.getString("status");
                System.out.println("\nStatus: " + status);

                if (status.equals("Pending review")) {
                    System.out.println("Your application is being reviewed.");
                    System.out.println("Expected decision by: " + rs.getString("decision_due"));
                } else if (status.equals("Needs correction") || status.equals("Rejected")) {
                    System.out.println("Details: " + rs.getString("status_reason"));
                } else if (status.equals("Payment due")) {
                    System.out.println("Payment is required before the pass can be issued.");
                    showFare(rs.getString("category"), rs.getInt("months"));
                } else if (status.equals("Payment verification pending")) {
                    System.out.println("Payment is awaiting admin verification. The pass is not issued yet.");
                } else if (rs.getString("pass_id") != null) {
                    showPass(rs);
                }

                String passId = rs.getString("pass_id");
                if (passId != null) {
                    showLatestRequest(con, passId, phone);
                }
            }
        }
    }

    static void showLatestRequest(Connection con, String passId, String phone) throws Exception {
        try (PreparedStatement ps = con.prepareStatement("""
            SELECT request_type, status, admin_note, refund_amount
            FROM requests
            WHERE pass_id = ? AND phone = ?
            ORDER BY request_id DESC LIMIT 1
            """)) {
            ps.setString(1, passId);
            ps.setString(2, phone);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    System.out.println("Latest " + rs.getString("request_type")
                            + " request status: " + rs.getString("status"));
                    if (rs.getDouble("refund_amount") > 0) {
                        System.out.println("Estimated refund: Rs. "
                                + rs.getDouble("refund_amount"));
                    }
                    if (rs.getString("admin_note") != null) {
                        System.out.println("Staff note: " + rs.getString("admin_note"));
                    }
                }
            }
        }
    }

    static void adminMenu() throws Exception {
        while (true) {
            System.out.println("\n--- Admin Menu ---");
            System.out.println("1. Review pending applications");
            System.out.println("2. Review renewal/cancellation requests and refunds");
            System.out.println("3. Verify pending payments");
            System.out.println("4. Add another admin account");
            System.out.println("5. Log out");

            int choice = readInt("Choose 1 to 5: ", 1, 5);
            switch (choice) {
                case 1 -> reviewApplications();
                case 2 -> reviewRequests();
                case 3 -> verifyPendingPayments();
                case 4 -> {
                    String username = readNonBlank("New admin username: ");
                    String password = readPassword("New admin password (at least 8 characters): ");
                    if (password.length() < 8) {
                        System.out.println("Password is too short; account not created.");
                    } else {
                        addAdmin(username, password);
                        System.out.println("Admin account added.");
                    }
                }
                case 5 -> { return; }
            }
        }
    }

    static void reviewApplications() throws Exception {
        while (true) {
            System.out.println("\n--- Pending Applications ---");
            boolean any = false;

            try (Connection con = connect();
                 Statement st = con.createStatement();
                 ResultSet rs = st.executeQuery("""
                     SELECT application_id, name, category, decision_due
                     FROM applications WHERE status = 'Pending review'
                     ORDER BY submitted_at
                     """)) {
                while (rs.next()) {
                    any = true;
                    System.out.println(rs.getString("application_id") + " — "
                            + rs.getString("name") + " — " + rs.getString("category")
                            + " — due " + rs.getString("decision_due"));
                }
            }

            if (!any) {
                System.out.println("No pending approvals.");
            }

            System.out.println("Enter an application ID to review, or BACK to return.");
            String appId = input.nextLine().trim();
            if (appId.equalsIgnoreCase("BACK")) {
                return;
            }

            reviewOneApplication(appId);
        }
    }

    static void reviewOneApplication(String appId) throws Exception {
        String category;
        String name;
        String proofId;
        String institution;
        String course;
        String parentName;
        String dob;
        int age;
        int studentSigned;
        int parentSigned;
        int institutionStamped;

        try (Connection con = connect();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT * FROM applications WHERE application_id = ? AND status = 'Pending review'")) {
            ps.setString(1, appId);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    System.out.println("No pending application found with that exact ID.");
                    return;
                }
                category = rs.getString("category");
                name = rs.getString("name");
                age = rs.getInt("age");
                dob = rs.getString("date_of_birth");
                proofId = rs.getString("proof_id");
                institution = rs.getString("institution");
                course = rs.getString("course");
                parentName = rs.getString("parent_name");
                studentSigned = rs.getInt("student_signed");
                parentSigned = rs.getInt("parent_signed");
                institutionStamped = rs.getInt("institution_stamped");
            }
        }

        System.out.println("\nApplicant: " + name);
        System.out.println("Category: " + category);
        System.out.println("Age: " + age);
        System.out.println("Proof ID entered: " + proofId);
        if (category.equals("Senior")) {
            System.out.println("Date of birth: " + dob);
        }
        if (category.equals("Student")) {
            System.out.println("Institution: " + institution);
            System.out.println("Course/class: " + course);
            System.out.println("Parent/guardian: " + parentName);
            System.out.println("Student signed: " + yesNo(studentSigned));
            System.out.println("Parent signed: " + yesNo(parentSigned));
            System.out.println("Institution stamp checked: " + yesNo(institutionStamped));
        }

        System.out.println("1. Approve");
        System.out.println("2. Needs correction");
        System.out.println("3. Reject");
        int decision = readInt("Choose 1 to 3: ", 1, 3);

        if (decision == 1 && category.equals("Student")
                && (studentSigned == 0 || parentSigned == 0 || institutionStamped == 0)) {
            System.out.println("Cannot approve: check the undertaking, signatures, and stamp.");
            return;
        }

        if (decision == 1) {
            try (Connection con = connect();
                 PreparedStatement ps = con.prepareStatement("""
                     UPDATE applications SET status = 'Payment due', status_reason = NULL
                     WHERE application_id = ?
                     """)) {
                ps.setString(1, appId);
                ps.executeUpdate();
            }
            System.out.println("Application approved. Customer must pay before pass issuance.");
        } else {
            String reason = readNonBlank("Explain what needs correction or why it is rejected: ");
            String status = decision == 2 ? "Needs correction" : "Rejected";

            try (Connection con = connect();
                 PreparedStatement ps = con.prepareStatement("""
                     UPDATE applications SET status = ?, status_reason = ?
                     WHERE application_id = ?
                     """)) {
                ps.setString(1, status);
                ps.setString(2, reason);
                ps.setString(3, appId);
                ps.executeUpdate();
            }
            System.out.println("Application status updated to " + status + ".");
        }
    }

    static void verifyPendingPayments() throws Exception {
        while (true) {
            System.out.println("\n--- Payments Awaiting Verification ---");
            boolean any = false;

            try (Connection con = connect();
                 Statement st = con.createStatement();
                 ResultSet rs = st.executeQuery("""
                     SELECT payment_id, application_id, payment_type, amount, method
                     FROM payments WHERE payment_status = 'Pending'
                     ORDER BY paid_at
                     """)) {
                while (rs.next()) {
                    any = true;
                    System.out.println(rs.getString("payment_id") + " — application "
                            + rs.getString("application_id") + " — "
                            + rs.getString("payment_type") + " — Rs. "
                            + rs.getDouble("amount") + " — " + rs.getString("method"));
                }
            }

            if (!any) {
                System.out.println("No pending payments.");
            }

            System.out.println("Enter a payment ID to verify, or BACK to return.");
            String paymentId = input.nextLine().trim();
            if (paymentId.equalsIgnoreCase("BACK")) {
                return;
            }

            verifyOnePayment(paymentId);
        }
    }

    static void verifyOnePayment(String paymentId) throws Exception {
        String appId;
        Integer requestId;
        String type;
        String method;
        String reference;
        String from;
        String until;
        double amount;

        try (Connection con = connect();
             PreparedStatement ps = con.prepareStatement("""
                 SELECT * FROM payments WHERE payment_id = ? AND payment_status = 'Pending'
                 """)) {
            ps.setString(1, paymentId);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    System.out.println("No pending payment found with that ID.");
                    return;
                }

                appId = rs.getString("application_id");
                int requestValue = rs.getInt("request_id");
                requestId = rs.wasNull() ? null : requestValue;
                type = rs.getString("payment_type");
                method = rs.getString("method");
                reference = rs.getString("transaction_ref");
                from = rs.getString("valid_from");
                until = rs.getString("valid_until");
                amount = rs.getDouble("amount");
            }
        }

        System.out.println("Payment type: " + type);
        System.out.println("Amount: Rs. " + amount);
        System.out.println("Method: " + method);
        System.out.println("Reference submitted: " + reference);
        System.out.print("Have you verified that this payment was received? (y/n): ");

        if (!input.nextLine().trim().equalsIgnoreCase("y")) {
            System.out.println("Payment remains pending verification.");
            return;
        }

        String newPassId = null;
        int extraMonths = 0;

        if (type.equals("PASS")) {
            newPassId = newId("BP");
        } else if (type.equals("RENEWAL") && requestId != null) {
            try (Connection con = connect();
                 PreparedStatement ps = con.prepareStatement(
                         "SELECT extra_months FROM requests WHERE request_id = ?")) {
                ps.setInt(1, requestId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        System.out.println("Related renewal request was not found.");
                        return;
                    }
                    extraMonths = rs.getInt("extra_months");
                }
            }
        }

        String receipt = newId("R");

        try (Connection con = connect()) {
            con.setAutoCommit(false);

            try {
                if (type.equals("PASS")) {
                    try (PreparedStatement ps = con.prepareStatement("""
                        UPDATE applications
                        SET pass_id = ?, status = 'Active', issue_date = ?,
                            expiry_date = ?, status_reason = NULL
                        WHERE application_id = ? AND status = 'Payment verification pending'
                        """)) {
                        ps.setString(1, newPassId);
                        ps.setString(2, from);
                        ps.setString(3, until);
                        ps.setString(4, appId);
                        if (ps.executeUpdate() != 1) {
                            throw new SQLException("Application was not awaiting payment verification.");
                        }
                    }
                } else if (type.equals("RENEWAL") && requestId != null) {
                    try (PreparedStatement ps = con.prepareStatement("""
                        UPDATE applications
                        SET months = months + ?, expiry_date = ?, status = 'Active'
                        WHERE application_id = ?
                        """)) {
                        ps.setInt(1, extraMonths);
                        ps.setString(2, until);
                        ps.setString(3, appId);
                        ps.executeUpdate();
                    }

                    try (PreparedStatement ps = con.prepareStatement("""
                        UPDATE requests SET status = 'Completed',
                            admin_note = 'Payment verified; renewal completed'
                        WHERE request_id = ?
                        """)) {
                        ps.setInt(1, requestId);
                        ps.executeUpdate();
                    }
                }

                try (PreparedStatement ps = con.prepareStatement("""
                    UPDATE payments SET payment_status = 'Verified', receipt = ?, paid_at = ?
                    WHERE payment_id = ? AND payment_status = 'Pending'
                    """)) {
                    ps.setString(1, receipt);
                    ps.setString(2, LocalDateTime.now().toString());
                    ps.setString(3, paymentId);
                    ps.executeUpdate();
                }

                con.commit();
            } catch (Exception e) {
                con.rollback();
                throw e;
            }
        }

        System.out.println("Payment verified. Receipt: " + receipt);
        if (type.equals("PASS")) {
            System.out.println("Pass issued. Pass ID: " + newPassId);
            System.out.println("Valid through: " + until);
        } else if (type.equals("RENEWAL")) {
            System.out.println("Renewal completed. New expiry date: " + until);
        }
    }

    static void reviewRequests() throws Exception {
        while (true) {
            System.out.println("\n--- Pending Requests / Refunds ---");
            boolean any = false;

            try (Connection con = connect();
                 Statement st = con.createStatement();
                 ResultSet rs = st.executeQuery("""
                     SELECT request_id, pass_id, request_type, status, refund_amount
                     FROM requests
                     WHERE status IN ('Pending', 'Payment due', 'Refund due')
                     ORDER BY request_id
                     """)) {
                while (rs.next()) {
                    any = true;
                    System.out.println("Request " + rs.getInt("request_id") + " — "
                            + rs.getString("request_type") + " — pass "
                            + rs.getString("pass_id") + " — " + rs.getString("status")
                            + (rs.getDouble("refund_amount") > 0
                               ? " — refund Rs. " + rs.getDouble("refund_amount") : ""));
                }
            }

            if (!any) {
                System.out.println("No pending requests or refunds.");
            }

            System.out.println("Enter a request number, or 0 to return to Admin Menu.");
            int requestId = readInt("Request: ", 0, Integer.MAX_VALUE);
            if (requestId == 0) {
                return;
            }

            processRequest(requestId);
        }
    }

    static void processRequest(int requestId) throws Exception {
        String passId;
        String phone;
        String type;
        String status;
        int extraMonths;
        String reason;
        String submittedAt;

        try (Connection con = connect();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT * FROM requests WHERE request_id = ?")) {
            ps.setInt(1, requestId);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    System.out.println("Request not found.");
                    return;
                }

                passId = rs.getString("pass_id");
                phone = rs.getString("phone");
                type = rs.getString("request_type");
                status = rs.getString("status");
                extraMonths = rs.getInt("extra_months");
                reason = rs.getString("reason");
                submittedAt = rs.getString("submitted_at");
            }
        }

        if (status.equals("Refund due")) {
            double refund = getRefundAmount(requestId);
            System.out.println("Refund amount due: Rs. " + refund);
            System.out.print("Has the refund been issued? (y/n): ");

            if (input.nextLine().trim().equalsIgnoreCase("y")) {
                try (Connection con = connect()) {
                    con.setAutoCommit(false);
                    try (PreparedStatement payment = con.prepareStatement("""
                            INSERT INTO payments
                            (payment_id, application_id, request_id, payment_type, amount,
                             method, receipt, paid_at, payment_status, transaction_ref)
                            SELECT ?, application_id, ?, 'REFUND', ?, 'Refund', ?, ?,
                                   'Verified', 'Refund issued'
                            FROM applications WHERE pass_id = ?
                            """);
                         PreparedStatement request = con.prepareStatement("""
                            UPDATE requests SET status = 'Refunded', admin_note = 'Refund issued'
                            WHERE request_id = ?
                            """);
                         PreparedStatement app = con.prepareStatement("""
                            UPDATE applications SET status = 'Cancelled'
                            WHERE pass_id = ? AND phone = ?
                            """)) {
                        payment.setString(1, newId("PAY"));
                        payment.setInt(2, requestId);
                        payment.setDouble(3, refund);
                        payment.setString(4, newId("R"));
                        payment.setString(5, LocalDateTime.now().toString());
                        payment.setString(6, passId);
                        payment.executeUpdate();

                        request.setInt(1, requestId);
                        request.executeUpdate();

                        app.setString(1, passId);
                        app.setString(2, phone);
                        app.executeUpdate();

                        con.commit();
                    } catch (Exception e) {
                        con.rollback();
                        throw e;
                    }
                }
                System.out.println("Refund recorded and pass cancelled.");
            }
            return;
        }

        if (status.equals("Payment due")) {
            System.out.println("Waiting for the customer to submit renewal payment.");
            return;
        }

        if (!status.equals("Pending")) {
            System.out.println("This request has already been processed.");
            return;
        }

        if (type.equals("RENEW")) {
            System.out.println("Renewal request: " + extraMonths + " additional month(s).");
            System.out.println("1. Approve; payment will then be due");
            System.out.println("2. Reject");
            int decision = readInt("Choose 1 or 2: ", 1, 2);

            if (decision == 1) {
                setRequestStatus(requestId, "Payment due", "Approved; waiting for payment");
                System.out.println("Renewal approved. Customer must pay before the pass is extended.");
            } else {
                String note = readNonBlank("Rejection reason: ");
                setRequestStatus(requestId, "Rejected", note);
                System.out.println("Renewal request rejected.");
            }
        } else {
            System.out.println("Cancellation reason: " + reason);
            System.out.println("1. Approve cancellation and calculate refund");
            System.out.println("2. Reject");
            int decision = readInt("Choose 1 or 2: ", 1, 2);

            if (decision == 1) {
                double refund = calculateRefund(passId, submittedAt);
                setRequestRefund(requestId, refund);
                setRequestStatus(requestId, "Refund due",
                        "Refund calculated from the cancellation request date");
                setApplicationStatus(passId, phone, "Cancellation pending");
                System.out.println("Estimated refund: Rs. " + refund);
                System.out.println("After issuing the refund, mark it as issued in this menu.");
            } else {
                String note = readNonBlank("Rejection reason: ");
                setRequestStatus(requestId, "Rejected", note);
                System.out.println("Cancellation request rejected; pass remains active.");
            }
        }
    }

    static double calculateRefund(String passId, String requestDateTime) throws Exception {
        LocalDate requested = LocalDate.parse(requestDateTime.substring(0, 10));
        double totalPaid = 0;
        double unusedValue = 0;
        LocalDate issueDate;
        LocalDate expiryDate;

        try (Connection con = connect();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT issue_date, expiry_date FROM applications WHERE pass_id = ?")) {
            ps.setString(1, passId);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || rs.getString("issue_date") == null) {
                    return 0;
                }
                issueDate = LocalDate.parse(rs.getString("issue_date"));
                expiryDate = LocalDate.parse(rs.getString("expiry_date"));
            }
        }

        try (Connection con = connect();
             PreparedStatement ps = con.prepareStatement("""
                 SELECT amount, valid_from, valid_until FROM payments
                 WHERE payment_type IN ('PASS', 'RENEWAL')
                   AND payment_status = 'Verified'
                   AND application_id = (
                       SELECT application_id FROM applications WHERE pass_id = ?
                   )
                 """)) {
            ps.setString(1, passId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    double amount = rs.getDouble("amount");
                    totalPaid += amount;

                    LocalDate from = LocalDate.parse(rs.getString("valid_from"));
                    LocalDate until = LocalDate.parse(rs.getString("valid_until"));
                    long totalDays = Math.max(1, ChronoUnit.DAYS.between(from, until));
                    long remainingDays;

                    if (!requested.isAfter(from)) {
                        remainingDays = totalDays;
                    } else if (!requested.isBefore(until)) {
                        remainingDays = 0;
                    } else {
                        remainingDays = ChronoUnit.DAYS.between(requested, until);
                    }

                    unusedValue += amount * ((double) remainingDays / totalDays);
                }
            }
        }

        if (totalPaid == 0 || !requested.isBefore(expiryDate)) {
            return 0;
        }

        long daysAfterIssue = ChronoUnit.DAYS.between(issueDate, requested);
        if (daysAfterIssue <= 3) {
            return roundMoney(totalPaid * 0.90);
        }

        return roundMoney(unusedValue * 0.90);
    }

    static double getRefundAmount(int requestId) throws Exception {
        try (Connection con = connect();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT refund_amount FROM requests WHERE request_id = ?")) {
            ps.setInt(1, requestId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0;
            }
        }
    }

    static double roundMoney(double amount) {
        return Math.round(amount * 100.0) / 100.0;
    }

    static void setRequestStatus(int requestId, String status, String note) throws Exception {
        try (Connection con = connect();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE requests SET status = ?, admin_note = ? WHERE request_id = ?")) {
            ps.setString(1, status);
            ps.setString(2, note);
            ps.setInt(3, requestId);
            ps.executeUpdate();
        }
    }

    static void setRequestRefund(int requestId, double amount) throws Exception {
        try (Connection con = connect();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE requests SET refund_amount = ? WHERE request_id = ?")) {
            ps.setDouble(1, amount);
            ps.setInt(2, requestId);
            ps.executeUpdate();
        }
    }

    static void setApplicationStatus(String passId, String phone, String status)
            throws Exception {
        try (Connection con = connect();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE applications SET status = ? WHERE pass_id = ? AND phone = ?")) {
            ps.setString(1, status);
            ps.setString(2, passId);
            ps.setString(3, phone);
            ps.executeUpdate();
        }
    }

    static void showPass(ResultSet rs) throws Exception {
        String category = rs.getString("category");
        int months = rs.getInt("months");

        System.out.println("Application ID: " + rs.getString("application_id"));
        System.out.println("Pass ID: " + rs.getString("pass_id"));
        System.out.println("Name: " + rs.getString("name"));
        System.out.println("Route: " + rs.getString("route"));
        System.out.println("Status: " + rs.getString("status"));
        System.out.println("Issue date: " + rs.getString("issue_date"));
        System.out.println("Expiry date: " + rs.getString("expiry_date"));
        showFare(category, months);
    }

    static void showFare(String category, int months) {
        int discount = switch (category) {
            case "Student" -> 40;
            case "Senior" -> 60;
            case "Ex-serviceman" -> 80;
            default -> 0;
        };

        int monthly = monthlyFare(category);
        System.out.println("Base fare per month: Rs. " + BASE_FARE);
        System.out.println("Discount: " + discount + "%");
        System.out.println("Fare after discount per month: Rs. " + monthly);
        System.out.println("Duration: " + months + " month(s)");
        System.out.println("Total fare: Rs. " + (monthly * months));
    }

    static int monthlyFare(String category) {
        int discount = switch (category) {
            case "Student" -> 40;
            case "Senior" -> 60;
            case "Ex-serviceman" -> 80;
            default -> 0;
        };
        return BASE_FARE * (100 - discount) / 100;
    }

    static String choosePaymentMethod() {
        System.out.println("Payment method:");
        System.out.println("1. Cash");
        System.out.println("2. UPI");
        System.out.println("3. Card");
        int choice = readInt("Choose 1 to 3: ", 1, 3);

        return switch (choice) {
            case 1 -> "Cash";
            case 2 -> "UPI";
            default -> "Card";
        };
    }

    static boolean validPassAndPhone1(String passId, String phone, String... statuses)
            throws Exception {
        try (Connection con = connect();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT status FROM applications WHERE pass_id = ? AND phone = ?")) {
            ps.setString(1, passId);
            ps.setString(2, phone);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    for (String status : statuses) {
                        if (status.equals(rs.getString("status"))) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    static void addRequest1(String passId, String phone, String type, int months, String reason)
            throws Exception {
        try (Connection con = connect();
             PreparedStatement ps = con.prepareStatement("""
                 INSERT INTO requests
                 (pass_id, phone, request_type, extra_months, reason, status, submitted_at)
                 VALUES (?, ?, ?, ?, ?, 'Pending', ?)
                 """)) {
            ps.setString(1, passId);
            ps.setString(2, phone);
            ps.setString(3, type);
            ps.setInt(4, months);
            setOptional(ps, 5, reason);
            ps.setString(6, LocalDateTime.now().toString());
            ps.executeUpdate();
        }
    }

    static void updateExpiredPasses() throws Exception {
        try (Connection con = connect();
             PreparedStatement ps = con.prepareStatement("""
                 UPDATE applications SET status = 'Expired'
                 WHERE status = 'Active' AND expiry_date < ?
                 """)) {
            ps.setString(1, LocalDate.now().toString());
            ps.executeUpdate();
        }
    }

    static LocalDate addWorkingDays(LocalDate date, int days) {
        LocalDate result = date;
        int added = 0;

        while (added < days) {
            result = result.plusDays(1);
            if (result.getDayOfWeek().getValue() <= 5) {
                added++;
            }
        }
        return result;
    }

    static String newId(String prefix) {
        return prefix + UUID.randomUUID().toString()
                .replace("-", "").substring(0, 8).toUpperCase();
    }

    static String readNonBlank(String prompt) {
        while (true) {
            System.out.print(prompt);
            String value = input.nextLine().trim();

            if (!value.isEmpty()) {
                return value;
            }

            System.out.println("This field cannot be blank.");
        }
    }

    static String readPassword(String prompt) {
        System.out.print(prompt);
        return input.nextLine();
    }

    static int readInt(String prompt, int min, int max) {
        while (true) {
            System.out.print(prompt);
            try {
                int value = Integer.parseInt(input.nextLine().trim());
                if (value >= min && value <= max) {
                    return value;
                }
            } catch (NumberFormatException e) {
                // Ask again.
            }

            System.out.println("Enter a whole number from " + min + " to " + max + ".");
        }
    }

    static int readYesNo(String prompt) {
        while (true) {
            System.out.print(prompt);
            String answer = input.nextLine().trim();

            if (answer.equalsIgnoreCase("y")) return 1;
            if (answer.equalsIgnoreCase("n")) return 0;

            System.out.println("Enter y or n.");
        }
    }

    static String yesNo(int value) {
        return value == 1 ? "Yes" : "No";
    }

    static void setOptional(PreparedStatement ps, int index, String value)
            throws Exception {
        if (value == null || value.isBlank()) {
            ps.setNull(index, Types.VARCHAR);
        } else {
            ps.setString(index, value);
        }
    }
}