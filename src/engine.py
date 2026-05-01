"""
Compatibility shim: forward to scheduler.engine
This file is kept for backwards compatibility. New code should import from:
    from scheduler.engine import start_engine
"""
from scheduler.engine import start_engine

if __name__ == "__main__":
    start_engine()
