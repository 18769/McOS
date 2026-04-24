import time

# 1. FCFS: 依照 ID 排序
def fcfs_logic(tasks, **kwargs):
    return sorted(tasks, key=lambda x: x['id'])

# 2. SJF: 依照準備時間排序
def sjf_logic(tasks, **kwargs):
    return sorted(tasks, key=lambda x: x['prep_time'])

# 3. Aging: 考慮等待時間的老化演算法 (對照筆記重點)
def aging_logic(tasks, **kwargs):
    now = kwargs.get('current_time', time.time())
    
    def calculate_priority(task):
        # 等待時間 = 現在時間 - 進入隊列時間
        wait_time = now - task.get('arrival_time', now)
        # 優先權 = 準備時間 - (等待時間 * 老化系數)
        # 值越小越優先
        priority_value = task['prep_time'] - (wait_time * 0.5)
        return (not task.get('is_takeout', False), priority_value)

    return sorted(tasks, key=calculate_priority)
