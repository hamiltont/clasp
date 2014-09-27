package clasp.core

import org.scalatest.junit.AssertionsForJUnit
import org.junit.Assert._
import org.junit.Test
import org.junit.Before
import org.junit.After

import java.io.File
import org.apache.commons.io.FileUtils

class SDKTest extends AssertionsForJUnit {
  import clasp.core.sdktools.sdk._
  
  @Before def initialize {
    start_adb
  }
  @After def tearDown {
    kill_adb
  }
  
  @Test def testAndroidAVDCreation { 
    assert(get_targets contains "android-17")
    assert(get_sdk contains
      "Intel x86 Atom System Image, Android API 17, revision 1")

    // Assume every target will have `armeabi-v7a`.
    val allTargetABIs = get_target_ABIs
    // println(allTargetABIs mkString " | ")
    for (targetABIs <- allTargetABIs) {
      assert(targetABIs.contains("armeabi-v7a")
        || targetABIs.contains("armeabi")
        || targetABIs.contains("x86"))
    }
    
    val avdName = "clasp-test"
    val avdNewName = avdName + "-new-name"
    assert(create_avd(avdName, "android-17", "armeabi-v7a", true))
    assert(!create_avd(avdName, "android-17", "armeabi-v7a", false))
    val avds = get_avd_names
    assert(avds contains avdName)
    assertFalse(avds contains "not-clasp-test")
    
    val home = sys.env("HOME")
    assert(move_avd(avdName, home + "/" + avdName, avdNewName))
    
    assert(delete_avd(avdNewName))
    assert(!delete_avd(avdNewName))
  }
 
 @Test def testAndroidAVDCreationNoABIname { 
    assert(get_targets contains "android-17")
    assert(get_sdk contains
      "Intel x86 Atom System Image, Android API 17, revision 1")

    // Assume every target will have `armeabi-v7a`.
    val allTargetABIs = get_target_ABIs
    // println(allTargetABIs mkString " | ")
    for (targetABIs <- allTargetABIs) {
      assert(targetABIs.contains("armeabi-v7a")
        || targetABIs.contains("armeabi") || 
        targetABIs.contains("x86"))
    }
    
    val avdName = "clasp-test"
    val avdName2 = "clasp-test2"
    val avdNewName = avdName + "-new-name"
    val avdNewName2 = avdName2 + "-new-name"
    assert(create_avd(avdName, "android-17", true))
    assert(create_avd(avdName2, "1", true))
    assert(!create_avd(avdName, "android-17", false))
    assert(!create_avd(avdName2, "1", false))
    val avds = get_avd_names
    assert(avds contains avdName)
    assert(avds contains avdName2)
    assertFalse(avds contains "not-clasp-test")
    
    val home = sys.env("HOME")
    assert(move_avd(avdName, home + "/" + avdName, avdNewName))
    assert(move_avd(avdName2, home + "/" + avdName2, avdNewName2))
        
    assert(delete_avd(avdNewName))
    assert(!delete_avd(avdNewName))
    assert(delete_avd(avdNewName2))
    assert(!delete_avd(avdNewName2))
  }

  @Test def testAndroidProjectCreation {
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
    
    create_uitest_project("claspUitest", projDirStr, "android-17")
    
    for (fileStr <- dirList) {
      val file: File = new File(fileStr)
      FileUtils.deleteDirectory(file)
    }
  }
  
  @Test def testEmulator {
    assertEquals("21.1.0", get_emulator_version)
    
    val avdName = "clasp-test"
    assert(create_avd(avdName, "android-17", "armeabi-v7a", true))
    val camList = get_webcam_list(avdName)
    //assert(camList contains "webcam0")
    
    var opts = new clasp.core.sdktools.EmulatorOptions
    opts = opts.copy(ui = opts.ui.copy(noBootAnim = Some(true)))
    opts = opts.copy(ui = opts.ui.copy(noWindow = Some(true)))
    // Unsupported: 
    // emuOpts.noSnapShotLoad = true    
    
    opts = opts.copy(network = opts.network.copy(consolePort = Some(5554)))
    opts = opts.copy(avdName = Some(avdName))
    val proc = start_emulator(opts)
    val serial = "emulator-5554"

    val tmpFileStr = sys.env("HOME") + "/clasp-temp-file"
    val backupStr = sys.env("HOME") + "/clasp-backup"
    val tmpFile = new File(tmpFileStr)
    val backupFile = new File(backupStr)
    try {
	    wait_for_device(serial)
	    
	    logcat_regex(serial, "Boot is finished")
	    
	    val devList = get_device_list
	    assert(devList contains serial)
	    
	    val packages = get_installed_packages(serial)
	    assert(packages contains "com.android.browser")
	    
	    tmpFile.createNewFile()
	    push_to_device(serial, tmpFileStr, "/data")
	    pull_from_device(serial, "/data/clasp-temp-file", tmpFileStr)
	    
	    remote_shell(serial, "ls")
    } finally {
	    tmpFile.delete()
	    backupFile.delete()
	    kill_emulator(serial)
	    delete_avd(avdName)
    }
  }
  
