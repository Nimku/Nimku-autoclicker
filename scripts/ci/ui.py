#!/usr/bin/env python3
"""Small adb UI helper for the emulator smoke test.

Commands:
  ui.py dump <name>            print the UI tree (texts, ids, bounds), the visible windows and a small
                               base64 JPEG screenshot (between SHOT_BEGIN/SHOT_END markers) into the log
  ui.py tap <query> [...]      tap the first node matching one of the queries, retrying for a few seconds.
                               query: id:<resource id suffix> | text:<text> | textc:<text part> | desc:<content desc>
  ui.py taprel <x%> <y%>       tap at a position relative to the screen size
  ui.py has <query>            exit 0 if a node matches
"""
import base64
import io
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET


def adb(*args, check=False, binary=False, timeout=60):
    res = subprocess.run(["adb", *args], capture_output=True, timeout=timeout)
    if check and res.returncode != 0:
        raise RuntimeError(f"adb {' '.join(args)} failed: {res.stderr.decode(errors='replace')}")
    return res.stdout if binary else res.stdout.decode(errors="replace")


def ui_tree():
    for _ in range(4):
        adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
        out = adb("exec-out", "cat", "/sdcard/ui.xml")
        start = out.find("<?xml")
        end = out.rfind("</hierarchy>")
        if start != -1 and end != -1:
            try:
                return ET.fromstring(out[start:end + len("</hierarchy>")])
            except ET.ParseError:
                pass
        time.sleep(1.5)
    return None


def nodes(root):
    if root is None:
        return []
    return [n for n in root.iter("node")]


def center(node):
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
    if not m:
        return None
    x1, y1, x2, y2 = map(int, m.groups())
    return (x1 + x2) // 2, (y1 + y2) // 2


def matches(node, query):
    kind, _, value = query.partition(":")
    if kind == "id":
        return node.get("resource-id", "").endswith(":id/" + value)
    if kind == "text":
        return node.get("text", "").strip().lower() == value.lower()
    if kind == "textc":
        return value.lower() in node.get("text", "").lower()
    if kind == "desc":
        return value.lower() in node.get("content-desc", "").lower()
    return False


def find(queries, tries=6):
    """Find the first node matching one of the queries. A "#N" suffix selects the Nth match (1-based)."""
    for _ in range(tries):
        all_nodes = nodes(ui_tree())
        for q in queries:
            base, _, idx = q.partition("#")
            wanted = int(idx) if idx else 1
            found = [n for n in all_nodes if matches(n, base) and center(n)]
            if len(found) >= wanted:
                return found[wanted - 1], q
        time.sleep(1.5)
    return None, None


def screenshot_b64(width=360):
    png = adb("exec-out", "screencap", "-p", binary=True)
    if not png:
        return None
    try:
        from PIL import Image
        img = Image.open(io.BytesIO(png)).convert("RGB")
        ratio = width / img.width
        img = img.resize((width, int(img.height * ratio)))
        buf = io.BytesIO()
        img.save(buf, "JPEG", quality=45)
        return base64.b64encode(buf.getvalue()).decode()
    except Exception as ex:  # noqa: BLE001
        print(f"(screenshot conversion failed: {ex})")
        return None


def window_frames():
    """Frames of the app windows (activities, dialogs and overlays), from dumpsys window."""
    out = adb("shell", "dumpsys window windows")
    frames = {}
    order = []
    current = None
    for line in out.splitlines():
        m = re.search(r"Window #\d+ Window\{([0-9a-f]+) u0 ([^}]*)\}", line)
        if m:
            current = (m.group(1), m.group(2)) if "com.nimku" in m.group(2) else None
            if current:
                order.append(current)
            continue
        if not current:
            continue
        # Real frame on screen (preferred): "frame=[l,t][r,b]" or "mFrame=[l,t][r,b]"
        f = re.search(r"\b(?:m)?[Ff]rame=\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]", line)
        if f:
            frames[current] = ("frame", tuple(map(int, f.groups())))
            continue
        # Fallback, layout attributes: mAttrs={(x,y)(wxh) gr=TOP START ...
        a = re.search(r"mAttrs=\{\((-?\d+),(-?\d+)\)\((\d+|fill|wrap)x(\d+|fill|wrap)\)", line)
        if a and a.group(3).isdigit() and a.group(4).isdigit() and current not in frames:
            x, y, w, h = int(a.group(1)), int(a.group(2)), int(a.group(3)), int(a.group(4))
            frames[current] = ("attrs", (x, y, x + w, y + h))
    return [(title, frames[(h, title)][1]) for h, title in order if (h, title) in frames]


