package examples

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Try

import mhtml.Rx
import mhtml.Var

object Utils {
  def fromFuture[T](future: Future[T])(implicit ec: ExecutionContext): Rx[Option[Try[T]]] = {
    val result = Var(Option.empty[Try[T]])
    future.onComplete(x => result := Some(x))
    result
  }
}
