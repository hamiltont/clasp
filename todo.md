# Todo.

## High priority.
+ Shutting down.
  + Update termination conditions to catch and graceful term even if someone
  sends us signal 15 via the kill command
  + Upon shutdown request, system waits 10 seconds then requests a non-graceful
  termination. However, while waiting for user input the system could
  successfully terminate, in which case the prompt should be abandoned to allow
  the system to finish termination
  + When a node has a java process running on it, Clasp won't fully terminate.
+ logstash
+ Look at process management library.

## Medium priority.
+ Implement emulator hibernation.
+ Delete all sdcard images on shutdown.
  Make this an option.
+ Add an option to block until all tasks are complete.
+ Add the ability to obtain many different device configurations,
  as specified as parameters to the Clasp system.
  This can involve asking Clasp, for example, to
  run tasks across a full factorial or LHS of configuration options.
+ Add the option to return logcat output of emulators.
+ Network traffic redirection through tor.

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
+ If you use netcat to listen on an arbitrary socket, and then direct Android's
  radio socket (an emulator command line parameter) to talk on that socket, you
  start getting some protocol for communication. There are a number of
  recognizable messages if you do something like send an SMS message from the
  device. 

  I'm wondering if that protocol is the one defined in this document:
  http://m10.home.xs4all.nl/mac/downloads/3GPP-27007-630.pdf

  A lot of the commands in that document look familiar, I may have seen them when
  I was playing with this radio thing. If this is the right protocol, perhaps we
  can find a library or create one that allows us to read/write to this protocol
  and interface with the radio chip on Android devices in a more comprehensive
  manner. 
+ Fix command watcher...
+ (Hamilton) Create logic to automatically inject root CA into emulator cacerts file, 
  add options for redirecting traffic to a proxy that now has the ability to MITM the 
  emulator. See http://intrepidusgroup.com/insight/2011/08/setting-up-a-persistent-trusted-ca-in-an-android-emulator/
  and http://www.floyd.ch/?p=244 and http://www.portswigger.net/burp/downloadfree.html
  and https://github.com/wuntee/androidAuditTools
+ (Hamilton) Create 'main' script that allows directly launching the development
  attack code on local computer. Potentially allow direct launching of unmanaged 
  emulators and then use attack command to control these emulators after they have 
  been created (e.g. a scripting tool) 

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
+ Limit Java heap size with sbt stage.

# Noah
+ Rebooting
  + Add the functionality within EmulatorActor to restart an emulator.
  + Once we have rebooting, add a flag to wipe the device when
    rebooting, and test this thoroughly.
    I've had issues wiping an entire device before.
    Reset system and user information.
