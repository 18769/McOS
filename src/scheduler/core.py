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
        import urllib.request, urllib.error, json
        try:
            req = urllib.request.Request("http://120.107.152.110/~a0303/DB/get_workers.php")
            with urllib.request.urlopen(req, timeout=5) as response:
                if response.status == 200:
                    data = json.loads(response.read().decode('utf-8'))
                    worker_ids = [int(w.get("worker_id")) for w in data.get("data", []) if "worker_id" in w]
                    worker_ids = [wid for wid in worker_ids if wid > 0]
                    if worker_ids:
                        return sorted(worker_ids)
        except Exception:
            pass
            
        worker_file = Path(__file__).resolve().parents[2] / "DB" / "worker.json"
        try:
            data = json.loads(worker_file.read_text(encoding="utf-8"))
            worker_ids = [int(w.get("worker_id")) for w in data if "worker_id" in w]
            worker_ids = [wid for wid in worker_ids if wid > 0]
            if worker_ids:
                return sorted(worker_ids)
        except Exception:
            pass
        return [1, 2]

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
            
            # --- 防呆機制 1：如果訂單沒包裝在 items 裡，就把訂單自己當成 item ---
            items = order.get("items", [])
            if not items:
                items = [order]
                
            task_list = []
            full_names = ",".join([item.get("meal_name", item.get("item", "unknown_meal")) for item in items])

            for idx, item in enumerate(items): 
                base_meal_name = f"{item.get('meal_name', item.get('item', 'unknown_meal'))}_{idx}"
                
                # --- 防呆機制 2：兼容新版 Recipe (有 steps) 與舊版格式 (無 steps) ---
                steps = item.get("steps", [])
                if steps:
                    for step in steps:
                        task_list.append({
                            "item": step.get("step_name"),  
                            "meal_name": base_meal_name,    
                            "prep_time": step.get("duration_sec", 5), 
                            "description": step.get("step_name"),
                            "equipment_type": step.get("equipment_type", ""),
                            "task_index": step.get("step_order", 0) 
                        })
                else:
                    task_list.append({
                        "item": item.get("item", "unknown_item"),  
                        "meal_name": base_meal_name,    
                        "prep_time": item.get("prep_time", 5), 
                        "description": item.get("description", item.get("item", "")),
                        "equipment_type": item.get("equipment_type", ""),
                        "task_index": item.get("task_index", 0) 
                    })

            self.order_tracker[order_id] = {
                "full_content_names": full_names,
                "remaining_tasks": len(task_list),
                "is_takeout": is_takeout
            }

            for task in task_list:
                self.pending_queue.append({
                    "id": order_id,
                    "item": task["item"],
                    "meal_name": task["meal_name"],  
                    "prep_time": task["prep_time"],
                    "description": task["description"],
                    "equipment_type": task["equipment_type"],
                    "arrival_time": time.time(),
                    "is_takeout": is_takeout,
                    "task_index": task["task_index"],
                    "is_pack_task": False 
                })

        return self._reschedule()

    def _reschedule(self):
        self.pending_queue = self.strategy(self.pending_queue, current_time=time.time())

        self._clear_equipment_statuses()
        
        allocated_tasks = []
        new_tasks = []
        for task in self.pending_queue:
            if task.get("worker_id") is not None and task.get("expected_at") is not None:
                allocated_tasks.append(task)
            else:
                new_tasks.append(task)
        
        for worker_id in self.workers:
            self.worker_available[worker_id] = 0
        for equipment_id in list(self.equipment_available.keys()):
            self.equipment_available[equipment_id] = 0
            
        for task in allocated_tasks:
            wid = task.get("worker_id")
            expected_at = task.get("expected_at", 0)
            if wid is not None:
                self.worker_available[wid] = max(self.worker_available.get(wid, 0), expected_at)
            
            eid = task.get("equipment_id")
            if eid:
                self.equipment_available[eid] = max(self.equipment_available.get(eid, 0), expected_at)

        tasks_to_schedule = new_tasks
        self.pending_queue = allocated_tasks + tasks_to_schedule
        
        meal_groups = {}
        for task in tasks_to_schedule:  
            order_id = task.get("id")
            meal_name = task.get("meal_name", task.get("item", "unknown"))
            key = (order_id, meal_name)
            if key not in meal_groups:
                meal_groups[key] = []
            meal_groups[key].append(task)

        for (order_id, meal_name), tasks in meal_groups.items():
            group_id = f"{order_id}:{meal_name}"
            
            if group_id in self.group_worker_mapping:
                selected_worker = self.group_worker_mapping[group_id]
            else:
                selected_worker = min(self.worker_available.items(), key=lambda pair: (pair[1], pair[0]))[0]
                self.group_worker_mapping[group_id] = selected_worker
            
            worker_start = self.worker_available[selected_worker]
            current_time = worker_start

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
                task["group_id"] = group_id  

                if selected_equipment_id:
                    task["equipment_id"] = selected_equipment_id
                    task["equipment_name"] = selected_equipment.get("name", selected_equipment_id)
                    status_text = f"{self._worker_label(selected_worker)}:{task.get('item', 'unknown_item')}|{task.get('id')}"
                    task["equipment_status"] = status_text
                    
                    self.equipment_available[selected_equipment_id] = finish_time
                
                # 【修復：補回你遺失的時間推進邏輯！】
                current_time = finish_time

            self.worker_available[selected_worker] = current_time

        # --- 設備狀態更新邏輯 ---
        self._clear_equipment_statuses()
        
        worker_current_tasks = {}
        for task in self.pending_queue:
            worker_id = task.get("worker_id")
            if worker_id is not None:
                expected_at = task.get("expected_at", float('inf'))
                
                if worker_id not in worker_current_tasks or expected_at < worker_current_tasks[worker_id].get("expected_at", float('inf')):
                    worker_current_tasks[worker_id] = task
        
        equipment_display_map = {}
        for worker_id, task in worker_current_tasks.items():
            equipment_id = task.get("equipment_id")
            if equipment_id:
                expected_at = task.get("expected_at", float('inf'))
                if equipment_id not in equipment_display_map or expected_at < equipment_display_map[equipment_id]["expected_at"]:
                    equipment_display_map[equipment_id] = {
                        "worker_id": worker_id,
                        "task": task,
                        "expected_at": expected_at
                    }

        for equipment_id, display_info in equipment_display_map.items():
            worker_id = display_info["worker_id"]
            task = display_info["task"]
            status_text = f"{self._worker_label(worker_id)}:{task.get('item', 'unknown_item')}|{task.get('id')}"
            for equip in self.equipments:
                if equip.get("equipmentID") == equipment_id:
                    equip["status"] = status_text
                    break

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
                # 更新設備可用時間為該任務的完成時間（使用相對時間）
                self.equipment_available[equipment_id] = removed_task.get("expected_at", 0)
                self._save_equipment()
            
            # 重置剩餘任務所需設備的可用時間，並重新計算任務時間
            # 因為剩餘任務現在應該從新的時間開始，而不是依賴原計劃
            completion_time = removed_task.get("expected_at", 0)
            
            # 檢查剩餘任務需要什麼設備
            remaining_equipment_types = set()
            for task in self.pending_queue:
                equip_type = task.get("equipment_type", "").strip().lower()
                if equip_type:
                    remaining_equipment_types.add(equip_type)
            
            # 重置這些設備的可用性
            for equip in self.equipments:
                etype = str(equip.get("Etype", "")).strip().lower()
                if etype in remaining_equipment_types:
                    equip_id = equip.get("equipmentID")
                    # 重置為完成時間，因為該設備現在實際可用
                    self.equipment_available[equip_id] = completion_time
            
            # 更新 worker 的可用時間
            worker_id = removed_task.get("worker_id")
            if worker_id is not None:
                # worker 現在可以用於下一個任務
                # 使用已完成任務的時間作為基準
                self.worker_available[worker_id] = removed_task.get("expected_at", 0)

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
