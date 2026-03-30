import json
import socket

class McOSScheduler:
    def __init__(self):
        # 這是全域隊列，存儲所有「尚未完成」的訂單
        self.pending_queue = []

    def optimize_schedule(self, new_orders):
        """
        核心演算法區：請組員在這裡發揮
        """
        # 將新進來的訂單加入隊列
        self.pending_queue.extend(new_orders)
        
        # 範例：最短作業優先 (SJF)
        # 組員可以換成：優先級排程、多機台分配等
        self.pending_queue.sort(key=lambda x: x['prep_time'])
        
        # 重新計算每個任務的預計完成時間點
        current_time = 0
        for order in self.pending_queue:
            current_time += order['prep_time']
            order['expected_at'] = current_time
            
        return self.pending_queue

    def remove_finished(self, order_id):
        """ 當 Java 完成製作時，呼叫此項從隊列移除 """
        self.pending_queue = [o for o in self.pending_queue if o.get('id') != order_id]
        return self.pending_queue

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
                result = scheduler.remove_finished(request["order_id"])
            else:
                result = scheduler.pending_queue

            conn.sendall((json.dumps(result, ensure_ascii=False) + "\n").encode('utf-8'))
        finally:
            conn.close()

if __name__ == "__main__":
    start_engine()