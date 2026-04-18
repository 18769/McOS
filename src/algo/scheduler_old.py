import json
import socket

class McOSScheduler:
    def __init__(self):
        self.pending_queue = []
        # 定義套餐拆解規則
        self.combo_defs = {
            "大麥克套餐": [
                {"item": "大麥克", "prep_time": 8},
                {"item": "薯條", "prep_time": 3},
                {"item": "可樂", "prep_time": 2}
            ],
            "雞塊套餐": [
                {"item": "雞塊", "prep_time": 5},
                {"item": "薯條", "prep_time": 3},
                {"item": "可樂", "prep_time": 2}
            ]
        }
        # 用來追蹤每個 ID 的原始內容，判斷是否全部完成
        # 格式: { order_id: { "remaining_count": int, "is_takeout": bool } }
        self.order_tracker = {}

    def optimize_schedule(self, new_orders):
        processed_list = []
        
        for order in new_orders:
            order_id = order['id']
            item_name = order['item']
            is_takeout = order.get('is_takeout', False) # 假設 Java 端會傳這個
            
            # 初始化追蹤器 (如果這個 ID 還沒被紀錄過)
            if order_id not in self.order_tracker:
                self.order_tracker[order_id] = {"remaining_count": 0, "is_takeout": is_takeout}

            # 套餐拆解邏輯
            if item_name in self.combo_defs:
                for sub_item in self.combo_defs[item_name]:
                    task = sub_item.copy()
                    task.update({"id": order_id, "is_takeout": is_takeout, "is_pack_task": False})
                    processed_list.append(task)
                    self.order_tracker[order_id]["remaining_count"] += 1
            else:
                order["is_pack_task"] = False
                processed_list.append(order)
                self.order_tracker[order_id]["remaining_count"] += 1

        self.pending_queue.extend(processed_list)
        return self._reschedule()

    def _reschedule(self):
        """ 統一處理排序與預計時間計算 """
        # 排序權重：1. 外帶優先 2. 同品項合併 (Batching) 3. ID 順序
        self.pending_queue.sort(key=lambda x: (
            not x.get('is_takeout', False), 
            x['item'], 
            x['id']
        ))

        current_time = 0
        last_item = None
        for order in self.pending_queue:
            base_time = order.get('base_prep_time', order['prep_time'])
            order['base_prep_time'] = base_time
            
            # 批次優化邏輯
            if last_item is not None and order['item'] == last_item and not order.get('is_pack_task'):
                actual_time = max(1, int(base_time * 0.7))
            else:
                actual_time = base_time
            
            order['prep_time'] = actual_time
            current_time += actual_time
            order['expected_at'] = current_time
            last_item = order['item']
            
        return self.pending_queue

    def remove_finished(self, task_id, task_item):
        """ 
        當 Java 完成一個單項時呼叫。
        注意：這裡需要 task_item 來精確定位移除哪一個(因為同 ID 可能有多個品項)
        """
        found = False
        for i, o in enumerate(self.pending_queue):
            if o.get('id') == task_id and o.get('item') == task_item:
                self.pending_queue.pop(i)
                found = True
                break
        
        if found and task_id in self.order_tracker:
            self.order_tracker[task_id]["remaining_count"] -= 1
            
            # --- 套餐/外帶打包邏輯 ---
            # 如果該 ID 的所有東西都做完了，且它是「外帶」，自動加入打包任務
            if self.order_tracker[task_id]["remaining_count"] == 0:
                if self.order_tracker[task_id]["is_takeout"]:
                    print(f"系統：訂單 {task_id} 已完成所有單項，加入打包程序...")
                    pack_task = {
                        "id": task_id,
                        "item": "🥡 打包裝袋",
                        "prep_time": 4,
                        "is_takeout": True,
                        "is_pack_task": True
                    }
                    self.pending_queue.insert(0, pack_task) # 打包任務插到最前面
                    # 為了不讓它無限循環，把 tracker 清掉
                    del self.order_tracker[task_id]
                else:
                    # 內用做完就真的結束了
                    del self.order_tracker[task_id]

        return self._reschedule()

# --- Socket 伺服器修改：支援傳入 item 名稱 ---
def start_engine():
    scheduler = McOSScheduler()
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.bind(('localhost', 9999))
    server.listen(5)
    print("McOS Python 智慧核心 [套餐&外帶版] 已啟動...")

    while True:
        conn, addr = server.accept()
        try:
            raw_data = conn.recv(8192).decode('utf-8')
            if not raw_data: continue
            request = json.loads(raw_data)
            
            if request.get("type") == "ADD_ORDER":
                result = scheduler.optimize_schedule(request["data"])
            elif request.get("type") == "FINISH_ORDER":
                # 修改這裡：傳入 order_id 和 item 名稱
                # Java 端呼叫時也要補上 item
                result = scheduler.remove_finished(request["order_id"], request.get("item"))
            else:
                result = scheduler.pending_queue

            conn.sendall((json.dumps(result, ensure_ascii=False) + "\n").encode('utf-8'))
        finally:
            conn.close()

if __name__ == "__main__":
    start_engine()