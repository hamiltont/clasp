#!/bin/bash
#
# gather-logs.sh
#
# Centralize all of clasp's logs.
#
# Brandon Amos
# 2013.09.16

LOG_DIR=logs

function die { echo $*; exit 42; }

[[ $# != 1 ]] && die "Usage: ./gather-logs.sh <ips file>"
IPS=$1

mkdir -p $LOG_DIR

while read LINE; do
  echo "Syncing $LINE."
  rsync -zvr --exclude '*.img' $LINE:/tmp/clasp $LOG_DIR/$LINE
done < $IPS
