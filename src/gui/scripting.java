package gui;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JCheckBox;
import javax.swing.SwingUtilities;

import java.awt.Color;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.json.JSONArray;
import org.json.JSONObject;

public class scripting {
    private final KitchenGUI owner;
    private final javax.swing.JFrame frame;
    private final JButton scriptBtn;
    private final JLabel statusLabel;
    private final JCheckBox takeoutBox;

    private Thread scriptThread = null; // 用來保存當前執行的執行緒
    private boolean isScriptRunning = false;

    public scripting(KitchenGUI owner, javax.swing.JFrame frame, JButton scriptBtn, JLabel statusLabel, JCheckBox takeoutBox) {
        this.owner = owner;
        this.frame = frame;
        this.scriptBtn = scriptBtn;
        this.statusLabel = statusLabel;
        this.takeoutBox = takeoutBox;
    }

    public void handleScriptButton() {
        if (!isScriptRunning) {
            openScriptFileChooser();
        } else {
            executeScriptFile(null);
        }
    }

    private void openScriptFileChooser() {
        JFileChooser chooser = new JFileChooser(new File("./scripts"));
        chooser.setDialogTitle("選擇 JSON 測試腳本");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("JSON 腳本檔", "json"));
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            executeScriptFile(file);
        }
    }

    private void executeScriptFile(File file) {
        if (isScriptRunning) {
            if (scriptThread != null) {
                scriptThread.interrupt();
                SwingUtilities.invokeLater(() -> showAutoCloseMsg("腳本已中斷", 2000));
            }
            return;
        }
        isScriptRunning = true;

        SwingUtilities.invokeLater(() -> {
            scriptBtn.setText("🛑 中斷腳本");
            scriptBtn.putClientProperty("baseBg", new Color(220, 53, 69));
            scriptBtn.setBackground(new Color(220, 53, 69)); // 轉為紅色
        });

        scriptThread = new Thread(() -> {
            try {
                String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                JSONObject script = new JSONObject(content);
                JSONArray steps = script.getJSONArray("steps");

                SwingUtilities.invokeLater(() -> showAutoCloseMsg("腳本即將開始...", 2000));
                Thread.sleep(2000);

                SwingUtilities.invokeLater(() -> statusLabel.setText("● 系統狀態: 腳本執行中"));
                executeSteps(steps);

                SwingUtilities.invokeLater(() -> showAutoCloseMsg("腳本執行結束！", 2000));
                SwingUtilities.invokeLater(() -> statusLabel.setText("● 系統狀態: 腳本已完成"));
                Thread.sleep(2000);

            } catch (InterruptedException ex) {
                // 當 thread.interrupt() 被呼叫時，Thread.sleep 會拋出此異常
                SwingUtilities.invokeLater(() -> owner.appendHistoryMessage("⚠️ 腳本已被使用者強制中斷\n"));
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(frame, "腳本解析錯誤: " + ex.getMessage(),
                        "錯誤", JOptionPane.ERROR_MESSAGE));
            } finally {
                scriptThread = null;
                isScriptRunning = false;
                SwingUtilities.invokeLater(() -> {
                    scriptBtn.setText("📂 載入腳本");
                    scriptBtn.putClientProperty("baseBg", new Color(0, 120, 215));
                    scriptBtn.setBackground(new Color(0, 120, 215)); // 恢復藍色
                    statusLabel.setText("● 系統狀態: 待機中");
                });
            }
        });
        scriptThread.start();
    }

    private void showAutoCloseMsg(String msg, int delayMs) {
        JDialog dialog = new JDialog(frame, "系統提示", false);
        JOptionPane opt = new JOptionPane(msg, JOptionPane.INFORMATION_MESSAGE, JOptionPane.DEFAULT_OPTION, null,
                new Object[] {}, null);
        dialog.setContentPane(opt);
        dialog.pack();
        dialog.setLocationRelativeTo(frame);

        javax.swing.Timer timer = new javax.swing.Timer(delayMs, e -> dialog.dispose());
        timer.setRepeats(false);
        timer.start();

        dialog.setVisible(true);
    }

    // 腳本系統
    private void executeSteps(JSONArray steps) throws Exception {
        for (int i = 0; i < steps.length(); i++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("User requested stop");
            }

            JSONObject step = steps.getJSONObject(i);
            String action = step.optString("action", "");

            switch (action) {
                case "SET_MODE":
                    boolean isTakeout = step.optBoolean("takeout", false);
                    SwingUtilities.invokeAndWait(() -> takeoutBox.setSelected(isTakeout));
                    break;
                case "ADD_ORDER":
                    String item = step.getString("item");
                    int prepTime = step.optInt("prep_time", 0);
                    int count = step.optInt("count", 1);
                    SwingUtilities.invokeAndWait(() -> {
                        owner.addOrderToBuffer(item, prepTime, count, takeoutBox.isSelected());
                    });
                    break;
                case "SEND_ORDERS":
                    SwingUtilities.invokeAndWait(owner::processOrders);
                    break;
                case "CLEAR_BUFFER":
                    SwingUtilities.invokeAndWait(owner::clearOrderBuffer);
                    break;
                case "WAIT":
                    int seconds = step.optInt("seconds", 1);
                    Thread.sleep(seconds * 1000L);
                    break;
                case "REPEAT":
                    int times = step.optInt("times", 1);
                    JSONArray subSteps = step.optJSONArray("steps");
                    if (subSteps != null) {
                        for (int t = 0; t < times; t++) {
                            executeSteps(subSteps);
                        }
                    }
                    break;
                default:
                    System.out.println("未知腳本指令: " + action);
            }
            Thread.sleep(200); // 讓連續動畫不會太快閃現
        }
    }
}
