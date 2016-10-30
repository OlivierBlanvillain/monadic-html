package mhtml.examples

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

import mhtml.Rx
import mhtml.Var
import org.scalajs.dom._
import org.scalajs.dom.raw.HTMLInputElement

object Utils {
  def fromFuture[T](future: Future[T]): Rx[Option[Try[T]]] = {
    val result = Var(Option.empty[Try[T]])
    future.onComplete(x => result := Some(x))
    result
  }

  def inputEvent(f: HTMLInputElement => Unit): Event => Unit = {
    event: Event =>
      event.target match {
        case e: HTMLInputElement =>
          f(e)
        case _ =>
      }
  }
}
