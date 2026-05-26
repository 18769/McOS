import time


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
