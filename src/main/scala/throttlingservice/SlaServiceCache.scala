package throttlingservice

import com.typesafe.scalalogging.Logger

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

class SlaServiceCache extends SlaService {
  private val logger  = Logger(classOf[SlaServiceCache])
  val slaService: SlaService = SlaServiceMock
  var slaCache: scala.collection.mutable.Map[String, Option[Sla]] = scala.collection.mutable.Map()

  override def getSlaByToken(token: String): Future[Option[Sla]] = slaCache.get(token) match {
    case Some(someSla) => Future(someSla)
    case None =>
      slaCache.+=((token, None))
      logger.info("SLA cache record has been created for token: " + token)
      val slaFuture: Future[Option[Sla]] = slaService.getSlaByToken(token)
      slaFuture.onComplete {
        case Success(maybeSla) => maybeSla match {
          case Some(someSla) =>
            slaCache.update(token, Option(someSla))
            logger.info("SLA cache has been updated with (token -> SLA): ("
              + token + "-> (" + someSla.user + ", " + someSla.rps + "))")
          case None =>
        }
        case Failure(_) =>
      }
      Future(slaCache.get(token).flatten)
  }
}
