import AssemblyKeys._

assemblySettings

name := "Clasp"

jarName in assembly := "Clasp-Assembly.jar"

test in assembly := {}

version := "1.0"

scalaVersion := "2.10.0"

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

libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-actors" % "2.10.0-M6",
  "org.scalatest" % "scalatest_2.10" % "1.9.1" % "test",
  "com.novocode" % "junit-interface" % "0.10-M2" % "test"
)
