#!/usr/bin/env python
from __future__ import print_function
import subprocess, json, sys

def get_ip_with_alias(alias):
    try:
        di_out = subprocess.Popen(["docker","inspect", alias], stdout=subprocess.PIPE).stdout.read().decode('utf-8')
        di = json.loads(di_out)
        if not di or len(di) < 1:
            return 1

        networks = di[0]["NetworkSettings"]["Networks"]

        for k in networks:
            if alias in networks[k]["Aliases"]:
                addr = networks[k]["IPAddress"]
                if addr != "":
                    print(networks[k]["IPAddress"])
                    return 0
        return 2
    except:
        return 3

if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit(1)
    else:
        sys.exit(get_ip_with_alias(sys.argv[1]))
