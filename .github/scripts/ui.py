#!/usr/bin/env python3
"""Tiny UI Automator helper for shell-level emulator checks.

ui.py tap <dump.xml> <text-or-content-desc>   -> prints "x y" of the first matching node centre
ui.py has <dump.xml> <text-or-content-desc>   -> exit 0 if any node matches
"""
import re, sys
import xml.etree.ElementTree as ET

mode, path, needle = sys.argv[1], sys.argv[2], sys.argv[3]
root = ET.parse(path).getroot()
for node in root.iter("node"):
    if needle in (node.get("text"), node.get("content-desc")):
        if mode == "has":
            sys.exit(0)
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
        print((x1 + x2) // 2, (y1 + y2) // 2)
        sys.exit(0)
sys.exit(1)
