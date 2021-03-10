package examples

import mhtml._
import scala.scalajs.js

object Timer extends Example {
  var interval: js.UndefOr[js.timers.SetIntervalHandle] = js.undefined
  def app: xml.Node = {
    val counter = Var(0)
    interval = js.timers.setInterval(1000)(counter.update(_ + 1))
    <p>Seconds elapsed: {counter}</p>
  }
  override def cancel() = {
    interval foreach js.timers.clearInterval
    interval = js.undefined
  }
}
