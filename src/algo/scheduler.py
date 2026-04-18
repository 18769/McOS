import json
import socket

class McOSScheduler:
    def __init__(self):
        # 這是全域隊列，存儲所有「尚未完成」的訂單
        self.pending_queue = []
        
        # 用來追蹤每個 ID 的原始內容，判斷是否全部完成
        # 格式: { order_id: { "remaining_count": int, "is_takeout": bool } }
        self.order_tracker = {}

    def optimize_schedule(self, new_orders):
        """
        核心演算法區：批次優化、外帶打包
        注意：套餐拆解由 GUI 負責，傳進來的都是單項任務
        """
        processed_list = []
        
        for order in new_orders:
            order_id = order['id']
            is_takeout = order.get('is_takeout', False)
            
            # 初始化追蹤器 (如果這個 ID 還沒被紀錄過)
            if order_id not in self.order_tracker:
                self.order_tracker[order_id] = {"remaining_count": 0, "is_takeout": is_takeout}

            # GUI 已拆解套餐，這裡直接加入隊列
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

    def remove_finished(self, task_id, task_item=None):
        """ 
        當 Java 完成一個單項時呼叫。
        支援兩種模式：
        - 傳入 task_id + task_item：精確移除單項任務（套餐模式）
        - 僅傳入 task_id：移除該訂單所有項目（簡單模式）
        """
        found = False
        
        if task_item is not None:
            # 精確模式：移除特定項目
            for i, o in enumerate(self.pending_queue):
                if o.get('id') == task_id and o.get('item') == task_item:
                    self.pending_queue.pop(i)
                    found = True
                    break
            
            if found and task_id in self.order_tracker:
                self.order_tracker[task_id]["remaining_count"] -= 1
                
                # 套餐/外帶打包邏輯
                if self.order_tracker[task_id]["remaining_count"] == 0:
                    if self.order_tracker[task_id]["is_takeout"]:
                        pack_task = {
                            "id": task_id,
                            "item": "🥡 打包裝袋",
                            "prep_time": 4,
                            "is_takeout": True,
                            "is_pack_task": True
                        }
                        self.pending_queue.insert(0, pack_task)
                        del self.order_tracker[task_id]
                    else:
                        del self.order_tracker[task_id]
        else:
            # 簡單模式：移除訂單的所有項目
            self.pending_queue = [o for o in self.pending_queue if o.get('id') != task_id]
            if task_id in self.order_tracker:
                del self.order_tracker[task_id]
        
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