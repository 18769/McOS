import time
import json
from pathlib import Path
import algorithms

class McOSScheduler:
    def __init__(self):
        self.pending_queue = []
        self.order_tracker = {}
        self.strategy = algorithms.fcfs_logic 
        self.workers = self._load_workers()
        self.worker_available = {worker_id: 0 for worker_id in self.workers}

    def _load_workers(self):
        worker_file = Path(__file__).resolve().parents[1] / "DB" / "worker.json"
        try:
            data = json.loads(worker_file.read_text(encoding="utf-8"))
            worker_ids = [int(w.get("worker_id")) for w in data if "worker_id" in w]
            worker_ids = [wid for wid in worker_ids if wid > 0]
            if worker_ids:
                return sorted(worker_ids)
        except Exception:
            pass
        return [1]

    def set_strategy(self, mode):
        if mode == "SJF": self.strategy = algorithms.sjf_logic
        elif mode == "AGING": self.strategy = algorithms.aging_logic
        else: self.strategy = algorithms.fcfs_logic

    def optimize_schedule(self, new_orders):
        for order in new_orders:
            oid = order['id']
            is_takeout = order.get('is_takeout', False)
            
            # --- 關鍵修正：串接所有品項名稱 ---
            items = order.get('items', [])
            if items:
                # 這裡會產生如 "蘋果派、玉米湯、可樂" 的字串
                names_list = [i['item'] for i in items]
                full_names = "、".join(names_list)
                task_list = items
            else:
                full_names = order.get('item', '未知品項')
                task_list = [{"item": full_names, "prep_time": order.get('prep_time', 5)}]
            
            # 存入追蹤器
            self.order_tracker[oid] = {
                "full_content_names": full_names,
                "remaining_tasks": len(task_list),
                "is_takeout": is_takeout
            }

            for t in task_list:
                self.pending_queue.append({
                    "id": oid,
                    "item": t['item'],
                    "prep_time": t['prep_time'],
                    "arrival_time": time.time(),
                    "is_takeout": is_takeout
                })
        return self._reschedule()

    def _reschedule(self):
        self.pending_queue = self.strategy(self.pending_queue, current_time=time.time())

        for worker_id in self.workers:
            self.worker_available.setdefault(worker_id, 0)

        for t in self.pending_queue:
            prep_time = t['prep_time']
            selected_worker = min(self.worker_available.items(), key=lambda x: (x[1], x[0]))[0]
            start_time = self.worker_available[selected_worker]
            finish_time = start_time + prep_time

            t['worker_id'] = selected_worker
            t['expected_at'] = finish_time
            self.worker_available[selected_worker] = finish_time

        return self.pending_queue

    def remove_finished(self, order_id, item_name):
        # 1. 移除任務
        for i, task in enumerate(self.pending_queue):
            if task['id'] == order_id and task['item'] == item_name:
                self.pending_queue.pop(i)
                break

        # 2. 準備回傳結果
        result_data = {"queue": self._reschedule(), "all_items_completed": False}

        if order_id in self.order_tracker:
            self.order_tracker[order_id]["remaining_tasks"] -= 1
            
            # 如果全部做完，把存好的 full_content_names 噴回給 Java
            if self.order_tracker[order_id]["remaining_tasks"] <= 0:
                result_data["all_items_completed"] = True
                result_data["order_content"] = self.order_tracker[order_id]["full_content_names"]
                del self.order_tracker[order_id]
                
        return result_data