
package core

import scala.collection.mutable.ListBuffer
import scala.collection.mutable.MutableList

//import org.hyperic.sigar.Sigar
import org.slf4j.LoggerFactory

import core.sdktools.sdk

/*
 * Handles starting nodes, either to represent this 
 * computer or other computers on the network. If this 
 * computer, we can start the node directly (granted, at some point I may want to put it in a separate process for sandboxing, but that's not important now). If another 
 * computer, we have to send a message across the 
 * communication mechanism and await the callback 
 * response
 * 
 */
object NodeLauncher extends App {
  lazy val log = LoggerFactory.getLogger(getClass())
  import log.{error, debug, info, trace}

  val nodes = ListBuffer[Node]()
  
  //var s: Sigar = new Sigar
  //info(s.getCpuPerc.toString)

  var n: Node = new Node
  val emu: Emulator = n.run_emulator(null)
  //val pt: ProcTime = new ProcTime
  //pt.gather(s, emu.emulator_processid)
  //info(pt)

  //info(s getCpuPerc)

  info("Created Node")
  
  
  val out = sdk.send_telnet_command(emu.telnetPort, "avd snapshot list")
  println(out)
  

  //pt.gather(s, emu.emulator_processid)
  //info(pt)
  n.cleanup
  info("Cleaned Node")

  //info(s getCpuPerc)


  def testADB() {
    assert(sdk.is_adb_available)
  }
}

class Node() {
  lazy val log = LoggerFactory.getLogger(getClass())
  import log.{error, debug, info, trace}
  import core.sdktools.EmulatorOptions
  
  val devices: MutableList[Emulator] = MutableList[Emulator]()
  var current_emulator_port = 5555
  info("A new Node is being constructed")
  /*val load_monitor: Actor = actor {
    val s: Sigar = new Sigar 
    
    val steady_cpu: Double = (for (i <- 1 to 10) yield {Thread.sleep(10); s.getCpuPerc.getUser}).sum / 10.0 
    
    println("Steady state is " + "%.2f".format(100*steady_cpu))
    val cpu = Queue[Double](steady_cpu, steady_cpu, steady_cpu, steady_cpu, steady_cpu)

    loop {
      self.receiveWithin(1000) {
        case "EXIT" => exit()
        case TIMEOUT => {
          cpu enqueue s.getCpuPerc.getUser
          cpu dequeue
          
          val std:Double = 100*scala.math.sqrt(variance(cpu))
          println("%.2f".format(100*cpu.front) + ": " + "%.2f".format(std) + ": " + "%.2f".format((100*cpu.front - std)/std))
        }
      }
    }
  }
  // Allow sigar a second to build a model of system steady state
  Thread.sleep(10 * 10)
  * */
  

  def mean[T](item: Traversable[T])(implicit n: Numeric[T]) = {
    n.toDouble(item.sum) / item.size.toDouble
  }

  def variance[T](items: Traversable[T])(implicit n: Numeric[T]): Double = {
    val itemMean = mean(items)
    val count = items.size
    val sumOfSquares = items.foldLeft(0.0d)((total, item) => {
      val itemDbl = n.toDouble(item)
      val square = math.pow(itemDbl - itemMean, 2)
      total + square
    })
    sumOfSquares / count.toDouble
  }

  // TODO: Option to run a specific AVD.
  def run_emulator(opts: EmulatorOptions = null): Emulator = {
    devices += EmulatorBuilder.build(current_emulator_port, opts)
    current_emulator_port += 2
    devices.last.asInstanceOf[Emulator]
  }

  def cleanup {
    devices.foreach(phone => phone.cleanup)
  }
}


