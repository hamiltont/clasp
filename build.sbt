import com.typesafe.sbt.SbtStartScript             // Imports xsbt
import AssemblyKeys._                              // Imports sbt-assembly keys

assemblySettings       // Appends sbt-assembly settings to build settings

seq(SbtStartScript.startScriptForClassesSettings: _*)

name := "Clasp"        // Project name

jarName in assembly := "Clasp-Assembly.jar"    // Name of fat jar built by sbt-assembly

test in assembly := {}                         // Informs sbt-assembly to not run tests 
                                               // creating the fat jar

version := "1.0"

scalaVersion := "2.10.1"

maxErrors := 5

scalacOptions ++= Seq("-feature", "-unchecked", "-deprecation")

scalaSource in Compile <<= baseDirectory(_ / "src")

scalaSource in Test <<= baseDirectory(_ / "test")

// Read `application.conf` from `src`.
resourceDirectory in Test <<= baseDirectory(_ / "src")

resourceDirectory in Compile <<= baseDirectory(_ / "src")

testOptions += Tests.Argument(TestFrameworks.JUnit, "-v", "-a", "-n")

// TODO: Get sigar working.
// libraryDependencies += "org.fusesource" % "sigar" % "1.6.4"
// unmanagedJars in Compile +=
//    file("lib/hyperic-sigar-1.6.4/sigar-bin/lib/sigar.jar")

unmanagedJars in Compile ++= Seq(
  file("lib/akka-2.1.0/akka/slf4j-api-1.7.2.jar"),
  file("lib/akka-2.1.0/akka/akka-actor_2.10-2.1.0.jar"),
  file("lib/akka-2.1.0/akka/config-1.0.0.jar"),
  file("lib/akka-2.1.0/akka/akka-remote_2.10-2.1.0.jar"),
  file("lib/akka-2.1.0/akka/protobuf-java-2.4.1.jar"),
  file("lib/akka-2.1.0/akka/netty-3.5.8.Final.jar"),
  file("lib/commons-net-3.2/commons-net-3.2.jar"),
  file("lib/commons-net-3.2/commons-net-3.2.jar"),
  file("lib/logback-1.0.9/logback-classic-1.0.9.jar"),
  file("lib/logback-1.0.9/logback-core-1.0.9.jar")
)

unmanagedJars in Test ++= Seq(
  file("lib/akka-2.1.0/akka/config-1.0.0.jar"),
  file("lib/akka-2.1.0/akka/akka-testkit_2.10-2.1.0.jar"),
  file("lib/commons-io-2.4/commons-io-2.4.jar")
)

// TODO add "com.typesafe.akka" %% "akka-remote" % "2.1.0"
// and remove other libraries

libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-actors" % "2.10.0-M6",
  "org.scalatest" % "scalatest_2.10" % "1.9.1" % "test",
  "com.novocode" % "junit-interface" % "0.10-M2" % "test",
  "org.rogach" %% "scallop" % "0.8.1"
)
