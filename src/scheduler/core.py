import json
import time
from pathlib import Path

from . import algorithms


class McOSScheduler:
    def __init__(self, persistent_state=False):
        self.persistent_state = persistent_state
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
        if self.persistent_state:
            self._load_state()

    def _get_state_paths(self):
        """Return candidate paths for state file, in order of preference."""
        import os
        project_root = Path(__file__).resolve().parents[2]
        candidates = [
            project_root / "DB" / "scheduler_state.json",       # local dev: McOS/DB/
            project_root / "scheduler_state.json",               # server: project_root itself is DB/
            project_root / "webApp" / "scheduler_state.json",    # webApp subdir
            Path("/tmp") / "mcOS_scheduler_state.json",          # Linux tmp (always writable)
            Path(os.environ.get("TMPDIR", "/tmp")) / "mcOS_scheduler_state.json",
        ]
        return candidates

    def _save_state(self):
        if not self.persistent_state:
            return
        strategy_name = "FCFS"
        if self.strategy == algorithms.sjf_logic:
            strategy_name = "SJF"
        elif self.strategy == algorithms.aging_logic:
            strategy_name = "AGING"

        state = {
            "pending_queue": self.pending_queue,
            "order_tracker": self.order_tracker,
            "strategy_name": strategy_name,
            "group_worker_mapping": self.group_worker_mapping
        }
        state_json = json.dumps(state, ensure_ascii=False, indent=2)

        for path in self._get_state_paths():
            try:
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(state_json, encoding="utf-8")
                return  # Success - stop trying
            except Exception:
                continue
        # All paths failed - log to stderr so PHP can capture it
        import sys
        sys.stderr.write("WARNING: McOSScheduler could not save state to any path\n")

    def _load_state(self):
        for path in self._get_state_paths():
            if not path.exists():
                continue
            try:
                state = json.loads(path.read_text(encoding="utf-8"))
                self.pending_queue = state.get("pending_queue", [])
                self.order_tracker = state.get("order_tracker", {})
                self.group_worker_mapping = state.get("group_worker_mapping", {})
                
                strategy_name = state.get("strategy_name", "FCFS")
                if strategy_name == "SJF":
                    self.strategy = algorithms.sjf_logic
                elif strategy_name == "AGING":
                    self.strategy = algorithms.aging_logic
                else:
                    self.strategy = algorithms.fcfs_logic
                return  # Loaded successfully
            except Exception:
                continue

    def _load_workers(self):
        import urllib.request, urllib.error, json
        try:
            req = urllib.request.Request("http://120.107.152.110/~a0303/DB/get_workers.php")
            with urllib.request.urlopen(req, timeout=5) as response:
                if response.status == 200:
                    data = json.loads(response.read().decode('utf-8'))
                    worker_ids = [int(w.get("workerID")) for w in data.get("data", []) if "workerID" in w]
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
        def _normalize(data):
            if not isinstance(data, list):
                return []
            for equip in data:
                equip.setdefault("status", "")
                if equip.get("Etype") is None and equip.get("etype") is not None:
                    equip["Etype"] = equip.get("etype")
                if equip.get("equipmentID") is not None:
                    equip["equipmentID"] = str(equip.get("equipmentID"))
                if equip.get("Etype") is not None:
                    equip["Etype"] = str(equip.get("Etype", "")).strip().lower()
            return data

        # Try local file first
        try:
            data = json.loads(self.equipment_path.read_text(encoding="utf-8"))
            normalized = _normalize(data)
            if normalized:
                return normalized
        except Exception:
            pass

        # Fallback: load from HTTP API (works on server even if file path is wrong)
        try:
            import urllib.request
            req = urllib.request.Request("http://120.107.152.110/~a0303/DB/get_equipment.php")
            with urllib.request.urlopen(req, timeout=5) as response:
                if response.status == 200:
                    api_data = json.loads(response.read().decode("utf-8"))
                    items = api_data.get("data", [])
                    normalized = _normalize(items)
                    if normalized:
                        return normalized
        except Exception:
            pass

        return []


    def _save_equipment(self):
        try:
            existing_list = []
            if self.equipment_path.exists():
                try:
                    existing_list = json.loads(self.equipment_path.read_text(encoding="utf-8"))
                    if not isinstance(existing_list, list):
                        existing_list = []
                except Exception:
                    existing_list = []

            index_map = {}
            for idx, equip in enumerate(existing_list):
                eid = equip.get("equipmentID")
                if eid is not None:
                    index_map[str(eid)] = idx

            for equip in self.equipments:
                eid = equip.get("equipmentID")
                if eid is None:
                    continue
                eid = str(eid)
                if eid in index_map:
                    existing_list[index_map[eid]].update(equip)
                else:
                    existing_list.append(equip)
                    index_map[eid] = len(existing_list) - 1

            self.equipment_path.write_text(
                json.dumps(existing_list, ensure_ascii=False, indent=2),
                encoding="utf-8"
            )
        except Exception:
            pass

    def _worker_label(self, worker_id):
        return "W{}".format(worker_id)

    def _clear_equipment_statuses(self):
        for equip in self.equipments:
            equip["status"] = ""

    def _find_equipment_for_task(self, task, earliest_time, equipment_usage_counts=None):
        equipment_type = str(task.get("equipment_type", "")).strip().lower()
        if not equipment_type:
            return None

        equipment_type_alias = equipment_type
        if "煎" in equipment_type or "烤" in equipment_type or "grill" in equipment_type:
            equipment_type_alias = "grill"
        elif "炸" in equipment_type or "fry" in equipment_type:
            equipment_type_alias = "fryer"
        elif "擺盤" in equipment_type or "plating" in equipment_type:
            equipment_type_alias = "plating_station"
        elif "備料" in equipment_type or "prep" in equipment_type:
            equipment_type_alias = "prep_station"
        elif "蒸" in equipment_type or "steam" in equipment_type:
            equipment_type_alias = "grill"
        elif "drink" in equipment_type or "飲料" in equipment_type:
            equipment_type_alias = "drink"
        elif "coffee" in equipment_type or "咖啡" in equipment_type:
            equipment_type_alias = "coffee"

        matching = []
        for equip in self.equipments:
            etype = str(equip.get("Etype", "")).strip().lower()
            name = str(equip.get("name", "")).strip().lower()
            if etype == equipment_type_alias or etype == equipment_type:
                matching.append(equip)
                continue
            if equipment_type in name or name in equipment_type or equipment_type_alias in name:
                matching.append(equip)
                continue
        if not matching:
            return None

        if equipment_usage_counts is None:
            equipment_usage_counts = {}

        def sort_key(equip):
            eid = equip.get("equipmentID") or ""
            return (
                equipment_usage_counts.get(str(eid), 0),
                self.equipment_available.get(eid, 0),
                eid
            )

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
            order_id = str(order["id"])
            is_takeout = order.get("is_takeout", False)
            
            # --- 防呆機制 1：如果訂單沒包裝在 items 裡，就把訂單自己當成 item ---
            items = order.get("items", [])
            if not items:
                items = [order]
                
            task_list = []
            full_names = ",".join([item.get("meal_name", item.get("item", "unknown_meal")) for item in items])

            for idx, item in enumerate(items): 
                base_meal_name = "{}_{}".format(item.get('meal_name', item.get('item', 'unknown_meal')), idx)
                
                # --- 防呆機制 2：兼容新版 Recipe (有 steps) 與舊版格式 (無 steps) ---
                steps = item.get("steps", [])
                if steps:
                    def step_order_value(s, index):
                        if isinstance(s, dict):
                            if "step_order" in s:
                                try:
                                    return int(s.get("step_order"))
                                except Exception:
                                    return s.get("step_order")
                            if "stepOrder" in s:
                                try:
                                    return int(s.get("stepOrder"))
                                except Exception:
                                    return s.get("stepOrder")
                        return index + 1

                    indexed_steps = list(enumerate(list(steps)))
                    steps_sorted = sorted(
                        indexed_steps,
                        key=lambda pair: step_order_value(pair[1], pair[0])
                    )
                    for index, (_, step) in enumerate(steps_sorted):
                        step_order = step_order_value(step, index)
                        task_list.append({
                            "item": step.get("step_name"),  
                            "meal_name": base_meal_name,    
                            "prep_time": int(step.get("duration_sec", 5)), 
                            "description": step.get("step_name"),
                            "equipment_type": step.get("equipment_type", ""),
                            "task_index": step_order if step_order is not None else index
                        })
                else:
                    task_list.append({
                        "item": item.get("item", "unknown_item"),  
                        "meal_name": base_meal_name,    
                        "prep_time": int(item.get("prep_time", 5)), 
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
        
        # Initialize available times to now (tasks cannot start before now)
        current_timestamp = time.time()
        for worker_id in self.workers:
            # Start from now or from whatever was previously scheduled
            self.worker_available[worker_id] = current_timestamp
        for equipment_id in list(self.equipment_available.keys()):
            self.equipment_available[equipment_id] = current_timestamp

        # Rebuild available times from already-allocated tasks
        # These tasks have real expected_at timestamps, so we respect them
        for task in allocated_tasks:
            wid = task.get("worker_id")
            expected_at = task.get("expected_at", 0)
            # Only update if expected_at is in the future (task not yet complete)
            if expected_at > current_timestamp:
                if wid is not None:
                    self.worker_available[wid] = max(self.worker_available.get(wid, current_timestamp), expected_at)
                eid = task.get("equipment_id")
                if eid:
                    eid = str(eid)
                    self.equipment_available[eid] = max(self.equipment_available.get(eid, current_timestamp), expected_at)

        # Build current equipment usage counts based on each worker's current task
        worker_current_tasks = {}
        for task in allocated_tasks:
            worker_id = task.get("worker_id")
            if worker_id is None:
                continue
            expected_at = task.get("expected_at", float('inf'))
            if worker_id not in worker_current_tasks or expected_at < worker_current_tasks[worker_id].get("expected_at", float('inf')):
                worker_current_tasks[worker_id] = task

        equipment_usage_counts = {}
        workers_with_current_task = set()
        for worker_id, task in worker_current_tasks.items():
            workers_with_current_task.add(worker_id)
            equipment_id = task.get("equipment_id")
            if equipment_id:
                equipment_id = str(equipment_id)
                equipment_usage_counts[equipment_id] = equipment_usage_counts.get(equipment_id, 0) + 1

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
            tasks = sorted(tasks, key=lambda t: int(t.get("task_index", 0)))
            group_id = "{}:{}".format(order_id, meal_name)
            
            if group_id in self.group_worker_mapping:
                selected_worker = self.group_worker_mapping[group_id]
            else:
                selected_worker = min(self.worker_available.items(), key=lambda pair: (pair[1], pair[0]))[0]
                self.group_worker_mapping[group_id] = selected_worker
            
            worker_start = self.worker_available[selected_worker]
            current_time = worker_start
            worker_has_current = selected_worker in workers_with_current_task

            for task in tasks:
                prep_time = int(task.get("prep_time", 5))
                start_time = current_time

                selected_equipment_id = None
                selected_equipment = None
                equipment_start = current_time
                if task.get("equipment_type"):
                    match = self._find_equipment_for_task(task, current_time, equipment_usage_counts)
                    if match is not None:
                        selected_equipment, selected_equipment_id, equipment_start = match
                        start_time = max(current_time, equipment_start)

                finish_time = start_time + prep_time

                task["worker_id"] = selected_worker
                task["expected_at"] = finish_time
                task["prep_time"] = prep_time
                task["group_id"] = group_id  

                if selected_equipment_id:
                    selected_equipment_id = str(selected_equipment_id)
                    task["equipment_id"] = selected_equipment_id
                    task["equipment_name"] = selected_equipment.get("name", selected_equipment_id)
                    status_text = "{}:{}|{}".format(self._worker_label(selected_worker), task.get('item', 'unknown_item'), task.get('id'))
                    task["equipment_status"] = status_text
                    
                    self.equipment_available[selected_equipment_id] = finish_time

                    if not worker_has_current:
                        equipment_usage_counts[selected_equipment_id] = equipment_usage_counts.get(selected_equipment_id, 0) + 1
                
                # 【修復：補回你遺失的時間推進邏輯！】
                current_time = finish_time

                if not worker_has_current:
                    worker_has_current = True
                    workers_with_current_task.add(selected_worker)

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
        equipment_usage_counts = {}
        for worker_id, task in worker_current_tasks.items():
            equipment_id = task.get("equipment_id")
            if equipment_id:
                equipment_id = str(equipment_id)
                equipment_usage_counts[equipment_id] = equipment_usage_counts.get(equipment_id, 0) + 1
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
            user_count = equipment_usage_counts.get(str(equipment_id), 1)
            status_text = "使用中:{}人".format(user_count)
            for equip in self.equipments:
                if str(equip.get("equipmentID")) == str(equipment_id):
                    equip["status"] = status_text
                    break

        self._save_equipment()
        self._save_state()

        return self.pending_queue

    def remove_finished(self, order_id, task_item=None):
        # Convert order_id to string for consistent lookup
        order_id = str(order_id)
        
        # Remove task from queue
        removed_task = None
        for index in range(len(self.pending_queue) - 1, -1, -1):
            task = self.pending_queue[index]
            if str(task.get("id")) == order_id and (task_item is None or task.get("item") == task_item):
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
                equipment_id = str(equipment_id)
                for equip in self.equipments:
                    if str(equip.get("equipmentID")) == equipment_id:
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
            tracker_key = None
            if order_id in self.order_tracker:
                tracker_key = order_id
            elif int(order_id) in self.order_tracker:
                tracker_key = int(order_id)
                
            if tracker_key is not None:
                if removed_task.get("is_pack_task") or removed_task.get("item") == "pack-bag":
                    del self.order_tracker[tracker_key]
                else:
                    self.order_tracker[tracker_key]["remaining_tasks"] -= 1
                    if self.order_tracker[tracker_key]["remaining_tasks"] <= 0:
                        order_info = self.order_tracker[tracker_key]
                        response["all_items_completed"] = True
                        response["order_content"] = order_info["full_content_names"]
                        
                        # 清理該訂單相關的 group_id 映射
                        prefix = "{}:".format(order_id)
                        groups_to_remove = [gid for gid in list(self.group_worker_mapping.keys()) if str(gid).startswith(prefix) or str(gid).startswith(str(tracker_key) + ":")]
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
                            del self.order_tracker[tracker_key]

        response["queue"] = self._reschedule()
        return response
