addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.9.2")

addSbtPlugin("com.typesafe.sbt" % "sbt-start-script" % "0.10.0")

// Adds the command sbt eclipse, which generates a .project file
// that can then easily be imported into eclipse
addSbtPlugin("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "2.2.0")


// Plugin for running Typesafe Console during development
addSbtPlugin("com.typesafe.sbt" % "sbt-atmos" % "0.3.2")

resolvers += Resolver.url("artifactory", url("http://scalasbt.artifactoryonline.com/scalasbt/sbt-plugin-releases"))(Resolver.ivyStylePatterns)

