## Getting Started
Run `tools/autodetect-config.sh` script to automate detection of
appropriate SDK binaries and configuration setup.

If `tools/autodetect-config.sh` fails, configuration has to be
done manually.
The first item to specify is the path to the appropriate SDK binaries e.g.
android, emulator, adb, etc.  Configuration is done using
[typesafe config](https://github.com/typesafehub/config),
which make it simple to define and use configuration.
Locate `$ROOT/src/application.conf.example`, copy
the example contents into `$ROOT/src/application.conf` (or anywhere else on
your build path), and modify the paths of `sdk.root` to reflect your sdk root.
