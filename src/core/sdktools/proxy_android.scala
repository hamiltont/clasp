package clasp.core.sdktools

import scala.language.postfixOps

import scala.sys.process._

import sdk_config.log.error
import sdk_config.log.info

/**
 * Provides an interface to the
 * [[http://developer.android.com/tools/help/android.html `android`]]
 * command line tool.
 * 
 * This, along with other components of the Android SDK, is included in
 * [[clasp.core.sdktools.sdk]].
 */
trait AndroidProxy {
  val android:String = sdk_config.config.getString(sdk_config.android_config)
  import sdk_config.log.{error, debug, info, trace}
  
  /**
   * Return the available Android Virtual Devices.
   */
  def get_avd_names: Vector[String] = {
    val command = s"$android list avd";
    val output: String = command !!
    val regex = """Name: (.*)""".r

    val result = for (regex(name) <- regex findAllIn output) yield name
    result.toVector
  }
  
  /**
   * Lists existing targets.
   */
  def get_targets: Vector[String] = {
    val command = s"$android list targets";
    val output: String = command !!
    val regex = "id: [0-9]* or \"(.*)\"".r
    
    val result = for (regex(target) <- regex findAllIn output) yield target
    result.toVector
  }

  /**
   * List ABIs corresponding with each entry from the existing
   * targets Vector.
   */
  def get_target_ABIs: Vector[Vector[String]] = {
    val command = s"$android list targets";
    val output: String = command !!

    val regex = "ABIs : (.*)".r
    val result = for (regex(target) <- regex findAllIn output)
      yield target.split(", ").toVector
    result.toVector
  }
  
  /**
   * Lists remote SDK repository.
   */
  def get_sdk: Vector[String] = {
    val command = s"$android list sdk";
    val output: String = command !!
    val regex = """[0-9]+- (.*)""".r
    
    val result = for (regex(target) <- regex findAllIn output) yield target
    result.toVector
  }
  
  /**
   * Creates a new Android Virtual Device.
   */
  def create_avd(name: String,
                 target: String,
                 abiName: String,
                 force: Boolean = false): Boolean = {
    if (!force && (get_avd_names contains name)) {
      val errorMsg = s"Error: AVD '$name' already exists."
      error(errorMsg)
      return false
    }
    
    // TODO can we verify that the eabi exists for this target before we attempt to 
    // run this? 
    var command:String = s"$android create avd -n $name -t $target -b $abiName"
    if (force) {
      command += " --force"
    }
    info(s"Building an AVD using $command")
    
    var create = Process(command)
    var output = List[String]()

    val logger = ProcessLogger( line => output ::= "out: " + line, 
      line => output ::= "err : " + line );
    // TODO No need to create two processes, just pass "no" directly to the 
    // stdin of create
    val echocommand = """echo no"""
    val exit = echocommand.#|(create).!(logger)

    output = output.reverse
    output foreach info

    (exit == 0)
  }

 /**
   * Creates a new Android Virtual Device with no ABI
   * only RESTRICTION : target MUST be the ID not the name.
   */
  def create_avd(name1: String,
                 target1: String)
  {

  /**
   * Determines the default ABI depending on the target number passed into the
   * method.
   * the case are for targets:
   * 1-14 , 16, 17 default to armeabi
   * 15, 18 default to x86
   * 19 - 25 default to armeabi-v7a
   */
    def parseTarget(arg: String): String = arg match {
    case "1"|"2"|"3"|"4"|"5"|"6"|"7"|"8"|"9"|"10"|"11"|"12"|"13"|"14"|"16"|"17" => "armeabi"
    case "15"|"18" => "x86"
    case "19"|"20"|"21"|"22"|"23"|"24"|"25"|"26"  => "armeabi-v7a"
    }

    val defaultABI = parseTarget(target1)
    create_avd(name1, target1, defaultABI)

  }
  
