#!/usr/bin/env python3
"""Build the standalone Turkish LSPosed module from the CameraAI API 102 bridge.
This module contains ONLY CameraTurkishHook as an entrypoint, so it cannot
alter XCamera's v2.10 capture hooks. Never publish the signing private key.
"""
from __future__ import annotations

import argparse
import hashlib
import io
import pathlib
import struct
import zipfile

from cryptography.hazmat.primitives.asymmetric import rsa

from merge_and_sign import make_certificate, sign, verify

OLD_LABEL = "CameraAI API102 Bridge (build component)"
NEW_LABEL = "CameraAI Türkçe Kamera Dil Paketi 2.11  "
assert len(OLD_LABEL) == len(NEW_LABEL) == 40

META = {
    "META-INF/xposed/java_init.list": b"dev.cameraai.override.CameraTurkishHook\n",
    "META-INF/xposed/module.prop": (
        b"minApiVersion=102\ntargetApiVersion=102\nstaticScope=true\n"
        b"exceptionMode=protective\nautoHotReload=false\n"
    ),
    "META-INF/xposed/scope.list": b"com.android.camera\n",
}


def build(bridge: pathlib.Path, destination: pathlib.Path) -> None:
    with zipfile.ZipFile(bridge, "r") as src:
        dex = src.read("classes2.dex")
        assert b"Ldev/cameraai/override/CameraTurkishHook;" in dex
        manifest = src.read("AndroidManifest.xml")
        old = OLD_LABEL.encode("utf-16le")
        assert manifest.count(old) == 1, "Unexpected AndroidManifest label layout"
        manifest = manifest.replace(old, NEW_LABEL.encode("utf-16le"))
        arsc = src.read("resources.arsc")

    output = io.BytesIO()
    with zipfile.ZipFile(output, "w") as z:
        entries = [
            ("AndroidManifest.xml", manifest, zipfile.ZIP_DEFLATED),
            ("classes.dex", dex, zipfile.ZIP_DEFLATED),
            ("resources.arsc", arsc, zipfile.ZIP_STORED),
        ] + [(k, v, zipfile.ZIP_DEFLATED) for k, v in META.items()]
        for filename, data, compression in entries:
            item = zipfile.ZipInfo(filename, (2026, 11, 8, 0, 0, 0))
            item.compress_type = compression
            if filename == "resources.arsc":
                # Android requires the uncompressed resource table to be aligned.
                pad = (-(z.fp.tell() + 30 + len(filename.encode()) + 4)) % 4
                item.extra = struct.pack("<HH", 0xFFFE, pad) + bytes(pad)
            z.writestr(item, data)

    # Ephemeral CI signing key. Distribute this one release without promising
    # in-place upgrades from future re-signs.
    key = rsa.generate_private_key(public_exponent=65537, key_size=3072)
    cert = make_certificate(key)
    apk = sign(output.getvalue(), key, cert)
    info = verify(apk)
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_bytes(apk)
    with zipfile.ZipFile(destination) as z:
        assert z.testzip() is None
        assert z.read("META-INF/xposed/java_init.list") == META["META-INF/xposed/java_init.list"]
        assert z.read("META-INF/xposed/scope.list") == b"com.android.camera\n"
        i = z.getinfo("resources.arsc")
        assert (i.header_offset + 30 + len(i.filename.encode()) + len(i.extra)) % 4 == 0
    sha = hashlib.sha256(apk).hexdigest()
    (destination.parent / "SHA256SUMS.txt").write_text(
        f"{sha}  {destination.name}\n", encoding="utf-8"
    )
    print(f"APK: {destination} bytes={len(apk)} sha256={sha} signature={info}")


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--bridge", type=pathlib.Path, required=True)
    ap.add_argument("--output", type=pathlib.Path, required=True)
    args = ap.parse_args()
    build(args.bridge, args.output)
