# Clasp

## Directory structure
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

## Dependencies

* Ubuntu 14.04
    * For EC2, I used ami-864d84ee
    * Other OS'es should work fine, but this was the target
* Java, Scala
    * `apt-get install -y openjdk-7-jdk scala`
* sbt
    * `wget http://repo.scala-sbt.org/scalasbt/sbt-native-packages/org/scala-sbt/sbt/0.13.0/sbt.deb --no-verbose`
    * `dpkg -i sbt.deb`
* Passwordless SSH from the launch machine to all other machines
    * If you only have one machine, then your `localhost` will double as master and worker, and `ssh localhost` needs to work
    * `ssh-keygen -t rsa -P '' -f ~/.ssh/id_rsa`
    * `cat ~/.ssh/id_rsa.pub >> ~/.ssh/authorized_keys`

## Build and Run


To build and run, use `sbt run` from the project root

To create a Jar file, use `sbt assembly` from the project root, and find the result inside 
of `target/scala-<version>/` (e.g. `target/scala-2.10/clasp-0.0.1.jar`. You can use 
`java -jar clasp-0.0.1.jar` to run this file directly, including passing additional 
flags using `java -jar clasp-0.0.1.jar --help`

## Options

*Note: As of Sept 2014*

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
