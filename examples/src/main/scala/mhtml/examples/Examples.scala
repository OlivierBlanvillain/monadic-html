package examples

import scala.scalajs.js.annotation._
import scala.util.Success
import scala.xml.Node
import scala.concurrent.ExecutionContext.Implicits.global

import mhtml._
import mhtml.future.syntax._
import org.scalajs.dom
import org.scalajs.dom.Event
import org.scalajs.dom.ext.Ajax

trait Example {
  def app: Node
  def cancel(): Unit = ()
  val name = {
    val simpleName = this.getClass.getSimpleName
    if (simpleName.endsWith("$"))
      simpleName.dropRight(1)
    else
      simpleName
  }
  val url = "#/" + name

  private val organization = "OlivierBlanvillain"
  private def githubUrl =
    s"https://github.com/" +
      s"$organization/monadic-html/" +
      s"blob/master/examples/src/main/scala/mhtml/examples/" +
      s"$name.scala"
  private def rawUrl =
    s"https://raw.githubusercontent.com/" +
      s"$organization/monadic-html/" +
      s"master/examples/src/main/scala/mhtml/examples/" +
      s"$name.scala"

  lazy val sourceCode: Rx[String] = {
    val init = Var("Loading...")
    Ajax.get(rawUrl).toRx.impure.run {
      case Some(Success(x)) =>
        init := x.responseText
      case _ =>
    }
    init
  }
  def demo: Node =
    <div class="highlight">
      <h1>{name}</h1>
      <div class="demo">
        {app}
      </div>
      <h2>Source code</h2>
      <p><a href={githubUrl}>{githubUrl}</a></p>
      script
      <pre><code>{sourceCode}</code></pre>
    </div>
}

@JSExportTopLevel("Main")
object Main {
  val examples = Seq[Example](
    HelloWorld,
    HelloWorldInteractive,
    Doge,
    Timer,
    FocusElement,
    Counter,
    StepCounter,
    ContactList,
    GithubAvatar,
    SelectList,
    ProductTable,
    Mario,
    Editor
  )

  def getActiveApp =
    examples.find(_.url == dom.window.location.hash).getOrElse(examples.head)

  val activeExample: Var[Example] = Var(getActiveApp)

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

  @JSExport
  def main(args: Array[String]): Unit = {
    mount(dom.document.body, mainApp)
    ()
  }
}
