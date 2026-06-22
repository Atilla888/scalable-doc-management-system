"""Make the worker modules importable from tests/ regardless of the invocation cwd."""

import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
