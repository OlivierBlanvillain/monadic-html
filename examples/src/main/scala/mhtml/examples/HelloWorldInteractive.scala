package examples

import mhtml._

object HelloWorldInteractive extends Example {
  def app: xml.Node = {
    val rxName = Var("world")
    <div>
      <input type="text"
             placeholder="Enter your name..."
             oninput={Utils.inputEvent(rxName := _.value)}/>
      <h2>Hello {rxName}!</h2>
    </div>
  }
}
