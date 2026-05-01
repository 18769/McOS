import time

try:
    from .algo_scheduler import McOSScheduler
except Exception:
    McOSScheduler = None

_scheduler_instance = None


def get_scheduler():
    global _scheduler_instance
    if McOSScheduler is None:
        return None
    if _scheduler_instance is None:
        _scheduler_instance = McOSScheduler()
    return _scheduler_instance


def mcosscheduler_logic(orders, **kwargs):
    scheduler = get_scheduler()
    if scheduler is None:
        raise RuntimeError("McOSScheduler is not available. Check src/scheduler/algo_scheduler.py import.")
    return scheduler.optimize_schedule(orders)


def fcfs_logic(tasks, **kwargs):
    return sorted(tasks, key=lambda x: x['id'])


def sjf_logic(tasks, **kwargs):
    return sorted(tasks, key=lambda x: x['prep_time'])


def aging_logic(tasks, **kwargs):
    now = kwargs.get('current_time', time.time())

    def calculate_priority(task):
        wait_time = now - task.get('arrival_time', now)
        priority_value = task['prep_time'] - (wait_time * 0.5)
        return (not task.get('is_takeout', False), priority_value)

    return sorted(tasks, key=calculate_priority)
