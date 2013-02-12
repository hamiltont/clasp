# Eclipse Setup

## Install Eclipse (Juno) addons
+ Use the integrated software installer (help->install new software) to
install the Scala IDE from
"http://download.scala-ide.org/nightly-update-juno-master-2.10.x"
+ Install Scala read eval print loop (REPL) from
"http://scala-ide.dreamhosters.com/nightly-update-worksheet-scalaide21-210/site/"

## Create a new project and link the directories
+ In an Eclipse workspace, create a new project `Clasp` and link the
`src`, `test`, and `lib` directories.
+ Right click on the `Clasp` directory and select
`Build Path`->`Add External Archives`.
+ Add `junit`, `sigar`, and other libraries.
