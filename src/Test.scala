import scala.sys.process._

object Test {

  def main(args: Array[String]): Unit = {

    val adb: String = "/Development/adt-bundle-mac-x86_64/platform-tools/adb devices" !!
    
    val serial = adb.dropWhile(_!='\n').tail.takeWhile(_!='\t');

    val x = new Device(serial)
    
    x.install_package("/Users/hamiltont/Downloads/CS.apk")
    
    
  }

}