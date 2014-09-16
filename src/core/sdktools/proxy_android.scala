package clasp.core.sdktools

import scala.language.postfixOps
import scala.sys.process._
import util.control.Breaks._
import sdk_config.log.error
import sdk_config.log.info
import java.nio.file.Files
import java.nio.file.Paths
import java.util.regex.Pattern

/**
 * Provides an interface to the
 * [[http://developer.android.com/tools/help/android.html `android`]]
 * command line tool.
 *
 * This, along with other components of the Android SDK, is included in
 * [[clasp.core.sdktools.sdk]].
 */
trait AndroidProxy {
  val android: String = sdk_config.config.getString(sdk_config.android_config)
  import sdk_config.log.{ error, debug, info, trace }

  def valid = {
    debug(s"Checking for binary at $android")
    Files.exists(Paths.get(android))
  }

  /**
   * Return the available Android Virtual Devices.
   */
  def get_avd_names: Vector[String] = {
    val command = s"$android list avd";
    val output: String = command !!
    val regex = """Name: (.*)""".r

    val result = for (regex(name) <- regex findAllIn output) yield name
    val res = result.toVector
    debug(s"get_avd_names: ${res.mkString(" ")}")
    res
  }

  /**
   * Lists existing targets.
   */
  def get_targets: Vector[String] = {
    val command = s"$android list targets";
    val output: String = command !!
    val regex = "id: [0-9]* or \"(.*)\"".r

    val result = for (regex(target) <- regex findAllIn output) yield target
    val res = result.toVector
    debug(s"get_targets: ${res.mkString(" ")}")
    res
  }

  /**
   * List ABIs corresponding with each entry from the existing
   * targets Vector.
   */
  def get_target_ABIs: Vector[Vector[String]] = {
    val command = s"$android list targets";
    val output: String = command !!

    // New or old format?
    if (output.contains("Tag/ABIs")) {
      // New: Tag/ABIs : android-tv/armeabi-v7a, android-tv/x86, default/armeabi-v7a, default/x86
      
      debug("get_target_ABIs: Using new method")
      
      val regex = "Tag/ABIs : (.*)".r
      val splitR = ".+?/(.+?)".r
      val result = for (regex(target) <- regex findAllIn output)
        yield {  
        (for (pair <- target.split(", ")) yield 
          pair match {
            case splitR(abi) => abi
            case _ => "" 
          }
        ).toVector }
      val res = result.toVector
      debug(s"get_target_abis: ${res.mkString(" ")}")
      res
    } else {
      // Old
      debug("get_target_ABIs: Using old method")
      val regex = "ABIs : (.*)".r
      val result = for (regex(target) <- regex findAllIn output)
        yield target.split(", ").toVector
      val res = result.toVector
      debug(s"get_target_abis: ${res.mkString(" ")}")
      res
    }
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
    force: Boolean): Boolean = {
    if (!force && (get_avd_names contains name)) {
      val errorMsg = s"Error: AVD '$name' already exists."
      error(errorMsg)
      return false
    }

    val targetIndex = get_target_index(target)
    debug(s"Found index `${targetIndex}` for target `$target`")
    if (targetIndex.isEmpty) {
      error(s"Unable to create AVD `$name`, target `$target` not found")
      return false
    }

    val abis = get_target_ABIs(targetIndex.get)
    debug(s"Found ABIs `${abis.mkString(" ")}` for $targetIndex")

    breakable {
      for (abi <- abis) {
        if (abi.equals(abiName)) {
          break
        }
      }
      error(s"Error: ABIname '$abiName' does not exist for specified target.")
      return false
    }

    var command: String = s"$android create avd -n $name -t $target -b $abiName"
    if (force) {
      command += " --force"
    }
    info(s"Building an AVD using $command")

    var create = Process(command)
    var output = List[String]()

    val logger = ProcessLogger(line => output ::= "out: " + line,
      line => output ::= "err : " + line);
    // TODO No need to create two processes, just pass "no" directly to the 
    // stdin of create
    val echocommand = """echo no"""
    val exit = echocommand.#|(create).!(logger)

    output = output.reverse
    output foreach info

    (exit == 0)
  }

  /**
   * Creates a new Android Virtual Device with no ABI specified.
   *  If multiple ABIs are found, default creat avd with ABI Name
   *  1) armeabi-v7a 2) armeabi 3) x86 4) first ABI listed.
   */
  def create_avd(name1: String,
    target2: String,
    force1: Boolean): Boolean = {

    var targetID = get_target_index(target2)
    val abis = get_target_ABIs(targetID.get)

    val armV7Reg = "\\barmeabi-v7a\\b"
    val armReg = "\\barmeabi\\b"
    val x86 = "\\bx86\\b"
    var abiName: Option[String] = None

    breakable {
      for (abi <- abis) {
        if (abi.matches(armV7Reg)) {
          abiName = Option("armeabi-v7a")
          break
        } else if (abi.matches(armReg)) {
          abiName = Option("armeabi")
          break
        } else if (abi.matches(x86)) {
          abiName = Option("x86")
          break
        }
      }
    }
    if (abiName.isEmpty) {
      abiName = Option(abis(0))
    }
    return create_avd(name1, target2, abiName.get, force1)
  }

  /**
   * get_target_index returns the target index of the specified target name
   * or ID. The index is in the list targetNames.
   * If no index is found then an error message is returned.
   */
  def get_target_index(targetNameOrID: String): Option[Int] = {

    if (targetNameOrID.matches("^[0-9]+$")) {
      debug(s"get_target_index: Detected id")
      return Option(targetNameOrID.toInt)
    } else {
      debug(s"get_target_index: Detected name")
      val targetNames = get_targets
      val nameReg = "\\b" + Pattern.quote(targetNameOrID) + "\\b"
      var index = 0

      for (name <- targetNames) {
        if (name.matches(nameReg)) {
          return Option(index)
        }
        index = index + 1
      }

      error(s"Error: TargetABI '$targetNameOrID' does not exist.")
      return None
    }
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
      error(s"Error: AVD '$name' does not exist.")
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
  def create_test_project(path: String, name: String, main: String) {
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
