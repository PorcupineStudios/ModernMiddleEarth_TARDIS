"""A small NBT writer for structure templates (.nbt, gzipped). Values: Byte/Int/Double wrappers, str, list, dict."""
import gzip
import struct


class Byte(int):
    pass


class Int(int):
    pass


class Double(float):
    pass


def _tag_id(v):
    if isinstance(v, Byte):
        return 1
    if isinstance(v, bool):
        return 1
    if isinstance(v, (Int, int)):
        return 3
    if isinstance(v, (Double, float)):
        return 6
    if isinstance(v, str):
        return 8
    if isinstance(v, list):
        return 9
    if isinstance(v, dict):
        return 10
    raise TypeError(f"no NBT tag for {type(v)}")


def _str(s):
    b = s.encode("utf-8")
    return struct.pack(">H", len(b)) + b


def _payload(v):
    t = _tag_id(v)
    if t == 1:
        return struct.pack(">b", int(v))
    if t == 3:
        return struct.pack(">i", int(v))
    if t == 6:
        return struct.pack(">d", float(v))
    if t == 8:
        return _str(v)
    if t == 9:
        inner = _tag_id(v[0]) if v else 0
        return struct.pack(">bi", inner, len(v)) + b"".join(_payload(x) for x in v)
    out = b""
    for k, x in v.items():
        out += struct.pack(">b", _tag_id(x)) + _str(k) + _payload(x)
    return out + b"\x00"


def write(path, root):
    """Writes a gzipped NBT file whose root is the compound {@code root}."""
    data = struct.pack(">b", 10) + _str("") + _payload(root)
    with gzip.GzipFile(path, "wb", mtime=0) as f:
        f.write(data)
