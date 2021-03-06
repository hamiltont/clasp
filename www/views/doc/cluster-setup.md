## Cluster Setup
This roughly describes the operating system setup that is expected for 
our current system architecture to work. 

Basically, there should be a number of nodes, all of which have a directory
shared via something like NFS. The point here is that there is no need to
build anything like a fat jar, you simply compile on one machine and then 
run the result on all the other machines. 

### Dependencies
This means that all machines should be very similar in setup. 
Scala/akka/sbt are all pretty intense about dependencies. Having manged 
dependencies does help to alleviate this issue, but you will still find 
problem if machines have different versions of scala installed e.g. 2.9 vs 
2.10. We are trying to remove unmanaged jars from the project slowly and 
only rely on the managed variants

### Installed libraries
To this effect, all machines currently have scala 2.10.1 installed. It 
was installed by downloading the linux tar, copying all files to 
`/opt/scala-2.10.1/`, and creating `/etc/profile.d/scala.sh` that uses
pathmunge to add the binaries to the path. SBT was also installed this
way, version 0.12.3. 

### Firewalls / Access
1. Port 2552 is open on all computers in the system
2. NATs not supported (use a VPN if you want public internet for now, 
this is an akka limitation)
3. Master should be able to ssh to all clients without needing a password
4. All clients should be able to connect to master IP address:2552
