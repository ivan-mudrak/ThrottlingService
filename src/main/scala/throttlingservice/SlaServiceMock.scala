package throttlingservice

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object SlaServiceMock extends SlaService {
  val sla = Map(
    "Andy" -> 300,
    "Sam" -> 200,
    "Kevin" -> 400
  )
  val tokens = Map(
    "aaa" -> "Andy",
    "sss" -> "Sam",
    "ddd" -> "Andy",
    "kkk" -> "Kevin",
    "nnn" -> "Kevin"
  )

  override def getSlaByToken(token: String): Future[Option[Sla]] = Future {
    Thread.sleep((250 * scala.util.Random.nextDouble()).toInt)
    tokens.get(token) match {
      case None => None
      case Some(user) =>
        sla.get(user) match {
          case None => None
          case Some(rps) => Option(Sla(user, rps))
        }
    }
  }
}