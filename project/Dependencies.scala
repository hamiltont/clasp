import sbt._

object Dependencies {

  val resolutionRepos = Seq(
    "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
    "spray" at "http://repo.spray.io",
    "spray nightly" at "http://nightlies.spray.io/"
  )

  val akkaVersion  = "2.3.6"
  val sprayVersion = "1.3.1"
  val scalaVersion =  "2.10.4"
  
  object Libraries {
    val scalaActors      = "org.scala-lang" % "scala-actors" % scalaVersion
    val scalaTest        = "org.scalatest" % "scalatest_2.10" % "1.9.1" % "test"
    val junit            = "com.novocode" % "junit-interface" % "0.10-M2" % "test"
    val scallop          = "org.rogach" %% "scallop" % "0.8.1"
    val akkaActor        = "com.typesafe.akka" %% "akka-actor" % akkaVersion
    val akkaRemote       = "com.typesafe.akka" %% "akka-remote" % akkaVersion
    val akkaSlf4j        = "com.typesafe.akka" %% "akka-slf4j" % akkaVersion
    val akkaTestkit      = "com.typesafe.akka" %% "akka-testkit" % akkaVersion
    val logback          = "ch.qos.logback" % "logback-classic" % "1.0.9"
    val spray            = "io.spray" %% "spray-can" % sprayVersion
    val sprayRouting     = "io.spray" %% "spray-routing" % sprayVersion
    val sprayJson        = "io.spray" %%  "spray-json" % "1.2.6"
    val sprayHttpx       = "io.spray" %% "spray-httpx" % sprayVersion
    val sprayClient      = "io.spray" %% "spray-client" % sprayVersion
    val sprayCan         = "io.spray" %% "spray-can" % sprayVersion
    val sprayWebsocket   = "com.wandoulabs.akka" %% "spray-websocket" % "0.1.3"
  }
}
