"""Repackage user-provided XCamera 1.2 with modern CameraAI DEX and sign locally.

This tool does not include or redistribute XCamera binaries.
APK Signature Scheme v2 SHA256/RSA-PKCS1-v1_5 is used and verified.
"""
import io
import os
import re
import struct
import hashlib
import zipfile
import pathlib
import argparse
import datetime
from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa, padding
from cryptography.x509.oid import NameOID

PROJECT_ROOT = pathlib.Path(__file__).resolve().parent.parent


def v2chunks(data):
    parts = []
    for i in range(0, len(data), 1024*1024):
        chunk = data[i:i+1024*1024]
        parts.append(hashlib.sha256(b'\xa5' + struct.pack('<I', len(chunk)) + chunk).digest())
    return parts


def digest_sections(a, b, c):
    parts = v2chunks(a) + v2chunks(b) + v2chunks(c)
    return hashlib.sha256(b'\x5a' + struct.pack('<I', len(parts)) + b''.join(parts)).digest()


def lp(data):
    return struct.pack('<I', len(data)) + data


def make_signer(digest, private_key, cert):
    algorithm = 0x0103
    digests = lp(lp(struct.pack('<I', algorithm) + lp(digest)))
    signed_data = digests + lp(lp(cert)) + lp(b'')
    signature = private_key.sign(signed_data, padding.PKCS1v15(), hashes.SHA256())
    signatures = lp(lp(struct.pack('<I', algorithm) + lp(signature)))
    public_key = private_key.public_key().public_bytes(
        serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)
    return lp(signed_data) + signatures + lp(public_key)


def block_for_digest(digest, key, cert):
    signers = lp(make_signer(digest, key, cert))
    value = lp(signers)
    pair = struct.pack('<Q', len(value) + 4) + struct.pack('<I', 0x7109871a) + value
    size = len(pair) + 24
    return struct.pack('<Q', size) + pair + struct.pack('<Q', size) + b'APK Sig Block 42'


def find_eocd(apk):
    i = apk.rfind(b'PK\x05\x06')
    assert i >= 0 and len(apk) - i >= 22
    comment_size = struct.unpack_from('<H', apk, i+20)[0]
    assert i+22+comment_size == len(apk)
    cd_size, cd_offset = struct.unpack_from('<II', apk, i+12)
    assert apk[cd_offset:cd_offset+4] == b'PK\x01\x02'
    assert cd_offset+cd_size == i
    return i, cd_offset


def read_lp(buf, offset):
    n = struct.unpack_from('<I', buf, offset)[0]
    return buf[offset+4:offset+4+n], offset+4+n


def verify(apk):
    eocd, cd = find_eocd(apk)
    size = struct.unpack_from('<Q', apk, cd-24)[0]
    assert apk[cd-16:cd] == b'APK Sig Block 42'
    start = cd-(size+8)
    assert struct.unpack_from('<Q', apk, start)[0] == size
    off = start+8
    pair_n = struct.unpack_from('<Q', apk, off)[0]; off += 8
    identifier = struct.unpack_from('<I', apk, off)[0]; off += 4
    assert identifier == 0x7109871a
    signers, _ = read_lp(apk[off:off+pair_n-4], 0)
    signer, _ = read_lp(signers, 0)
    signed_data, ix = read_lp(signer, 0)
    signatures, ix = read_lp(signer, ix)
    public_key, ix = read_lp(signer, ix)
    digest_records, ix = read_lp(signed_data, 0)
    digest_record, _ = read_lp(digest_records, 0)
    algorithm = struct.unpack_from('<I', digest_record, 0)[0]
    digest, _ = read_lp(digest_record, 4)
    assert algorithm == 0x0103
    certs, ix = read_lp(signed_data, ix)
    cert, _ = read_lp(certs, 0)
    signature_record, _ = read_lp(signatures, 0)
    assert struct.unpack_from('<I', signature_record, 0)[0] == algorithm
    signature, _ = read_lp(signature_record, 4)
    key = serialization.load_der_public_key(public_key)
    key.verify(signature, signed_data, padding.PKCS1v15(), hashes.SHA256())
    e = bytearray(apk[eocd:])
    struct.pack_into('<I', e, 16, start)
    observed = digest_sections(apk[:start], apk[cd:eocd], e)
    assert observed == digest, (observed.hex(), digest.hex())
    return {
        'v2': True, 'algorithm': algorithm,
        'digest': digest.hex(),
        'certificate_sha256': hashlib.sha256(cert).hexdigest()
    }


def sign(unsigned, key, certificate):
    eocd, cd = find_eocd(unsigned)
    # Signed section digest uses the original central directory offset in EOCD.
    digest = digest_sections(unsigned[:cd], unsigned[cd:eocd], unsigned[eocd:])
    signing_block = block_for_digest(digest, key, certificate)
    signed_eocd = bytearray(unsigned[eocd:])
    struct.pack_into('<I', signed_eocd, 16, cd+len(signing_block))
    signed = unsigned[:cd]+signing_block+unsigned[cd:eocd]+signed_eocd
    verify(signed)
    return signed


