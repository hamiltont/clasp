package core.sdktools

import scala.sys.process.stringToProcess

import sdk_config.log.error
import sdk_config.log.info


trait AndroidProxy {
  val android:String = sdk_config.config.getString(sdk_config.android_config)
  import sdk_config.log.{error, debug, info, trace}
  
  def get_avd_names: Vector[String] = {
    val command = s"$android list avd";
    val output: String = command !!
    val regex = """Name: (.*)""".r

    val result = for (regex(name) <- regex findAllIn output) yield name
    result.toVector
  }
  
  def get_targets: Vector[String] = {
    val command = s"$android list targets";
    val output: String = command !!
    val regex = """id: [0-9]* or \"(.*)\"""".r
    
    val result = for (regex(target) <- regex findAllIn output) yield target
    result.toVector
  }
  
  def get_sdk: Vector[String] = {
    val command = s"$android list sdk";
    val output: String = command !!
    val regex = """[0-9]+- (.*)""".r
    
    val result = for (regex(target) <- regex findAllIn output) yield target
    result.toVector
  }

  def create_avd(name: String,
                 target: String,
                 force: Boolean = false): Boolean = {
    if (!force && (get_avd_names contains name)) {
      error(s"Error: AVD '$name' already exists.")
      return false
    }
    
    var command = s"$android create avd -n $name -t $target"
    if (force) {
      command += " --force"
    }
    
    val output: String = "echo no" #| command !!;
    info(output)
    true
  }

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
  
  def update_avd(name: String) {
    val command = s"$android update avd -n $name"
    val output: String = command !!
    
    info(output)
  }
  
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
  
  def create_test_project(path: String, name: String, main:String) {
    val command = s"$android create test-project -p $path -n $name -m $main"
    val output: String = command !!
    
    info(output)
  }
  
  def update_test_project(main: String, path: String) {
    val command = s"$android update test-project -m $main -p $path"
    val output: String = command !!
    
    info(output)
  }
  
  def create_lib_project(name: String,
                         target: String,
                         pkg: String,
                         path: String) {
    val command = s"$android create lib-project -n $name " +
      s" -t $target -k $pkg -p $path"
    val output: String = command !!
    
    info(output)
  }
  
  def update_lib_project(path: String, target: String = null) {
    var command = s"$android update lib-project -p $path"
    if (target != null) command += s" -t $target"
    val output: String = command !!
    
    info(output)
  }
  
  def create_uitest_project(name: String, path: String, target: String) {
    val command = s"$android create uitest-project -n $name " +
      s" -p $path -t $target"
    val output: String = command !!
    
    info(output)
  }
  
  def update_adb {
    val command = s"$android update adb"
    val output: String = command !!;
    info(output)
  }
  
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
