import sbt._
import Keys._

object BuildSettings {

  // TODO: Get sigar working.
  // libraryDependencies += "org.fusesource" % "sigar" % "1.6.4"
  // unmanagedJars in Compile +=
  //    file("lib/hyperic-sigar-1.6.4/sigar-bin/lib/sigar.jar")

  import sbtassembly.Plugin._ // sbt-assembly settings for building a fat jar
  import AssemblyKeys._ // Imports sbt-assembly keys.

  lazy val basicSettings = Seq[Setting[_]](
    organization :=  "Magnum Research Group",
    version :=  "0.0.1",
    description :=  "Distributed Android emulator framework.",
    scalaVersion :=  "2.10.1",
    scalacOptions :=  Seq("-deprecation", "-encoding", "utf8",
      "-feature", "-unchecked"),
    //scalacOptions in Test :=  Seq("-Yrangepos"),
    resolvers  ++= Dependencies.resolutionRepos
  )

  // Makes our SBT app settings available from within the app
  lazy val scalifySettings = Seq(sourceGenerators in Compile <+= (sourceManaged in Compile, version, name, organization) map { (d, v, n, o) =>
    val file = d / "settings.scala"
    IO.write(file, """package clasp.generated
      |object Settings {
      |  val organization = "%s"
      |  val version = "%s"
      |  val name = "%s"
      |}
      |""".stripMargin.format(o, v, n))
    Seq(file)
  })

  lazy val sbtAssemblySettings = assemblySettings ++ Seq(
    test in assembly := {}, // Don't run tests for 'assembly'.
    jarName in assembly := { name.value + "-" + version.value + ".jar" }
  )

  lazy val buildSettings = basicSettings ++ scalifySettings ++ sbtAssemblySettings
}
