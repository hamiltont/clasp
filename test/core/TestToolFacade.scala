package core

import org.scalatest.junit.AssertionsForJUnit
import org.junit.Assert._
import org.junit.Test
import org.junit.Before

import java.io.File
import org.apache.commons.io.FileUtils

class ToolFacadeTest extends AssertionsForJUnit {
  import core.ToolFacade._
  
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
    create_project("testProject",
    			   "android-17",
    			   projDirStr,
    			   "clasp.test",
    			   "claspActivity")

    update_project(projDirStr, null, "newname", null, true)
    create_test_project(testProjDirStr, "clasptest", projDirStr)
    update_test_project(projDirStr, testProjDirStr)
    
    val projDirFile: File = new File(projDirStr)
    FileUtils.deleteDirectory(projDirFile)
  }
}