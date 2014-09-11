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

* Developed for Ubuntu 12.04, use others cautiously, but it should work fine
    * For EC2, I used ami-0d9c9f64
* Java, Scala
    * `apt-get install -y openjdk-7-jdk scala`
* sbt
    * `wget http://repo.scala-sbt.org/scalasbt/sbt-native-packages/org/scala-sbt/sbt/0.13.0/sbt.deb --no-verbose`
    * `dpkg -i sbt.deb`

## Build and Run

To build, run sbt from the root level. `sbt assembly` builds a library

