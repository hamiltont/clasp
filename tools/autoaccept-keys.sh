#!/bin/bash
#
# Automatically accept keys from all nodes.

# http://askubuntu.com/questions/123072
for ((i=1; i<=34; i++)); do
    yes yes | ssh "10.0.2.$i" "uptime";
done
