var port = 9090;
var express = require('express'), app = express(), http = require('http'),
    path = require('path'), auth = require('http-auth'),
    server = http.createServer(app), io = require('socket.io').listen(server);
var config = require('./config'), routes = require('./routes'),
    claspDash = require('./dashboard-server');

claspDash.setPidRegex(config.mainNames);
claspDash.setAPIRoot(config.claspAPI);

app.use(auth.connect(auth.basic({
  realm: "Clasp",
  file: __dirname + "/users.htpasswd",
})));

app.configure(function(){
  app.set('port', port);
  app.set('views', __dirname + '/views');
  app.set('view engine', 'jade');
  app.use(express.logger('tiny'));
  app.use(express.bodyParser());
  app.use(express.methodOverride());
  app.use(app.router);
  app.use(require('stylus').middleware(__dirname + '/public'));
  app.use(express.static(path.join(__dirname, 'public')));
});

app.configure('development', function(){
  app.use(express.errorHandler());
  app.locals.pretty = true;
});

app.get('/', routes.index);
app.get('/doc', routes.doc);
app.get('/dashboard', routes.dashboard);
app.get('/vnc', routes.vnc);

server.listen(port, function(){
  console.log("Express server listening on port " + app.get('port'));
});

/*
// Leaving this in as valuable example code, but there's really no reason
// to have a persistent connection between the dashboard and nodejs server. 
// Currently all we use node for is to compile the jade and do simple
// serving. The web interface directly connects to the clasp master using 
// websockets for continual updates. 
// Including socket.io in the client pages causes a ton of extra JS to be 
// downloaded, so I've removed that as we're not using it anyway
io.sockets.on('connection', function(socket) {
  console.log('WebSocket connection');

  // TODO get servers from localhost:9090/nodes/all
  var servers = config['servers'];
  var serverLength = config['servers'].length;
  for (var i = 0; i < serverLength; i++) {
    socket.emit('serverRegister', servers[i]);
    // claspDash.refresh(socket, servers[i]);
  }

  socket.on('refresh', function(data) {
    console.log('Refresh requested');
    for (var i = 0; i < serverLength; i++) {
      // claspDash.refresh(socket, servers[i]);
    }
  });

  socket.on('killClasp', function(server) {
    console.log('Kill requested');
    claspDash.killClasp(socket, server, servers);
  });

  socket.on('killEmu', function(server) {
    claspDash.killEmu(socket, server, servers);
  });

  socket.on('compileAndRun', function(data) {
    claspDash.compileAndRun(data);
  });
    
  socket.on('test', function(data) {
    console.log('I see you are testing over there');
  });
});
*/