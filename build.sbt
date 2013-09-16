import com.typesafe.sbt.SbtStartScript // Imports xsbt
import AssemblyKeys._ // Imports sbt-assembly keys

assemblySettings // Appends sbt-assembly settings to build settings

seq(SbtStartScript.startScriptForClassesSettings: _*)

name := "Clasp" // Project name

jarName in assembly := "Clasp-Assembly.jar" // Name of fat jar built by sbt-assembly

test in assembly := {} // Don't run tests when creating the fat jar

version := "1.0"

scalaVersion := "2.10.1"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

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
  file("lib/commons-net-3.2/commons-net-3.2.jar")            // For proxy_telnet
)

unmanagedJars in Test ++= Seq(
  file("lib/akka-2.1.0/akka/config-1.0.0.jar"),
  file("lib/akka-2.1.0/akka/akka-testkit_2.10-2.1.0.jar"),
  file("lib/commons-io-2.4/commons-io-2.4.jar")
)

libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-actors" % "2.10.0-M6",
  "org.scalatest" % "scalatest_2.10" % "1.9.1" % "test",
  "com.novocode" % "junit-interface" % "0.10-M2" % "test",
  "org.rogach" %% "scallop" % "0.8.1",
  "com.typesafe.akka" %% "akka-actor" % "2.1.2",
  "com.typesafe.akka" %% "akka-remote" % "2.1.2",
  "com.typesafe.akka" %% "akka-slf4j" % "2.1.2",
  "com.typesafe.akka" %% "akka-testkit" % "2.1.2",
  "ch.qos.logback" % "logback-classic" % "1.0.9"
)
