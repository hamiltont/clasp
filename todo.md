# Todo.

## High priority.
+ Update termination conditions to catch and graceful term even if someone
  sends us signal 15 via the kill command
+ Upon shutdown request, system waits 10 seconds then requests a
  non-graceful termination. However, while waiting for user input the system
  could successfully terminate, in which case the prompt should be abandoned to
  allow the system to finish termination

## Medium priority.
+ Implement emulator hibernation.
+ Delete all sdcard images on shutdown.

## Low priority.
+ Make script to start a virtual X11 server and VNC on any node.
+ Add the option for Clasp to create custom sdcards for emulators.
  Currently, I've hard-coded this into EmulatorBuilder to
  always build an sdcard, but this is not optimal.
  For this, you'll have to reason the most logical
  place to put this flag and code.
+ More robust tests.
+ Add `sigar` libraries when building with `sbt`.
  Reference: `http://stackoverflow.com/questions/14966414`
+ Provide an AVD manager class for utility functions - ?

## Issues.
+ If "android list targets" is empty, then creation of an emulator will 
  always fail. The failure currently happens when a new emulator is constructed, 
  but it should happen sooner and a more intelligent error message should appear. 
  When the framework starts a Node, that Node should cache the available targets
  and something should predict from the EmulatorConfig that the emulator boot
  will fail. More importantly, we can likely auto-fix the issue by calling
  'android update sdk --no-gui' on the command line.

## Module ideas.
+ SMS network modeling.
+ Sensor data.
+ GPS simulation.

# Brandon
+ Resource consumption
  + Memory of scala - Single node vs emulators.
  + Network latency - Heartbeat/1s
+ Look at process management library.
+ logstash

# Noah
+ Rebooting
  + Add the functionality within EmulatorActor to restart an emulator.
  + Once we have rebooting, add a flag to wipe the device when
    rebooting, and test this thoroughly.
    I've had issues wiping an entire device before.
