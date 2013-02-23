package core.sdktools

import java.io.BufferedReader
import java.io.IOException
import org.apache.commons.net.telnet.TelnetClient
import java.io.OutputStream
import java.io.InputStreamReader

trait TelnetProxy {
  /**
   * Send a telnet command to an Android device and return the output.
   */
  def send_telnet_command(port: Int, command: String): String = {
    val telnet = new TelnetClient
    var response: String = ""

    try {
      telnet.connect("localhost", port)

      val reader = new BufferedReader(new InputStreamReader(telnet.getInputStream))

      // Eat the Android header
      while (!reader.readLine.contains("OK")) {}

      // Print the command
      val command_bytes: Array[Byte] = command.getBytes
      val output: OutputStream = telnet.getOutputStream()
      output.write(command_bytes)

      // Hit the enter key e.g. ASCII 10
      output.write(10.toChar)
      output.flush

      var line: String = ""
      val response_buffer: StringBuffer = new StringBuffer
      while ({ line = reader.readLine; !(line.contains("OK") || line.contains("KO")) })
        response_buffer.append(line).append('\n')
      response = response_buffer.toString

    } catch {
      case ioe: IOException => println("Error!")
      case e: Exception => println("Error 2")
    } finally {
      telnet.disconnect
    }

    response
  }

  /**
   * Send a series of events to the kernel.
   */
  def send_hardware_event(port: Int,
    typeStr: String,
    code: String,
    value: String): String = {
    send_telnet_command(port, s"event send $typeStr:$code:$value")
  }

  // TODO: This crashes the emulator!
  /**
   * Simulate keystrokes from a given text.
   */
  def send_event_text(port: Int, text: String): String = {
    send_telnet_command(port, s"event text $text")
  }

  /**
   * List all type aliases.
   */
  def get_event_types(port: Int): Vector[String] = {
    val raw = send_telnet_command(port, "event types")
    // TODO: This regex excludes the last item?
    val regex = """[ ]+([^ _]*_[^ ]+).*""".r

    var result = for (regex(name) <- regex findAllIn raw) yield name
    result.toVector
  }

  /**
   * List all code aliases for a given type.
   */
  def get_event_codes(port: Int, typeStr: String): Vector[String] = {
    val raw = send_telnet_command(port, s"event codes $typeStr")
    // TODO: This regex excludes many items...
    val regex = """[ ]+([^ _]*_[^ ]+).*""".r

    var result = for (regex(name) <- regex findAllIn raw) yield name
    result.toVector
  }

  /**
   * Send a GPS NMEA sentence.
   */
  def send_geo_nmea(port: Int, sentence: String) {
    send_telnet_command(port, s"geo nmea $sentence")
  }

  /**
   * Send a simple GPS fix.
   */
  def send_geo_fix(port: Int,
      longitude: String,
      latitude: String,
      altitude: String = "",
      satellites: String = "") {
    send_telnet_command(port, s"geo fix $longitude $latitude $altitude $satellites")
  }
  
  /**
   * List current phone calls.
   */
  def get_gsm_list(port: Int): Vector[String] = {
    val raw = send_telnet_command(port, s"gsm list")
    val regex = """(inbound from [+0-9]+)""".r

    var result = for (regex(name) <- regex findAllIn raw) yield name
    result.toVector
  }
  
  /**
   * Create inbound phone call.
   * 
   * @param number The phone number format with only digits, '#', and '+'.
   */
  def gsm_call(port: Int, number: String) {
    send_telnet_command(port, s"gsm call $number")
  }
  
  //TODO: How can outbound calls be sent?
  /**
   * Close waiting outbound call as busy.
   * 
   * @param number The phone number format with only digits, '#', and '+'.
   */
  def gsm_busy(port: Int, number: String) {
    send_telnet_command(port, s"gsm busy $number")
  }
  
  /**
   * Change the state of an outbound call to 'held'
   * 
   * @param number The phone number format with only digits, '#', and '+'.
   */
  def gsm_hold(port: Int, number: String) {
    send_telnet_command(port, s"gsm hold $number")
  }
  
  /**
   * Change the state of an outbound call to 'active'.
   * 
   * @param number The phone number format with only digits, '#', and '+'.
   */
  def gsm_accept(port: Int, number: String) {
    send_telnet_command(port, s"gsm accept $number")
  }
  
  /**
   * Disconnect an inbound or outbound phone call.
   */
  def gsm_cancel(port: Int, number: String) {
    send_telnet_command(port, s"gsm cancel $number")
  }
  
  /**
   * Modify data connection state.
   * 
   * Valid values for <state> are the following:
   *
   *   unregistered    no network available
   *   home            on local network, non-roaming
   *   roaming         on roaming network
   *   searching       searching networks
   *   denied          emergency calls only
   *   off             same as 'unregistered'
   *   on              same as 'home'
   */
  def gsm_data(port: Int, state: String) {
    send_telnet_command(port, s"gsm data $state")
  }
  
