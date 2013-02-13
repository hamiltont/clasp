package core

import org.scalatest.junit.AssertionsForJUnit
import org.junit.Assert._
import org.junit.Test
import org.junit.Before

class ToolFacadeTest extends AssertionsForJUnit {
  import core.ToolFacade._
  
  @Test def testAndroid() { 
    assert(get_targets contains "android-17")
    assert(get_sdk contains
      "Intel x86 Atom System Image, Android API 17, revision 1")
    
    assert(create_avd("clasp-test", "android-17", true))
    assert(!create_avd("clasp-test", "android-17"))
    val avds = get_avd_names
    assert(avds contains "clasp-test")
    assertFalse(avds contains "not-clasp-test")
    
    assert(delete_avd("clasp-test"))
    assert(!delete_avd("clasp-test"))
  }
}