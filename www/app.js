var port = 9090;
var express = require('express'), app = express(), http = require('http'),
    path = require('path'), auth = require('http-auth'),
    server = http.createServer(app), io = require('socket.io').listen(server);
var config = require('./config'), routes = require('./routes'),
    claspDash = require('./dashboard');

claspDash.setPidRegex(config.mainNames);

app.use(auth.connect(auth.basic({
  realm: "Clasp",
  file: __dirname + "/users.htpasswd",
})));

app.configure(function(){
  app.set('port', port);
  app.set('views', __dirname + '/views');
  app.set('view engine', 'jade');
  app.use(express.logger('dev'));
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

server.listen(port, function(){
  console.log("Express server listening on port " + app.get('port'));
});

io.sockets.on('connection', function(socket) {
  var servers = config['servers'];
  var serverLength = config['servers'].length;
  for (var i = 0; i < serverLength; i++) {
    socket.emit('serverRegister', servers[i]);
    claspDash.refresh(socket, servers[i]);
  }

  socket.on('killClasp', function(server) {
    claspDash.killClasp(socket, server, servers);
  });
});

