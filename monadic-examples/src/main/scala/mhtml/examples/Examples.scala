package mhtml.examples

import scala.scalajs.js
import scala.scalajs.js.JSApp
import scala.scalajs.js.annotation.JSGlobalScope
import scala.util.Failure
import scala.util.Success
import scala.xml.Node

import mhtml._
import org.scalajs.dom
import org.scalajs.dom.Event
import org.scalajs.dom.ext.Ajax

trait Example {
  def app: Node
  def cancel(): Unit = ()
  val name = this.getClass.getSimpleName
  val url = "#/" + name
  private def rawUrl =
    s"https://raw.githubusercontent.com/" +
      s"olafurpg/monadic-html/" +
      s"examples/monadic-examples/src/main/scala/mhtml/examples/" +
      s"$name.scala"

  lazy val sourceCode: Rx[String] = {
    val init = Var("Loading...")
    Utils.fromFuture(Ajax.get(rawUrl)).foreach {
      case Some(Success(x)) =>
        init := x.responseText
      case Some(Failure(x)) =>
        init := x.toString
      case _ =>
    }
    init
  }

  def demo: Node =
    <div>
      <h1>{name}</h1>
      {app}
      <h2>Source code</h2>
      <pre><code class="scala">{sourceCode}</code></pre>
    </div>

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

  val activeExample: Var[Example] = Var(getActiveApp)
  val runCodeHighlighter = activeExample.foreach { _ =>
    js.timers.setTimeout(300) {
      try {
        DOMGlobalScope.hljs.highlightBlock(
          dom.document.getElementsByTagName("code")(0))
      } catch {
        case e: Throwable =>
          e.printStackTrace()
      }
    }
    ()
  }

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
      {activeExample.map(_.demo)}
    </div>

  def main(): Unit = {
    mount(dom.document.body, mainApp)
  }
}

@js.native
@JSGlobalScope
object DOMGlobalScope extends js.Object {
  val hljs: HighlightJS = js.native
}
@js.native
trait HighlightJS extends js.Object {
  def highlightBlock(block: dom.Node): Unit = js.native
}
