"""
Compatibility shim: expose a package-style `scheduler` while keeping
the scheduler implementation files under `src/scheduler/` directory.

This module sets its __path__ so Python can treat it as a package and
re-exports `McOSScheduler` from `scheduler.core`.
"""
import os
from importlib import import_module

# Make this module behave like a package by pointing __path__ to the
# `scheduler` directory sitting alongside this file.
this_dir = os.path.dirname(__file__)
package_dir = os.path.join(this_dir, "scheduler")
if os.path.isdir(package_dir):
    __path__ = [package_dir]

# Import and re-export the core scheduler implementation
try:
    core = import_module('scheduler.core')
    McOSScheduler = core.McOSScheduler
except Exception:
    # fallback: if package isn't available, try to import local implementation
    try:
        local = import_module('src.scheduler')
        McOSScheduler = getattr(local, 'McOSScheduler')
    except Exception:
        McOSScheduler = None