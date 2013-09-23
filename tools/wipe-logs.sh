#!/bin/bash
#
# wipe-logs.sh
#
# Wipe all of clasp's logs.
#
# Brandon Amos
# 2013.09.23

function die { echo $*; exit 42; }

[[ $# != 1 ]] && die "Usage: $0 <ips file>"
IPS=$1

while read IP; do
  echo "Deleting from $IP."
  timeout -s 9 20s ssh $IP rm -rf /tmp/clasp &
done < $IPS

wait
