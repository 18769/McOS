package gui;

import db.DBRequest;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Human Resource dashboard for viewing current worker roster from remote API.
 */
public class HumanResGUI extends JFrame {
    private static final String DASHBOARD_URL = "http://120.107.152.110/~a0303/DB/dashboard_real.html";

    private final DefaultTableModel tableModel;
    private final JTable workerTable;
    private final JavaFxWebViewPanel webViewPanel;
    private final JLabel statusLabel;

    public HumanResGUI() {
        setTitle("McOS - Human Resource GUI");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(980, 620);
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JLabel title = new JLabel("McOS HR Center");
        title.setFont(new Font("Microsoft JhengHei", Font.BOLD, 22));
        root.add(title, BorderLayout.NORTH);

        statusLabel = new JLabel("Status: Ready");

        tableModel = new DefaultTableModel(new String[]{
                "workerID", "name", "role", "status", "phone", "skill", "raw"
        }, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        workerTable = new JTable(tableModel);
        workerTable.setRowHeight(24);

        webViewPanel = new JavaFxWebViewPanel(DASHBOARD_URL, msg -> SwingUtilities.invokeLater(() -> statusLabel.setText(msg)));

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Workers", new JScrollPane(workerTable));
        tabbedPane.addTab("Dashboard (WebView)", webViewPanel);
        root.add(tabbedPane, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(8, 8));
        JPanel buttonBar = new JPanel(new FlowLayout(FlowLayout.LEFT));

        JButton refreshBtn = new JButton("Refresh Workers");
        refreshBtn.addActionListener(e -> loadWorkers());

        JButton reloadDashboardBtn = new JButton("Reload WebView Dashboard");
        reloadDashboardBtn.addActionListener(e -> webViewPanel.reload());

        JButton exportBtn = new JButton("Export Snapshot (JSON)");
        exportBtn.addActionListener(e -> exportSnapshot());

        JButton openDashboardBtn = new JButton("Open Dashboard in Browser");
        openDashboardBtn.addActionListener(e -> openDashboardInBrowser());

        buttonBar.add(refreshBtn);
        buttonBar.add(reloadDashboardBtn);
        buttonBar.add(exportBtn);
        buttonBar.add(openDashboardBtn);

        bottom.add(buttonBar, BorderLayout.WEST);
        bottom.add(statusLabel, BorderLayout.SOUTH);

        root.add(bottom, BorderLayout.SOUTH);

        add(root);
        loadWorkers();
        webViewPanel.reload();
    }

    private void loadWorkers() {
        statusLabel.setText("Status: Loading workers...");
        tableModel.setRowCount(0);

        new SwingWorker<JSONArray, Void>() {
            @Override
            protected JSONArray doInBackground() {
                return DBRequest.loadWorkers();
            }

            @Override
            protected void done() {
                try {
                    JSONArray workers = get();
                    for (int i = 0; i < workers.length(); i++) {
                        JSONObject w = workers.getJSONObject(i);
                        tableModel.addRow(new Object[]{
                                w.optInt("workerID", w.optInt("worker_id", 0)),
                                w.optString("name", ""),
                                w.optString("role", w.optString("etype", "")),
                                w.optString("status", ""),
                                w.optString("phone", ""),
                                w.optString("skill", w.optString("skills", "")),
                                w.toString()
                        });
                    }
                    statusLabel.setText("Status: Loaded " + workers.length() + " workers");
                } catch (Exception ex) {
                    statusLabel.setText("Status: Load failed - " + ex.getMessage());
                    JOptionPane.showMessageDialog(HumanResGUI.this,
                            "Worker load failed: " + ex.getMessage(),
                            "Error",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void exportSnapshot() {
        try {
            JSONArray workers = DBRequest.loadWorkers();
            Path dir = Paths.get("data");
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            Path output = dir.resolve("workers_snapshot_" + ts + ".json");
            Files.write(output, workers.toString(2).getBytes(StandardCharsets.UTF_8));
            statusLabel.setText("Status: Snapshot exported to " + output.toString());
        } catch (IOException ex) {
            statusLabel.setText("Status: Export failed - " + ex.getMessage());
            JOptionPane.showMessageDialog(this,
                    "Export failed: " + ex.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openDashboardInBrowser() {
        String url = DASHBOARD_URL;
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(java.net.URI.create(url));
                statusLabel.setText("Status: Dashboard opened");
            } else {
                JOptionPane.showMessageDialog(this,
                        "Desktop API is not supported on this environment.\nOpen manually:\n" + url,
                        "Info",
                        JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception ex) {
            statusLabel.setText("Status: Failed to open dashboard");
            JOptionPane.showMessageDialog(this,
                    "Failed to open dashboard: " + ex.getMessage() + "\nURL: " + url,
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new HumanResGUI().setVisible(true));
    }
}
