package core

import org.scalatest.junit.AssertionsForJUnit
import org.junit.Assert._
import org.junit.Test
import org.junit.Before

class ToolFacadeTest extends AssertionsForJUnit {
  import core.ToolFacade._
  
  @Before def initialize() {
  }
  
  @Test def testAndroid() { 
    assert(get_targets contains "android-17")
    
    assert(create_avd("clasp-test", "android-17", true))
    assert(!create_avd("clasp-test", "android-17"))
    val avds = get_avd_names
    assert(avds contains "clasp-test")
    assertFalse(avds contains "not-clasp-test")
  }
}