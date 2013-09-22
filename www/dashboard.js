(function() {
  module.exports.getStatus = function(servers) {
    var statuses = [], length = servers.length;
    for (var i = 0; i < length; i++) {
      statuses.push({"name": servers[i], "status": "online"});
    }
    return statuses;
  }
}());
