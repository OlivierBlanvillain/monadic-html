package mhtml.future

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Try
import mhtml.{Rx, Var}

object Utils {
  /** Convert a `Future` to a `Rx` */
  def futureToRx[T](f: Future[T])(implicit ec: ExecutionContext): Rx[Option[Try[T]]] = {
    val result: Var[Option[Try[T]]] = Var(None)
    f.onComplete(x => result := Some(x))
    result
  }
}

object syntax {
  implicit class FutureToRxSyntax[T](f: Future[T]) {
    def toRx(implicit ec: ExecutionContext): Rx[Option[Try[T]]] =
      Utils.futureToRx(f)
  }
}


