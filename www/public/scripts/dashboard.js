// dashboard.js
// Provides a client-side interface to obtain the status
// and control servers for Clasp.
//
// Brandon Amos
// 2013.09.22

var socket = io.connect('http://ataack.ece.vt.edu:9090')
var servers = {}

function refresh() {
  socket.emit('refresh', null);
}

window.setInterval(function() {
  refresh()
}, 10000);

socket.on('serverRegister', function(serverName) {
  servers[serverName] = {'name': serverName};
  redraw();
});

socket.on('serverPing', function(data) {
  servers[data['server']]['ping'] = data['ping'];
  redraw();
});

socket.on('serverSsh', function(data) {
  servers[data['server']]['ssh'] = data['ssh'];
  redraw();
});

socket.on('serverClasp', function(data) {
  servers[data['server']]['clasp'] = data['clasp'];
  if (data['clasp'] === 'No') {
    servers[data['server']]['status'] = 'success';
  } else {
    delete servers[data['server']]['status'];
  }
  redraw();
});

socket.on('serverEmu', function(data) {
  servers[data['server']]['emu'] = data['emu'];
  redraw();
});

socket.on('killClasp', function(data) {
  console.log(data);
});

function redraw() {
  var newHtml =
    '<table id="servers" class="table table-condensed table-hover">' +
    "<tr>" +
      "<td><b>Server</b></td>" +
      "<td><b>Pingable</b></td>" +
      "<td><b>ssh-able</b></td>" +
      "<td><b>Clasp running?</b></td>" +
      "<td><b>Emulators</b></td>" +
    "</tr>";
  var serversLength = servers.length;
  for (var i in servers) {
    newHtml += serverTmpl(servers[i]);
  }
  newHtml += '</table>';
  $('#server-table').html(newHtml);
}

function killAllClasp() {
  for (var i in servers) {
    if (servers[i]['clasp'] === "Yes")  {
      console.log("Killing " + i);
      killClasp(i);
    }
  }
}

function killClasp(server) {
  socket.emit("killClasp", server);
}

function killAllEmu() {
  for (var i in servers) {
    socket.emit("killEmu", i);
  }
}

$(function() {
  $('#server-table').on('click', 'tr', function() {
    var server = servers[$(this)[0].id];
    if (server['status'] == 'success') {
      console.log(server);
    }
  });
});
