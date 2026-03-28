# -*- coding: utf-8 -*-
import socket
import json
import sys

# 解決 Windows 終端機顯示中文問題
if sys.platform == "win32":
    sys.stdout.reconfigure(encoding='utf-8')

def simple_scheduler(orders):
    # 簡單排程：按準備時間排序
    return sorted(orders, key=lambda x: x['prep_time'])

def start_server():
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    # 允許地址重用，避免 WinError 10048
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    
    server.bind(('localhost', 9999))
    server.listen(5)
    print("--- 智慧廚房排程引擎已啟動 (Port: 9999) ---")

    while True:
        # 注意：以下這幾行必須在 while 迴圈內縮排
        conn, addr = server.accept() 
        try:
            data = conn.recv(1024).decode('utf-8')
            if data:
                orders = json.loads(data)
                print(f"收到新訂單: {orders}")
                
                # 呼叫算法
                result = simple_scheduler(orders)
                
                # 回傳 JSON 字串
                response = json.dumps(result, ensure_ascii=False)
                conn.send(response.encode('utf-8'))
                print(f"排程建議已發送: {response}")
        except Exception as e:
            print(f"處理出錯: {e}")
        finally:
            conn.close() # 確保每次連線都會關閉

if __name__ == "__main__":
    start_server()