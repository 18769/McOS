import sys
import json
import os
import time

# Adjust path so we can import scheduler modules
src_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if src_dir not in sys.path:
    sys.path.insert(0, src_dir)

from src.scheduler.core import McOSScheduler

def safe_print_json(data):
    try:
        output_bytes = (json.dumps(data, ensure_ascii=False) + "\n").encode('utf-8')
        sys.stdout.buffer.write(output_bytes)
        sys.stdout.buffer.flush()
    except Exception:
        print(json.dumps(data, ensure_ascii=True))

def main():
    # 1. Read JSON request from PHP via stdin
    try:
        raw_input_bytes = sys.stdin.buffer.read()
        raw_input = raw_input_bytes.decode('utf-8')
        if not raw_input:
            safe_print_json({"error": "No input received"})
            return
        request = json.loads(raw_input)
    except Exception as e:
        safe_print_json({"error": "JSON parse error: {}".format(str(e))})
        return

    # 2. Initialize scheduler (loads workers/equipment from DB API)
    scheduler = McOSScheduler(persistent_state=True)

    # 3. Process requests
    req_type = request.get("type")
    result = []

    try:
        if req_type == "SWITCH_MODE":
            scheduler.set_strategy(request.get("mode", "FCFS"))
            result = scheduler._reschedule()
        elif req_type == "ADD_ORDER":
            orders = request.get("data", [])
            expanded = []
            from src.scheduler import recipes
            for order in orders:
                # If it already has items (e.g. from a combo expansion on frontend), use them
                if 'items' in order and isinstance(order['items'], list) and len(order['items']) > 0:
                    for item in order['items']:
                        item.setdefault('description', item.get('item', 'unknown_item'))
                        # Apply equipment_type heuristic if missing
                        if not item.get('equipment_type'):
                            name_i = item.get('meal_name', item.get('item', ''))
                            eq = ""
                            if any(x in name_i.lower() for x in ["雞", "炸", "魚", "nugget", "fry", "fries", "fish"]):
                                eq = "fryer"
                            elif any(x in name_i.lower() for x in ["薯", "條"]):
                                eq = "fryer"
                            elif any(x in name_i.lower() for x in ["堡", "牛", "麥克", "burger", "beef", "patty", "mac"]):
                                eq = "grill"
                            elif any(x in name_i.lower() for x in ["可樂", "飲", "咖啡", "茶", "炫風", "美式", "coke", "cola", "drink", "coffee", "tea", "mcflurry"]):
                                eq = "drink"
                            item['equipment_type'] = eq
                    expanded.append(order)
                    continue

                meal_name = order.get('item')
                prep_time = order.get('prep_time', 0)
                is_takeout = order.get('is_takeout', False)

                recipe = recipes.get_recipe_by_meal_name(meal_name)
                if recipe and isinstance(recipe.get('steps'), list) and len(recipe.get('steps')) > 0:
                    items = []
                    for idx, step in enumerate(recipe['steps']):
                        step_name = step.get('step_name', "{} step".format(meal_name))
                        items.append({
                            'item': step_name,
                            'prep_time': int(step.get('duration_sec', 1)),
                            'equipment_type': step.get('equipment_type', ''),
                            'task_index': idx,
                            'description': step_name
                        })
                    
                    # Apply equipment heuristic to sub-items
                    for i in items:
                        if not i.get('equipment_type'):
                            meal_name_i = i.get('item', '')
                            eq_type = ""
                            if any(x in meal_name_i.lower() for x in ["雞", "薯", "炸", "魚", "nugget", "fry", "fries", "fish"]):
                                eq_type = "fryer"
                            elif any(x in meal_name_i.lower() for x in ["堡", "牛", "麥克", "burger", "beef", "patty", "mac"]):
                                eq_type = "grill"
                            elif any(x in meal_name_i.lower() for x in ["茶", "可樂", "飲", "咖啡", "coke", "cola", "drink", "coffee", "tea", "mcflurry"]):
                                eq_type = "drink"
                            i['equipment_type'] = eq_type
                        
                    expanded.append({
                        'id': order.get('id'),
                        'item': meal_name,
                        'is_takeout': is_takeout,
                        'items': items,
                        'total_prep_time': sum(int(i['prep_time']) for i in items)
                    })
                else:
                    eq_type = ""
                    if any(x in meal_name.lower() for x in ["雞", "薯", "炸", "魚", "nugget", "fry", "fries", "fish"]):
                        eq_type = "fryer"
                    elif any(x in meal_name.lower() for x in ["堡", "牛", "麥克", "burger", "beef", "patty", "mac"]):
                        eq_type = "grill"
                    elif any(x in meal_name.lower() for x in ["茶", "可樂", "飲", "咖啡", "coke", "cola", "drink", "coffee", "tea", "mcflurry"]):
                        eq_type = "drink"
                        
                    expanded.append({
                        'id': order.get('id'),
                        'item': meal_name,
                        'is_takeout': is_takeout,
                        'prep_time': prep_time,
                        'description': meal_name,
                        'equipment_type': eq_type
                    })
            result = scheduler.optimize_schedule(expanded)
        elif req_type == "GET_STATUS":
            result = scheduler.pending_queue
        elif req_type == "FINISH_ORDER":
            order_id = request.get("order_id")
            item_name = request.get("item")
            result = scheduler.remove_finished(order_id, item_name)
        else:
            result = {"error": "Unknown request type"}

        tasks_to_process = []
        if isinstance(result, list):
            tasks_to_process = result
        elif isinstance(result, dict) and 'queue' in result and isinstance(result['queue'], list):
            tasks_to_process = result['queue']

        now = time.time()
        for task in tasks_to_process:
            if 'expected_at' in task:
                task['remaining_time'] = max(0, int(task['expected_at'] - now))
            else:
                task['remaining_time'] = task.get('prep_time', 0)

        # 4. Output final JSON result to stdout for PHP to capture
        safe_print_json(result)
    except Exception as e:
        safe_print_json({"error": "Scheduling execution error: {}".format(str(e))})

if __name__ == "__main__":
    main()
