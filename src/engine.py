"""
Compatibility shim: forward to scheduler.engine
This file is kept for backwards compatibility. New code should import from:
    from scheduler.engine import start_engine
"""
from pathlib import Path
import sys

src_dir = Path(__file__).resolve().parent
if str(src_dir) not in sys.path:
    sys.path.insert(0, str(src_dir))

from scheduler.engine import start_engine

if __name__ == "__main__":
    start_engine()
