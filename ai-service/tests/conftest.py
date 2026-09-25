"""pytest 的公共前置。

把 ai-service 根目录加进 ``sys.path``，这样测试文件里可以直接
``import schema`` / ``import llm_client``，不需要把整个目录做成一个包、
也不需要在每次运行前手动设 PYTHONPATH。

（pytest 默认只会把「测试文件所在目录」加进 sys.path，也就是 tests/，
所以不加这一段的话，测试里是找不到 config.py 的。）
"""

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))
