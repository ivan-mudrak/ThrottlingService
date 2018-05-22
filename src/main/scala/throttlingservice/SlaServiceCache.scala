package throttlingservice

import com.typesafe.scalalogging.Logger

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

class SlaServiceCache(slaService: SlaService) extends SlaService {
  private val logger = Logger(classOf[SlaServiceCache])
  var slaCache: TrieMap[String, Option[Sla]] = TrieMap()

  override def getSlaByToken(token: String): Future[Option[Sla]] = slaCache.get(token) match {
    case Some(someSla) => Future(someSla)
    case None =>
      slaCache.+=((token, None))
      logger.info("SLA cache record has been created for token: " + token)
      val slaFuture: Future[Option[Sla]] = slaService.getSlaByToken(token)
      slaFuture.onComplete {
        case Success(maybeSla) => maybeSla match {
          case Some(someSla) =>
            slaCache.+=((token, Option(someSla)))
            logger.info("SLA cache has been updated with (token -> SLA): " +
              "(" + token + "-> (" + someSla.user + ", " + someSla.rps + "))")
          case None =>
        }
        case Failure(_) =>
      }
      Future(slaCache.get(token).flatten)
  }
}