  /** Run a command, collecting the stdout, stderr and exit status */
  def run(cmd: String): (List[String], List[String], Int) = {
    val pb = Process(cmd)
    var out = List[String]()
    var err = List[String]()

    val exit = pb ! ProcessLogger(line => out ::= line,
      line => err ::= line)
          
    (out.reverse, err.reverse, exit) 
  }

  /**
   * Moves or renames an Android Virtual Device.
   */
  def move_avd(name: String,
               path: String,
               newName: String = null): Boolean = {
    var command = s"$android move avd -n $name -p $path"
    if (newName != null) {
      command += s" -r $newName"
    }
    
    val output: String = command !!;
    true
  }

  /**
   * Deletes an Android Virtual Device.
   */
  def delete_avd(name: String): Boolean = {
    if (!(get_avd_names contains name)) {
      error("Error: AVD '$name' does not exist.")
      return false
    }
    
    val command = s"$android delete avd -n $name"
    val output: String = command !!
    
    info(output)
    true
  }
  
  /**
   * Updates an Android Virtual Device to match the folders
   * of a new SDK.
   */
  def update_avd(name: String) {
    val command = s"$android update avd -n $name"
    val output: String = command !!
    
    info(output)
  }
  
  /**
   * Creates a new Android project.
   */
  def create_project(name: String,
                     target: String,
                     path: String,
                     pkg: String,
                     activity: String) {
    var command = s"$android create project"
    command += s" -n $name"
    command += s" -t $target"
    command += s" -p $path"
    command += s" -k $pkg"
    command += s" -a $activity"
    val output: String = command !!

    
    info(output)
  }
  
  /**
   * Updates an Android project. Must already have
   * an `AndroidManifest.xml`.
   */
  def update_project(path: String,
                     library: String = null,
                     name: String = null,
                     target: String = null,
                     subprojects: Boolean = false) {
    var command = s"$android update project -p $path"
    if (library != null) command += s" -l $library"
    if (name != null) command += s" -n $name"
    if (target != null) command += s" -t $target"
    if (subprojects) command += " -s"
    val output: String = command !!
    
    info(output)
  }
  
  /**
   * Creates a new Android project for a test package.
   */
  def create_test_project(path: String, name: String, main:String) {
    val command = s"$android create test-project -p $path -n $name -m $main"
    val output: String = command !!
    
    info(output)
  }
  
  /**
   * Updates the Android project for a test package. Must already
   * have an `AndroidManifest.xml`.
   */
  def update_test_project(main: String, path: String) {
    val command = s"$android update test-project -m $main -p $path"
    val output: String = command !!
    
    info(output)
  }
  
  /**
   * Creates a new Android library project.
   */
  def create_lib_project(name: String,
                         target: String,
                         pkg: String,
                         path: String) {
    val command = s"$android create lib-project -n $name " +
      s" -t $target -k $pkg -p $path"
    val output: String = command !!
    
    info(output)
  }
  
  /**
   * Updates an Android library project. Must already have an
   * `AndroidManifest.xml`.
   */
  def update_lib_project(path: String, target: String = null) {
    var command = s"$android update lib-project -p $path"
    if (target != null) command += s" -t $target"
    val output: String = command !!
    
    info(output)
  }
  
  /**
   * Creates a new UI test project.
   */
  def create_uitest_project(name: String, path: String, target: String) {
    val command = s"$android create uitest-project -n $name " +
      s" -p $path -t $target"
    val output: String = command !!
    
    info(output)
  }
  
  /**
   * Updates `adb` to support the USB devices declared in the SDK add-ons.
   */
  def update_adb {
    val command = s"$android update adb"
    val output: String = command !!;
    info(output)
  }
  
  /**
   * Updates the SDK by suggesting new platforms to install if available.
   */
  def update_sdk(filter: String = null,
                 noHttps: Boolean = false,
                 all: Boolean = false,
                 force: Boolean = false) {
    var command = s"$android update sdk -u"
    if (filter != null) command += s" -t $filter"
    if (noHttps) command += " -s"
    if (all) command += " -a"
    if (force) command += " -f"
    val output: String = command !!;
    info(output)
  }
}