def make_certificate(key):
    subject = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME,
        'XCamera CameraAI User Local Build')])
    now = datetime.datetime.now(datetime.timezone.utc).replace(microsecond=0)
    cert = (x509.CertificateBuilder()
        .subject_name(subject).issuer_name(subject)
        .public_key(key.public_key()).serial_number(x509.random_serial_number())
        .not_valid_before(now).not_valid_after(now+datetime.timedelta(days=3650))
        .sign(key, hashes.SHA256()))
    return cert.public_bytes(serialization.Encoding.DER)


def repack(xcamera_path, bridge_path, version_name):
    with zipfile.ZipFile(xcamera_path) as original, zipfile.ZipFile(bridge_path) as bridge:
        modern = []
        for n in bridge.namelist():
            if re.fullmatch(r'classes(?:\d+)?\.dex', n):
                blob = bridge.read(n)
                if b'Ldev/cameraai/override/ModernCameraAIHook;' in blob:
                    modern.append(blob)
        assert len(modern) == 1, 'Exactly one modern CameraAI DEX is required'
        assert 'classes2.dex' not in original.namelist(), 'XCamera already uses classes2.dex'
        out = io.BytesIO()
        with zipfile.ZipFile(out, 'w') as dest:
            for info in original.infolist():
                name = info.filename
                if name.startswith('META-INF/') and name.endswith((
                        '.RSA', '.SF', '.MF', '.DSA', '.EC')):
                    continue
                content = original.read(name)
                if name == 'AndroidManifest.xml':
                    original_version = '1.2'.encode('utf-16le')
                    assert content.count(original_version) == 1, 'XCamera 1.2 manifest expected'
                    content = content.replace(original_version, version_name.encode('utf-16le'))
                if name == 'META-INF/xposed/java_init.list':
                    content = (b'dev.cameraai.override.ModernCameraAIHook\n'
                               b'dev.cameraai.override.Pixel175Tracer\n'
                               b'com.leekinn.xcamera.XCameraModule\n')
                zi = zipfile.ZipInfo(name, info.date_time)
                zi.compress_type = info.compress_type
                zi.external_attr = info.external_attr
                zi.create_system = info.create_system
                if name == 'resources.arsc':
                    zi.compress_type = zipfile.ZIP_STORED
                    padding_size = (-(dest.fp.tell()+30+len(name.encode())+4)) % 4
                    zi.extra = struct.pack('<HH', 0xfffe, padding_size)+b'\x00'*padding_size
                dest.writestr(zi, content)
            zi = zipfile.ZipInfo('classes2.dex', date_time=(2026, 11, 7, 0, 0, 0))
            zi.compress_type = zipfile.ZIP_STORED
            dest.writestr(zi, modern[0])
        unsigned = out.getvalue()
        with zipfile.ZipFile(io.BytesIO(unsigned)) as result:
            assert result.read('classes.dex') == original.read('classes.dex')
            assert result.read('classes2.dex') == modern[0]
            assert result.read('META-INF/xposed/java_init.list').startswith(b'dev.cameraai')
            assert result.read('META-INF/xposed/scope.list').strip() == b'com.android.camera'
            for item in result.infolist():
                if item.filename == 'resources.arsc':
                    data_start = item.header_offset+30+len(item.filename.encode())+len(item.extra)
                    assert data_start % 4 == 0
        return unsigned


def main():
    parser = argparse.ArgumentParser(description='Merge XCamera 1.2 with CameraAI API102 bridge')
    parser.add_argument('--xcamera', type=pathlib.Path, required=True)
    parser.add_argument('--bridge', type=pathlib.Path, required=True)
    parser.add_argument('--output', type=pathlib.Path, required=True)
    parser.add_argument('--version-name', default='1.5', choices=['1.5','1.6','1.7','1.8','1.9','2.0','2.1','2.2','2.3','2.4'],
                        help='Merged APK versionName')
    parser.add_argument('--keydir', type=pathlib.Path,
        default=PROJECT_ROOT/'keys', help='Private signing keys; never upload')
    args = parser.parse_args()
    if not args.xcamera.is_file() or not args.bridge.is_file():
        parser.error('Both the XCamera APK and compiled CameraAI bridge APK are required')
    keydir = args.keydir.resolve()
    keydir.mkdir(parents=True, exist_ok=True)
    key_path, cert_path = keydir/'signing-key.pem', keydir/'signing-cert.der'
    if key_path.exists() != cert_path.exists():
        parser.error('Incomplete signing key pair; restore the missing file')
    if key_path.exists():
        key = serialization.load_pem_private_key(key_path.read_bytes(), password=None)
        certificate = cert_path.read_bytes()
        print('Reusing local signing key:', keydir)
    else:
        key = rsa.generate_private_key(public_exponent=65537, key_size=3072)
        certificate = make_certificate(key)
        key_path.write_bytes(key.private_bytes(
            serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8,
            serialization.NoEncryption()))
        os.chmod(key_path, 0o600)
        cert_path.write_bytes(certificate)
        print('Created private signing key; back up', keydir)
    unsigned = repack(args.xcamera, args.bridge, args.version_name)
    signed = sign(unsigned, key, certificate)
    output = args.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(signed)
    print('Created', output, len(signed), 'SHA256', hashlib.sha256(signed).hexdigest())
    print('Verified', verify(signed))
    with zipfile.ZipFile(io.BytesIO(signed)) as z:
        assert z.testzip() is None, 'Invalid ZIP checksum'
        print('ZIP OK; LSPosed entries:')
        print(z.read('META-INF/xposed/java_init.list').decode())


if __name__ == '__main__':
    main()
