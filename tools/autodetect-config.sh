#!/bin/bash
#
# autodetect.sh
# Automatically detects path to ANDROID_HOME and writes them to
# `application.conf`.
#
# TODO Make this target /bin/sh to be more universal

CONF_DIR=src
die () { echo $@; exit 42; }

write_conf () {
  local ANDROID_HOME=$1 # Does not have trailing backslash.
  sed "s|##ANDROID_ROOT##|\"$ANDROID_HOME/\"|g" \
    $CONF_DIR/application.conf.example \
    > $CONF_DIR/application.conf
  exit 0
}

# 1. Check ANDROID_HOME.
[[ $ANDROID_HOME ]] && write_conf $ANDROID_HOME

# 2. Check `which` and trim.
ANDROID=$(which android)
[[ $ANDROID ]] && write_conf ${ANDROID%/*/*}

die "Unable to find Android SDK."
