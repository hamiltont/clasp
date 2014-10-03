// dashboard.js
// 
// Sets up core dashboard functions, timers, connections, etc
//
// Brandon Amos
// 2013.09.22
//
// Hamilton Turner
// 2014.08 - Removed all functions and rewrote file

clasp.root = 'localhost:8080';
clasp._websocket = new WebSocket('ws://' + clasp.root);
clasp.pubsub = new WebSocketMultiplex(clasp._websocket); 
clasp.channels = {};
clasp.charts = {};

// Subscribe to all channels we definitely want
// clasp.channels.nodeupdates = clasp.pubsub.channel('/nodemanager/nodeupdates');
// WebSocket: Server Message: msg,/nodemanager/nodeupdates,{"ip":"127.0.0.1","status":"Failed","emulators":0,"asOf":1412042188840} dashboard.js:72

// Example pubsub usage: 
//   var foo = clasp.pubsub.channel('foo');
//   foo.send('barf');
//   // TODO ensure the syntax of these two is correct
//   elogs.addEventListener('message', function(dataObject) {
//     console.log('elogs got data ' + dataObject.data);
//   });
//   foo.onclose(function() {});
//   foo.close();

//var elogs = clasp.pubsub.channel('/emulator/' + uuid + '/log');
//elogs.addEventListener('message', function(dataObject) {
//  console.log('elogs got data ' + dataObject.data);
//  $('#emulatorlogs').append(dataObject.data + '<br />');
//});

/* 
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
*/

// TODO only refresh's emulators currently
clasp.refresh = function () {
    $.ajax('http://' + clasp.root + '/emulators')
      .done(function( data ) {
        redraw_emulator_list(data);
      }).fail(function( jqXHR, textStatus, errorThrown ) {
        console.log( "Server request failed: " + data );
      });    
}

// Setup some logging on the raw websocket
// Other consumers should use the pub-sub interface
// provided in clasp.pubsub
clasp._websocket.onopen = function () {
  clasp._websocket.onclose = function(close) {
    console.log('WebSocket: Connection closed');
  };
  clasp._websocket.onerror = function (error) {
    console.log('WebSocket: Error ' + error);
  };
  clasp._websocket.onmessage = function (e) {
    console.log('WebSocket: Server Message: ' + e.data);
  };
};

clasp.shutdown = function() {
    $.ajax('http://' + clasp.root + '/system/shutdown')
    .done(function( data ) {
        console.log('Shutting down');
    });
}


