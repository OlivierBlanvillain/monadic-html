package mhtml.examples

import mhtml.scalatags.mount.BufferCancelable
import mhtml.{Rx, Var}
import org.scalajs.dom
import org.scalajs.dom.html.Element
import scalatags.JsDom.all._
import scalatags.JsDom.tags2._
import mhtml.scalatags.mount._

object STContactList {
  case class Contact(name: Var[String])

  implicit val cancelables = new BufferCancelable()

  val container = dom.document.createElement("container")
  val classVar: Var[String] = Var("foo")
  val classRx: Rx[String] = classVar
  container.appendChild(span(cls := classRx)().render)

  def row(contact: Contact) = {
    tr()(
      td(contact.name),
      td(
        button(onclick := { () => contact.name.update("Mr." + _) })(
          "click me"
        )
      )
    )
  }.render

  def app: Element = {

    val contacts = Var(Seq.empty[Contact])

    section()(
      div()(
        button(
          onclick := { () => contacts.update(x => Contact(Var("Olaf")) +: x)},
          "Add a contact"
        )
      ),
      table(
        thead(
          tr(
            th("Name"),
            th()
          )
        ),
        tbody(
          contacts.map(_.map(row))
        )
      )
    ).render
  }
}
