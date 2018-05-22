package throttlingservice

import com.typesafe.config._

trait Settings {
  val slaIntervalsPerSecond: Int
  val slaTimeoutMilliseconds: Int
  val graceRps: Int
}

class SettingsFromConfig(config: Config) extends Settings {
  override val slaIntervalsPerSecond: Int = config.getInt("throttling-service.slaIntervalsPerSecond")
  override val slaTimeoutMilliseconds: Int = config.getInt("throttling-service.slaTimeoutMilliseconds")
  override val graceRps: Int = config.getInt("throttling-service.graceRps")
}
