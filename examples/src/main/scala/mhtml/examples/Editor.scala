package examples

import mhtml._
import styles.NeptuneStyles
import scala.scalajs.js
import org.scalajs.dom
import dom.{document, window}
import dom.raw.{MouseEvent, MutationObserver, MutationObserverInit, MutationRecord}
import org.scalajs.dom.html.Div
import scalacss.ProdDefaults._
import scala.xml.Node

/**
  Copyright (c) 2017 Amir Karimi, Brandon Barker
  Adapted from https://github.com/amirkarimi/neptune
  */
object Neptune {

  case class Component[D](view: Node, model: Rx[D])

  case class Action(icon: Node, title: String, command: () => Unit)

  object Act {
    def apply(icon: Node, title: String)(command: => Unit): Action = {
      Action.apply(icon, title, () => command)
    }
  }

  case class Settings(actions: Seq[Action] = actions, styleWithCss: Boolean = false)

  val actions = Seq(
    Act(<b>B</b>, "Bold") {
      exec("bold")
    },
    Act(<i>I</i>, "Italic") {
      exec("italic")
    },
    Act(<u>U</u>, "Underline") {
      exec("underline")
    },
    Act(<strike>S</strike>, "strikeThrough") {
      exec("strikeThrough")
    },
    Act(<b>H<sub>1</sub></b>, "Heading 1") {
      exec("formatBlock", "<H1>")
    },
    Act(<b>H<sub>2</sub></b>, "Heading 2") {
      exec("formatBlock", "<H2>")
    },
    Act(<div>¶</div>, "Paragraph") {
      exec("formatBlock", "<P>")
    },
    Act(<div>“”</div>, "Quote") {
      exec("formatBlock", "<BLOCKQUOTE>")
    },
    Act(<div>#</div>, "Ordered List") {
      exec("insertOrderedList")
    },
    Act(<div>•</div>, "Unordered List") {
      exec("insertUnorderedList")
    },
    Act(<div>&lt;/&gt;</div>, "Code") {
      exec("formatBlock", "<PRE>")
    },
    Act(<div>―</div>, "Horizontal Line") {
      exec("insertHorizontalRule")
    },
    Act(<div>🔗</div>, "Link") {
      val url = window.prompt("Enter the link URL")
      if (url.nonEmpty) exec("createLink", url)
    },
    Act(<div>📷</div>, "Image") {
      val url = window.prompt("Enter the image URL")
      if (url.nonEmpty) exec("insertImage", url)
    }
  )

  private def exec(command: String, value: scalajs.js.Any = null): Unit = {
    document.execCommand(command, false, value); ()
  }

  def editor(settings: Settings = Settings()): Component[String] = {
    // TODO: settings.classes.actionbar
    val actionBar = <div class={ NeptuneStyles.neptuneActionbar.htmlClass }>{
      actions.map { action =>
        <button class={ NeptuneStyles.neptuneButton.htmlClass }
                title={ action.title }
                onclick={ (ev: MouseEvent) => action.command() }
        >{ action.icon }</button>
      }
    }</div>


    val content: Var[String] = Var("")

    def updateContent(domNode: Div): Unit = {
      def observerCallback(muts: js.Array[MutationRecord], obs: MutationObserver) = {
        content := domNode.innerHTML
      }
      val contentObserver: MutationObserver = new MutationObserver(observerCallback _)
      val contentObserverParams = new js.Object{
        val subtree = true
        val attributes = true
        val childList =true
        val characterData = true
        val characterDataOldValue =true
      }.asInstanceOf[MutationObserverInit]
      contentObserver.observe(domNode, contentObserverParams)
    }

    val contentStore = <div
      class={ NeptuneStyles.neptuneContent.htmlClass }
      contentEditable="true" onkeydown={ preventTab _ }
      mhtml-onmount={ updateContent _ }
    />

    val view = <div>{ actionBar }{ contentStore }</div>

    if (settings.styleWithCss) exec("styleWithCSS")
    Component(view, content)
  }

  def preventTab(kev: dom.KeyboardEvent): Unit =
    if (kev.keyCode == 9) kev.preventDefault()
}

object Editor extends Example {
  def app: xml.Node = {
    NeptuneStyles.addToDocument()
    val editor = Neptune.editor()

    <div>
      { editor.view }
      <h3>Editor output follows</h3>
      { editor.model }
    </div>
  }
}