def dump(name, tree=True):
    print(f"\n========== UI DUMP: {name} ==========")
    # Screenshot first: the UI tree dump below connects UiAutomation, which suspends the app's
    # accessibility service (and so its floating menu) while it runs.
    shot = screenshot_b64()
    focus = adb("shell", "dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'").strip()
    print(focus)
    windows = adb("shell", "dumpsys window windows | grep -E 'Window #[0-9]+ Window\\{'").strip()
    print("Windows:\n" + windows)
    overlays = adb("shell", "dumpsys window windows | grep -E -A40 'Window #[0-9]+ Window\\{[0-9a-f]+ u0 com.nimku' "
                            "| grep -E 'Window #|mFrame=|ty=|mViewVisibility|isVisible'").strip()
    print("App windows:\n" + overlays)
    print("App window frames: " + "; ".join(f"{t} {f}" for t, f in window_frames()))
    root = ui_tree() if tree else None
    if root is None:
        print("(UI tree not dumped)")
    for n in nodes(root):
        text = n.get("text", "")
        desc = n.get("content-desc", "")
        rid = n.get("resource-id", "").split(":id/")[-1]
        if not (text or desc or rid):
            continue
        flags = "C" if n.get("clickable") == "true" else "-"
        flags += "E" if n.get("enabled") == "true" else "d"
        cls = n.get("class", "").split(".")[-1]
        print(f"  [{flags}] {cls:<18} id={rid:<32} text={text!r:<40} desc={desc!r} {n.get('bounds')}")
    if shot:
        print(f"SHOT_BEGIN {name}")
        for i in range(0, len(shot), 4000):
            print(shot[i:i + 4000])
        print(f"SHOT_END {name}")
    print(f"========== END UI DUMP: {name} ==========\n", flush=True)


def main():
    cmd = sys.argv[1]
    if cmd == "dump":
        dump(sys.argv[2])
    elif cmd == "shot":
        dump(sys.argv[2], tree=False)
    elif cmd == "tapwin":
        # tapwin <window title part> <x fraction> <y fraction> [index]: tap inside an app window (e.g. the overlay menu)
        part, fx, fy = sys.argv[2], float(sys.argv[3]), float(sys.argv[4])
        idx = int(sys.argv[5]) if len(sys.argv) > 5 else 0
        frames = window_frames()
        overlays = [f for t, f in frames if "/" not in t]  # overlay windows have no activity name
        if part == "menu":  # the floating menu: smallest overlay window
            wins = sorted(overlays, key=lambda f: (f[2] - f[0]) * (f[3] - f[1]))
        elif part == "dialog":  # biggest overlay window
            wins = sorted(overlays, key=lambda f: -(f[2] - f[0]) * (f[3] - f[1]))
        else:
            wins = [f for t, f in frames if part in t]
        if len(wins) <= idx:
            print(f"TAPWIN FAILED: no window '{part}' (#{idx}); frames: {window_frames()}")
            sys.exit(1)
        l, t, r, b = wins[idx]
        x, y = int(l + (r - l) * fx), int(t + (b - t) * fy)
        print(f"TAPWIN {part}#{idx} frame={wins[idx]} at {x},{y}")
        adb("shell", "input", "tap", str(x), str(y))
        time.sleep(2)
    elif cmd == "tap":
        node, q = find(sys.argv[2:])
        if node is None:
            print(f"TAP FAILED: none of {sys.argv[2:]} found")
            sys.exit(1)
        x, y = center(node)
        print(f"TAP {q} at {x},{y}")
        adb("shell", "input", "tap", str(x), str(y))
        time.sleep(2)
    elif cmd == "taprow":
        # taprow <row text> <id of the button in the same row>
        row_text, button_id = sys.argv[2], sys.argv[3]
        for _ in range(6):
            all_nodes = nodes(ui_tree())
            rows = [n for n in all_nodes if matches(n, "text:" + row_text) and center(n)]
            buttons = [n for n in all_nodes if matches(n, "id:" + button_id) and center(n)]
            if rows and buttons:
                ry = center(rows[0])[1]
                best = min(buttons, key=lambda b: abs(center(b)[1] - ry))
                x, y = center(best)
                print(f"TAPROW {row_text}/{button_id} at {x},{y}")
                adb("shell", "input", "tap", str(x), str(y))
                time.sleep(2)
                return
            time.sleep(1.5)
        print(f"TAPROW FAILED: {row_text}/{button_id}")
        sys.exit(1)
    elif cmd == "tapxy":
        print(f"TAPXY at {sys.argv[2]},{sys.argv[3]}")
        adb("shell", "input", "tap", sys.argv[2], sys.argv[3])
        time.sleep(2)
    elif cmd == "taprel":
        size = adb("shell", "wm", "size").strip().split()[-1]
        w, h = map(int, size.split("x"))
        x, y = int(w * float(sys.argv[2]) / 100), int(h * float(sys.argv[3]) / 100)
        print(f"TAPREL at {x},{y}")
        adb("shell", "input", "tap", str(x), str(y))
        time.sleep(2)
    elif cmd == "has":
        node, _ = find(sys.argv[2:], tries=2)
        sys.exit(0 if node is not None else 1)


if __name__ == "__main__":
    main()
