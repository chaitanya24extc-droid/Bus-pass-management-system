import java.awt.datatransfer.StringSelection;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.print.*;
import java.io.File;
import javax.imageio.ImageIO;
import java.security.MessageDigest;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.Vector;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;

public class BusPassGUI extends JFrame {
    private static final Color BG = new Color(28, 30, 33);
    private static final Color PANEL = new Color(38, 41, 45);
    private static final Color PANEL_2 = new Color(47, 51, 56);
    private static final Color TEXT = new Color(235, 238, 242);
    private static final Color MUTED = new Color(163, 171, 181);
    private static final Color BLUE = new Color(0, 153, 230);
    private static final Color RED = new Color(205, 77, 88);

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel pages = new JPanel(cardLayout);
    private JLabel message = new JLabel("Ready");
    private String selectedPassId;
    private String selectedPassPhone;

    public BusPassGUI() {
        super("Transit Pass | Bus Pass Management System");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1050, 700));
        setSize(1180, 790);
        setLocationRelativeTo(null);

        styleDefaults();
        buildWindow();
        showPage("home");
    }
        private void showApplicationSubmitted(String appId, String details) {
            JTextField idField = field();
            idField.setText(appId);
            idField.setEditable(false);

            JButton copyButton = makeButton("COPY ID", BLUE);
            copyButton.addActionListener(e -> {
                Toolkit.getDefaultToolkit()
                        .getSystemClipboard()
                        .setContents(new StringSelection(appId), null);
                copyButton.setText("COPIED");
            });

            JPanel idRow = new JPanel(new BorderLayout(8, 0));
            idRow.setBackground(PANEL);
            idRow.add(idField, BorderLayout.CENTER);
            idRow.add(copyButton, BorderLayout.EAST);

            JTextArea detailsArea = resultArea();
            detailsArea.setText(details);

            JPanel content = new JPanel(new BorderLayout(8, 10));
            content.setBackground(PANEL);
            content.add(new JLabel("Application ID — save this for later:"), BorderLayout.NORTH);
            content.add(idRow, BorderLayout.CENTER);
            content.add(new JScrollPane(detailsArea), BorderLayout.SOUTH);

            JOptionPane.showMessageDialog(
                    this, content, "Application submitted",
                    JOptionPane.INFORMATION_MESSAGE);
        
    }

    private void styleDefaults() {
        UIManager.put("OptionPane.background", PANEL);
        UIManager.put("Panel.background", PANEL);
        UIManager.put("OptionPane.messageForeground", TEXT);
        UIManager.put("Button.background", PANEL_2);
        UIManager.put("Button.foreground", TEXT);
    }

    private void buildWindow() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG);

        JPanel sidebar = new JPanel();
        sidebar.setBackground(new Color(22, 24, 27));
        sidebar.setPreferredSize(new Dimension(220, 0));
        sidebar.setBorder(new EmptyBorder(24, 16, 20, 16));
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));

        JLabel brand = new JLabel("<html><b>TRANSIT</b><br><font size='4'>PASS PORTAL</font></html>");
        brand.setForeground(TEXT);
        brand.setFont(new Font("SansSerif", Font.PLAIN, 22));
        brand.setBorder(new EmptyBorder(6, 8, 28, 0));
        sidebar.add(brand);

        addNav(sidebar, "Overview", "home");
        addNav(sidebar, "Apply for a pass", "apply");
        addNav(sidebar, "Payment", "payment");
        addNav(sidebar, "Check status", "status");
        addNav(sidebar, "Renew or cancel", "requests");

        sidebar.add(Box.createVerticalGlue());

        JButton admin = makeButton("Staff sign in", BLUE);
        admin.setAlignmentX(Component.LEFT_ALIGNMENT);
        admin.addActionListener(e -> staffLogin());
        sidebar.add(admin);
        sidebar.add(Box.createVerticalStrut(10));

        JButton exit = makeButton("Exit", PANEL_2);
        exit.setAlignmentX(Component.LEFT_ALIGNMENT);
        exit.addActionListener(e -> dispose());
        sidebar.add(exit);

        JPanel right = new JPanel(new BorderLayout());
        right.setBackground(BG);

        JPanel top = new JPanel(new BorderLayout());
        top.setBackground(BG);
        top.setBorder(new EmptyBorder(18, 26, 12, 26));

        JLabel heading = new JLabel("BUS PASS MANAGEMENT");
        heading.setForeground(TEXT);
        heading.setFont(new Font("SansSerif", Font.BOLD, 17));
        top.add(heading, BorderLayout.WEST);

        message.setForeground(MUTED);
        message.setFont(new Font("SansSerif", Font.PLAIN, 13));
        top.add(message, BorderLayout.EAST);

        pages.setBackground(BG);
        pages.add(homePage(), "home");
        pages.add(applyPage(), "apply");
        pages.add(paymentPage(), "payment");
        pages.add(statusPage(), "status");
        pages.add(requestPage(), "requests");
        pages.add(adminPage(), "admin");

        right.add(top, BorderLayout.NORTH);
        right.add(pages, BorderLayout.CENTER);

        root.add(sidebar, BorderLayout.WEST);
        root.add(right, BorderLayout.CENTER);
        setContentPane(root);
    }

    private void addNav(JPanel sidebar, String text, String page) {
        JButton button = makeButton(text, PANEL);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.addActionListener(e -> showPage(page));
        sidebar.add(button);
        sidebar.add(Box.createVerticalStrut(8));
    }

    private JButton makeButton(String text, Color color) {
        JButton b = new JButton(text);
        b.setForeground(TEXT);
        b.setBackground(color);
        b.setFocusPainted(false);
        b.setFont(new Font("SansSerif", Font.BOLD, 14));
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(70, 76, 83)),
                new EmptyBorder(11, 14, 11, 14)));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private JPanel page(String title, String subtitle) {
        JPanel p = new JPanel(new BorderLayout(18, 18));
        p.setBackground(BG);
        p.setBorder(new EmptyBorder(18, 26, 24, 26));

        JPanel heading = new JPanel();
        heading.setBackground(BG);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));

        JLabel t = new JLabel(title);
        t.setForeground(TEXT);
        t.setFont(new Font("SansSerif", Font.BOLD, 27));
        JLabel s = new JLabel(subtitle);
        s.setForeground(MUTED);
        s.setFont(new Font("SansSerif", Font.PLAIN, 14));
        s.setBorder(new EmptyBorder(5, 0, 0, 0));

        heading.add(t);
        heading.add(s);
        p.add(heading, BorderLayout.NORTH);
        return p;
    }

    private JPanel panel() {
        JPanel p = new JPanel();
        p.setBackground(PANEL);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(57, 62, 68)),
                new EmptyBorder(18, 18, 18, 18)));
        return p;
    }

    private JTextField field() {
        JTextField f = new JTextField();
        f.setForeground(TEXT);
        f.setCaretColor(TEXT);
        f.setBackground(new Color(29, 32, 35));
        f.setFont(new Font("SansSerif", Font.PLAIN, 14));
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(76, 83, 91)),
                new EmptyBorder(8, 10, 8, 10)));
        return f;
    }

    private JComboBox<String> combo(String... options) {
        JComboBox<String> c = new JComboBox<>(options);
        c.setForeground(TEXT);
        c.setBackground(PANEL_2);
        c.setFont(new Font("SansSerif", Font.PLAIN, 14));
        return c;
    }

    private JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(MUTED);
        l.setFont(new Font("SansSerif", Font.BOLD, 13));
        return l;
    }

    private void addField(JPanel form, String name, JComponent input) {
        JPanel row = new JPanel(new BorderLayout(8, 5));
        row.setBackground(PANEL);
        row.add(label(name), BorderLayout.NORTH);
        row.add(input, BorderLayout.CENTER);
        form.add(row);
    }

    private JPanel formGrid() {
        JPanel grid = new JPanel(new GridLayout(0, 2, 14, 12));
        grid.setBackground(PANEL);
        return grid;
    }

    private void showPage(String name) {
        cardLayout.show(pages, name);
        message.setText(name.equals("admin") ? "Staff area" : "Customer services");
    }

    private JPanel homePage() {
        JPanel p = page("Your travel, made simpler",
                "Apply for a pass, manage an existing pass, or check an application.");

        JPanel body = new JPanel(new GridLayout(2, 2, 16, 16));
        body.setBackground(BG);

        body.add(homeCard("01", "Apply for a pass",
                "Choose a pass category and submit an application.", "apply"));
        body.add(homeCard("02", "Make a payment",
                "Submit payment details for an application or renewal.", "payment"));
        body.add(homeCard("03", "Track your status",
                "Look up your application, pass, or latest request.", "status"));
        body.add(homeCard("04", "Renew or cancel",
                "Send a renewal or cancellation request for review.", "requests"));

        p.add(body, BorderLayout.CENTER);
        return p;
    }

    private JPanel homeCard(String number, String title, String details, String destination) {
        JPanel c = panel();
        c.setLayout(new BoxLayout(c, BoxLayout.Y_AXIS));

        JLabel n = new JLabel(number);
        n.setForeground(BLUE);
        n.setFont(new Font("SansSerif", Font.BOLD, 14));

        JLabel t = new JLabel(title);
        t.setForeground(TEXT);
        t.setFont(new Font("SansSerif", Font.BOLD, 20));
        t.setBorder(new EmptyBorder(12, 0, 8, 0));

        JLabel d = new JLabel("<html><div style='width:260px'>" + details + "</div></html>");
        d.setForeground(MUTED);
        d.setFont(new Font("SansSerif", Font.PLAIN, 14));

        JButton go = makeButton("Open", BLUE);
        go.setAlignmentX(Component.LEFT_ALIGNMENT);
        go.addActionListener(e -> showPage(destination));

        c.add(n);
        c.add(t);
        c.add(d);
        c.add(Box.createVerticalGlue());
        c.add(go);
        return c;
    }

    private JPanel applyPage() {
        JPanel p = page("Apply for a bus pass",
                "Student, senior, and ex-serviceman concessions are reviewed by staff.");

        JTextField name = field();
        JTextField phone = field();
        JTextField age = field();
        JTextField dob = field();
        JTextField proof = field();
        JTextField institution = field();
        JTextField course = field();
        JTextField parent = field();
        JTextField route = field();
        JTextField months = field();
        JComboBox<String> category = combo("None", "Student", "Senior", "Ex-serviceman");

        JCheckBox studentSign = check("Student signed undertaking");
        JCheckBox parentSign = check("Parent/guardian signed");
        JCheckBox stamp = check("Institution stamp checked");

        JPanel form = formGrid();
        addField(form, "Applicant name", name);
        addField(form, "Contact phone", phone);
        addField(form, "Pass category", category);
        addField(form, "Age (not needed for senior)", age);
        addField(form, "Date of birth (senior only, YYYY-MM-DD)", dob);
        addField(form, "Admission/card/service ID (concession only)", proof);
        addField(form, "School/college (student only)", institution);
        addField(form, "Course/class (student only)", course);
        addField(form, "Parent/guardian (student only)", parent);
        addField(form, "Route", route);
        addField(form, "Duration in months (1–36)", months);

        JPanel confirmations = new JPanel(new GridLayout(3, 1, 5, 5));
        confirmations.setBackground(PANEL);
        confirmations.add(studentSign);
        confirmations.add(parentSign);
        confirmations.add(stamp);

        JPanel body = panel();
        body.setLayout(new BorderLayout(12, 12));
        body.add(form, BorderLayout.CENTER);
        body.add(confirmations, BorderLayout.SOUTH);

        JButton submit = makeButton("SUBMIT APPLICATION", BLUE);
        submit.addActionListener(e -> {
            try {
                submitApplication(name.getText(), phone.getText(), age.getText(),
                        dob.getText(), (String) category.getSelectedItem(), proof.getText(),
                        institution.getText(), course.getText(), parent.getText(),
                        route.getText(), months.getText(), studentSign.isSelected(),
                        parentSign.isSelected(), stamp.isSelected());
            } catch (Exception ex) {
                error(ex);
            }
        });

        p.add(body, BorderLayout.CENTER);
        p.add(submit, BorderLayout.SOUTH);
        return p;
    }

    private JCheckBox check(String text) {
        JCheckBox c = new JCheckBox(text);
        c.setForeground(TEXT);
        c.setBackground(PANEL);
        c.setFont(new Font("SansSerif", Font.PLAIN, 13));
        return c;
    }

    private void submitApplication(String name, String phone, String ageText,
            String dobText, String category, String proof, String institution,
            String course, String parent, String route, String monthsText,
            boolean studentSign, boolean parentSign, boolean stamp) throws Exception {

        name = required(name, "Applicant name");
        phone = required(phone, "Contact phone");
        route = required(route, "Route");
        int months = number(monthsText, "Duration", 1, 36);

        int age;
        String dob = null;
        if ("Senior".equals(category)) {
            dob = required(dobText, "Date of birth");
            LocalDate birth = LocalDate.parse(dob);
            age = Period.between(birth, LocalDate.now()).getYears();
            if (age < 60) throw new IllegalArgumentException("Senior concession requires age 60 or above.");
        } else {
            age = number(ageText, "Age", 1, 120);
        }

        if (!"None".equals(category)) proof = required(proof, "Concession proof ID");
        if ("Student".equals(category)) {
            institution = required(institution, "School/college");
            course = required(course, "Course/class");
            parent = required(parent, "Parent/guardian name");
        }

        String status = "None".equals(category) ? "Payment due" : "Pending review";
        String due = "None".equals(category) ? null
                : BusPassManagementSystem.addWorkingDays(LocalDate.now(), 3).toString();
        String appId = BusPassManagementSystem.newId("APP");

        try (Connection con = BusPassManagementSystem.connect();
             PreparedStatement ps = con.prepareStatement("""
                 INSERT INTO applications (
                     application_id, name, phone, age, date_of_birth, route, months,
                     category, proof_id, institution, course, parent_name,
                     student_signed, parent_signed, institution_stamped, status,
                     submitted_at, decision_due
                 ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                 """)) {
            ps.setString(1, appId);
            ps.setString(2, name);
            ps.setString(3, phone);
            ps.setInt(4, age);
            setOptional(ps, 5, dob);
            ps.setString(6, route);
            ps.setInt(7, months);
            ps.setString(8, category);
            setOptional(ps, 9, proof);
            setOptional(ps, 10, institution);
            setOptional(ps, 11, course);
            setOptional(ps, 12, parent);
            ps.setInt(13, studentSign ? 1 : 0);
            ps.setInt(14, parentSign ? 1 : 0);
            ps.setInt(15, stamp ? 1 : 0);
            ps.setString(16, status);
            ps.setString(17, LocalDateTime.now().toString());
            setOptional(ps, 18, due);
            ps.executeUpdate();
        }

        String msg = "Application ID: " + appId + "\nStatus: " + status;
        if ("None".equals(category)) {
            msg += "\nPayment is required before a pass is issued.\nEstimated fare: Rs. "
                    + (BusPassManagementSystem.monthlyFare(category) * months);
        } else {
            msg += "\nExpected decision by: " + due
                    + "\nAfter approval, submit payment from the Payment page.";
        }
        showApplicationSubmitted(appId, msg);
        showPage("status");
    }

    private JPanel paymentPage() {
        JPanel p = page("Payment centre",
                "Payment details are submitted for staff verification. A pass is issued only after verification.");

        JComboBox<String> type = combo("New pass application", "Approved renewal");
        JTextField appId = field();
        JTextField requestId = field();
        JTextField phone = field();
        JComboBox<String> method = combo("Cash", "UPI", "Card");
        JTextField reference = field();

        JPanel form = formGrid();
        addField(form, "Payment for", type);
        addField(form, "Application ID (new pass)", appId);
        addField(form, "Renewal request number (renewal)", requestId);
        addField(form, "Contact phone", phone);
        addField(form, "Payment method", method);
        addField(form, "Transaction reference / cash slip number", reference);

        JTextArea result = resultArea();
        JButton submit = makeButton("SUBMIT PAYMENT DETAILS", BLUE);
        submit.addActionListener(e -> {
            try {
                submitPayment(type.getSelectedIndex(), appId.getText(), requestId.getText(),
                        phone.getText(), (String) method.getSelectedItem(),
                        reference.getText(), result);
            } catch (Exception ex) {
                error(ex);
            }
        });

        JPanel body = panel();
        body.setLayout(new BorderLayout(14, 14));
        body.add(form, BorderLayout.NORTH);
        body.add(new JScrollPane(result), BorderLayout.CENTER);
        p.add(body, BorderLayout.CENTER);
        p.add(submit, BorderLayout.SOUTH);
        return p;
    }

    private void submitPayment(int choice, String appId, String requestId, String phone,
            String method, String reference, JTextArea result) throws Exception {
        phone = required(phone, "Contact phone");
        method = required(method, "Payment method");
        reference = required(reference, "Payment reference");

        String category;
        int months;
        String passId = null;
        String oldExpiry = null;
        Integer reqId = null;

        if (choice == 0) {
            appId = required(appId, "Application ID");
            try (Connection con = BusPassManagementSystem.connect();
                 PreparedStatement ps = con.prepareStatement("""
                     SELECT category, months FROM applications
                     WHERE application_id = ? AND phone = ? AND status = 'Payment due'
                     """)) {
                ps.setString(1, appId);
                ps.setString(2, phone);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new IllegalArgumentException("No matching application is awaiting payment.");
                    category = rs.getString("category");
                    months = rs.getInt("months");
                }
            }
        } else {
            reqId = number(requestId, "Renewal request number", 1, Integer.MAX_VALUE);
            try (Connection con = BusPassManagementSystem.connect();
                 PreparedStatement ps = con.prepareStatement("""
                     SELECT pass_id, extra_months FROM requests
                     WHERE request_id = ? AND phone = ? AND request_type = 'RENEW'
                       AND status = 'Payment due'
                     """)) {
                ps.setInt(1, reqId);
                ps.setString(2, phone);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new IllegalArgumentException("No approved renewal is awaiting payment.");
                    passId = rs.getString("pass_id");
                    months = rs.getInt("extra_months");
                }
            }
            try (Connection con = BusPassManagementSystem.connect();
                 PreparedStatement ps = con.prepareStatement("""
                     SELECT application_id, category, expiry_date FROM applications
                     WHERE pass_id = ? AND phone = ?
                     """)) {
                ps.setString(1, passId);
                ps.setString(2, phone);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new IllegalArgumentException("Pass record not found.");
                    appId = rs.getString("application_id");
                    category = rs.getString("category");
                    oldExpiry = rs.getString("expiry_date");
                }
            }
        }

        int amount = BusPassManagementSystem.monthlyFare(category) * months;
        LocalDate today = LocalDate.now();
        LocalDate from = today;
        if (oldExpiry != null) {
            LocalDate end = LocalDate.parse(oldExpiry);
            if (end.isAfter(today)) from = end;
        }
        LocalDate until = from.plusMonths(months);

        try (Connection con = BusPassManagementSystem.connect()) {
            con.setAutoCommit(false);
            try {
                try (PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO payments
                    (payment_id, application_id, request_id, payment_type, amount, method,
                     receipt, paid_at, valid_from, valid_until, payment_status, transaction_ref)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'Pending', ?)
                    """)) {
                    ps.setString(1, BusPassManagementSystem.newId("PAY"));
                    ps.setString(2, appId);
                    if (choice == 0) ps.setNull(3, Types.INTEGER); else ps.setInt(3, reqId);
                    ps.setString(4, choice == 0 ? "PASS" : "RENEWAL");
                    ps.setInt(5, amount);
                    ps.setString(6, method);
                    ps.setString(7, BusPassManagementSystem.newId("R"));
                    ps.setString(8, LocalDateTime.now().toString());
                    ps.setString(9, from.toString());
                    ps.setString(10, until.toString());
                    ps.setString(11, reference);
                    ps.executeUpdate();
                }

                String updateSql = choice == 0
                        ? "UPDATE applications SET status='Payment verification pending' WHERE application_id=? AND phone=? AND status='Payment due'"
                        : "UPDATE requests SET status='Payment verification pending' WHERE request_id=? AND phone=? AND status='Payment due'";

                try (PreparedStatement ps = con.prepareStatement(updateSql)) {
                    if (choice == 0) {
                        ps.setString(1, appId);
                        ps.setString(2, phone);
                    } else {
                        ps.setInt(1, reqId);
                        ps.setString(2, phone);
                    }
                    if (ps.executeUpdate() != 1) throw new SQLException("Record changed before payment was saved.");
                }

                con.commit();
            } catch (Exception ex) {
                con.rollback();
                throw ex;
            }
        }

        result.setText("Payment submitted for staff verification.\n\n"
                + "Amount: Rs. " + amount + "\n"
                + "Method: " + method + "\n"
                + "Reference: " + reference + "\n\n"
                + "The pass will be issued or renewed after payment verification.");
        setReady("Payment details submitted");
    }

    private JPanel statusPage() {
        JPanel p = page("Application and pass status",
                "Enter the exact ID and phone number used in the application.");
        JTextField id = field();
        JTextField phone = field();
        JPanel form = formGrid();
        addField(form, "Application ID or pass ID (case-sensitive)", id);
        addField(form, "Contact phone", phone);
        JTextArea result = resultArea();
        JButton search = makeButton("LOOK UP STATUS", BLUE);
        JButton passButton = makeButton("VIEW / SAVE / PRINT PASS", PANEL_2);
        passButton.setEnabled(false);
        search.addActionListener(e -> {
            try {
                lookupStatus(id.getText(), phone.getText(), result);
                passButton.setEnabled(selectedPassId != null);
            } catch (Exception ex) { error(ex); }
        });
        passButton.addActionListener(e -> {
            try { showIssuedPass(); } catch (Exception ex) { error(ex); }
        });
        JPanel body = panel();
        body.setLayout(new BorderLayout(14, 14));
        body.add(form, BorderLayout.NORTH);
        body.add(new JScrollPane(result), BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.setBackground(BG);
        actions.add(search);
        actions.add(passButton);
        p.add(body, BorderLayout.CENTER);
        p.add(actions, BorderLayout.SOUTH);
        return p;
    }
    private void lookupStatus(String id, String phone, JTextArea result) throws Exception {
        id = required(id, "Application/pass ID");
        phone = required(phone, "Contact phone");
        selectedPassId = null;
        selectedPassPhone = null;
        StringBuilder text = new StringBuilder();

        try (Connection con = BusPassManagementSystem.connect();
             PreparedStatement ps = con.prepareStatement("""
                 SELECT * FROM applications
                 WHERE (application_id = ? OR pass_id = ?) AND phone = ?
                 """)) {
            ps.setString(1, id);
            ps.setString(2, id);
            ps.setString(3, phone);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    result.setText("No matching record. Check the ID and phone number.");
                    return;
                }

                selectedPassId = rs.getString("pass_id");
                if (selectedPassId != null) selectedPassPhone = phone;

                String status = rs.getString("status");
                text.append("STATUS: ").append(status).append("\n\n")
                    .append("Application ID: ").append(rs.getString("application_id")).append("\n")
                    .append("Pass ID: ").append(value(rs.getString("pass_id"))).append("\n")
                    .append("Name: ").append(rs.getString("name")).append("\n")
                    .append("Route: ").append(rs.getString("route")).append("\n")
                    .append("Category: ").append(rs.getString("category")).append("\n");

                if ("Pending review".equals(status)) {
                    text.append("\nYour application is being reviewed.\nExpected decision by: ")
                        .append(value(rs.getString("decision_due")));
                } else if ("Needs correction".equals(status) || "Rejected".equals(status)) {
                    text.append("\nDetails: ").append(value(rs.getString("status_reason")));
                } else if ("Payment due".equals(status)) {
                    text.append("\nPayment is required before a pass is issued.\nEstimated total: Rs. ")
                        .append(BusPassManagementSystem.monthlyFare(rs.getString("category"))
                                * rs.getInt("months"));
                } else if ("Payment verification pending".equals(status)) {
                    text.append("\nPayment is awaiting staff verification; no pass has been issued yet.");
                } else if (rs.getString("pass_id") != null) {
                    text.append("\nIssue date: ").append(value(rs.getString("issue_date")))
                        .append("\nExpiry date: ").append(value(rs.getString("expiry_date")))
                        .append("\nPass status: ").append(status);
                }

                String passId = rs.getString("pass_id");
                if (passId != null) {
                    try (PreparedStatement req = con.prepareStatement("""
                        SELECT request_type, status, admin_note, refund_amount
                        FROM requests WHERE pass_id = ? AND phone = ?
                        ORDER BY request_id DESC LIMIT 1
                        """)) {
                        req.setString(1, passId);
                        req.setString(2, phone);
                        try (ResultSet r = req.executeQuery()) {
                            if (r.next()) {
                                text.append("\n\nLatest request: ").append(r.getString("request_type"))
                                    .append(" — ").append(r.getString("status"));
                                if (r.getDouble("refund_amount") > 0) {
                                    text.append("\nRefund amount: Rs. ").append(r.getDouble("refund_amount"));
                                }
                                if (r.getString("admin_note") != null) {
                                    text.append("\nStaff note: ").append(r.getString("admin_note"));
                                }
                            }
                        }
                    }
                }
            }
        }
        result.setText(text.toString());
    }

    private void showIssuedPass() throws Exception {
        String name, route, category, issue, expiry, status;
        try (Connection con = BusPassManagementSystem.connect();
             PreparedStatement ps = con.prepareStatement("SELECT name, route, category, issue_date, expiry_date, status FROM applications WHERE pass_id=? AND phone=?")) {
            ps.setString(1, selectedPassId);
            ps.setString(2, selectedPassPhone);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Issued pass not found.");
                name = rs.getString("name"); route = rs.getString("route");
                category = rs.getString("category"); issue = rs.getString("issue_date");
                expiry = rs.getString("expiry_date"); status = rs.getString("status");
            }
        }
        PassCard card = new PassCard(name, selectedPassId, route, category, issue, expiry, status);
        JButton copy = makeButton("COPY PASS ID", PANEL_2);
        JButton save = makeButton("SAVE AS PNG", BLUE);
        JButton print = makeButton("PRINT", PANEL_2);
        copy.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(selectedPassId), null);
            copy.setText("COPIED");
        });
        save.addActionListener(e -> savePassImage(card));
        print.addActionListener(e -> printPass(card));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.setBackground(PANEL);
        buttons.add(copy); buttons.add(save); buttons.add(print);
        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBackground(PANEL);
        content.add(card, BorderLayout.CENTER); content.add(buttons, BorderLayout.SOUTH);
        JOptionPane.showMessageDialog(this, content, "Issued bus pass", JOptionPane.PLAIN_MESSAGE);
    }

    private void savePassImage(PassCard card) {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("BusPass-" + selectedPassId + ".png"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            File file = chooser.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(".png")) file = new File(file.getParentFile(), file.getName() + ".png");
            card.setSize(card.getPreferredSize());
            BufferedImage image = new BufferedImage(card.getWidth(), card.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics(); card.paint(g); g.dispose();
            ImageIO.write(image, "png", file);
            info("Pass image saved:\n" + file.getAbsolutePath());
        } catch (Exception ex) { error(ex); }
    }

    private void printPass(PassCard card) {
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setJobName("Bus Pass " + selectedPassId);
        job.setPrintable((graphics, format, page) -> {
            if (page > 0) return Printable.NO_SUCH_PAGE;
            double width = 242.0;
            double scale = width / card.getPreferredSize().width;
            double height = card.getPreferredSize().height * scale;
            Graphics2D g = (Graphics2D) graphics.create();
            g.translate(format.getImageableX() + (format.getImageableWidth() - width) / 2,
                    format.getImageableY() + (format.getImageableHeight() - height) / 2);
            g.scale(scale, scale); card.setSize(card.getPreferredSize()); card.printAll(g); g.dispose();
            return Printable.PAGE_EXISTS;
        });
        try { if (job.printDialog()) job.print(); } catch (PrinterException ex) { error(ex); }
    }

    private static class PassCard extends JPanel {
        private final String name, id, route, category, issue, expiry, status;
        PassCard(String name, String id, String route, String category, String issue, String expiry, String status) {
            this.name=name; this.id=id; this.route=route; this.category=category;
            this.issue=issue; this.expiry=expiry; this.status=status;
            setPreferredSize(new Dimension(900, 540)); setOpaque(false);
        }
        private String safe(String s) { return s == null || s.isBlank() ? "—" : s; }
        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g=(Graphics2D)graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w=getWidth(), h=getHeight();
            g.setPaint(new GradientPaint(0,0,new Color(18,39,65),w,h,new Color(24,91,130)));
            g.fillRoundRect(0,0,w,h,34,34);
            g.setColor(new Color(0,175,220)); g.fillRoundRect(0,0,18,h,18,18);
            g.setColor(new Color(255,255,255,30)); g.fillOval(w-230,-115,340,340); g.fillOval(w-135,h-175,220,220);
            g.setColor(Color.WHITE); g.setFont(new Font("SansSerif",Font.BOLD,34)); g.drawString("CITYLINE TRANSIT",54,70);
            g.setFont(new Font("SansSerif",Font.PLAIN,17)); g.setColor(new Color(205,225,239)); g.drawString("BUS PASS",56,105);
            g.setColor(new Color(0,175,220)); g.fillRoundRect(w-205,42,150,42,18,18);
            g.setColor(Color.WHITE); g.setFont(new Font("SansSerif",Font.BOLD,16)); g.drawString(safe(status).toUpperCase(),w-187,69);
            g.setColor(new Color(205,225,239)); g.setFont(new Font("SansSerif",Font.BOLD,14));
            g.drawString("PASS HOLDER",58,175); g.drawString("PASS ID",58,285); g.drawString("ROUTE",470,175);
            g.drawString("CATEGORY",470,285); g.drawString("ISSUED",58,395); g.drawString("VALID UNTIL",470,395);
            g.setColor(Color.WHITE); g.setFont(new Font("SansSerif",Font.BOLD,27));
            g.drawString(safe(name),58,215); g.drawString(safe(id),58,325); g.drawString(safe(route),470,215); g.drawString(safe(category),470,325);
            g.setFont(new Font("SansSerif",Font.PLAIN,21)); g.drawString(safe(issue),58,435); g.drawString(safe(expiry),470,435);
            g.setColor(new Color(255,255,255,65)); g.drawRoundRect(1,1,w-3,h-3,34,34); g.dispose();
        }
    }
    private JPanel requestPage() {
        JPanel p = page("Renew or cancel a pass",
                "Requests are reviewed by staff. Use the BP pass ID, not the APP application ID.");

        JComboBox<String> type = combo("Renewal", "Cancellation");
        JTextField passId = field();
        JTextField phone = field();
        JTextField months = field();
        JTextField reason = field();

        JPanel form = formGrid();
        addField(form, "Request type", type);
        addField(form, "Pass ID (case-sensitive, begins BP)", passId);
        addField(form, "Contact phone", phone);
        addField(form, "Additional months (renewal only)", months);
        addField(form, "Cancellation reason (cancellation only)", reason);

        JTextArea result = resultArea();
        JButton submit = makeButton("SUBMIT REQUEST", BLUE);
        submit.addActionListener(e -> {
            try {
                submitRequest(type.getSelectedIndex(), passId.getText(), phone.getText(),
                        months.getText(), reason.getText(), result);
            } catch (Exception ex) {
                error(ex);
            }
        });

        JPanel body = panel();
        body.setLayout(new BorderLayout(14, 14));
        body.add(form, BorderLayout.NORTH);
        body.add(new JScrollPane(result), BorderLayout.CENTER);
        p.add(body, BorderLayout.CENTER);
        p.add(submit, BorderLayout.SOUTH);
        return p;
    }

    private void submitRequest(int choice, String passId, String phone,
            String monthsText, String reason, JTextArea result) throws Exception {
        passId = required(passId, "Pass ID");
        phone = required(phone, "Contact phone");
        int months = choice == 0 ? number(monthsText, "Additional months", 1, 36) : 0;

        try (Connection con = BusPassManagementSystem.connect();
             PreparedStatement ps = con.prepareStatement("""
                 SELECT status FROM applications WHERE pass_id = ? AND phone = ?
                 """)) {
            ps.setString(1, passId);
            ps.setString(2, phone);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || !(rs.getString(1).equals("Active")
                        || rs.getString(1).equals("Expired"))) {
                    throw new IllegalArgumentException("No active or expired pass matched those details.");
                }
            }
        }

        int requestId;
        try (Connection con = BusPassManagementSystem.connect();
             PreparedStatement ps = con.prepareStatement("""
                 INSERT INTO requests
                 (pass_id, phone, request_type, extra_months, reason, status, submitted_at)
                 VALUES (?, ?, ?, ?, ?, 'Pending', ?)
                 """, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, passId);
            ps.setString(2, phone);
            ps.setString(3, choice == 0 ? "RENEW" : "CANCEL");
            ps.setInt(4, months);
            setOptional(ps, 5, reason);
            ps.setString(6, LocalDateTime.now().toString());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                requestId = keys.next() ? keys.getInt(1) : -1;
            }
        }

        result.setText("Request submitted for staff review.\n\n"
                + "Request number: " + requestId + "\n"
                + "Type: " + (choice == 0 ? "Renewal" : "Cancellation")
                + (choice == 0 ? "\nAdditional months: " + months : "")
                + "\n\nCheck its status from the Check status page.");
        setReady("Request submitted");
    }

    private JPanel adminPage() {
        JPanel p = page("Staff workspace",
                "Review applications, verify submitted payments, and process pass requests.");

        JTabbedPane tabs = new JTabbedPane();
        tabs.setBackground(PANEL);
        tabs.setForeground(TEXT);
        tabs.addTab("Applications", adminTablePanel("applications"));
        tabs.addTab("Payments", adminTablePanel("payments"));
        tabs.addTab("Requests", adminTablePanel("requests"));

        JButton logout = makeButton("LOG OUT", PANEL_2);
        logout.addActionListener(e -> showPage("home"));

        JPanel body = new JPanel(new BorderLayout(10, 10));
        body.setBackground(BG);
        body.add(tabs, BorderLayout.CENTER);
        body.add(logout, BorderLayout.SOUTH);
        p.add(body, BorderLayout.CENTER);
        return p;
    }

    private JPanel adminTablePanel(String kind) {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBackground(PANEL);
        JTable table = new JTable();
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        styleTable(table);

        JButton refresh = makeButton("Refresh", PANEL_2);
        JButton action = makeButton(kind.equals("applications") ? "Review selected"
                : kind.equals("payments") ? "Verify selected" : "Process selected", BLUE);

        refresh.addActionListener(e -> loadAdminTable(kind, table));
        action.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) {
                info("Select a row first.");
                return;
            }
            try {
                Object id = table.getValueAt(row, 0);
                if (kind.equals("applications")) reviewApplication(id.toString());
                else if (kind.equals("payments")) verifyPayment(id.toString());
                else processRequest(Integer.parseInt(id.toString()));
                loadAdminTable(kind, table);
            } catch (Exception ex) {
                error(ex);
            }
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.setBackground(PANEL);
        buttons.add(refresh);
        buttons.add(action);

        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        panel.add(buttons, BorderLayout.SOUTH);
        loadAdminTable(kind, table);
        return panel;
    }

    private void styleTable(JTable table) {
        table.setBackground(new Color(29, 32, 35));
        table.setForeground(TEXT);
        table.setGridColor(new Color(64, 70, 76));
        table.setSelectionBackground(new Color(0, 105, 160));
        table.setSelectionForeground(Color.WHITE);
        table.setRowHeight(27);
        table.getTableHeader().setBackground(PANEL_2);
        table.getTableHeader().setForeground(TEXT);
        table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 13));
    }

    private void loadAdminTable(String kind, JTable table) {
        try (Connection con = BusPassManagementSystem.connect();
             Statement st = con.createStatement()) {
            String sql = switch (kind) {
                case "applications" -> """
                    SELECT application_id, name, category, phone, decision_due
                    FROM applications WHERE status = 'Pending review' ORDER BY submitted_at
                    """;
                case "payments" -> """
                    SELECT payment_id, payment_type, application_id, amount, method, transaction_ref
                    FROM payments WHERE payment_status = 'Pending' ORDER BY paid_at
                    """;
                default -> """
                    SELECT request_id, request_type, pass_id, status, refund_amount
                    FROM requests WHERE status IN ('Pending','Payment due','Refund due')
                    ORDER BY request_id
                    """;
            };
            try (ResultSet rs = st.executeQuery(sql)) {
                table.setModel(toModel(rs));
            }
            styleTable(table);
        } catch (Exception ex) {
            error(ex);
        }
    }

    private DefaultTableModel toModel(ResultSet rs) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        Vector<String> columns = new Vector<>();
        for (int i = 1; i <= md.getColumnCount(); i++) columns.add(md.getColumnLabel(i));
        Vector<Vector<Object>> rows = new Vector<>();
        while (rs.next()) {
            Vector<Object> row = new Vector<>();
            for (int i = 1; i <= md.getColumnCount(); i++) row.add(rs.getObject(i));
            rows.add(row);
        }
        return new DefaultTableModel(rows, columns) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
    }

    private void staffLogin() {
        JTextField user = field();
        JPasswordField pass = new JPasswordField();
        pass.setBackground(new Color(29, 32, 35));
        pass.setForeground(TEXT);
        pass.setCaretColor(TEXT);
        JPanel form = new JPanel(new GridLayout(2, 2, 8, 8));
        form.setBackground(PANEL);
        form.add(label("Username"));
        form.add(user);
        form.add(label("Password"));
        form.add(pass);

        if (JOptionPane.showConfirmDialog(this, form, "Staff sign in",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;

        try (Connection con = BusPassManagementSystem.connect();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT salt, password_hash FROM admins WHERE username = ?")) {
            ps.setString(1, user.getText().trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    info("Incorrect username or password.");
                    return;
                }
                byte[] entered = BusPassManagementSystem.passwordHash(
                        new String(pass.getPassword()), rs.getBytes("salt"));
                if (!MessageDigest.isEqual(entered, rs.getBytes("password_hash"))) {
                    info("Incorrect username or password.");
                    return;
                }
            }
            showPage("admin");
            setReady("Signed in as " + user.getText().trim());
        } catch (Exception ex) {
            error(ex);
        }
    }

    private void reviewApplication(String appId) throws Exception {
        String category;
        int studentSigned, parentSigned, stamped;
        String details;

        try (Connection con = BusPassManagementSystem.connect();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT * FROM applications WHERE application_id=? AND status='Pending review'")) {
            ps.setString(1, appId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Pending application not found.");
                category = rs.getString("category");
                studentSigned = rs.getInt("student_signed");
                parentSigned = rs.getInt("parent_signed");
                stamped = rs.getInt("institution_stamped");
                details = "Applicant: " + rs.getString("name")
                        + "\nCategory: " + category
                        + "\nAge: " + rs.getInt("age")
                        + "\nProof ID entered: " + value(rs.getString("proof_id"))
                        + "\nInstitution: " + value(rs.getString("institution"))
                        + "\nCourse/class: " + value(rs.getString("course"))
                        + "\nParent/guardian: " + value(rs.getString("parent_name"))
                        + "\nStudent signed: " + yesNo(studentSigned)
                        + "\nParent signed: " + yesNo(parentSigned)
                        + "\nInstitution stamp: " + yesNo(stamped);
            }
        }

        Object[] choices = {"Approve", "Needs correction", "Reject", "Cancel"};
        int choice = JOptionPane.showOptionDialog(this, details, "Review " + appId,
                JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, choices, choices[0]);
        if (choice == 3 || choice < 0) return;

        String status;
        String reason = null;
        if (choice == 0) {
            if ("Student".equals(category)
                    && (studentSigned == 0 || parentSigned == 0 || stamped == 0)) {
                info("Cannot approve: confirm the undertaking, signatures, and institution stamp.");
                return;
            }
            status = "Payment due";
        } else {
            status = choice == 1 ? "Needs correction" : "Rejected";
            reason = JOptionPane.showInputDialog(this, "Enter the reason:");
            if (reason == null || reason.isBlank()) return;
        }

        try (Connection con = BusPassManagementSystem.connect();
             PreparedStatement ps = con.prepareStatement("""
                 UPDATE applications SET status=?, status_reason=?
                 WHERE application_id=? AND status='Pending review'
                 """)) {
            ps.setString(1, status);
            setOptional(ps, 2, reason);
            ps.setString(3, appId);
            ps.executeUpdate();
        }
        info("Application updated: " + status);
    }

    private void verifyPayment(String paymentId) throws Exception {
        String appId, type, from, until;
        String reference;
        Integer requestId;
        double amount;

        try (Connection con = BusPassManagementSystem.connect();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT * FROM payments WHERE payment_id=? AND payment_status='Pending'")) {
            ps.setString(1, paymentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Pending payment not found.");
                appId = rs.getString("application_id");
                type = rs.getString("payment_type");
                reference = rs.getString("transaction_ref");
                from = rs.getString("valid_from");
                until = rs.getString("valid_until");
                amount = rs.getDouble("amount");
                int id = rs.getInt("request_id");
                requestId = rs.wasNull() ? null : id;
            }
        }

        String question = "Type: " + type + "\nAmount: Rs. " + amount
                + "\nReference: " + value(reference) + "\n\nConfirm payment was received?";
        if (JOptionPane.showConfirmDialog(this, question, "Verify payment",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;

        String passId = type.equals("PASS") ? BusPassManagementSystem.newId("BP") : null;
        String receipt = BusPassManagementSystem.newId("R");

        try (Connection con = BusPassManagementSystem.connect()) {
            con.setAutoCommit(false);
            try {
                if ("PASS".equals(type)) {
                    try (PreparedStatement ps = con.prepareStatement("""
                        UPDATE applications SET pass_id=?, status='Active', issue_date=?,
                            expiry_date=?, status_reason=NULL
                        WHERE application_id=? AND status='Payment verification pending'
                        """)) {
                        ps.setString(1, passId);
                        ps.setString(2, from);
                        ps.setString(3, until);
                        ps.setString(4, appId);
                        if (ps.executeUpdate() != 1) throw new SQLException("Application is not awaiting payment verification.");
                    }
                } else if ("RENEWAL".equals(type) && requestId != null) {
                    int extra;
                    try (PreparedStatement ps = con.prepareStatement(
                            "SELECT extra_months FROM requests WHERE request_id=?")) {
                        ps.setInt(1, requestId);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next()) throw new SQLException("Renewal request not found.");
                            extra = rs.getInt(1);
                        }
                    }
                    try (PreparedStatement ps = con.prepareStatement("""
                        UPDATE applications SET months=months+?, expiry_date=?, status='Active'
                        WHERE application_id=?
                        """)) {
                        ps.setInt(1, extra);
                        ps.setString(2, until);
                        ps.setString(3, appId);
                        ps.executeUpdate();
                    }
                    try (PreparedStatement ps = con.prepareStatement("""
                        UPDATE requests SET status='Completed',
                            admin_note='Payment verified; renewal completed'
                        WHERE request_id=?
                        """)) {
                        ps.setInt(1, requestId);
                        ps.executeUpdate();
                    }
                }

                try (PreparedStatement ps = con.prepareStatement("""
                    UPDATE payments SET payment_status='Verified', receipt=?, paid_at=?
                    WHERE payment_id=?
                    """)) {
                    ps.setString(1, receipt);
                    ps.setString(2, LocalDateTime.now().toString());
                    ps.setString(3, paymentId);
                    ps.executeUpdate();
                }
                con.commit();
            } catch (Exception ex) {
                con.rollback();
                throw ex;
            }
        }

        info("Payment verified.\nReceipt: " + receipt
                + (passId == null ? "" : "\nPass ID: " + passId)
                + "\nValid through: " + until);
    }

    private void processRequest(int requestId) throws Exception {
        String type, status, passId, phone, submittedAt;
        int extraMonths;
        try (Connection con = BusPassManagementSystem.connect();
             PreparedStatement ps = con.prepareStatement("SELECT * FROM requests WHERE request_id=?")) {
            ps.setInt(1, requestId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Request not found.");
                type = rs.getString("request_type");
                status = rs.getString("status");
                passId = rs.getString("pass_id");
                phone = rs.getString("phone");
                submittedAt = rs.getString("submitted_at");
                extraMonths = rs.getInt("extra_months");
            }
        }

        if ("Payment due".equals(status)
                || "Payment verification pending".equals(status)) {
            info("This request is waiting for the customer's renewal payment.");
            return;
        }

        if ("Refund due".equals(status)) {
            double refund = BusPassManagementSystem.getRefundAmount(requestId);
            if (JOptionPane.showConfirmDialog(this,
                    "Record refund of Rs. " + refund + " as issued?",
                    "Issue refund", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;

            try (Connection con = BusPassManagementSystem.connect()) {
                con.setAutoCommit(false);
                try (PreparedStatement pay = con.prepareStatement("""
                        INSERT INTO payments
                        (payment_id, application_id, request_id, payment_type, amount,
                         method, receipt, paid_at, payment_status, transaction_ref)
                        SELECT ?, application_id, ?, 'REFUND', ?, 'Refund', ?, ?,
                               'Verified', 'Refund issued'
                        FROM applications WHERE pass_id=?
                        """);
                     PreparedStatement req = con.prepareStatement(
                             "UPDATE requests SET status='Refunded', admin_note='Refund issued' WHERE request_id=?");
                     PreparedStatement app = con.prepareStatement(
                             "UPDATE applications SET status='Cancelled' WHERE pass_id=? AND phone=?")) {
                    pay.setString(1, BusPassManagementSystem.newId("PAY"));
                    pay.setInt(2, requestId);
                    pay.setDouble(3, refund);
                    pay.setString(4, BusPassManagementSystem.newId("R"));
                    pay.setString(5, LocalDateTime.now().toString());
                    pay.setString(6, passId);
                    pay.executeUpdate();
                    req.setInt(1, requestId);
                    req.executeUpdate();
                    app.setString(1, passId);
                    app.setString(2, phone);
                    app.executeUpdate();
                    con.commit();
                } catch (Exception ex) {
                    con.rollback();
                    throw ex;
                }
            }
            info("Refund recorded and pass cancelled.");
            return;
        }

        if (!"Pending".equals(status)) {
            info("This request has already been processed.");
            return;
        }

        if ("RENEW".equals(type)) {
            Object[] choices = {"Approve (payment due)", "Reject", "Cancel"};
            int choice = JOptionPane.showOptionDialog(this,
                    "Renewal request for " + extraMonths + " month(s).\nPass: " + passId,
                    "Review renewal", JOptionPane.DEFAULT_OPTION,
                    JOptionPane.QUESTION_MESSAGE, null, choices, choices[0]);
            if (choice == 2 || choice < 0) return;
            String newStatus = choice == 0 ? "Payment due" : "Rejected";
            String note = choice == 0 ? "Approved; waiting for payment" : JOptionPane.showInputDialog(this, "Rejection reason:");
            if (note == null) return;
            setRequestStatus(requestId, newStatus, note);
            info("Renewal request updated: " + newStatus);
        } else {
            Object[] choices = {"Approve cancellation", "Reject", "Cancel"};
            int choice = JOptionPane.showOptionDialog(this,
                    "Cancellation request for pass " + passId,
                    "Review cancellation", JOptionPane.DEFAULT_OPTION,
                    JOptionPane.QUESTION_MESSAGE, null, choices, choices[0]);
            if (choice == 2 || choice < 0) return;
            if (choice == 0) {
                double refund = BusPassManagementSystem.calculateRefund(passId, submittedAt);
                try (Connection con = BusPassManagementSystem.connect();
                     PreparedStatement ps = con.prepareStatement("""
                         UPDATE requests SET status='Refund due', refund_amount=?,
                             admin_note='Refund calculated from the cancellation request date'
                         WHERE request_id=?
                         """)) {
                    ps.setDouble(1, refund);
                    ps.setInt(2, requestId);
                    ps.executeUpdate();
                }
                try (Connection con = BusPassManagementSystem.connect();
                     PreparedStatement ps = con.prepareStatement(
                             "UPDATE applications SET status='Cancellation pending' WHERE pass_id=? AND phone=?")) {
                    ps.setString(1, passId);
                    ps.setString(2, phone);
                    ps.executeUpdate();
                }
                info("Cancellation approved.\nRefund due: Rs. " + refund
                        + "\nSelect this request again after issuing the refund.");
            } else {
                String reason = JOptionPane.showInputDialog(this, "Rejection reason:");
                if (reason == null || reason.isBlank()) return;
                setRequestStatus(requestId, "Rejected", reason);
                info("Cancellation request rejected.");
            }
        }
    }

    private void setRequestStatus(int id, String status, String note) throws Exception {
        try (Connection con = BusPassManagementSystem.connect();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE requests SET status=?, admin_note=? WHERE request_id=?")) {
            ps.setString(1, status);
            ps.setString(2, note);
            ps.setInt(3, id);
            ps.executeUpdate();
        }
    }

    private static void setOptional(PreparedStatement ps, int index, String value)
            throws SQLException {
        if (value == null || value.isBlank()) ps.setNull(index, Types.VARCHAR);
        else ps.setString(index, value.trim());
    }

    private String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return value.trim();
    }

    private int number(String value, String label, int min, int max) {
        try {
            int result = Integer.parseInt(required(value, label));
            if (result < min || result > max) {
                throw new IllegalArgumentException(label + " must be from " + min + " to " + max + ".");
            }
            return result;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(label + " must be a whole number.");
        }
    }

    private String yesNo(int value) {
        return value == 1 ? "Yes" : "No";
    }

    private String value(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private JTextArea resultArea() {
        JTextArea a = new JTextArea();
        a.setEditable(false);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setForeground(TEXT);
        a.setBackground(new Color(29, 32, 35));
        a.setCaretColor(TEXT);
        a.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        a.setBorder(new EmptyBorder(12, 12, 12, 12));
        return a;
    }

    private void info(String text) {
        JOptionPane.showMessageDialog(this, text, "Bus Pass Management",
                JOptionPane.INFORMATION_MESSAGE);
        setReady("Ready");
    }

    private void error(Exception ex) {
        JOptionPane.showMessageDialog(this,
                ex.getMessage() == null ? ex.toString() : ex.getMessage(),
                "Please check", JOptionPane.ERROR_MESSAGE);
    }

    private void setReady(String text) {
        message.setText(text);
    }

    private void createInitialAdminIfNeeded() throws Exception {
        int count;
        try (Connection con = BusPassManagementSystem.connect();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM admins")) {
            count = rs.next() ? rs.getInt(1) : 0;
        }

        if (count == 0) {
            JTextField username = field();
            JPasswordField password = new JPasswordField();
            password.setBackground(new Color(29, 32, 35));
            password.setForeground(TEXT);

            JPanel form = new JPanel(new GridLayout(2, 2, 8, 8));
            form.setBackground(PANEL);
            form.add(label("Create first staff username"));
            form.add(username);
            form.add(label("Password (at least 8 characters)"));
            form.add(password);

            if (JOptionPane.showConfirmDialog(this, form, "Initial staff account",
                    JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;

            String user = required(username.getText(), "Username");
            String pass = new String(password.getPassword());
            if (pass.length() < 8) {
                throw new IllegalArgumentException("Password must have at least 8 characters.");
            }
            BusPassManagementSystem.addAdmin(user, pass);
            info("Initial staff account created.");
        }
    }

    private static void initialize() throws Exception {
        Class.forName("org.sqlite.JDBC");
        BusPassManagementSystem.createTables();
        BusPassManagementSystem.updateExpiredPasses();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                initialize();
                BusPassGUI gui = new BusPassGUI();
                gui.setVisible(true);
                gui.createInitialAdminIfNeeded();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(null,
                        "Could not start the GUI:\n" + ex.getMessage(),
                        "Startup error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}