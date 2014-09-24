package clasp.utils

object LinuxProcessUtils {
  def appendToEnv(key: String, value: String) = 
    if (util.Properties.envOrNone(key).isEmpty)
      value
    else
      s"${util.Properties.envOrElse(key, "")}${new sys.SystemProperties()("path.separator")}${value}"
}