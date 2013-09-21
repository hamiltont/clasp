import com.typesafe.sbt.SbtStartScript

name := "App testert"

version := "1.0"

scalaVersion := "2.10.1"

maxErrors := 5

libraryDependencies += "org.rogach" %% "scallop" % "0.8.1"

scalacOptions ++= Seq("-feature", "-unchecked", "-deprecation")

seq(SbtStartScript.startScriptForClassesSettings: _*)
