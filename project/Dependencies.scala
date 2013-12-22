import sbt._

object Dependencies {

  val resolutionRepos = Seq(
    "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"
  )

  object Libraries {
    val scalaActors = "org.scala-lang" % "scala-actors" % "2.10.0-M6"
    val scalaTest = "org.scalatest" % "scalatest_2.10" % "1.9.1" % "test"
    val junit = "com.novocode" % "junit-interface" % "0.10-M2" % "test"
    val scallop = "org.rogach" %% "scallop" % "0.8.1"
    val akkaActor = "com.typesafe.akka" %% "akka-actor" % "2.1.2"
    val akkaRemote = "com.typesafe.akka" %% "akka-remote" % "2.1.2"
    val akkaSlf4j = "com.typesafe.akka" %% "akka-slf4j" % "2.1.2"
    val akkaTestkit = "com.typesafe.akka" %% "akka-testkit" % "2.1.2"
    val logback = "ch.qos.logback" % "logback-classic" % "1.0.9"
  }
}
