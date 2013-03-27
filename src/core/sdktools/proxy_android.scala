package core.sdktools

import scala.sys.process.stringToProcess

import sdk_config.log.error
import sdk_config.log.info

/**
 * Provides an interface to the
 * [[http://developer.android.com/tools/help/android.html `android`]]
 * command line tool.
 * 
 * This, along with other components of the Android SDK, is included in
 * [[core.sdktools.sdk]].
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
    
    var command = s"$android create avd -n $name -t $target -b $abiName"
    if (force) {
      command += " --force"
    }
    
    val output: String = "echo no" #| command !!;
    info(output)
    true
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
