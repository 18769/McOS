import json
import socket

class McOSScheduler:
    def __init__(self):
        # 廚房排隊（可拆散的任務列表）
        self.pending_queue = []
        
        # 訂單層級追蹤（不拆散）
        # 格式: { order_id: { "name": "套餐名", "items": [...], "remaining_tasks": int, 
        #                      "is_takeout": bool, "total_prep_time": int } }
        self.order_tracker = {}

    def optimize_schedule(self, new_orders):
        """
        接收訂單（可能是套餐或單項）
        支持同一個 order_id 對應多個 items（一次送出的批量訂單）
        """
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
            
            # 判斷是否為套餐（有 items 欄位）
            if 'items' in order:
                # 套餐：多個 items
                orders_by_id[order_id]['items'].extend(order['items'])
            else:
                # 單項：直接加入
                orders_by_id[order_id]['items'].append({
                    "item": order_name,
                    "prep_time": order.get('prep_time', 5)
                })
        
        # 現在處理分組後的訂單
        for order_id, order_data in orders_by_id.items():
            items = order_data['items']
            is_takeout = order_data['is_takeout']
            
            # 組合訂單名稱（所有項目名稱）
            order_name = "、".join(order_data['names'])
            
            total_time = sum(item.get('prep_time', 0) for item in items)
            task_count = len(items)
            
            # 註冊訂單
            self.order_tracker[order_id] = {
                "name": order_name,
                "items": items,
                "total_tasks": task_count,  # 總任務數
                "remaining_tasks": task_count,  # 剩餘任務數
                "is_takeout": is_takeout,
                "total_prep_time": total_time
            }
            
            # 內部拆散為任務加入排隊（用於廚房排程）
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
            
            # 批次優化邏輯：同品項連續製作時可降速 (70%)
            if last_item is not None and order['item'] == last_item and not order.get('is_pack_task'):
                actual_time = max(1, int(base_time * 0.7))
            else:
                actual_time = base_time
            
            order['prep_time'] = actual_time
            current_time += actual_time
            order['expected_at'] = current_time
            last_item = order['item']
            
        return self.pending_queue

    def remove_finished(self, order_id, task_item=None):
        """ 
        當完成一個任務時呼叫。
        邏輯：
        1. 先刪除該任務
        2. 遞減 remaining_tasks
        3. 當 remaining_tasks = 0 時，整個訂單完成
           - 外帶 → 加打包任務
           - 完成 → 返回完成訊號（含完整訂單資訊）
        """
        
        if order_id not in self.order_tracker:
            return self._reschedule()
        
        # 移除該任務
        removed_count = 0
        for i in range(len(self.pending_queue) - 1, -1, -1):
            task = self.pending_queue[i]
            if task.get('id') == order_id and (task_item is None or task.get('item') == task_item):
                self.pending_queue.pop(i)
                removed_count += 1
                break  # 只移除一個
        
        if removed_count > 0:
            # 遞減任務計數
            self.order_tracker[order_id]["remaining_tasks"] -= 1
            
            # 檢查訂單是否全部完成
            if self.order_tracker[order_id]["remaining_tasks"] <= 0:
                order_info = self.order_tracker[order_id]
                
                # 外帶訂單 → 加打包任務
                if order_info.get("is_takeout", False):
                    pack_task = {
                        "id": order_id,
                        "order_name": order_info["name"],
                        "item": "🥡 打包裝袋",
                        "prep_time": 4,
                        "is_takeout": True,
                        "is_pack_task": True,
                        "task_index": 999
                    }
                    self.pending_queue.insert(0, pack_task)
                else:
                    # 內用訂單 → 直接完成
                    del self.order_tracker[order_id]
        
        return self._reschedule()

# --- Socket 伺服器邏輯 ---
def start_engine():
    scheduler = McOSScheduler()
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.bind(('localhost', 9999))
    server.listen(5)
    print("McOS Python 核心引擎 [已啟動]，等待 Java 指令...")

    while True:
        conn, addr = server.accept()
        try:
            raw_data = conn.recv(8192).decode('utf-8')
            if not raw_data: continue
            
            request = json.loads(raw_data)
            
            # 根據 Java 傳來的指令類型執行動作
            if request.get("type") == "ADD_ORDER":
                result = scheduler.optimize_schedule(request["data"])
            elif request.get("type") == "FINISH_ORDER":
                # ✓ 支援精確模式：傳入 order_id 和 item，準確移除單項
                order_id = request.get("order_id")
                item = request.get("item")  # 套餐模式會有這個
                result = scheduler.remove_finished(order_id, item)
            else:
                result = scheduler.pending_queue

            conn.sendall((json.dumps(result, ensure_ascii=False) + "\n").encode('utf-8'))
        finally:
            conn.close()

if __name__ == "__main__":
    start_engine()