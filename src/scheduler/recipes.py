import json
from pathlib import Path

RECIPE_PATH = Path(__file__).resolve().parents[2] / "DB" / "recipe.json"

def load_recipes():
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
