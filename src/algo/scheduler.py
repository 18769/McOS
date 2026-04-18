import json
import socket
import time

class McOSScheduler:
    def __init__(self):
        self.pending_queue = []
        self.mode = "NORMAL" 

    def optimize_schedule(self, new_orders):
        now = time.time()
        for order in new_orders:
            if 'arrival_time' not in order:
                order['arrival_time'] = now
        self.pending_queue.extend(new_orders)
        
        if self.mode == "NORMAL":
            self.apply_fcfs()
        else:
            self.apply_advanced_logic()
            self.solve_scheduling_issues()
        
        return self.recalculate_times()

    def apply_fcfs(self):
        self.pending_queue.sort(key=lambda x: x['id'])

    def apply_advanced_logic(self):
        self.pending_queue.sort(key=lambda x: x['prep_time'])

    def solve_scheduling_issues(self):
        now = time.time()
        for order in self.pending_queue:
            wait_time = now - order.get('arrival_time', now)
            order['is_starving'] = True if wait_time > 20 else False

        self.pending_queue.sort(key=lambda x: (
            not x.get('is_starving', False), 
            not x.get('is_takeout', False),  
            x['prep_time']                   
        ))

    def recalculate_times(self):
        current_total = 0
        for order in self.pending_queue:
            current_total += order['prep_time']
            order['expected_at'] = current_total
        return self.pending_queue

    def remove_finished(self, order_id):
        self.pending_queue = [o for o in self.pending_queue if o.get('id') != order_id]
        return self.recalculate_times()

def start_engine():
    scheduler = McOSScheduler()
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind(('localhost', 9999))
    server.listen(5)
    print(f"McOS Python 核心引擎 [已啟動] 目前模式: {scheduler.mode}")

    while True:
        conn, addr = server.accept()
        try:
            raw = conn.recv(8192).decode('utf-8')
            if not raw: continue
            request = json.loads(raw)
            cmd = request.get("type")
            
            if cmd == "ADD_ORDER":
                result = scheduler.optimize_schedule(request["data"])
            elif cmd == "SWITCH_MODE":
                scheduler.mode = request.get("mode", "NORMAL")
                print(f">>> 模式切換至: {scheduler.mode}")
                result = scheduler.optimize_schedule([])
            elif cmd == "FINISH_ORDER":
                result = scheduler.remove_finished(request["order_id"])
            else:
                result = scheduler.pending_queue

            # --- 關鍵修正：包裝成物件回傳 ---
            response_data = {
                "mode": scheduler.mode,
                "orders": result
            }
            conn.sendall((json.dumps(response_data, ensure_ascii=False) + "\n").encode('utf-8'))
        finally:
            conn.close()

if __name__ == "__main__":
    start_engine()