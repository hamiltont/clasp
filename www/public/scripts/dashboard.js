// dashboard.js
// Provides a client-side interface to obtain the status
// and control servers for Clasp.
//
// Brandon Amos
// 2013.09.22

var socket = io.connect('http://ataack.ece.vt.edu:9090')
var servers = {}

socket.on('serverRegister', function(serverName) {
  servers[serverName] = {'name': serverName, 'online': 'Waiting'};
  servers[serverName]['status'] = 'none';
  redraw();
});

socket.on('serverPing', function(data) {
  servers[data['server']]['ping'] = data['ping'];
  redraw();
});

socket.on('serverSsh', function(data) {
  servers[data['server']]['ssh'] = data['ssh'];
  if (data['ssh'] === 'Online') {
    servers[data['server']]['status'] = 'success';
  }
  redraw();
});

function redraw() {
  var newHtml =
    '<table id="servers" class="table table-condensed table-hover">' +
    "<tr><td><b>Server</b></td>" +
    "<td><b>Pingable</b></td>" +
    "<td><b>ssh-able</b></td></tr>";
  var serversLength = servers.length;
  for (var i in servers) {
    newHtml += serverTmpl(servers[i]);
  }
  newHtml += '</table>';
  $('#server-table').html(newHtml);
}


$(function() {
  $('#server-table').on('click', 'tr', function() {
    var server = servers[$(this)[0].id];
    console.log(server);
  });
});
