import sbt._
import sbt.Keys._

object ClaspBuild extends sbt.Build {

  import Dependencies._
  import BuildSettings._
  import com.typesafe.sbt.SbtStartScript // Imports xsbt.

  // Configure prompt to show current project
  override lazy val settings = super.settings :+ {
    shellPrompt := { s => Project.extract(s).currentProject.id + " > " }
  }

  // Define our project, with basic project information and library dependencies
  lazy val project = Project("clasp", file("."))
    .settings(SbtStartScript.startScriptForClassesSettings: _*)
    .settings(basicSettings: _*)
    .settings(
      libraryDependencies ++= Seq(
        Libraries.scalaActors,
        Libraries.scalaTest,
        Libraries.junit,
        Libraries.scallop,
        Libraries.akkaActor,
        Libraries.akkaRemote,
        Libraries.akkaSlf4j,
        Libraries.akkaTestkit,
        Libraries.logback,
        Libraries.spray,
        Libraries.sprayRouting,
        Libraries.sprayJson,
        Libraries.sprayHttpx,
        Libraries.sprayClient,
        Libraries.sprayCan,
        Libraries.sprayWebsocket))
    .settings(
      unmanagedJars in Compile ++= Seq(
        file("lib/commons-net-3.2/commons-net-3.2.jar"), // For proxy_telnet
        file("lib/hyperic-sigar-1.6.4/sigar-bin/lib/sigar.jar") 
        ))
    .settings(
      unmanagedJars in Test ++= Seq(
        file("lib/akka-2.1.4/lib/config-1.0.0.jar"),
        file("lib/akka-2.1.4/lib/akka-testkit_2.10-2.1.4.jar"),
        file("lib/commons-io-2.4/commons-io-2.4.jar")))
    .settings(scalaSource in Compile <<= baseDirectory(_ / "src"))
    .settings(scalaSource in Test <<= baseDirectory(_ / "test"))
    .settings(resourceDirectory in Test <<= baseDirectory(_ / "src"))
    .settings(resourceDirectory in Compile <<= baseDirectory(_ / "src"))
    .settings(testOptions += Tests.Argument(TestFrameworks.JUnit, "-v", "-a", "-n"))
}
