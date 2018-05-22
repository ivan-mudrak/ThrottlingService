package throttlingservice

import com.typesafe.config._

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import akka.actor.{ActorSystem, Cancellable}
import com.typesafe.scalalogging.Logger

import scala.collection.concurrent.TrieMap

trait ThrottlingService {
  val graceRps: Int // configurable
  val slaService: SlaService // use mocks/stubs for testing

  // Should return true if the request is within allowed RPS.
  def isRequestAllowed(token: Option[String]): Boolean
}

class ThrottlingServiceImpl(settings: Settings, _slaService: SlaService)(implicit system: ActorSystem) extends ThrottlingService {
  private val logger = Logger(classOf[ThrottlingServiceImpl])

  private val slaIntervalsPerSecond = settings.slaIntervalsPerSecond
  private val slaTimeoutMilliseconds = settings.slaTimeoutMilliseconds
  private var userRequestsCount: TrieMap[String, Int] = TrieMap()

  val userRequestsCountReset: Cancellable = system.scheduler.schedule(0.milliseconds, (1000 / slaIntervalsPerSecond).milliseconds) {
    userRequestsCount.clear()
    logger.info("User requests counter has been reset, count = " + userRequestsCount.values.sum)
  }

  override val graceRps: Int = settings.graceRps
  override val slaService: SlaService = new SlaServiceCache(_slaService)

  override def isRequestAllowed(token: Option[String]): Boolean = {
    logger.info("Request received, token: " + token)

    def isLimitReached(sla: Sla): Boolean = {
      userRequestsCount.get(sla.user) match {
        case Some(x) if x >= (sla.rps / slaIntervalsPerSecond) =>
          logger.info("Request denied for user = " + sla.user)
          false
        case Some(x) if x < (sla.rps / slaIntervalsPerSecond) =>
          userRequestsCount.update(sla.user, x + 1)
          logger.info("Request allowed for user = " + sla.user)
          true
        case None if sla.rps <= 0 =>
          logger.info("Request denied for user = " + sla.user)
          false
        case None if sla.rps > 0 =>
          userRequestsCount.+=((sla.user, 1))
          logger.info("Request allowed for user = " + sla.user)
          true
      }
    }

    val slaUnknownUser = Sla("", graceRps)
    token match {
      case None => isLimitReached(slaUnknownUser)
      case Some(someToken) =>
        val slaFuture: Future[Option[Sla]] = slaService.getSlaByToken(someToken)
        try {
          Await.result(slaFuture, slaTimeoutMilliseconds.milliseconds) match {
            case Some(someSla) => isLimitReached(someSla)
            case None => isLimitReached(slaUnknownUser)
          }
        } catch {
          case _: Exception => isLimitReached(slaUnknownUser)
        }
    }
  }
}

object ThrottlingServiceImpl {
  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem("ThrottlingService")
    val slaServiceMock: SlaService = new SlaServiceMock(Map(
      "aaa" -> Sla("Andy", 300),
      "sss" -> Sla("Sam", 200),
      "ddd" -> Sla("Andy", 300),
      "kkk" -> Sla("Kevin", 400),
      "nnn" -> Sla("Kevin", 400)
    ))
    val throttlingService = new ThrottlingServiceImpl(new SettingsFromConfig(ConfigFactory.load()), slaServiceMock)
    println(throttlingService.isRequestAllowed(Option("aaa")))
    println(throttlingService.isRequestAllowed(Option("aaa")))
    println(throttlingService.isRequestAllowed(Option("sss")))
    println(throttlingService.isRequestAllowed(Option("kkk")))
    println(throttlingService.isRequestAllowed(Option("aaa")))
    println(throttlingService.isRequestAllowed(Option("aaa")))
    println(throttlingService.isRequestAllowed(Option("sss")))
    println(throttlingService.isRequestAllowed(Option("ddd")))
    println(throttlingService.isRequestAllowed(Option("aaa")))
    println(throttlingService.isRequestAllowed(Option("nnn")))
    println(throttlingService.isRequestAllowed(Option("aaa")))
    println(throttlingService.isRequestAllowed(Option("aaa")))
    system.terminate()
  }
}


