#!/bin/bash
#
# Automatically accept keys from all nodes.

for ((i=1; i<=34; i++)); do
    ssh-keyscan -t rsa "10.0.2.$i" >> ~/.ssh/known_hosts
    ssh-keyscan -t rsa "b$i" >> ~/.ssh/known_hosts
done
