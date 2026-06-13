package gui;

import javax.swing.*;
import java.awt.*;

/**
 * Unified GUI switcher for opening the target McOS modules.
 */
public class SwitchGUI extends JFrame {
    public SwitchGUI() {
        setTitle("McOS - GUI Switch Launcher");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(560, 360);
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JLabel title = new JLabel("McOS Integrated Launcher");
        title.setFont(new Font("Microsoft JhengHei", Font.BOLD, 24));
        root.add(title, BorderLayout.NORTH);

        JPanel buttonPanel = new JPanel(new GridLayout(4, 1, 10, 10));

        JButton kitchenBtn = new JButton("Open KitchenGUI");
        kitchenBtn.setFont(new Font("Microsoft JhengHei", Font.BOLD, 16));
        kitchenBtn.addActionListener(e -> {
            SwingUtilities.invokeLater(KitchenGUI::new);
            dispose();
        });

        JButton mealBtn = new JButton("Open MealManagerGUI");
        mealBtn.setFont(new Font("Microsoft JhengHei", Font.BOLD, 16));
        mealBtn.addActionListener(e -> {
            SwingUtilities.invokeLater(() -> new MealManagerGUI().setVisible(true));
            dispose();
        });

        JButton humanBtn = new JButton("Open humanResGUI");
        humanBtn.setFont(new Font("Microsoft JhengHei", Font.BOLD, 16));
        humanBtn.addActionListener(e -> {
            SwingUtilities.invokeLater(() -> new HumanResGUI().setVisible(true));
            dispose();
        });

        JButton openAllBtn = new JButton("Open All");
        openAllBtn.setFont(new Font("Microsoft JhengHei", Font.PLAIN, 14));
        openAllBtn.addActionListener(e -> {
            SwingUtilities.invokeLater(KitchenGUI::new);
            SwingUtilities.invokeLater(() -> new MealManagerGUI().setVisible(true));
            SwingUtilities.invokeLater(() -> new HumanResGUI().setVisible(true));
            dispose();
        });

        buttonPanel.add(kitchenBtn);
        buttonPanel.add(mealBtn);
        buttonPanel.add(humanBtn);
        buttonPanel.add(openAllBtn);

        root.add(buttonPanel, BorderLayout.CENTER);

        JLabel foot = new JLabel("Select a GUI module to launch", SwingConstants.CENTER);
        foot.setFont(new Font("Microsoft JhengHei", Font.PLAIN, 13));
        root.add(foot, BorderLayout.SOUTH);

        add(root);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new SwitchGUI().setVisible(true));
    }
}
