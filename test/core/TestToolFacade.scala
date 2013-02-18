package core

import org.scalatest.junit.AssertionsForJUnit
import org.junit.Assert._
import org.junit.Test
import org.junit.Before

import java.io.File
import org.apache.commons.io.FileUtils

class ToolFacadeTest extends AssertionsForJUnit {
  import core.sdktools.sdk._
  
  @Test def testAndroidAVDCreation() { 
    assert(get_targets contains "android-17")
    assert(get_sdk contains
      "Intel x86 Atom System Image, Android API 17, revision 1")
    
    val avdName = "clasp-test"
    val avdNewName = avdName + "-new-name"
    assert(create_avd(avdName, "android-17", true))
    assert(!create_avd(avdName, "android-17"))
    val avds = get_avd_names
    assert(avds contains avdName)
    assertFalse(avds contains "not-clasp-test")
    
    val home = sys.env("HOME")
    assert(move_avd(avdName, home + "/" + avdName, avdNewName))
    
    assert(delete_avd(avdNewName))
    assert(!delete_avd(avdNewName))
  }
  
  @Test def testAndroidProjectCreation() {
    assert(get_targets contains "android-17")
    
    val projDirStr = sys.env("HOME") + "/clasp-temp"
    val testProjDirStr = sys.env("HOME") + "/clasp-temp-test"
    val libProjDirStr = sys.env("HOME") + "/clasp-lib-test"
    val uiTestStr = sys.env("HOME") + "/clasp-uitest"
    val dirList = List(projDirStr, testProjDirStr, libProjDirStr, uiTestStr)
    
    create_project("testProject",
    			   "android-17",
    			   projDirStr,
    			   "clasp.test",
    			   "claspActivity")

    update_project(projDirStr, null, "newname", null, true)
    create_test_project(testProjDirStr, "claspTest", projDirStr)
    update_test_project(projDirStr, testProjDirStr)
    
    create_lib_project("claspLib", "android-17", "clasp.test", libProjDirStr)
    update_lib_project(libProjDirStr)
    
    update_project(projDirStr, libProjDirStr)
    
    create_uitest_project("claspUitest", uiTestStr, "android-17")
    
    for (fileStr <- dirList) {
      val file: File = new File(fileStr)
      FileUtils.deleteDirectory(file)
    }
  }
  
  @Test def testEmulator() {
    assertEquals("21.1.0", get_emulator_version)
    
    val avdName = "clasp-test"
    assert(create_avd(avdName, "android-17", true))
    val camList = get_webcam_list(avdName)
    assert(camList contains "webcam0")
    
    var emuOpts = new core.sdktools.EmulatorOptions
    emuOpts.noBootAnim = true
    val emulatorInfo = start_emulator(avdName, 5554, emuOpts)
    val proc = emulatorInfo._1
    val serial = emulatorInfo._2
    
    Thread.sleep(5000);
    
    val devList = get_device_list
    println(devList)
    // assert(devList contains serial)
    
    kill_emulator(serial)
    delete_avd(avdName)
  }
  
}