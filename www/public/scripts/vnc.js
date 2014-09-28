INCLUDE_URI="scripts/noVNC/";
// Load supporting scripts
Util.load_scripts(["webutil.js", "base64.js", "websock.js", "des.js",
                     "keysymdef.js", "keyboard.js", "input.js", "display.js",
                     "jsunzip.js", "rfb.js"]);

clasp.dash.vnc.setup = function (host, port, target) {
  console.log('noVNC: Connecting to ' + host + ':' + port);
  WebUtil.init_logging('debug');

  // var rfb = new RFB({'target': $D('noVNC_canvas'),
  var rfb = new RFB({'target': $D(target),
       'encrypt':      WebUtil.getQueryVar('encrypt', false),
       'repeaterID':   WebUtil.getQueryVar('repeaterID', ''),
       'true_color':   WebUtil.getQueryVar('true_color', true),
       'local_cursor': WebUtil.getQueryVar('cursor', false),
       'shared':       WebUtil.getQueryVar('shared', true),
       'view_only':    WebUtil.getQueryVar('view_only', false)
       });
  var password = '', path='websockify';
  rfb.connect(host, port, password, path);
  
  return rfb;
};

// Translates RFB keypresses into Android emulator actions
// These are probably dependant on my mac keyboard, not sure how to 
// map to others at the moment
// See http://developer.android.com/tools/help/emulator.html
function rfbPressEsc() {
  rfbPressKey(27);
}
function rfbPressHome() {
  rfbPressKey(65360);
}
function rfbPressMenu() {
  rfbPressKey(65471);
}
function rfbPressPower() {
  rfbPressKey(65476);
}
function rfbPressKey(key) {
  rfb.sendKey(key);
}


function rfbPressCamera() {
  // control + Keypad 5
  rfb._handleKeyPress( 0xFFE3 , true);
  rfb._handleKeyPress( 65461  , true);
  rfb._handleKeyPress( 65461  , false);
  rfb._handleKeyPress( 0xFFE3 , false);
}
function rfbPressVolumeUp() {
  // control + F5
  rfb._handleKeyPress( 0xFFE3 , true);
  rfb._handleKeyPress( 65474  , true);
  rfb._handleKeyPress( 65474  , false);
  rfb._handleKeyPress( 0xFFE3 , false);
}
function rfbPressOrientation() {
  rfb._handleKeyPress( 0xFFE3 , true);
  rfb._handleKeyPress( 65481  , true);
  rfb._handleKeyPress( 65481  , false);
  rfb._handleKeyPress( 0xFFE3 , false);
}
function rfbPressVolumeDown() {
  // control + F6
  rfb._handleKeyPress( 0xFFE3 , true);
  rfb._handleKeyPress( 65475  , true);
  rfb._handleKeyPress( 65475  , false);
  rfb._handleKeyPress( 0xFFE3 , false);
}
function vncUpdateScale(amount) {
  var origscale = rfb.get_display()._scale;
  var origx = rfb.get_display().absX(0);
  var origy = rfb.get_display().absY(0);

  var update = origscale + amount;
  rfb.get_display().set_scale(update);
  var newScale = rfb.get_display()._scale;
  console.log('Updated display scale from ' + origscale + ' to ' + newScale);

  // Update mouse to reflect new scale
  var origMscale = rfb.get_mouse().get_scale();
  rfb.get_mouse().set_scale(newScale);
  var newMscale = rfb.get_mouse().get_scale();
  console.log('Updated mouse scale from ' + origMscale  + ' to ' + newMscale);

  // Fix CSS properties
  var target = rfb.get_target();
  $(target).css( "transform", 'scale(' + newScale + ')');
  $(target).css( "transform-origin", 'top left' );
}
