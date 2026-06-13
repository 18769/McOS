import json
import sys
from pathlib import Path

RECIPE_PATH = Path(__file__).resolve().parents[2] / "DB" / "recipe.json"

def load_recipes():
    import urllib.request
    
    # Try fetching from remote API first
    try:
        # 1. Fetch meals to get name mappings
        meal_map = {}
        try:
            req_meals = urllib.request.Request("http://120.107.152.110/~a0303/DB/get_meals.php", headers={'User-Agent': 'Mozilla/5.0'})
            with urllib.request.urlopen(req_meals, timeout=3) as res:
                meals_data = json.loads(res.read().decode('utf-8'))
                for m in meals_data.get('data', []):
                    mid = m.get('meal_id') or m.get('mealID')
                    if mid:
                        meal_map[int(mid)] = m.get('meal_name')
        except Exception as e:
            sys.stderr.write("Fetch meals failed: " + str(e) + "\n")
            
        # 2. Fetch recipes
        req_recipes = urllib.request.Request("http://120.107.152.110/~a0303/DB/get_recipes.php", headers={'User-Agent': 'Mozilla/5.0'})
        with urllib.request.urlopen(req_recipes, timeout=3) as res:
            recipes_data = json.loads(res.read().decode('utf-8'))
            data = recipes_data.get('data', [])
            
            # Group steps into recipes
            recipe_map = {}
            for row in data:
                meal_id = row.get("mealID") or row.get("meal_id")
                if meal_id:
                    meal_id = int(meal_id)
                else:
                    meal_id = -1
                
                step_order = int(row.get("stepOrder") or row.get("step_order") or 1)
                step_name = row.get("stepDescription") or row.get("step_description") or row.get("step_name") or row.get("description") or "Step"
                
                # duration
                duration_sec = 0
                if "timeMinutes" in row:
                    try:
                        duration_sec = int(row["timeMinutes"]) * 3
                    except ValueError:
                        pass
                elif "duration_sec" in row:
                    try:
                        duration_sec = int(row["duration_sec"])
                    except ValueError:
                        pass
                
                etype = str(row.get("etype") or row.get("equipment_type") or "").strip().lower()
                
                step = {
                    "step_order": step_order,
                    "step_name": step_name,
                    "duration_sec": duration_sec,
                    "equipment_type": etype
                }
                
                recipe_name = row.get("recipe_name") or row.get("recipeName") or ""
                meal_name = row.get("meal_name") or row.get("mealName") or meal_map.get(meal_id) or ("Recipe " + str(meal_id))
                
                key = meal_name
                if key not in recipe_map:
                    recipe_map[key] = {
                        "meal_id": meal_id,
                        "meal_name": meal_name,
                        "recipe_name": recipe_name or (meal_name + " Recipe"),
                        "steps": []
                    }
                recipe_map[key]["steps"].append(step)
            
            # Sort steps by step_order
            for r in recipe_map.values():
                r["steps"].sort(key=lambda s: s["step_order"])
                
            return list(recipe_map.values())
    except Exception as e:
        sys.stderr.write("Fetch remote recipes failed: " + str(e) + ". Falling back to local recipe.json\n")
        
    # Fallback to local recipe.json
    try:
        return json.loads(RECIPE_PATH.read_text(encoding='utf-8'))
    except Exception:
        return []

def get_recipe_by_meal_name(meal_name):
    recipes = load_recipes()
    for r in recipes:
        if r.get('meal_name') == meal_name:
            return r
    return None

