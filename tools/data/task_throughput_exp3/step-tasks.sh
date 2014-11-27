#!/bin/bash
#
# and then this script will launch a new emulator every 8
# minutes until it's launched 10

REST_API=$1

steps=( 10 50 100 250 500 1000 1500 2000 2500 3000 3500 4000 4500 5000 5500 6000 6500 7000 7500 8000 8500 9000 9500 10000)

for trial in {0..60}; do
    echo "Running trial $trial"
    for step in "${steps[@]}"; do
        # Used a rough linear regression and gave myself strong safety factors
        # if two task queues are triggered together they will 100% interfere
        wait=$(expr 9000 + 14 \* $step)
        wait=$(expr $wait / 1000)
        echo "Trial $trial, step $step"
        echo "Using curl $REST_API/emulators/measuretps/$step"
        curl "$REST_API/emulators/measuretps/$step"
        echo "Launch triggered, waiting $wait seconds..."
        sleep $wait
        echo "  ...finished, moving on"
    done
done