  @Test def testTelnet {
    val avdName = "clasp-test"
    assert(create_avd(avdName, "android-17", "armeabi-v7a", true))
    
    var opts = new clasp.core.sdktools.EmulatorOptions
    opts = opts.copy(ui = opts.ui.copy(noBootAnim = Some(true)))
    opts = opts.copy(ui = opts.ui.copy(noWindow = Some(true)))
    // Unsupported: 
    // emuOpts.noSnapShotLoad = true    

    val port = 5554
    opts = opts.copy(network = opts.network.copy(consolePort = Some(port)))
    opts = opts.copy(avdName = Some(avdName))
    val proc = start_emulator(opts)
    val serial = "emulator-5554"
    
    val cdmaDump = sys.env("HOME") + "/cdma-dump"
    val networkDump = sys.env("HOME") + "/network-dump"
    val cdmaDumpFile = new File(cdmaDump)
    val networkDumpFile = new File(cdmaDump)
    try {
	    wait_for_device(serial)
	    logcat_regex(serial, "Boot is finished")
	    
	    val events = get_event_types(5554)
	    assertTrue(events contains "EV_KEY")
	    val eventCodes = get_event_codes(port, "EV_KEY")
	    assertTrue(eventCodes contains "KEY_HOME")
	    send_hardware_event(port, "EV_KEY", "KEY_HOME", "0")
	    // TODO: This crashes!
	    //send_event_text(port, "text")
	    
	    send_geo_nmea(port, "$GPGGA,001431.092,0118.2653,N,10351.1359,E,0,00,,-19.6,M,4.1,M,,0000*5B")
	    send_geo_fix(port, "-83.411629", "28.054553")
	    
	    gsm_call(port, "0123456789")
	    var gsmList = get_gsm_list(port)
	    assertTrue(gsmList contains "inbound from 0123456789")
	    gsm_cancel(port, "0123456789")
	    gsmList = get_gsm_list(port)
	    assertFalse(gsmList contains "inbound from 0123456789")
	    var gsmStatus = gsm_status(port)
	    assertEquals("home", gsmStatus(0))
	    assertEquals("home", gsmStatus(1))
	    gsm_voice(port, "roaming")
	    gsm_data(port, "denied")
	    gsmStatus = gsm_status(port)
	    assertEquals("roaming", gsmStatus(0))
	    assertEquals("denied", gsmStatus(1))
	    gsm_signal(20, 7)
	    
	    var cdmaStatus = get_cdma_status(port)
	    assertEquals("0", cdmaStatus(0))
	    assertEquals("0", cdmaStatus(1))
	    assertEquals("0", cdmaStatus(2))
	    assertEquals("0", cdmaStatus(3))
	    cdma_ssource(port, "nv")
	    set_cdma_speed(port, "10")
	    set_cdma_delay(port, "15")
	    cdmaStatus = get_cdma_status(port)
	    assertEquals("10000", cdmaStatus(0))
	    assertEquals("15", cdmaStatus(2))
	    cdma_capture_start(port, cdmaDump)
	    Thread.sleep(1000)
	    assertTrue(cdmaDumpFile.exists())
	    cdma_capture_stop(port)
	    
	    var networkStatus = get_network_status(port)
	    assertEquals("10000", networkStatus(0))
	    assertEquals("15", networkStatus(2))
	    set_network_speed(port, "0")
	    set_network_delay(port, "0")
	    networkStatus = get_network_status(port)
	    assertEquals("0", networkStatus(0))
	    assertEquals("0", networkStatus(2))
	    network_capture_start(port, networkDump)
	    Thread.sleep(1000)
	    assertTrue(networkDumpFile.exists())
	    network_capture_stop(port)
	    
	    var powerState = get_power_state(port)
	    assertEquals("online", powerState(0))
	    assertEquals("Charging", powerState(1))
	    assertEquals("Good", powerState(2))
	    assertEquals("true", powerState(3))
	    assertEquals("50", powerState(4))
	    
	    power_ac_state(port, false)
	    power_status(port, "full")
	    power_present(port, false)
	    power_health(port, "dead")
	    power_capacity(port, 42)
	    powerState = get_power_state(port)
	    assertEquals("offline", powerState(0))
	    assertEquals("Full", powerState(1))
	    assertEquals("Dead", powerState(2))
	    assertEquals("false", powerState(3))
	    assertEquals("42", powerState(4))
	    
	    var redirList = get_redir_list(port)
	    assertTrue(redirList contains "no active redirections")
	    redir_add(port, "tcp", 5000, 6000)
	    redirList = get_redir_list(port)
	    println(redirList)
	    assertTrue(redirList contains "tcp:5000  => 6000 ")
	    
	    assertEquals(avdName, get_avd_name(port))
	    assertEquals("running", get_avd_status(port))
	    avd_stop(port)
	    assertEquals("stopped", get_avd_status(port))
	    avd_start(port)
	    
	    var sensorStatus = get_sensor_status(port)
	    assertEquals("enabled", sensorStatus(0))
	    assertEquals("enabled", sensorStatus(1))
	    assertEquals("enabled", sensorStatus(2))
	    assertEquals("enabled", sensorStatus(3))
	    assertEquals("enabled", sensorStatus(4))
	    assertEquals("0:0:0", get_sensor_val(port, "magnetic-field"))
	    set_sensor_val(port, "magnetic-field", "1:1:1")
	    assertEquals("1:1:1", get_sensor_val(port, "magnetic-field"))
    } finally {
        cdmaDumpFile.delete()
        networkDumpFile.delete()
	    kill_emulator(serial)
	    delete_avd(avdName)
    }
  }
}
