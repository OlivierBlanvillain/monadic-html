package examples

import mhtml._
import scala.scalajs.js

object HelloWorldInteractive extends Example {
  def app: xml.Node = {
    val rxName = Var("world")
    def handler(event: js.Dynamic): Unit =
      rxName := event.target.value.asInstanceOf[String]
    <div>
      <input type="text"
             placeholder="Enter your name..."
             oninput={ handler _ }/>
      <h2>Hello {rxName}!</h2>
    </div>
  }
}
