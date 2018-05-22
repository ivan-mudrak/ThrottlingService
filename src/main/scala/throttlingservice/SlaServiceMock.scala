package throttlingservice

import com.typesafe.config.ConfigFactory
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class SlaServiceMock(sla: Map[String, Sla]) extends SlaService {
  private val conf = ConfigFactory.load()
  private val slaServiceDelayMilliseconds = conf.getInt("throttling-service-test.slaServiceDelayMilliseconds")

  override def getSlaByToken(token: String): Future[Option[Sla]] = Future {
    Thread.sleep((slaServiceDelayMilliseconds * scala.util.Random.nextDouble()).toInt)
    sla.get(token)
  }
}