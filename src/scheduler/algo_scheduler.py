import json
import socket
from pathlib import Path

class McOSScheduler:
    def __init__(self):
        # 廚房排隊（可拆散的任務列表）
        self.pending_queue = []
        
        # 訂單層級追蹤（不拆散）
        self.order_tracker = {}

        # 員工池（worker_id -> available_time）
        self.workers = self._load_workers()
        self.worker_available = {worker_id: 0 for worker_id in self.workers}

    def _load_workers(self):
        worker_file = Path(__file__).resolve().parents[2] / "DB" / "worker.json"
        try:
            data = json.loads(worker_file.read_text(encoding="utf-8"))
            worker_ids = [int(w.get("worker_id")) for w in data if "worker_id" in w]
            worker_ids = [wid for wid in worker_ids if wid > 0]
            if worker_ids:
                return sorted(worker_ids)
        except Exception:
            pass
        return [1]

    def optimize_schedule(self, new_orders):
        # 先按 order_id 分組
        orders_by_id = {}
        for order in new_orders:
            order_id = order['id']
            if order_id not in orders_by_id:
                orders_by_id[order_id] = {
                    'is_takeout': order.get('is_takeout', False),
                    'items': [],
                    'names': []
                }
            
            order_name = order.get('item', 'Unknown')
            orders_by_id[order_id]['names'].append(order_name)
            
            if 'items' in order:
                orders_by_id[order_id]['items'].extend(order['items'])
            else:
                orders_by_id[order_id]['items'].append({
                    "item": order_name,
                    "prep_time": order.get('prep_time', 5)
                })
        
        for order_id, order_data in orders_by_id.items():
            items = order_data['items']
            is_takeout = order_data['is_takeout']
            order_name = "、".join(order_data['names'])
            total_time = sum(item.get('prep_time', 0) for item in items)
            task_count = len(items)
            self.order_tracker[order_id] = {
                "name": order_name,
                "items": items,
                "total_tasks": task_count,
                "remaining_tasks": task_count,
                "is_takeout": is_takeout,
                "total_prep_time": total_time
            }
            for idx, item in enumerate(items):
                task = {
                    "id": order_id,
                    "order_name": order_name,
                    "item": item.get('item', 'Unknown'),
                    "prep_time": item.get('prep_time', 5),
                    "is_takeout": is_takeout,
                    "task_index": idx,
                    "is_pack_task": False
                }
                self.pending_queue.append(task)
        
        return self._reschedule()

    def _reschedule(self):
        for worker_id in self.workers:
            self.worker_available.setdefault(worker_id, 0)

        order_groups = {}
        for task in self.pending_queue:
            base_time = task.get('base_prep_time', task.get('prep_time', 5))
            task['base_prep_time'] = base_time
            group = order_groups.setdefault(task['id'], {"tasks": [], "total": 0})
            group["tasks"].append(task)
            group["total"] += base_time

        ordered_groups = sorted(order_groups.items(), key=lambda x: (x[1]["total"], x[0]))

        new_queue = []
        for _, group in ordered_groups:
            group_tasks = sorted(group["tasks"], key=lambda t: (t["base_prep_time"], t.get("task_index", 0)))
            new_queue.extend(group_tasks)

        self.pending_queue = new_queue

        for task in self.pending_queue:
            actual_time = task.get('base_prep_time', task.get('prep_time', 5))
            task['prep_time'] = actual_time
            selected_worker = min(self.worker_available.items(), key=lambda x: (x[1], x[0]))[0]
            start_time = self.worker_available[selected_worker]
            finish_time = start_time + actual_time
            task['worker_id'] = selected_worker
            task['expected_at'] = finish_time
            self.worker_available[selected_worker] = finish_time

        return self.pending_queue

    def remove_finished(self, order_id, task_item=None):
        if order_id not in self.order_tracker:
            return self._reschedule()

        removed_count = 0
        for i in range(len(self.pending_queue) - 1, -1, -1):
            task = self.pending_queue[i]
            if task.get('id') == order_id and (task_item is None or task.get('item') == task_item):
                self.pending_queue.pop(i)
                removed_count += 1
                break

        if removed_count > 0:
            self.order_tracker[order_id]["remaining_tasks"] -= 1
            if self.order_tracker[order_id]["remaining_tasks"] <= 0:
                order_info = self.order_tracker[order_id]
                if order_info.get("is_takeout", False):
                    pack_task = {
                        "id": order_id,
                        "order_name": order_info["name"],
                        "item": "? 打包裝袋",
                        "prep_time": 4,
                        "is_takeout": True,
                        "is_pack_task": True,
                        "task_index": 999
                    }
                    self.pending_queue.insert(0, pack_task)
                else:
                    del self.order_tracker[order_id]

        return self._reschedule()
