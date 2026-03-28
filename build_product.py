import os
import shutil
import time

def build_project():
    # 1. 產生暫存的中繼啟動腳本 (PyInstaller 需要此檔案作為入口點)
    print("==> [1/4] 正在產生打包用中繼程式碼...")
    launcher_code = """import os
import sys
import subprocess
import threading
import time

def resource_path(relative_path):
    try:
        base_path = sys._MEIPASS
    except Exception:
        base_path = os.path.abspath(".")
    return os.path.join(base_path, relative_path)

def start_python_scheduler():
    import scheduler
    scheduler.start_engine()

if __name__ == '__main__':
    os.system('chcp 65001 > nul')
    print("=======================================")
    print("[1/3] 正在檢查並清理舊的排程引擎...")
    os.system('for /f "tokens=5" %a in (\\'netstat -ano ^| findstr :9999\\') do taskkill /F /PID %a 2>nul')
    
    print("[2/3] 正在背景啟動 Python 排程引擎 (Port 9999)...")
    t = threading.Thread(target=start_python_scheduler, daemon=True)
    t.start()
    
    time.sleep(1)
    
    print("[3/3] 正在啟動 Java 模擬器畫面...")
    json_jar_path = resource_path('json-20240303.jar')
    java_class_dir = resource_path('.')
    
    cp_separator = ';' if os.name == 'nt' else ':'
    classpath = f"{java_class_dir}{cp_separator}{json_jar_path}"
    
    try:
        print("=======================================")
        print(">> 開啟成功！若要結束程式，請直接關閉 Java 視窗。")
        subprocess.run(['java', '-Dfile.encoding=UTF-8', '-cp', classpath, 'KitchenGUI'], cwd=java_class_dir)
    except FileNotFoundError:
        print("[錯誤] 找不到 Java 執行環境 (java.exe)，請確認系統已有安裝 JRE。")
        os.system("pause")
    except Exception as e:
        print(f"[錯誤] 發生未預期的例外狀況: {e}")
        os.system("pause")
    
    print("=======================================")
    print("Java 視窗已關閉，系統即將結束所有程序...")
    time.sleep(1)
"""
    with open("temp_launcher.py", "w", encoding="utf-8") as f:
        f.write(launcher_code)

    # 2. 編譯 Java 以確保拿到最新的 .class 檔
    print("==> [2/4] 正在重新編譯 Java 原始碼...")
    if os.system('javac -encoding utf-8 -cp ".;json-20240303.jar" KitchenGUI.java') != 0:
        print("[錯誤] Java 編譯失敗，請檢查程式碼再重新打包。")
        return

    # 3. 呼叫 PyInstaller 進行打包
    print("==> [3/4] 正在利用 PyInstaller 將專案封裝為 EXE... (此步驟可能需要 1~2 分鐘)")
    cmd = 'pyinstaller --onefile --noconfirm --add-data "KitchenGUI.class;." --add-data "json-20240303.jar;." --name McOS_Product temp_launcher.py'
    if os.system(cmd) != 0:
        print("[錯誤] PyInstaller 打包失敗。")
        return

    # 4. 將生成的 exe 移動到 product 資料夾並清理所有暫存檔
    print("==> [4/4] 正在轉移成品至 product 目錄並還原開發環境...")
    
    # 建立 product 目錄
    os.makedirs("product", exist_ok=True)
    
    # 搬移 exe
    exe_path = os.path.join("dist", "McOS_Product.exe")
    if os.path.exists(exe_path):
        target_path = os.path.join("product", "McOS_Product.exe")
        if os.path.exists(target_path):
            os.remove(target_path)
        shutil.move(exe_path, target_path)
        print(f"\\n🎉 打包成功！執行檔已輸出至: {target_path}\\n")
    else:
        print("\\n[錯誤] 找不到打包後的 exe 檔案。\\n")

    # 安全地刪除暫存檔案與目錄
    def safe_remove(path):
        try:
            if os.path.isdir(path):
                shutil.rmtree(path)
            elif os.path.isfile(path):
                os.remove(path)
        except Exception as e:
            print(f"警告：無法清理暫存檔案 {path}: {e}")

    safe_remove("temp_launcher.py")
    safe_remove("temp_launcher.spec")
    safe_remove("McOS_Product.spec")
    safe_remove("build")
    safe_remove("dist")

if __name__ == '__main__':
    build_project()
    os.system("pause")
