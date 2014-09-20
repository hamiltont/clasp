exports.index = function(req, res){
  res.render('index');
};

exports.doc = function(req, res) {
  res.render('doc');
}

exports.dashboard = function(req, res) {
  res.render('dashboard');
}

exports.vnc = function(req, res) {
  res.render('vnc');
}
