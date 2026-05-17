package gui;

import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

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

    // recipesGroupedByMeal: meal_id -> list of step JSONObjects
    private java.util.LinkedHashMap<Integer, java.util.List<JSONObject>> recipesGroupedByMeal = new java.util.LinkedHashMap<>();
    private java.util.LinkedHashMap<Integer, String> mealNames = new java.util.LinkedHashMap<>();
    private java.util.List<Integer> mealIdOrder = new java.util.ArrayList<>();

    private void loadRecipes() {
        try {
            // load normalized recipes (each recipe has meal_id and steps[])
            JSONArray recipes = loadRecipesLocal();

            // group steps by meal_id
            recipesGroupedByMeal.clear();
            for (int i = 0; i < recipes.length(); i++) {
                JSONObject r = recipes.getJSONObject(i);
                int mealId = r.optInt("meal_id", -1);
                JSONArray steps = r.optJSONArray("steps");
                if (mealId == -1) continue; // skip recipes without meal mapping
                java.util.List<JSONObject> list = recipesGroupedByMeal.getOrDefault(mealId, new java.util.ArrayList<>());
                if (steps != null) {
                    for (int j = 0; j < steps.length(); j++) list.add(steps.getJSONObject(j));
                }
                recipesGroupedByMeal.put(mealId, list);
            }

            // use meal ids discovered in recipesGroupedByMeal
            mealNames.clear();
            mealIdOrder.clear();
            for (Integer mid : recipesGroupedByMeal.keySet()) {
                if (!mealIdOrder.contains(mid)) mealIdOrder.add(mid);
                mealNames.putIfAbsent(mid, "");
            }

            // if mealIdOrder is empty (no meals from API), use grouped keys
            if (mealIdOrder.isEmpty()) {
                for (Integer mid : recipesGroupedByMeal.keySet()) {
                    mealIdOrder.add(mid);
                    mealNames.putIfAbsent(mid, "");
                }
            }

            // populate list model with "mealName (meal_id)"
            DefaultListModel<String> lm = (DefaultListModel<String>) recipeList.getModel();
            lm.clear();
            for (Integer mid : mealIdOrder) {
                String mname = mealNames.getOrDefault(mid, "");
                String title = String.format("%s (meal_id=%d)", mname.isEmpty() ? "<unnamed>" : mname, mid);
                lm.addElement(title);
            }
            if (!mealIdOrder.isEmpty()) recipeList.setSelectedIndex(0);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to load recipes: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private JSONArray loadRecipesLocal() throws Exception {
        java.nio.file.Path path = Paths.get("DB", "recipe.json");
        if (!Files.exists(path)) {
            return new JSONArray();
        }
        String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        return new JSONArray(content);
    }

    private void showStepsFor(int idx) {
        stepModel.setRowCount(0);
        if (idx < 0 || idx >= mealIdOrder.size()) return;
        int mealId = mealIdOrder.get(idx);
        java.util.List<JSONObject> steps = recipesGroupedByMeal.getOrDefault(mealId, new java.util.ArrayList<>());
        // sort by step_order if present
        steps.sort((a, b) -> Integer.compare(a.optInt("step_order", Integer.MAX_VALUE), b.optInt("step_order", Integer.MAX_VALUE)));
        for (int i = 0; i < steps.size(); i++) {
            JSONObject s = steps.get(i);
            stepModel.addRow(new Object[]{s.optInt("step_order", i+1), s.optString("step_name", ""), s.optInt("duration_sec", 0), s.optString("equipment_type", "")});
        }
    }
}
