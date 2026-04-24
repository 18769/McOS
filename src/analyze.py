import pandas as pd

def show_experiment_report():
    try:
        # 讀取你的實驗紀錄
        df = pd.read_csv('experiment_results.csv')
        
        # 依照演算法統計核心指標 (等待時間與周轉時間)
        summary = df.groupby('Algo').agg({
            'WaitTime': ['mean', 'max'],
            'TurnaroundTime': ['mean']
        }).round(2)
        
        summary.columns = ['平均等待時間(s)', '最長等待時間(s)', '平均周轉時間(s)']
        
        print("\n" + "="*60)
        print("          McOS 智慧廚房 - 演算法實驗分析報表")
        print("="*60)
        print(summary)
        print("="*60)
        print("\n【解釋指引】")
        print("1. 若 FCFS 的平均等待時間遠高於 SJF，證明了『護車效應』的存在。")
        print("2. 若 SJF 出現極長的等待時間，證明了『飢餓現象』。")
        print("3. Aging 模式應能讓長任務的最長等待時間保持在合理範圍。")

    except Exception as e:
        print(f"目前尚無數據可分析，請先完成一輪點餐流程！")

if __name__ == "__main__":
    show_experiment_report()