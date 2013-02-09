
package core

import scala.sys.process._
import scala.annotation.elidable
import scala.annotation.elidable.ASSERTION
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.MutableList

/*
 * Handles starting nodes, either to represent this 
 * computer or other computers on the network. If this 
 * computer, we can start the node directly (granted, at some point I may want to put it in a separate process for sandboxing, but that's not important now). If another 
 * computer, we have to send a message across the 
 * communication mechanism and await the callback 
 * response
 * 
 */
object NodeLauncher {
  val nodes = ListBuffer[Node]()
  
  // By default it launches a single Node for 
  // the current computer
  def main(args: Array[String]): Unit = {
    
	  var n:Node = new Node()
	  
	  println("Created Node")
	  n.cleanup
	  println("Cleaned Node")
	  
  }

  def testADB() {
    assert(AdbProxy.is_adb_available)
  }
}


class Node() {
  val devices: MutableList[AbstractDevice] = MutableList[AbstractDevice]()
  
  // TODO make this initialization not part of the constructor
  var current_emulator_port = 5555;
  println("A new Node is being constructed")
  val foo: AbstractDevice = EmulatorBuilder.build(current_emulator_port)
  devices += foo
  
  def cleanup {
    devices.foreach(phone => phone.cleanup)
  }
  
}


