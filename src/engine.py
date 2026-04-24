import json
import socket
import time
import csv
from scheduler import McOSScheduler

class ExperimentLogger:
    def __init__(self, filename="experiment_results.csv"):
        self.filename = filename
        with open(self.filename, 'w', newline='', encoding='utf-8') as f:
            writer = csv.writer(f)
            writer.writerow(['OrderID', 'Item', 'Algo', 'WaitTime', 'TurnaroundTime'])

    def record(self, order_id, item, algo, arrival_time, finish_time, prep_time):
        turnaround = finish_time - arrival_time
        wait = turnaround - prep_time
        with open(self.filename, 'a', newline='', encoding='utf-8') as f:
            writer = csv.writer(f)
            writer.writerow([order_id, item, algo, round(max(0, wait), 2), round(turnaround, 2)])
        self.print_summary() # 每完成一筆就印出最新分析

    def print_summary(self):
        """核心：輸出成解釋用的曲線表格邏輯 (文字版)"""
        try:
            import pandas as pd
            df = pd.read_csv(self.filename)
            if df.empty: return
            
            summary = df.groupby('Algo').agg({
                'WaitTime': ['mean', 'max'],
                'TurnaroundTime': ['mean']
            }).round(2)
            
            print("\n" + "="*55)
            print("        McOS 演算法實驗即時分析 (解釋用數據)")
            print("="*55)
            print(summary.to_string())
            print("-" * 55)
            print("解釋重點：SJF 平均等待應最低；FCFS 最長等待常出現在長任務後。")
            print("="*55 + "\n")
        except ImportError:
            print(" [提醒] 請安裝 pandas (pip install pandas) 以顯示自動化表格紀錄")
        except Exception:
            pass

def start_engine():
    scheduler = McOSScheduler()
    logger = ExperimentLogger()
    
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind(('localhost', 9999))
    server.listen(5)
    print("McOS Python 引擎已啟動 [自動分析模式]...")

    while True:
        conn, addr = server.accept()
        try:
            raw_data = conn.recv(8192).decode('utf-8')
            if not raw_data: continue
            request = json.loads(raw_data)
            
            result = []
            if request.get("type") == "SWITCH_MODE":
                scheduler.set_strategy(request["mode"])
                result = scheduler._reschedule()
            elif request.get("type") == "ADD_ORDER":
                result = scheduler.optimize_schedule(request["data"])
            elif request.get("type") == "GET_STATUS":
                result = scheduler.pending_queue
            elif request.get("type") == "FINISH_ORDER":
                order_id = request.get("order_id")
                item_name = request.get("item")
                
                task_info = next((t for t in scheduler.pending_queue if t['id'] == order_id and t['item'] == item_name), None)
                if task_info:
                    logger.record(order_id, item_name, scheduler.strategy.__name__, 
                                  task_info['arrival_time'], time.time(), task_info['prep_time'])
                
                result = scheduler.remove_finished(order_id, item_name)

            conn.sendall((json.dumps(result, ensure_ascii=False) + "\n").encode('utf-8'))
        finally:
            conn.close()

if __name__ == "__main__":
    start_engine()