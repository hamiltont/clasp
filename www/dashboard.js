(function() {
  var exec = require('child_process').exec;

  module.exports.registerServer = function(socket, server) {
    socket.emit('serverRegister', server);
    exec("ping -c 1 " + server,
      function(err, stdout, stderr) {
        if (err == null) {
          socket.emit('serverPing', {'server': server, 'ping': 'Online'});
        } else {
          socket.emit('serverPing', {'server': server, 'ping': 'Offline'});
        }
    });
    exec("timeout -s 9 15s ssh " + server + " echo",
      function(err, stdout, stderr) {
        if (err == null) {
          socket.emit('serverSsh', {'server': server, 'ssh': 'Online'});
        } else {
          socket.emit('serverSsh', {'server': server, 'ssh': 'Offline'});
        }
    });
  }
}());
