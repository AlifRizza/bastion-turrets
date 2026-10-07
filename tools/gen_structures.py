#!/usr/bin/env python3
"""GameTest structure templates (data/bastion/structures): closed stone boxes, so turrets in one test can never see
mobs in a neighbouring one (GameTest places tests side by side and leaves them standing).

    python3 tools/gen_structures.py
"""
import gzip
import struct
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/data/bastion/structures"
DATA_VERSION = 3465  # 1.20.1


def _str(s):
    b = s.encode()
    return struct.pack(">H", len(b)) + b


def _tag(tag_type, name, payload):
    return bytes([tag_type]) + _str(name) + payload


def _int(v):
    return struct.pack(">i", v)


def _list(element_type, items):
    return bytes([element_type]) + struct.pack(">i", len(items)) + b"".join(items)


def _compound(entries):
    return b"".join(entries) + b"\x00"


def stone_box(size):
    """Hollow box: every block on the outer shell is stone."""
    sx, sy, sz = size
    blocks = []
    for x in range(sx):
        for y in range(sy):
            for z in range(sz):
                if x in (0, sx - 1) or y in (0, sy - 1) or z in (0, sz - 1):
                    pos = _list(3, [_int(x), _int(y), _int(z)])
                    blocks.append(_compound([_tag(9, "pos", pos), _tag(3, "state", _int(1))]))
    palette = _list(10, [_compound([_tag(8, "Name", _str("minecraft:air"))]), _compound([_tag(8, "Name", _str("minecraft:stone"))])])
    root = _compound([
        _tag(3, "DataVersion", _int(DATA_VERSION)),
        _tag(9, "size", _list(3, [_int(v) for v in size])),
        _tag(9, "palette", palette),
        _tag(9, "blocks", _list(10, blocks)),
        _tag(9, "entities", _list(10, [])),
    ])
    return gzip.compress(_tag(10, "", root))


TEMPLATES = {
    "arena": (13, 7, 7),   # 11 x 5 x 5 inside: hitscan weapons
    "range": (26, 12, 13),  # 24 x 10 x 11 inside: missiles climb before they dive
    "pit": (26, 32, 13),    # 24 x 30 x 11 inside: mortar shells arc ~22 blocks up
}

if __name__ == "__main__":
    for name, size in TEMPLATES.items():
        (OUT / f"{name}.nbt").write_bytes(stone_box(size))
        print("wrote", name, size)
