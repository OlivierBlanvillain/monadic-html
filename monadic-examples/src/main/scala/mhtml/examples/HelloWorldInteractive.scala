package mhtml.examples

import mhtml._
import Utils.inputEvent

object HelloWorldInteractive extends Example {
  def app = {
    val rxName = Var("World")
    <div>
      <input type="text" placeholder="Enter your name..." onkeyup={inputEvent(rxName := _.value)}/>
      <h2>Hello {rxName}!</h2>
    </div>
  }
}
