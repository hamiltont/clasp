// dashboard.js
// Provides a client-side interface to obtain the status
// and control servers for Clasp.
//
// Brandon Amos
// 2013.09.22

var root = 'localhost:8080';
var connection = new WebSocket('ws://' + root);

var servers = []
var emulators = []



$.ajax('http://' + root + '/nodes/all')
  .done(function( data ) {
    console.log( "Server data received: " + data );
    servers = data
      
    $.each(data, function(i, item) {
      console.log(item);
    });
    
    redraw();
  }).fail(function( jqXHR, textStatus, errorThrown ) {
    console.log( "Server request failed: " + data );
  });

//[{
//  "ip": "10.0.0.2",
//  "status": "Booting",
//  "asOf": 1411625429517
//}, {
//  "ip": "10.0.34.5",
//  "status": "Failed",
//  "asOf": 1411625429562
//}]
function redraw() {
  var newHtml =
    '<table id="servers" class="table table-condensed table-hover">' +
    "<tr>" +
      "<td><b>Server</b></td>" +
      "<td><b>Status</b></td>" +
      "<td><b>asOf</b></td>" +
    "</tr>";
  
  $.each(servers, function(i, item) {
    newHtml += serverTmpl(item);
  });

  newHtml += '</table>';
  $('#server-table').html(newHtml);
}


function refreshEmulatorList() {
    $.ajax('http://' + root + '/emulators')
      .done(function( data ) {
        redraw_emulator_list(data);
      }).fail(function( jqXHR, textStatus, errorThrown ) {
        console.log( "Server request failed: " + data );
      });    
}

//[{
//  "publicip": "10.0.0.2",
//  "consolePort": 5556,
//  "vncPort": 5902,
//  "wsVncPort": 6082,
//  "actorPath": "Actor[akka.tcp://clasp@10.0.0.2:2553/user/node-10.0.0.2/emulator-5554#1224132290]",
//  "uuid": "0308c4bf-4f0f-4b9f-8ea6-60cc814820a1"
//}]
function redraw_emulator_list(emulators) {
  var newHtml =
    '<table id="emulators" class="table table-condensed table-hover">' +
    "<tr>" +
      "<td><b>UUID</b></td>" +
      "<td><b>VNC</b></td>" +
      "<td><b>ADB</b></td>" +
      "<td><b>Actor</b></td>" +
    "</tr>";
  
  $.each(emulators, function(i, item) {
    newHtml += emulatorTmpl(item);
  });

  newHtml += '</table>';
  $('#emulator-table').html(newHtml);
}

connection.onopen = function () {
  connection.send('Ping'); // Send the message 'Ping' to the server
    
  var timer = window.setInterval(function() {
    connection.send('my message');
  }, 10*1000);
    
  connection.onclose = function(close) {
    console.log('Connection closed');
    window.clearInterval(timer);
  };

  connection.onerror = function (error) {
    console.log('WebSocket Error ' + error);
  };

  connection.onmessage = function (e) {
    console.log('Server: ' + e.data);
  };
};


/*
// Leaving this all in for later copypaste, but we are no longer using 
// socket.io
var socket = io.connect('http://localhost:9090')
var servers = {}

function refresh() {
  socket.emit('refresh', null);
  console.log('skipping refresh');
}

console.log('loading dashboard.js');
window.setInterval(function() {
  refresh()
}, 30*1000);

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

function getAvailableClients(serverName) {
  var availableClients = {};
  for (var i in servers) {
    if (servers[i]['clasp'] === "No" &&
        servers[i]['name'] !== serverName)  {
      availableClients[servers[i]['name']] =
        {'clientName': (servers[i]['name'])};
    }
  }
  return availableClients;
}

function stageClaspMain(serverName) {
  alert("Clasp main currently not supported.")
}

function runClaspMain(serverName) {
  alert("Clasp main currently not supported.")
}

function stageAntimalware(serverName) {
  console.log(serverName);
  $('#controlModal .modal-body').html(
    modalTmpl({'name': 'Antimalware', 'server': serverName,
      'clients': getAvailableClients(serverName)}))
}

function runAntimalware(serverName) {
  clients = [];
  $('input:checked').each(function() {
    clients.push(this.id);
  });
  var data = {'programDir': '/home/brandon/antimalware/driver',
    'master': serverName, 'clients': clients};
  console.log(serverName);
  console.log(data);
  socket.emit('compileAndRun', data);
  $('#controlModal').modal('hide');
}

$(function() {
  $('#server-table').on('click', 'tr', function() {
    var server = servers[$(this)[0].id];
    if (server['status'] == 'success') {
      $('#controlModal .modal-body').html(
        modalTmpl({'server': server['name']}))
      $('#controlModal').modal('show')
    }
  });
});

*/