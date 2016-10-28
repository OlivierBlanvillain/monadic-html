package mhtml.examples

import scala.scalajs.js
import scala.scalajs.js.JSApp
import scala.xml.Node

import mhtml._
import mhtml.examples.Utils._
import org.scalajs.dom
import org.scalajs.dom.Event
import org.scalajs.dom.raw.HTMLInputElement

trait Example {
  def app: Node
  def cancel(): Unit = ()
  val name = this.getClass.getSimpleName
  val url = "#/" + name
}

object HelloWorld extends Example {
  val app = <h2>Hello {"World"}!</h2>
}

object HelloWorldInteractive extends Example {
  def app = {
    val rxName = Var("World")
    <div>
      <input type="text" placeholder="Enter your name..." onkeyup={inputEvent(rxName := _.value)}/>
      <h2>Hello {rxName}!</h2>
    </div>
  }
}

object Timer extends Example {
  var interval: js.UndefOr[js.timers.SetIntervalHandle] = js.undefined
  def app: Node = {
    val counter = Var(0)
    interval = js.timers.setInterval(1000)(counter.update(_ + 1))
    <p>Seconds elapsed: {counter}</p>
  }
  override def cancel() = {
    interval foreach js.timers.clearInterval
    interval = js.undefined
  }
}

object FocusElement extends Example {
  val id = "theInput"
  def focusOnInput(): Unit = dom.document.getElementById(id) match {
    case input: HTMLInputElement => input.focus()
    case _ =>
  }
  def app: Node = {
    <div>
      <div onclick={() => focusOnInput()}>click to focus on input</div>
      <input type="text" id={id}/>
    </div>
  }
}

object Counter extends Example {
  def app: Node = {
    val counter = Var(0)
    <div>
      <button onclick={() => counter.update(_ - 1)}>-</button>
      {counter}
      <button onclick={() => counter.update(_ + 1)}>+</button>
    </div>
  }
}

object Main extends JSApp {
  val examples = Seq[Example](
    HelloWorld,
    HelloWorldInteractive,
    Timer,
    FocusElement,
    Counter,
    ContactList,
    GithubAvatar,
    SelectList,
    ProductTable
  )

  def getActiveApp =
    examples.find(_.url == dom.window.location.hash).getOrElse(examples.head)

  val activeExample = Var(getActiveApp)

  dom.window.onhashchange = { _: Event =>
    activeExample.update { old =>
      old.cancel()
      getActiveApp
    }
  }
  val navigation =
    <ul>
      {examples.map(x => <li><a href={x.url}>{x.name}</a></li>)}
    </ul>

  val mainApp =
    <div>
      {navigation}
      <div>
        <h1>{activeExample.map(_.name)}</h1>
        {activeExample.map(_.app)}
      </div>
    </div>

  def main(): Unit = {
    mount(dom.document.body, mainApp)
  }
}
