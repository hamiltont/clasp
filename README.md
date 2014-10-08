# Clasp

## Dependencies

**Both Master/Worker**

* Ubuntu 14.04
    * For EC2, I used ami-864d84ee
    * Other OS'es should work fine, but this was the target
* Java 7, Scala
    * `sudo apt-get install -y openjdk-7-jdk scala`
    * If you already had java6 installed, you need to use `sudo update-alternatives --config java` to set the version to 7
* sbt
    * `wget http://repo.scala-sbt.org/scalasbt/sbt-native-packages/org/scala-sbt/sbt/0.13.0/sbt.deb --no-verbose`
    * `dpkg -i sbt.deb`
    * **NOTE:** sbt seems to be able to download and install the scala version it needs for our project, which is slick. Rely on this, and use *sbt console* or *sbt consoleQuick* if you need to run scala console directly
    * The time on master and workers needs to be (roughly e.g. within a second) sychronized. The easiest method is to manually run NTP once a day: `sudo ntpdate ntp.ubuntu.com` (if you're using a VT network, you have to use their NTP servers e.g. `sudo ntpdate ntp-1.vt.edu`)

**Master**
* Passwordless SSH from the launch machine to all other machines
    * If you only have one machine, then your `localhost` will double as master and worker, and `ssh localhost` needs to work
    * `ssh-keygen -t rsa -P '' -f ~/.ssh/id_rsa`
    * `cat ~/.ssh/id_rsa.pub >> ~/.ssh/authorized_keys`
* node package manager, to install and run web site
    * `sudo apt-get install -y npm`

**Workers**
* Android SDK on worker machines
    * Basically, this involves running `wget http://dl.google.com/android/android-sdk_r23.0.2-linux.tgz` then `tar xvzf android-sdk_r23.0.2-linux.tgz` then finding the `android` binary and running 
    `android update sdk --all --no-ui` (you may want to use `yes | <...>` if you don't want to stick around)
    * You need to `sudo apt-get install -y ia32-libs` on any 64-bit machines to get things like adb to run. Yes, it's annoying
    * Xvfb, x11vnc on Linux workers if you want to use remote VNC
    * Just use `sudo apt-get install -y xvfb x11vnc`
    * If your worker is on the ataack cloud, these by default run ubuntu 12.04 with a local apt mirror. Our local mirror is limited, so you'll need to replace `/etc/apt/sources.list` with the default `sources.list` before you can install ia32-libs. Use `http://repogen.simplylinux.ch/generate.php` to get the default easily, or just copy it from b17
* Virtualization Support (critical). See notes below

## Notes on Hardware Virtualization

Intel's VT-x or AMD's SVM is critical to running fast emulators on all 
worker nodes. This only works with intel x86 based emulators, and the 
process is different if the host OS is linux or not-linux. These docs
are mostly for linux, for non-linux you need to use HAXM. The only 
way to 100% determine if you have turned this on correctly is to start
the emulator with the `-verbose` flag and grep for KVM (if linux) or 
HAX (if non-linux). 

The KVM message is either a success like so: 

     emulator: KVM mode auto-enabled!

Or an error message like so: 

     emulator: KVM device file is not readable for this user.

Use `egrep -c '(vmx|svm)' /proc/cpuinfo` to check if this is supported 
by the CPU. Being supported does not mean it's enabled, just supported. 
It must be enabled in the BIOS, and is normally termed something like 
Virtualization Technology (e.g. VT). If VT-x is disabled in BIOS on an 
ATAACK node you need to use the iDRAC console to enable it. If it's 
disabled on a cloud provider (EC2, openstack, etc) then you're stuck 
using the non-accelerated mode. 

You also need to see if it's enabled in the host OS. Install 
cpu-checker with `sudo apt-get install -y cpu-checker` and run 
`sudo /usr/sbin/kvm-ok` to check. 
You may need to load the KVM kernel module with `sudo modprobe kvm_intel`. 
Being enabled and having the kernel module loaded is *still* not a guarantee. 
For Linux, you need to ensure that your user has proper permissions on 
`/dev/kvm`. The fastest way to 100% ensure this is to do these steps (this
actually does a lot more than is needed, but it works): 

    $ sudo apt-get install -y qemu-kvm libvirt-bin ubuntu-vm-builder bridge-utils
    $ sudo adduser your_user_name kvm
    $ sudo adduser your_user_name libvirtd
    # now log out and log back in

For more info, see [this](https://software.intel.com/en-us/blogs/2012/03/12/how-to-start-intel-hardware-assisted-virtualization-hypervisor-on-linux-to-speed-up-intel-android-x86-gingerbread-emulator). Also, [this site](intel.com/software/android) 
is a good resource. 

## Notes on Host GPU

The option "Use Host GPU" should probably be called "Use Host OpenGL 
Implementation" instead. Even if you don't have a graphics card, this 
should fallback to a host CPU-based OpenGL impl. Main challenge here is that 
it's really hard to debug and I don't know if anything is even printed to 
the log to indicate success/failure of attemptint to use host GPU. It's also 
mutually exclusive with the snapshot option, you cannot use both. Note that 
boot delay is tiny when you get KVM working properly (like 10-20 seconds) so 
the loss of snapshots doesn't mean much for reducing boot time if you are using
KVM. 

As far as I can tell with `sudo lshw -C display`, the attack nodes have
Matrox MGA G200eW WPCM450 graphics cards and should therefore be 
able to support the "use host GPU" option as long as OpenGL is working 
properly. This may mean that you need to link libGL into the Android 
SDK as shown [here](http://stackoverflow.com/a/24978664/119592). 

There is also apparently a "hardware acceleration" that is specific to 
GPU operations. I am not yet familiar with what this is or how well it 
works. Potentially more info [here](http://www.binarytides.com/linux-get-gpu-information/)

## Build and Run Methods

### SBT Run

Use `sbt run` from the project root. Project will compile and run

You can pass arguments using `sbt "run --client"`

### Run and View Typesafe Console

**Note: doesn't work anymore, use typesafe activator instead**

Use `sbt atmos:run`. Project will compile and run, and will output the 
port where you can access the 
[Typesafe Console](http://typesafe.com/platform/runtime/console), which 
the image below shows is pretty slick (mem/cpu/messages sent/etc)

<img src="http://i.imgur.com/QKmm2Bz.png" style="width:400px">

### Fat Jar

This is a Jar that has all dependencies baked in, so it's large in size but guaranteed to run. Run `sbt assembly` from
the project root, and find the Jar inside `target/scala-<version>/` (e.g. `target/scala-2.10/clasp-0.0.1.jar`). 

You can run using `java -jar clasp-0.0.1.jar`, including passing additional flags (e.g. `java -jar clasp-0.0.1.jar --help`)

To create the fat jar, we use [sbt assembly](https://github.com/sbt/sbt-assembly)

### Skinny Jar and Target Script

Use `sbt stage` from the project root to compile a jar without merging in all the dependencies. There
will be a bash script output as `target/start` that will properly setup the classpath and then launch 
this jar for you. Run using `target/start --help`

This is provided by the [sbt-start-script plugin](https://github.com/sbt/sbt-start-script)

### Unit Testing

Use `sbt test` to run any unit tests found in the project

## Android SDK Location

Update the file `src/application.conf.example` to point to your SDK location

## Development

In the scala community, version numbers are huge! We use SBT 0.13, Scala 2.10.4, and Akka 2.3.6

### Using ScalaIDE for Eclipse

1. Download from [here](http://scala-ide.org/)
2. Run `sbt eclipse` to create `.project` and `.classpath` files
3. In eclipse, use import existing project

You may need to modify the build path to remove `src/main` and `src/test` as some eclipse versions 
can't handle nested source folders. These are empty anyway. 

Also, for changes to `application.conf` and other configuration files
you should run `sbt clean` instead of relying on eclipse's clean or trying to just
rebuild. Eclipse doesn't seem to detect those as source files and will not copy
them to target if they already exist, even if you've made modifications. 

### Directory structure
 * `android-config` - Android project we build+install on each emulator
 * `attic` - Old but interesting files
 * `examples`
    * `antimalware` - Dynamic Android malware analysis.
    * `app-tester` - APK tester across many configurations. 
 * `project` - SBT build files (replaces `build.sbt`)
 * `src` - Clasp source.
 * `test` - Clasp tests.
 * `tools`
    * `output-parser`
    * `autoaccept-keys.sh` - Autoaccept SSH keys.
    * `autodetect-config.sh` - Autodetect and populate Clasp configuration.
    * `gather-logs.sh`
    * `log-info.py` - Obtain heartbeat statistics from logs.
    * `resources.py` - Profile process resource usage.
    * `wipe-logs.sh` - Wipe logs from the notes.
 * `www` - Web application (ClaspWeb).
    * Dashboard
    * Configuration documentation and ScalaDoc

## Website and Documentation

To turn on the website, run these:

```
$ sudo apt-get install nodejs npm
$ cd www
$ npm install
$ nodejs app.js
```

## Options

*As of Sept 2014*

```
Usage: clasp [-c|--client] [-i|--ip <external ip>] [-m|--mip <master ip>] [-w|--workers <number>]

By default clasp runs as though it was a server with only
the local node. This makes it easier for people running in
a non-distributed manner. If you use sbt, then to run a
client use sbt "run --client". To run a whole system you
need a server running on the main node and then clients on
all other nodes

  -c, --client                 Should this run as a client instance
  -i, --ip  <arg>              Informs Clasp of the IP address it should bind to.
                               This should be reachable by the master and by all
                               other clients in the system.
                               (default = 172.30.0.153)
  -l, --local                  Indicates that you are running Clasp on only one
                               computer, instead of the (more typical) distribute
                               system. If ip, pool, or user were not explicitely
                               provided, this wil update them. --ip will become
                               127.0.0.1, user will be the current user, pool to
                               be 127.0.0.1. Currently forces emulators to run in
                               non-headless mode
  -m, --mip  <arg>             The master ip address. Only used with --client, an
                               required for clients
  -n, --num-emulators  <arg>   The number of emulators to start on each node.
                               (default = 1)
  -p, --pool  <arg>            Override the worker pool provided in client.conf
                               file by providing a comma-separated list of IP
                               addresses e.g. [--pool "10.0.2.1,10.0.2.2"].
                               Workers will be launched in the order given. No
                               spaces are allowed after commas
  -u, --user  <arg>            The username that clasp should use when SSHing int
                               worker systems (default = clasp)
  -w, --workers  <arg>         The number of worker clients Clasp should start by
                               default. This number can grow or shrink dynamicall
                               as the system runs. All clients are picked from th
                               pool of IP addresses inside client.conf
                               (default = 3)
      --help                   Show help message
      --version                Show version of this program
```

## VNC Support

We support viewing and controlling emulators using VNC. This is exposed natively 
in the dashboard, or you can connect to a single emulator using your VNC viewer
of choice. 

Each emulator is launched as so: 

```bash
$ # Create virtual framebuffer of necessary size
$ Xvfb :5 -screen 0 1024x768x16 &
$ # Launch desired emulator
$ DISPLAY=:5 emulator 
$ # Convert X11 into VNC server (Enables any VNC connection)
$ x11vnc -display :5 -bg -nopw -listen localhost -xkb
$ # Use websockify to proxy TCP port (enables noVNC connection)
$ noVNC/utils/launch.sh --vnc localhost:5901
```

Both `Xvfb` and `x11vnc` are supported natively on Ubuntu and Mac OSX