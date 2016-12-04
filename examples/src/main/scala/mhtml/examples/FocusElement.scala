package mhtml.examples

import org.scalajs.dom
import org.scalajs.dom.raw.HTMLInputElement

object FocusElement extends Example {
  val id = "theInput"
  def focusOnInput(): Unit = dom.document.getElementById(id) match {
    case input: HTMLInputElement => input.focus()
    case _ =>
  }
  def app: xml.Node = {
    <div>
      <div onclick={() => focusOnInput()}>click to focus on input</div>
      <input type="text" id={id}/>
    </div>
  }
}
