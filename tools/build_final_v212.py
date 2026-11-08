#!/usr/bin/env python3
"""Build one CameraAI + XCamera APK with XCamera hooks, 200 MP UI and Turkish UI.
Input is an authorized pre-existing merged XCamera APK; do not redistribute
third-party source as if it were CameraAI source. No changes to the camera HAL.
"""
import argparse
import hashlib
import io
import os
from pathlib import Path
import struct
import zipfile
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from merge_and_sign import make_certificate, sign, verify

EXPECTED_XCAMERA_SHA256 = "36b3f29a1707704d11dce5228b7795758c8daa0dd308bded59f6c738a087fc83"
ENTRIES = (b"dev.cameraai.override.ModernCameraAIHook\n"
           b"dev.cameraai.override.Pixel175Tracer\n"
           b"dev.cameraai.override.CameraTurkishHook\n"
           b"com.leekinn.xcamera.XCameraModule\n")

def update_binary_xml_version(xml, new="2.12"):
    """Rewrite versionName in UTF-16 Android binary XML string pool safely."""
    kind, header_size, document_size = struct.unpack_from("<HHI", xml, 0)
    assert kind == 3 and header_size == 8 and document_size == len(xml)
    base = header_size
    kind, pool_header_size, pool_size = struct.unpack_from("<HHI", xml, base)
    assert kind == 1 and pool_header_size == 28
    count, styles, flags, strings_start, styles_start = struct.unpack_from("<IIIII", xml, base + 8)
    assert styles == 0 and styles_start == 0 and (flags & 0x100) == 0, "Unsupported string pool"
    strings = []
    for i in range(count):
        offset = struct.unpack_from("<I", xml, base + 28 + i * 4)[0]
        pos = base + strings_start + offset
        length = struct.unpack_from("<H", xml, pos)[0]
        assert length < 32768
        value = xml[pos+2:pos+2+length*2].decode("utf-16le")
        assert xml[pos+2+length*2:pos+4+length*2] == b"\0\0"
        strings.append(value)
    matches = [i for i, s in enumerate(strings) if s in ("1.2", "2.3", "2.10", "2.11")]
    assert len(matches) == 1, "Expected exactly one prior versionName"
    old_version = strings[matches[0]]
    strings[matches[0]] = new
    data = bytearray()
    offsets = []
    for value in strings:
        offsets.append(len(data))
        data += struct.pack("<H", len(value))
        data += value.encode("utf-16le") + b"\0\0"
    data += bytes((-len(data)) % 4)
    new_header = bytearray(xml[base:base+28])
    struct.pack_into("<I", new_header, 4, 28 + 4*count + len(data))
    struct.pack_into("<I", new_header, 20, 28 + 4*count)
    new_pool = bytes(new_header) + struct.pack("<" + "I"*count, *offsets) + data
    result = bytearray(xml[:base] + new_pool + xml[base+pool_size:])
    struct.pack_into("<I", result, 4, len(result))
    assert new.encode("utf-16le") in result
    print("AndroidManifest versionName:", old_version, "->", new)
    return bytes(result)

def repack(base_path, bridge_path):
    with zipfile.ZipFile(base_path) as base, zipfile.ZipFile(bridge_path) as bridge:
        original_xcamera = base.read("classes.dex")
        actual = hashlib.sha256(original_xcamera).hexdigest()
        assert actual == EXPECTED_XCAMERA_SHA256, "Unrecognized XCamera binary: " + actual
        assert base.read("META-INF/xposed/scope.list").strip() == b"com.android.camera"
        modern = bridge.read("classes2.dex")
        for entry in (b"ModernCameraAIHook", b"Pixel175Tracer", b"CameraTurkishHook", b"ProcessingHint"):
            assert entry in modern, "Missing entry: " + repr(entry)
        output = io.BytesIO()
        with zipfile.ZipFile(output, "w") as target:
            for item in base.infolist():
                name = item.filename
                if name == "classes2.dex" or (
                    name.startswith("META-INF/") and name.endswith((".RSA", ".DSA", ".SF", ".MF", ".EC"))
                ):
                    continue
                data = base.read(name)
                if name == "AndroidManifest.xml":
                    data = update_binary_xml_version(data)
                if name == "META-INF/xposed/java_init.list":
                    data = ENTRIES
                info = zipfile.ZipInfo(name, item.date_time)
                info.compress_type = item.compress_type
                info.external_attr = item.external_attr
                info.create_system = item.create_system
                if name == "resources.arsc":
                    info.compress_type = zipfile.ZIP_STORED
                    padding = (-(target.fp.tell() + 30 + len(name.encode()) + 4)) % 4
                    info.extra = struct.pack("<HH", 0xfffe, padding) + bytes(padding)
                target.writestr(info, data)
            info = zipfile.ZipInfo("classes2.dex", (2026, 11, 9, 0, 0, 0))
            info.compress_type = zipfile.ZIP_STORED
            target.writestr(info, modern)
        unsigned = output.getvalue()
        with zipfile.ZipFile(io.BytesIO(unsigned)) as final:
            assert final.testzip() is None
            assert final.read("classes.dex") == original_xcamera
            assert final.read("classes2.dex") == modern
            assert final.read("META-INF/xposed/java_init.list") == ENTRIES
            assert final.read("META-INF/xposed/scope.list").strip() == b"com.android.camera"
            i = final.getinfo("resources.arsc")
            assert (i.header_offset + 30 + len(i.filename.encode()) + len(i.extra)) % 4 == 0
        return unsigned

def main():
    p = argparse.ArgumentParser()
    p.add_argument("--base", required=True, type=Path)
    p.add_argument("--bridge", required=True, type=Path)
    p.add_argument("--output", required=True, type=Path)
    p.add_argument("--keydir", type=Path, default=Path("/tmp/cameraai-v212-signing"))
    args = p.parse_args()
    args.keydir.mkdir(parents=True, exist_ok=True)
    key_path = args.keydir / "key.pem"
    cert_path = args.keydir / "cert.der"
    if key_path.exists() and cert_path.exists():
        key = serialization.load_pem_private_key(key_path.read_bytes(), password=None)
        cert = cert_path.read_bytes()
    else:
        assert not key_path.exists() and not cert_path.exists()
        key = rsa.generate_private_key(public_exponent=65537, key_size=3072)
        cert = make_certificate(key)
        key_path.write_bytes(key.private_bytes(
            serialization.Encoding.PEM,
            serialization.PrivateFormat.PKCS8,
            serialization.NoEncryption()))
        os.chmod(key_path, 0o600)
        cert_path.write_bytes(cert)
    result = sign(repack(args.base, args.bridge), key, cert)
    signature = verify(result)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_bytes(result)
    digest = hashlib.sha256(result).hexdigest()
    (args.output.parent / "SHA256SUMS.txt").write_text(
        f"{digest}  {args.output.name}\n", encoding="utf-8")
    print("APK SHA256:", digest, "signature:", signature, "bytes:", len(result))

if __name__ == "__main__":
    main()
