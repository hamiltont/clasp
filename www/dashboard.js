(function() {
  var exec = require('child_process').exec;
  var pidRegex = null;

  module.exports.setPidRegex = function(names) {
    regexStr = "([0-9]+) (" + names.join("|") + ")";
    pidRegex = new RegExp(regexStr, "g");
  }

  module.exports.refresh = function(socket, server) {
    try {
      exec("ping -c 1 " + server,
        function(err, stdout, stderr) {
          if (err == null) {
            socket.emit('serverPing', {'server': server, 'ping': 'Online'});
          } else {
            socket.emit('serverPing', {'server': server, 'ping': 'Offline'});
          }
      });
      exec("timeout -s 9 15s ssh " + server + " jps",
        function(err, stdout, stderr) {
          if (err != null) {
            socket.emit('serverSsh', {'server': server, 'ssh': 'Offline'});
          } else {
            socket.emit('serverSsh', {'server': server, 'ssh': 'Online'});
            if (stdout.match(pidRegex) != null) {
              socket.emit('serverClasp', {'server': server, 'clasp': 'Yes'});
            } else {
              socket.emit('serverClasp', {'server': server, 'clasp': 'No'});
            }
          }
      });
      exec("timeout -s 9 15s ssh " + server + " pgrep emulator",
        function(err, stdout, stderr) {
          // Don't check `stderr`.
          // pgrep returns nonzero when no processes are detected.
          var numEmus = stdout.split("\n").length - 1;
          socket.emit('serverEmu', {'server': server, 'emu': numEmus});
      });
    } catch (err) {
      console.log("Error caught.");
      console.log(err);
    }
  }

  module.exports.refreshAll = function(socket, servers) {
    var serverLength = servers.length;
    for (var i = 0; i < serverLength; i++) {
      module.exports.refresh(socket, servers[i]);
    }
  }

  module.exports.killClasp = function(socket, server, servers) {
    console.log("Killing Clasp on " + server);
    // TODO: Break these out into separate functions for
    //   1) Readibility, and
    //   2) Power.
    // Currently, sometimes `kill` does not actually kill the process,
    // but doesn't return an error.
    // To fix this, instead of relying on the error,
    // check `jps` again.
    exec("timeout -s 9 15s ssh " + server + " jps",
      function(err, stdout, stderr) {
        if (err != null) {
          socket.emit('killClasp', {'status': 'jpsFailed'});
        } else {
          var match = pidRegex.exec(stdout);
          while (match != null) {
            exec("timeout -s 9 20s ssh " + server + " kill " + match[1],
              function(err, stdout, stderr) {
                if (err != null) {
                  socket.emit('killClasp', {'status': 'niceKillFailed'});
                  exec("timeout -s 9 15s ssh "+server+" kill -9 "+match[1],
                    function(err, stdout, stderr) {
                      if (err != null) {
                        socket.emit('killClasp', {'status': 'forceKillFailed'});
                      } else {
                        socket.emit('killClasp', {'status': 'success'});
                        module.exports.refreshAll(socket, servers);
                      }
                    });
                } else {
                  socket.emit('killClasp', {'status': 'success'});
                  module.exports.refreshAll(socket, servers);
                }
            });
            match = pidRegex.exec(stdout);
          }
        }
    });
  }

  module.exports.killEmu = function(socket, server, servers) {
    console.log("Killing emulators on " + server);
    exec("timeout -s 9 15s ssh " + server + " pkill emulator",
      function(err, stdout, stderr) {
        exec("timeout -s 9 15s ssh " + server + " pgrep emulator",
          function(err, stdout, stderr) {
            var numEmus = stdout.split("\n").length - 1;
            socket.emit('serverEmu', {'server': server, 'emu': numEmus});
        });
    });
  }

  module.exports.compileAndRun = function(data) {
    console.log("compileAndRun: " + data);
    console.log("TODO: Compile.")
    // TODO: Add options.
    var runCmd = "ssh " + data['master'] + " 'cd " + data['programDir'] +
      "; target/start -a google-play --arffTag google-play" +
      " -n 8 -p " + data['clients'].join(",") + " -w " +
      data['clients'].length +
      " --profilerApk /home/brandon/antimalware/profiler/bin/Profiler-debug.apk" +
      " > antimalware.out'";
    console.log("Running using: " + runCmd);
    exec(runCmd,
      function(err, stdout, stderr) {
        console.log(stdout);
    });
  }
}());
