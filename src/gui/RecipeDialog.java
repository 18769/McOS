package gui;

import db.DBRequest;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

public class RecipeDialog extends JDialog {
    private JList<String> recipeList;
    private DefaultTableModel stepModel;

    public RecipeDialog(JFrame owner) {
        super(owner, "Recipe Viewer", false);
        initUI();
        loadRecipes();
    }

    private void initUI() {
        setSize(700, 420);
        setLayout(new BorderLayout());

        recipeList = new JList<>(new DefaultListModel<>());
        recipeList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        recipeList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int idx = recipeList.getSelectedIndex();
                if (idx >= 0) showStepsFor(idx);
            }
        });

        JScrollPane left = new JScrollPane(recipeList);
        left.setPreferredSize(new Dimension(240, 0));
        add(left, BorderLayout.WEST);

        String[] cols = {"Step Order", "Description", "Seconds", "Equipment Type"};
        stepModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
        JTable stepTable = new JTable(stepModel);
        JScrollPane right = new JScrollPane(stepTable);
        add(right, BorderLayout.CENTER);
    }

    private JSONArray recipes = new JSONArray();

    private void loadRecipes() {
        try {
            recipes = DBRequest.loadRecipes();
            DefaultListModel<String> lm = (DefaultListModel<String>) recipeList.getModel();
            lm.clear();
            for (int i = 0; i < recipes.length(); i++) {
                JSONObject r = recipes.getJSONObject(i);
                String title = String.format("%s (meal_id=%d) v%d",
                        r.optString("recipe_name", "<unnamed>"), r.optInt("meal_id", -1), r.optInt("version", 1));
                lm.addElement(title);
            }
            if (recipes.length() > 0) recipeList.setSelectedIndex(0);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to load recipes: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showStepsFor(int idx) {
        stepModel.setRowCount(0);
        if (idx < 0 || idx >= recipes.length()) return;
        JSONObject r = recipes.getJSONObject(idx);
        JSONArray steps = r.optJSONArray("steps");
        if (steps == null) return;
        for (int i = 0; i < steps.length(); i++) {
            JSONObject s = steps.getJSONObject(i);
            stepModel.addRow(new Object[]{s.optInt("step_order", i+1), s.optString("step_name", ""), s.optInt("duration_sec", 0), s.optString("equipment_type", "")});
        }
    }
}
