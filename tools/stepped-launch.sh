#!/bin/bash
#
# and then this script will launch a new emulator every 8
# minutes until it's launched 10

REST_API="http://10.0.2.17:8080"

for ((i=1; i<=20; i++)); do
    echo "Launching $i"
    echo "Using curl $REST_API/emulators/launch"
    curl "$REST_API/emulators/launch"
    echo "Launch triggered, waiting 8 minutes..."
    sleep 2m
    echo "Waiting 6 minutes..."
    sleep 2m
    echo "Waiting 4 minutes..."
    sleep 2m
    echo "Waiting 2 minutes..."
    sleep 2m
    echo "Awake, checking if emulators have launched..."
    echo "Using curl $REST_API/emulators"
    curl "$REST_API/emulators"
    curl "$REST_API/emulators" > "$i-emulators.out"
done
