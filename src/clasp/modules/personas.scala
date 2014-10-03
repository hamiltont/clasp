
package clasp.modules

import java.text.DecimalFormat
import java.util.Random
import java.util.UUID
import org.slf4j.LoggerFactory
import clasp.core.sdktools.EmulatorOptions
import clasp.core.sdktools.sdk
import clasp.core.sdktools.AndroidKeys

// TODO: Because Android changes so much between versions, and
// because configuring personas with adb isn't very well documented
// or in use, there will probably be a lot of difficulties setting
// personas across multiple versions.
//
// A good first step to addressing this would be to write tests
// across many different Android versions to locate areas where
// parts of personas are not set, and then document those in
// `personas.scala`.
//
// Next, separate cases should be put in here to handle
// each version and pass the tests.
object Personas {
  lazy val log = LoggerFactory.getLogger(getClass())
  import log.{error, debug, info, trace}

  private val rand = new Random()

  def applyAll(serialID: String, emuOpts: EmulatorOptions) {
    applyContacts(serialID, emuOpts)
    applyCalendar(serialID, emuOpts)
  }

  def applyContacts(serialID: String, emuOpts: EmulatorOptions) {
    goHome(serialID) // Go home to clear anything on the screen.

    if (emuOpts.clasp.randomContacts.getOrElse(0) > 0) {
      val df3 = new DecimalFormat("000")
      val df4 = new DecimalFormat("0000")
      for (i <- 0 to emuOpts.clasp.randomContacts.get) {
        val num1 = rand.nextInt(743)
        val num2 = rand.nextInt(10000)
        val name = UUID.randomUUID().toString()
        val num = s"${df3.format(num1)}-${df4.format(num2)}"
        info(s"Creating contact: $name $num")
        sdk.remote_shell(serialID,
          "am start -a android.intent.action.INSERT " +
          "-t vnd.android.cursor.dir/contact " +
          s"-e name $name -e phone $num")

        dpadCenter(serialID) // Accept any prompts.
        goHome(serialID) // Go home to save the contact and exit.
      }
    }
  }

  // TODO: Calendar events won't go through unless an account
  // is set up on the device.
  def applyCalendar(serialID: String, emuOpts: EmulatorOptions) {
    goHome(serialID) // Go home to clear anything on the screen.

    if (emuOpts.clasp.randomCalendarEvents.getOrElse(0) > 0) {
      for (i <- 0 to emuOpts.clasp.randomCalendarEvents.get) {
        val title = UUID.randomUUID().toString()
        val begin = System.currentTimeMillis() +
          rand.nextInt(10*24*60*60) - 5*24*60*60
        val end = begin + rand.nextInt(3*60*60)
        info(s"Creating event: $title $begin-$end")
        sdk.remote_shell(serialID,
          "am start -a android.intent.action.INSERT " +
          "-t vnd.android.cursor.item/event " +
          s"-e title $title -e beginTime $begin -e endTime $end")

        dpadCenter(serialID) // Accept any prompts.
        goHome(serialID) // Go home to save the contact and exit.
      }
    }
  }

  // Convenience aliases.
  private def goHome(serialID: String) = {
    //val keycodes = AndroidKeys.get_keycodes(1.6)
    
    //sdk.send_keyevent(serialID, keycodes.KEY_HOME)
    // for (i <- 0 to 3) sdk.remote_shell(serialID, "input keyevent 3")
    for (i <- 0 to 3) sdk.remote_shell(serialID, "input keyevent 122")
  }
    

  private def dpadCenter(serialID: String)  =
    for (i <- 0 to 3) sdk.remote_shell(serialID, "input keyevent 23")
}
