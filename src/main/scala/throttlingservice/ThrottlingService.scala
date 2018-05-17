package throttlingservice

import com.typesafe.config._

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import akka.actor.{ActorSystem, Cancellable}
import com.typesafe.scalalogging.Logger

trait ThrottlingService {
  val graceRps: Int // configurable
  val slaService: SlaService // use mocks/stubs for testing

  // Should return true if the request is within allowed RPS.
  def isRequestAllowed(token: Option[String]): Boolean
}

class ThrottlingServiceImpl(implicit system: ActorSystem) extends ThrottlingService {
  private val logger = Logger(classOf[ThrottlingServiceImpl])
  private val conf = ConfigFactory.load()
  private val slaIntervalsPerSecond = conf.getInt("throttling-service.slaIntervalsPerSecond")
  private val slaTimeoutMilliseconds = conf.getInt("throttling-service.slaTimeoutMilliseconds")
  private var userRequestsCount: scala.collection.mutable.Map[String, Int] = scala.collection.mutable.Map()

  val userRequestsCountReset: Cancellable = system.scheduler.schedule(0.milliseconds, (1000 / slaIntervalsPerSecond).milliseconds) {
    userRequestsCount.map(elm => (elm._1, 0))
    logger.info("User requests counter has been reset")
  }

  override val graceRps: Int = conf.getInt("throttling-service.graceRps")
  override val slaService: SlaService = new SlaServiceCache

  override def isRequestAllowed(token: Option[String]): Boolean = {
    logger.info("Request received, token: " + token)

    def isLimitReached(sla: Sla): Boolean = {
      userRequestsCount.get(sla.user) match {
        case Some(x) if x >= (sla.rps / slaIntervalsPerSecond) =>
          false
        case Some(x) if x < (sla.rps / slaIntervalsPerSecond) =>
          userRequestsCount.update(sla.user, x + 1)
          true
        case None if sla.rps <= 0 =>
          false
        case None if sla.rps > 0 =>
          userRequestsCount.+=((sla.user, 1))
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
    val throttlingService = new ThrottlingServiceImpl
    println(throttlingService.isRequestAllowed(Option("aaa")))
    println(throttlingService.isRequestAllowed(Option("aaa")))
    println(throttlingService.isRequestAllowed(Option("sss")))
    println(throttlingService.isRequestAllowed(Option("kkk")))
    println(throttlingService.isRequestAllowed(Option("aaa")))
    println(throttlingService.isRequestAllowed(Option("aaa")))
    println(throttlingService.isRequestAllowed(Option("sss")))
    println(throttlingService.isRequestAllowed(Option("aaa")))
    println(throttlingService.isRequestAllowed(Option("aaa")))
    println(throttlingService.isRequestAllowed(Option("nnn")))
    println(throttlingService.isRequestAllowed(Option("aaa")))
    println(throttlingService.isRequestAllowed(Option("aaa")))
    system.terminate()
  }
}


