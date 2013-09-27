## Building Clasp with sbt.

### Building Clasp.
After installing sbt, Clasp can be built with:

    $ sbt assembly

This will compile the sources and include the libraries in a single "fat" jar file
located at `<Clasp>/target/Clasp-Assembly.jar`. To build a "thin" jar that only
links to the libraries, use `sbt stage`

Note: In newer versions of sbt this file may be at <Clasp>/target/scala-X.XX/Clasp-Assembly.jar
Note: I've had trouble using this command from a NFS client.

### Overview of sbt.
"sbt is a build tool for Scala and Java projects that aims to do the
basics well" [Scala website](http://www.scala-sbt.org/)

Build definitions are done mostly in `<Clasp>/build.sbt` and plugin
definitions are contained in `<Clasp>/project/plugins.sbt`.
In general, more configuration can be done in `<Clasp>/project/*.scala`.

+ [Build Definition Documentation](http://www.scala-sbt.org/release/docs/Getting-Started/Basic-Def.html)
+ [Plugin Documentation](http://www.scala-sbt.org/release/docs/Extending/Plugins)


### build.sbt contents.
`build.sbt` starts with information about the project.
Then, a non-default source directory is configured with:

    scalaSource in Compile <<= baseDirectory(_ / "src")

The default source directory root is in `src/main/scala`. See
http://www.scala-sbt.org/release/docs/Getting-Started/Directories.html.

Next, jars are manually added to the classpath with:

    unmanagedJars in Compile +=
        file("lib/hyperic-sigar-1.6.4/sigar-bin/lib/sigar.jar")

See http://www.scala-sbt.org/release/docs/Detailed-Topics/Library-Management.



### plugins.sbt contents.
`plugins.sbt` includes `sbt-assembly`, which is a plugin used for fat jar
creation.
See [sbt/sbt-assembly](https://github.com/sbt/sbt-assembly).

    addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.8.6")

### Building Clasp using Eclipse

Generate .project and .classpath files

    $ sbt eclipse

and then import as existing project into eclipse 
