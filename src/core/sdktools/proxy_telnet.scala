package core.sdktools

import java.io.BufferedReader
import java.io.IOException
import org.apache.commons.net.telnet.TelnetClient
import java.io.OutputStream
import java.io.InputStreamReader

trait TelnetProxy {
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
      while ( { line = reader.readLine; !(line.contains("OK") || line.contains("KO")) } )
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
  
}
