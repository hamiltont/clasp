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

while read IP; do
  echo "Syncing $IP."
  rsync -zvr --exclude '*.img' --exclude '*.lock' \
    $IP:/tmp/clasp $LOG_DIR/$IP
  for USER_PATH in $LOG_DIR/$IP/clasp/*; do
    USER=$(basename $USER_PATH)
    mkdir -p $LOG_DIR/$USER
    mv "$LOG_DIR/$IP/clasp/$USER/nohup.$IP" "$LOG_DIR/$USER/"
  done
  rm -rf $LOG_DIR/$IP
done < $IPS

rm -rf $LOG_DIR/\*