  /**
   * Modify voice connection state.
   * 
   * Valid values for <state> are the following:
   *
   *   unregistered    no network available
   *   home            on local network, non-roaming
   *   roaming         on roaming network
   *   searching       searching networks
   *   denied          emergency calls only
   *   off             same as 'unregistered'
   *   on              same as 'home'
   */
  def gsm_voice(port: Int, state: String) {
    send_telnet_command(port, s"gsm voice $state")
    
  }
  
  /**
   * Return GSM status.
   * 
   * @return A Vector with the first element being the voice
   *         status and the second being the data status.
   */
  def gsm_status(port: Int): Vector[String] = {
    val raw = send_telnet_command(port, s"gsm status")
    val regex = """.*: *([^\s]*)""".r

    var result = for (regex(name) <- regex findAllIn raw) yield name
    result.toVector
  }
  
  /**
   * Sets the rssi and ber.
   * 
   * @param rssi range is 0..31 and 99 for unknown
   * @param ber range is 0..7 percent and 99 for unknown
   */
  def gsm_signal(port: Int, rssi: Int = 99, ber: Int = 99) {
    send_telnet_command(port, s"geo signal $rssi $ber")
  }
  
  /**
   * 'cdma ssource <ssource>' allows you to specify where to read the subscription from
   * 
   * 	nv: Read subscription from non-volatile RAM
   *    ruim: Read subscription from RUIM
   */
  def cdma_ssource(port: Int, ssource: String) {
    send_telnet_command(port, s"cdma ssource $ssource")
  }
  
  /**
   * Dump network status.
   * 
   * @return A vector with the download speed and upload speed (in bits/s),
   *         and the minimum and maximum latency (in ms).
   */
  def get_cdma_status(port: Int): Vector[String] = {
    var raw = send_telnet_command(port, s"cdma status")
    val speedRegex = """.*speed: *([0-9]*) bits/s""".r

    var speedResult = for (speedRegex(name) <- speedRegex findAllIn raw) yield name
    
    val latRegex = """.*latency: *([0-9]*) ms""".r
    var latResult = for (latRegex(name) <- latRegex findAllIn raw) yield name
    (speedResult++latResult).toVector
  }
  
  /**
   * Change network speed.
   */
  def set_cdma_speed(port: Int, value: String) {
    send_telnet_command(port, s"cdma speed $value")
  }
  
  /**
   * Change network latency.
   */
  def set_cdma_delay(port: Int, value: String) {
    send_telnet_command(port, s"cdma delay $value")
  }
  
  /**
   * Dump network packets to file.
   */
  def cdma_capture_start(port: Int, file: String) {
    send_telnet_command(port, s"cdma capture start $file")
  }
  
  /**
   * Stop dumping network packets.
   */
  def cdma_capture_stop(port: Int) {
    send_telnet_command(port, s"cdma capture stop")
  }
  
  /**
   * Kill the emulator instance.
   */
  def telnet_kill(port: Int) {
    send_telnet_command(port, s"kill")
  }
  
    
  /**
   * Dump network status.
   * 
   * @return A vector with the download speed and upload speed (in bits/s),
   *         and the minimum and maximum latency (in ms).
   */
  def get_network_status(port: Int): Vector[String] = {
    var raw = send_telnet_command(port, s"cdma status")
    val speedRegex = """.*speed: *([0-9]*) bits/s""".r

    var speedResult = for (speedRegex(name) <- speedRegex findAllIn raw) yield name
    
    val latRegex = """.*latency: *([0-9]*) ms""".r
    var latResult = for (latRegex(name) <- latRegex findAllIn raw) yield name
    (speedResult++latResult).toVector
  }
  
  /**
   * Change network speed.
   */
  def set_network_speed(port: Int, value: String) {
    send_telnet_command(port, s"network speed $value")
  }
  
  /**
   * Change network latency.
   */
  def set_network_delay(port: Int, value: String) {
    send_telnet_command(port, s"network delay $value")
  }
  
  /**
   * Dump network packets to file.
   */
  def network_capture_start(port: Int, file: String) {
    send_telnet_command(port, s"network capture start $file")
  }
  
  /**
   * Stop dumping network packets.
   */
  def network_capture_stop(port: Int) {
    send_telnet_command(port, s"network capture stop")
  }
  
  /**
   * Display battery and charger state.
   * 
   * @return A vector with AC, status, health, present, and capacity
   *         in indices 0-4.
   */
  def get_power_state(port: Int): Vector[String] = {
    var raw = send_telnet_command(port, s"power display")
    val regex = """.*: (.*)""".r

    var result = for (regex(name) <- regex findAllIn raw) yield name
    result.toVector
  }

  /**
   * Set AC charging state.
   */
  def power_ac_state(port: Int, isOn: Boolean) {
    if (isOn) send_telnet_command(port, s"power ac on")
    else      send_telnet_command(port, s"power ac off")
  }
  
  /**
   * Set battery status.
   * 
   * @param status unknown|charging|discharging|not-charging|full"
   */
  def power_status(port: Int, status: String) {
    send_telnet_command(port, s"power status $status")
  }
  
