package main
import scala.sys.process.stringToProcess

class Main extends App { 
	  if ( args(1) == "deploy" ) {
		  val command = "echo \"Funciona\"";
		  var output: String = command!!;
	  }
}