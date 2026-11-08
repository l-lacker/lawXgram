#!/usr/bin/env python3
"""Checks of built Neogram APKs (stdlib only).

    python tools/apkcheck.py signature <apk> [--cert <file.der|file.pem>]

`signature` reads the first certificate of the APK Signature Scheme v2 block the same way the
native check does (fork/jni/colorado/cert.cpp) and prints its size, CRC-32 and SHA-256. With
--cert (e.g. from `keytool -exportcert -rfc`) it fails unless the APK carries that certificate,
which is what the release build compiled into the native check.
"""

import argparse
import base64
import hashlib
import struct
import sys
import zlib
from pathlib import Path
from typing import Optional

EOCD_MAGIC = 0x06054B50
SIGNING_BLOCK_MAGIC = b"APK Sig Block 42"
V2_BLOCK_ID = 0x7109871A


class ApkError(Exception):
    pass


def first_v2_certificate(data: bytes) -> bytes:
    """DER of the first certificate of the first signer in the v2 signature block."""
    eocd = None
    for comment in range(0, min(0xFFFF, len(data) - 22) + 1):
        pos = len(data) - 22 - comment
        if struct.unpack_from("<I", data, pos)[0] == EOCD_MAGIC and struct.unpack_from("<H", data, pos + 20)[0] == comment:
            eocd = pos
            break
    if eocd is None:
        raise ApkError("no end of central directory: not a zip")
    central_dir = struct.unpack_from("<I", data, eocd + 16)[0]
    if data[central_dir - 16:central_dir] != SIGNING_BLOCK_MAGIC:
        raise ApkError("no APK signing block (v1-only signature?)")
    size = struct.unpack_from("<Q", data, central_dir - 24)[0]
    start = central_dir - size - 8
    if struct.unpack_from("<Q", data, start)[0] != size:
        raise ApkError("broken APK signing block")
    pos = start + 8
    end = central_dir - 24
    certificate = None
    while pos < end:
        length = struct.unpack_from("<Q", data, pos)[0]
        block_id = struct.unpack_from("<I", data, pos + 8)[0]
        if block_id == V2_BLOCK_ID:
            if certificate is not None:
                raise ApkError("several v2 blocks")
            p = pos + 12
            p += 4  # signer sequence length
            p += 4  # first signer length
            p += 4  # signed data length
            digests = struct.unpack_from("<I", data, p)[0]
            p += 4 + digests
            p += 4  # certificate sequence length
            cert_len = struct.unpack_from("<I", data, p)[0]
            certificate = data[p + 4:p + 4 + cert_len]
        pos += 8 + length
    if certificate is None:
        raise ApkError("no v2 signature block")
    return certificate


def read_certificate_file(path: Path) -> bytes:
    raw = path.read_bytes()
    if b"-----BEGIN CERTIFICATE-----" in raw:
        body = raw.split(b"-----BEGIN CERTIFICATE-----", 1)[1].split(b"-----END CERTIFICATE-----", 1)[0]
        return base64.b64decode(b"".join(body.split()))
    return raw


def describe(cert: bytes) -> str:
    return f"size {len(cert)} crc32 {zlib.crc32(cert) & 0xFFFFFFFF} sha256 {hashlib.sha256(cert).hexdigest()}"


def cmd_signature(args: argparse.Namespace) -> int:
    expected: Optional[bytes] = read_certificate_file(Path(args.cert)) if args.cert else None
    failed = False
    for apk in args.apk:
        try:
            cert = first_v2_certificate(Path(apk).read_bytes())
        except (ApkError, struct.error, OSError) as e:
            print(f"{apk}: {e}")
            failed = True
            continue
        print(f"{apk}: {describe(cert)}")
        if expected is not None and cert != expected:
            print(f"{apk}: does not carry the expected certificate ({describe(expected)})")
            failed = True
    return 1 if failed else 0


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    sub = parser.add_subparsers(dest="command", required=True)
    signature = sub.add_parser("signature", help="certificate of the APK v2 signature block")
    signature.add_argument("apk", nargs="+")
    signature.add_argument("--cert", help="expected certificate, DER or PEM")
    signature.set_defaults(func=cmd_signature)
    args = parser.parse_args(argv)
    return args.func(args)


if __name__ == "__main__":
    sys.exit(main())
