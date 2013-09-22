// dashboard.js
// Provides a client-side interface to obtain the status
// and control servers for Clasp.
//
// Brandon Amos
// 2013.09.22

var socket = io.connect('http://ataack.ece.vt.edu:9090')
function addServers() {
  socket.on('server', function(servers) {
    console.log(servers[0]);
    var length = servers.length;
    for (var i = 0; i < length; i++) {
      $('#servers').append(serverTmpl(servers[i]));
    }
  });
}
