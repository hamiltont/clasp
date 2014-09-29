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


function shutdown() {
    $.ajax('http://' + root + '/system/shutdown')
    .done(function( data ) {
        console.log('Shutting down');
    });
}

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

      .done(function( data ) {
        redraw_emulator_list(data);
      }).fail(function( jqXHR, textStatus, errorThrown ) {
        console.log( "Server request failed: " + data );
      });    
}

  };

  connection.onerror = function (error) {
    console.log('WebSocket Error ' + error);
  };

  connection.onmessage = function (e) {
    console.log('Server: ' + e.data);
  };
};

}


*/