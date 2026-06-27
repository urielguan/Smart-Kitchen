#!/usr/bin/env python3

import os
import subprocess
import sys


def main() -> int:
    if len(sys.argv) < 3:
        print("usage: launch-detached.py <log-file> <command> [args...]", file=sys.stderr)
        return 1

    log_file = sys.argv[1]
    command = sys.argv[2:]

    os.makedirs(os.path.dirname(log_file), exist_ok=True)

    with open(log_file, "ab", buffering=0) as log_handle:
        process = subprocess.Popen(
            command,
            stdin=subprocess.DEVNULL,
            stdout=log_handle,
            stderr=subprocess.STDOUT,
            start_new_session=True,
            cwd=os.getcwd(),
            env=os.environ.copy(),
        )

    print(process.pid)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
