package mhtml

import org.scalatest.funsuite.AnyFunSuite
import org.scalajs.dom
import _root_.scalatags.JsDom.all._
import org.scalatest.matchers.should.Matchers
import mhtml.scalatags.mount._
import org.scalajs.dom.html.Div
import org.scalatest.BeforeAndAfter
import _root_.scalatags.JsDom

class ScalaTagTest extends AnyFunSuite with Matchers with BeforeAndAfter {

  test("embed simple rx in string attribute") {
    implicit val cancelables = new BufferCancelable()

    val classVar: Var[String] = Var("foo")
    val classRx: Rx[String] = classVar

    val div = dom.document.createElement("container")
    div.appendChild(span(cls := classRx)().render)
    div.innerHTML shouldBe """<span class="foo"></span>"""
    classVar := "qux"
    div.innerHTML shouldBe """<span class="qux"></span>"""
  }

  test("embedded rx does effect the dom document") {
    implicit val cancelables = new BufferCancelable()
    val container = dom.document.createElement("container")
    val classVar: Var[String] = Var("foo")
    val classRx: Rx[String] = classVar
    container.appendChild(span(cls := classRx)().render)
    dom.document.body.appendChild(container)
    dom.document.body.getElementsByTagName("container")(0).innerHTML shouldBe """<span class="foo"></span>"""
    classVar := "bar"
    dom.document.body.getElementsByTagName("container")(0).innerHTML shouldBe """<span class="bar"></span>"""
  }

  test("embed a rx node as child node") {

    implicit val cancelables = new BufferCancelable()

    val nodeVar = Var(div()("foo").render)
    val nodeRx: Rx[Div] = nodeVar
    val container = dom.document.createElement("container")
    container.appendChild(span()(nodeRx).render)
    container.innerHTML shouldBe "<span><div>foo</div></span>"
    nodeVar := div()("bar").render
    nodeVar := div()("qux").render
    container.innerHTML shouldBe "<span><div>qux</div></span>"
  }

  test("embed a rx frag as child node") {

    implicit val cancelables = new BufferCancelable()

    val nodeVar = Var(div()("foo"))
    val nodeRx: Rx[JsDom.TypedTag[Div]] = nodeVar
    val container = dom.document.createElement("container")
    container.appendChild(span()(nodeRx).render)
    container.innerHTML shouldBe "<span><div>foo</div></span>"
    nodeVar := div()("bar")
    nodeVar := div()("qux")
    container.innerHTML shouldBe "<span><div>qux</div></span>"
  }

  test("check how we aggregate cancellables") {

    implicit val cancelables = new BufferCancelable()

    val nodeVar = Var(div()("foo"))
    val classVar = Var("blue")
    val classRx: Rx[String] = classVar
    val nodeRx: Rx[JsDom.TypedTag[Div]] = nodeVar
    val container = dom.document.createElement("container")
    container.appendChild(span(cls := classRx)(nodeRx).render)
    container.innerHTML shouldBe """<span class="blue"><div>foo</div></span>"""
    nodeVar := div()("bar")
    nodeVar := div()("qux")
    classVar := "red"
    container.innerHTML shouldBe """<span class="red"><div>qux</div></span>"""
    cancelables.buffer.size shouldBe 2
  }
}
