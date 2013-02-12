package core

import org.scalatest.junit.AssertionsForJUnit
import org.junit.Assert._
import org.junit.Test
import org.junit.Before

class ToolFacadeTest extends AssertionsForJUnit {
  import core.ToolFacade._
  
  @Before def initialize() {
  }
  
  @Test def verifyTemplate() {
    assertEquals("ScalaTest is easy!", "ScalaTest is easy!")
  }
}