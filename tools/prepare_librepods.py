#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Build/check the GPL LibrePods variant with connected non-Apple headphone support."""
import argparse
from pathlib import Path
from build_librepods import build
if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--offline',action='store_true');parser.add_argument('--sdk-cache',type=Path)
    args=parser.parse_args();build(offline=args.offline,sdk_cache=args.sdk_cache)
