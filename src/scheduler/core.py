import json
import time
from pathlib import Path

from . import algorithms


class McOSScheduler:
    def __init__(self):
        self.pending_queue = []
        self.order_tracker = {}
        self.strategy = algorithms.fcfs_logic
        self.workers = self._load_workers()
        self.worker_available = {worker_id: 0 for worker_id in self.workers}
        self.equipment_path = Path(__file__).resolve().parents[2] / "DB" / "equipment.json"
        self.equipments = self._load_equipment()
        self.equipment_available = {
            equip.get("equipmentID"): 0 for equip in self.equipments if equip.get("equipmentID")
        }
        # 新增：追?每個 meal_group 分配給哪個 worker，防止員工搶步驟
        self.group_worker_mapping = {}

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

    def _load_equipment(self):
        try:
            data = json.loads(self.equipment_path.read_text(encoding="utf-8"))
            if isinstance(data, list):
                for equip in data:
                    equip.setdefault("status", "")
                return data
        except Exception:
            pass
        return []

    def _save_equipment(self):
        try:
            self.equipment_path.write_text(
                json.dumps(self.equipments, ensure_ascii=False, indent=2),
                encoding="utf-8"
            )
        except Exception:
            pass

    def _worker_label(self, worker_id):
        return f"W{worker_id}"

    def _clear_equipment_statuses(self):
        for equip in self.equipments:
            equip["status"] = ""

    def _find_equipment_for_task(self, task, earliest_time):
        equipment_type = str(task.get("equipment_type", "")).strip().lower()
        if not equipment_type:
            return None

        matching = [
            equip for equip in self.equipments
            if str(equip.get("Etype", "")).strip().lower() == equipment_type
        ]
        if not matching:
            return None

        def sort_key(equip):
            eid = equip.get("equipmentID") or ""
            return (self.equipment_available.get(eid, 0), eid)

        selected = min(matching, key=sort_key)
        selected_id = selected.get("equipmentID")
        selected_available = self.equipment_available.get(selected_id, 0)
        start_time = max(earliest_time, selected_available)
        return selected, selected_id, start_time

    def set_strategy(self, mode):
        if mode == "SJF":
            self.strategy = algorithms.sjf_logic
        elif mode == "AGING":
            self.strategy = algorithms.aging_logic
        else:
            self.strategy = algorithms.fcfs_logic

    def optimize_schedule(self, new_orders):
        for order in new_orders:
            order_id = order["id"]
            is_takeout = order.get("is_takeout", False)
            items = order.get("items", [])

            if items:
                full_names = ",".join([item.get("item", "unknown_item") for item in items])
                task_list = items
            else:
                full_names = order.get("item", "unknown_item")
                task_list = [{
                    "item": full_names,
                    "prep_time": order.get("prep_time", 5),
                    "description": order.get("description", full_names),
                    "equipment_type": order.get("equipment_type", "")
                }]

            self.order_tracker[order_id] = {
                "full_content_names": full_names,
                "remaining_tasks": len(task_list),
                "is_takeout": is_takeout
            }

            for task in task_list:
                self.pending_queue.append({
                    "id": order_id,
                    "item": task.get("item", "unknown_item"),
                    "meal_name": order.get("item", "unknown_item"),
                    "prep_time": task.get("prep_time", 5),
                    "description": task.get("description", task.get("item", "unknown_item")),
                    "equipment_type": task.get("equipment_type", ""),
                    "arrival_time": time.time(),
                    "is_takeout": is_takeout,
                    "task_index": task.get("task_index", 0),
                    "is_pack_task": task.get("is_pack_task", False)
                })

        return self._reschedule()

    def _reschedule(self):
        self.pending_queue = self.strategy(self.pending_queue, current_time=time.time())

        for worker_id in self.workers:
            self.worker_available.setdefault(worker_id, 0)
        for equipment_id in list(self.equipment_available.keys()):
            self.equipment_available.setdefault(equipment_id, 0)

        self._clear_equipment_statuses()

        # Group tasks by (order_id, meal_name) to assign same meal to single worker
        meal_groups = {}
        for task in self.pending_queue:
            order_id = task.get("id")
            meal_name = task.get("meal_name", task.get("item", "unknown"))
            key = (order_id, meal_name)
            if key not in meal_groups:
                meal_groups[key] = []
            meal_groups[key].append(task)

        # Process each meal group as a unit
        for (order_id, meal_name), tasks in meal_groups.items():
            group_id = f"{order_id}:{meal_name}"
            
            # 檢查是否已經為這個 group 分配了 worker（防止員工搶步驟）
            if group_id in self.group_worker_mapping:
                selected_worker = self.group_worker_mapping[group_id]
            else:
                # 未分配過，選擇最早可用的 worker
                selected_worker = min(self.worker_available.items(), key=lambda pair: (pair[1], pair[0]))[0]
                self.group_worker_mapping[group_id] = selected_worker
            
            worker_start = self.worker_available[selected_worker]
            current_time = worker_start

            # Assign all tasks in this meal group to same worker
            for task in tasks:
                prep_time = task.get("prep_time", 5)
                start_time = current_time

                selected_equipment_id = None
                selected_equipment = None
                equipment_start = current_time
                if task.get("equipment_type"):
                    match = self._find_equipment_for_task(task, current_time)
                    if match is not None:
                        selected_equipment, selected_equipment_id, equipment_start = match
                        start_time = max(current_time, equipment_start)

                finish_time = start_time + prep_time

                task["worker_id"] = selected_worker
                task["expected_at"] = finish_time
                task["prep_time"] = prep_time
                task["group_id"] = group_id  # 追蹤 group_id

                if selected_equipment_id:
                    task["equipment_id"] = selected_equipment_id
                    task["equipment_name"] = selected_equipment.get("name", selected_equipment_id)
                    status_text = f"{self._worker_label(selected_worker)}:{task.get('item', 'unknown_item')}|{task.get('id')}"
                    task["equipment_status"] = status_text
                    for equip in self.equipments:
                        if equip.get("equipmentID") == selected_equipment_id:
                            equip["status"] = status_text
                            break
                    self.equipment_available[selected_equipment_id] = finish_time

                current_time = finish_time

            # Update worker availability with total meal prep time
            self.worker_available[selected_worker] = current_time

        self._save_equipment()
        return self.pending_queue

    def remove_finished(self, order_id, task_item=None):
        # Remove task from queue
        removed_task = None
        for index in range(len(self.pending_queue) - 1, -1, -1):
            task = self.pending_queue[index]
            if task.get("id") == order_id and (task_item is None or task.get("item") == task_item):
                removed_task = self.pending_queue.pop(index)
                break

        # Prepare response structure
        response = {
            "queue": None,
            "all_items_completed": False,
            "order_content": None
        }

        if removed_task is not None:
            # Clear equipment status
            equipment_id = removed_task.get("equipment_id")
            if equipment_id:
                for equip in self.equipments:
                    if equip.get("equipmentID") == equipment_id:
                        equip["status"] = ""
                        break
                self.equipment_available[equipment_id] = time.time()
                self._save_equipment()

            # Decrement and check if order is complete
            if order_id in self.order_tracker:
                self.order_tracker[order_id]["remaining_tasks"] -= 1
                if self.order_tracker[order_id]["remaining_tasks"] <= 0:
                    order_info = self.order_tracker[order_id]
                    response["all_items_completed"] = True
                    response["order_content"] = order_info["full_content_names"]
                    
                    # 清理該訂單相關的 group_id 映射
                    groups_to_remove = [gid for gid in self.group_worker_mapping.keys() if gid.startswith(f"{order_id}:")]
                    for gid in groups_to_remove:
                        del self.group_worker_mapping[gid]

                    if order_info.get("is_takeout", False):
                        self.pending_queue.insert(0, {
                            "id": order_id,
                            "order_name": order_info["full_content_names"],
                            "item": "pack-bag",
                            "prep_time": 4,
                            "description": "pack-bag",
                            "is_takeout": True,
                            "is_pack_task": True,
                            "task_index": 999
                        })
                    else:
                        del self.order_tracker[order_id]

        response["queue"] = self._reschedule()
        return response
