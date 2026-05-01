import json
import socket
import time
import csv
from pathlib import Path

from .core import McOSScheduler
from . import recipes


class ExperimentLogger:
    def __init__(self, filename="experiment_results.csv"):
        # Save to workspace root, not src/
        result_file = Path(__file__).resolve().parents[2] / filename
        self.filename = str(result_file)
        with open(self.filename, 'w', newline='', encoding='utf-8') as f:
            writer = csv.writer(f)
            writer.writerow(['OrderID', 'Item', 'Algo', 'WaitTime', 'TurnaroundTime'])

    def record(self, order_id, item, algo, arrival_time, finish_time, prep_time):
        turnaround = finish_time - arrival_time
        wait = turnaround - prep_time
        with open(self.filename, 'a', newline='', encoding='utf-8') as f:
            writer = csv.writer(f)
            writer.writerow([order_id, item, algo, round(max(0, wait), 2), round(turnaround, 2)])
        self.print_summary()

    def print_summary(self):
        """Show algorithm performance analysis"""
        try:
            import pandas as pd
            df = pd.read_csv(self.filename)
            if df.empty:
                return

            summary = df.groupby('Algo').agg({
                'WaitTime': ['mean', 'max'],
                'TurnaroundTime': ['mean']
            }).round(2)

            print("\n" + "="*55)
            print("        McOS Algorithm Analysis (Experimental Data)")
            print("="*55)
            print(summary.to_string())
            print("-" * 55)
            print("Note: SJF avg wait should be lowest; FCFS shows long waits after long tasks.")
            print("="*55 + "\n")
        except ImportError:
            print(" [Info] Install pandas (pip install pandas) for automatic table logging")
        except Exception:
            pass


def start_engine():
    scheduler = McOSScheduler()
    logger = ExperimentLogger()

    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind(('localhost', 9999))
    server.listen(5)
    print("McOS Python Engine Started [Auto Analysis Mode]...")

    while True:
        conn, addr = server.accept()
        try:
            raw_data = conn.recv(8192).decode('utf-8')
            if not raw_data:
                continue
            request = json.loads(raw_data)

            result = []
            if request.get("type") == "SWITCH_MODE":
                scheduler.set_strategy(request["mode"])
                result = scheduler._reschedule()
            elif request.get("type") == "ADD_ORDER":
                expanded = []
                for order in request["data"]:
                    if 'items' in order and isinstance(order['items'], list) and len(order['items']) > 0:
                        for item in order['items']:
                            item.setdefault('description', item.get('item', 'unknown_item'))
                        expanded.append(order)
                        continue

                    meal_name = order.get('item')
                    prep_time = order.get('prep_time', 0)
                    is_takeout = order.get('is_takeout', False)

                    recipe = recipes.get_recipe_by_meal_name(meal_name)
                    if recipe and isinstance(recipe.get('steps'), list) and len(recipe.get('steps')) > 0:
                        items = []
                        for idx, step in enumerate(recipe['steps']):
                            step_name = step.get('step_name', f"{meal_name} step")
                            items.append({
                                'item': step_name,
                                'prep_time': int(step.get('duration_sec', 1)),
                                'equipment_type': step.get('equipment_type', ''),
                                'task_index': idx,
                                'description': step_name
                            })
                        expanded.append({
                            'id': order.get('id'),
                            'item': meal_name,
                            'is_takeout': is_takeout,
                            'items': items,
                            'total_prep_time': sum(i['prep_time'] for i in items)
                        })
                    else:
                        expanded.append({
                            'id': order.get('id'),
                            'item': meal_name,
                            'is_takeout': is_takeout,
                            'prep_time': prep_time,
                            'description': meal_name
                        })

                result = scheduler.optimize_schedule(expanded)
            elif request.get("type") == "GET_STATUS":
                result = scheduler.pending_queue
            elif request.get("type") == "FINISH_ORDER":
                order_id = request.get("order_id")
                item_name = request.get("item")

                task_info = next((t for t in scheduler.pending_queue if t['id'] == order_id and t['item'] == item_name), None)
                if task_info:
                    logger.record(order_id, item_name, scheduler.strategy.__name__,
                                  task_info['arrival_time'], time.time(), task_info['prep_time'])

                finish_result = scheduler.remove_finished(order_id, item_name)
                result = finish_result

            conn.sendall((json.dumps(result, ensure_ascii=False) + "\n").encode('utf-8'))
        finally:
            conn.close()


if __name__ == "__main__":
    start_engine()

