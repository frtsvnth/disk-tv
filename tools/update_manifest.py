#!/usr/bin/env python3
"""Print update.json for the signed DiskTV.apk attached to a GitHub release."""
import argparse
import hashlib
import json
import re
import subprocess
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("apk", type=Path)
parser.add_argument("--aapt", required=True, help="Path to aapt from Android Build Tools")
args = parser.parse_args()
if args.apk.name != "DiskTV.apk":
    parser.error("The GitHub asset must be named DiskTV.apk")
package = subprocess.check_output(
    [args.aapt, "dump", "badging", str(args.apk)], text=True
).splitlines()[0]
match = re.match(
    r"package: name='ru[.]tuvio[.]tools[.]dlnaclient' versionCode='([0-9]+)' "
    r"versionName='([0-9]{1,8}[.][0-9]{1,8}[.][0-9]{1,8})'",
    package,
)
if match is None:
    parser.error("Unexpected APK package or version")
code, version = match.groups()
data = args.apk.read_bytes()
if not 0 < int(code) <= 2147483647 or not 0 < len(data) <= 64 * 1024 * 1024:
    parser.error("APK exceeds updater version or size limits")
print(json.dumps({
    "versionCode": int(code),
    "versionName": version,
    "apkUrl": f"https://github.com/frtsvnth/disk-tv/releases/download/v{version}/DiskTV.apk",
    "size": len(data),
    "sha256": hashlib.sha256(data).hexdigest(),
}, indent=2))
