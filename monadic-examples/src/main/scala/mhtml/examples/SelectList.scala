package mhtml.examples

import scala.xml.Node

import mhtml._

object SelectList extends Example {
  def app: Node = {
    val options = Seq(
      "HelloWorld",
      "HelloWorldInteractive",
      "Timer",
      "FocusElement",
      "Counter",
      "ContactList",
      "GithubAvatar",
      "SelectList",
      "ProductTable"
    )
    val (app, selected) = Chosen.singleSelect(_ => Var(options))
    val message = selected.map {
      case Some(x) => s"Selected value is $x"
      case _ => "Please select an item from the list"
    }
    <div>
      <p>{message}</p>
      {app}
    </div>
  }
}
