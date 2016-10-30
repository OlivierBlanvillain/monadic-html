package mhtml.examples

import mhtml._

object HelloWorldInteractive extends Example {
  def app = {
    val rxName = Var("World")
    <div>
      <input type="text"
             placeholder="Enter your name..."
             oninput={Utils.inputEvent(rxName := _.value)}/>
      <h2>Hello {rxName}!</h2>
    </div>
  }
}