  /**
   * Set battery present state.
   */
  def power_present(port: Int, isPresent: Boolean) {
    if (isPresent) send_telnet_command(port, s"power present true")
    else           send_telnet_command(port, s"power present false")
  }
  
  /**
   * Set battery health state.
   * 
   * @param state unknown|good|overheat|dead|overvoltage|failure
   */
  def power_health(port: Int, status: String) {
    send_telnet_command(port, s"power health $status")
  }
  
  /**
   * Set battery capacity state.
   */
  def power_capacity(port: Int, percentage: Int) {
    send_telnet_command(port, s"power capacity $percentage")
  }
  
  /**
   * List current redirections.
   */
  def get_redir_list(port: Int): Vector[String] = {
    var raw = send_telnet_command(port, s"redir list")
    raw.split("\n").toVector
  }
  
  /**
   * Add new redirection.
   * 
   * @param protocol tcp|udp
   */
  def redir_add(port: Int, protocol: String, hostPort: Int, devicePort:Int) {
    send_telnet_command(port, s"redir add $protocol:$hostPort:$devicePort")
  }
  
  /**
   * Remove existing redirection.
   */
  def redir_del(port: Int, protocol: String, hostPort: Int) {
    send_telnet_command(port, s"redir del $protocol:$hostPort")
  }
  
  /**
   * 'sms send <phonenumber> <message>' allows you to simulate a new 
   * inbound sms message.
   */
  def sms_send(port: Int, number: String, message: String) {
    send_telnet_command(port, s"sms send $number $message")
  }
  
  /**
   * 'sms pdu <hexstring>' allows you to simulate a new inbound sms PDU
   * (used internally when one emulator sends SMS messages to another instance).
   */
  def sms_pdu(port: Int, hex: String) {
    send_telnet_command(port, s"sms pdu $hex")
  }
  
  /**
   * Stop the virtual device.
   */
  def avd_stop(port: Int) {
    send_telnet_command(port, "avd stop")
  }
  
  /**
   * Start/restart the virtual device.
   */
  def avd_start(port: Int) {
    send_telnet_command(port, "avd start")
  }
  
  /**
   * Query virtual device status.
   */
  def get_avd_status(port: Int): String = {
    var raw = send_telnet_command(port, "avd status")
    val regex = """virtual device is (.*)""".r

    var result = for (regex(name) <- regex findAllIn raw) yield name
    val resultVect = result.toVector
    if (resultVect.size > 0) return resultVect(0)
    else return ""
  }
  
  /**
   * Query virtual device name.
   */
  def get_avd_name(port: Int): String = {
    send_telnet_command(port, "avd name").trim()
  }
  
  /**
   * List available state snapshots.
   */
  //TODO: Unfinished/untested due to "No available block device supports snapshots."
  def get_avd_snapshots(port: Int): Vector[String] = {
    var raw = send_telnet_command(port, "avd snapshot list")
    val regex = """(.*)""".r

    var result = for (regex(name) <- regex findAllIn raw) yield name
    result.toVector
  }
  
  /**
   * Save snapshot state.
   */
  //TODO: Unfinished/untested due to "No available block device supports snapshots."
  def save_snapshot(port: Int, name: String) {
    send_telnet_command(port, s"avd snapshot save $name")
  }
  
  /**
   * Load snapshot state.
   */
  //TODO: Unfinished/untested due to "No available block device supports snapshots."
  def load_snapshot(port: Int, name: String) {
    send_telnet_command(port, s"avd snapshot load $name")
  }
  
  /**
   * Delete snapshot state.
   */
  //TODO: Unfinished/untested due to "No available block device supports snapshots."
  def delete_snapshot(port: Int, name: String) {
    send_telnet_command(port, s"avd snapshot delete $name")
  }

  /**
   * Change the window scale.
   */
  def window_scale(port: Int, scale: String) {
    send_telnet_command(port, s"window scale $scale")
  }
  
  /**
   * List all sensors and their status.
   * 
   * @return A vector with the elements 'acceleration, magnetic-field,
   *         orientation, temperature, proximity
   */
  def get_sensor_status(port: Int): Vector[String] = {
    var raw = send_telnet_command(port, s"sensor status")
    val regex = """.*: ([^\.]*)""".r

    var result = for (regex(name) <- regex findAllIn raw) yield name
    result.toVector
  }

  /**
   * Get sensor value.
   */
  def get_sensor_val(port: Int, sensorName: String): String = {
    var raw = send_telnet_command(port, s"sensor get $sensorName")
    val regex = """.* = (.*)""".r

    var result = for (regex(name) <- regex findAllIn raw) yield name
    val resultVect = result.toVector
    if (resultVect.size > 0) return resultVect(0)
    else return ""
  }
  
  /**
   * Set sensor value.
   */
  def set_sensor_val(port: Int, sensorName: String, value: String) {
    send_telnet_command(port, s"sensor set $sensorName $value")
  }

}
