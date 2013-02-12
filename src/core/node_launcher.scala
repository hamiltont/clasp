
package core

import scala.actors._
import scala.actors.Actor._
import scala.actors.Actor
import scala.actors.Actor.actor
import scala.annotation.elidable.ASSERTION
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.MutableList
import scala.collection.mutable.Queue

import org.hyperic.sigar.ProcTime
import org.hyperic.sigar.Sigar

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

    var s: Sigar = new Sigar
    println(s getCpuPerc)

    var n: Node = new Node
    val emu: Emulator = n run_emulator
    val pt: ProcTime = new ProcTime
    pt.gather(s, emu.emulator_processid)
    println(pt)

    println(s getCpuPerc)

    println("Created Node")

    pt.gather(s, emu.emulator_processid)
    println(pt)
    n.cleanup
    println("Cleaned Node")

    println(s getCpuPerc)
  }

  def testADB() {
    assert(AdbProxy.is_adb_available)
  }
}

class Node() {
  val devices: MutableList[Emulator] = MutableList[Emulator]()
  var current_emulator_port = 5555
  println("A new Node is being constructed")
  val load_monitor: Actor = actor {
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

  def run_emulator: Emulator = {
    devices += EmulatorBuilder.build(current_emulator_port)
    current_emulator_port += 2
    devices.last.asInstanceOf[Emulator]
  }

  def cleanup {
    load_monitor ! "EXIT"
    devices.foreach(phone => phone.cleanup)
  }
}


