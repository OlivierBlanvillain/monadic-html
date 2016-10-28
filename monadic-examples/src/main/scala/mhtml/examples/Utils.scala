package mhtml.examples

import org.scalajs.dom._
import org.scalajs.dom.raw.HTMLInputElement

object Utils {
  def inputEvent(f: HTMLInputElement => Unit): Event => Unit = {
    event: Event =>
      event.target match {
        case e: HTMLInputElement =>
          f(e)
        case _ =>
      }
  }
}
