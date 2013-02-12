# Eclipse Setup

## Install Eclipse (Juno) addons
+ Use the integrated software installer (help->install new software) to
add the Scala 2.1-M2 update site from
"http://download.scala-ide.org/sdk/e38/scala210/dev/site/"
+ Install Scala IDE, Scala Worksheet, and ScalaTest

## Create a new project and link the directories
+ In an Eclipse workspace, create a new project `Clasp` with a default `src`
directory.
+ Delete the default `src` directory and link the `src` and `test` directories.
+ Right click on the `Clasp` directory and select
`Build Path`->`Add External Archives`.
+ Add `sigar`, `scalatest`, and other needed libraries.
+ Add the main JUnit library to the build path.
