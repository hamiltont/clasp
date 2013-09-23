(function() {
  var exec = require('child_process').exec;
  var pidRegex = null;

  module.exports.setPidRegex = function(names) {
    regexStr = "([0-9]+) (" + names.join("|") + ")";
    pidRegex = new RegExp(regexStr, "g");
  }

  module.exports.refresh = function(socket, server) {
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
  }

  module.exports.refreshAll = function(socket, servers) {
    var serverLength = servers.length;
    for (var i = 0; i < serverLength; i++) {
      module.exports.refresh(socket, servers[i]);
    }
  }

  module.exports.killClasp = function(socket, server, servers) {
    console.log("Killing Clasp on " + server);
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
                  var matchInner = pidRegex.exec(stdout);
                  socket.emit('killClasp', {'status': 'niceKillFailed'});
                  exec("timeout -s 9 15s ssh "+server+" kill -9 "+matchInner[1],
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
}());
