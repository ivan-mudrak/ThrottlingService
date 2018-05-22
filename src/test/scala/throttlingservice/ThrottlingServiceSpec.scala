package throttlingservice

import akka.actor.ActorSystem
import akka.testkit.{TestKit, TestProbe}
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}

import scala.util.Random

class ThrottlingServiceSpec(_system: ActorSystem)
  extends TestKit(_system)
    with Matchers
    with FlatSpecLike
    with BeforeAndAfterAll {

  def this() = this(ActorSystem("ThrottlingServiceSpec"))

  override def afterAll: Unit = {
    shutdown(system)
  }

  class SettingsForTest extends Settings {
    override val slaIntervalsPerSecond: Int = 10
    override val slaTimeoutMilliseconds: Int = 100
    override val graceRps: Int = 0
  }

  val slaTokens = Map(
    "aaa" -> Sla("Andy", 300),
    "sss" -> Sla("Sam", 200),
    "ddd" -> Sla("Andy", 300),
    "kkk" -> Sla("Kevin", 400),
    "nnn" -> Sla("Kevin", 400)
  )
  val slaServiceMock: SlaService = new SlaServiceMock(slaTokens)
  val throttlingService = new ThrottlingServiceImpl(new SettingsForTest(), slaServiceMock)
  "ThrottlingService" should "deny authorized token access when not enough time given to retrieve SLA" +
    " and graceRps is 0" in {
    assert(!throttlingService.isRequestAllowed(Some("aaa")))
  }

  "ThrottlingService" should "allow authorized token access when enough time is given to retrieve SLA" in {
    assert(!throttlingService.isRequestAllowed(Some("aaa")))
    Thread.sleep(250)
    assert(throttlingService.isRequestAllowed(Some("aaa")))
  }

  "ThrottlingService" should "allow number of requests not less than acceptable level" in {
    import scala.concurrent.duration._

    val deadline: Int = 10
    val deadlineInSeconds: Deadline = deadline.seconds.fromNow

    var count = 0
    val acceptableLevel = 0.90
    val tokens = slaTokens.keys.toSeq
    while (deadlineInSeconds.hasTimeLeft) {
      if (throttlingService.isRequestAllowed(Some(tokens(Random.nextInt(tokens.length))))) count += 1
    }
    assert(count >= acceptableLevel * deadline * slaTokens.values.toSeq.distinct.map(_.rps).sum)
  }
}
