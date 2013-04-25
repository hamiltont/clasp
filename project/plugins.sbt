addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.8.7")

addSbtPlugin("com.typesafe.sbt" % "sbt-start-script" % "0.7.0")

resolvers += Resolver.url("artifactory", url("http://scalasbt.artifactoryonline.com/scalasbt/sbt-plugin-releases"))(Resolver.ivyStylePatterns)

