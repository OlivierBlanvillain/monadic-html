package mhtml

import monix.execution.schedulers.TestScheduler
import org.scalajs.dom
import org.scalatest.FunSuite
import scala.xml.Elem

class RxTests extends FunSuite {
  implicit val s: TestScheduler = TestScheduler()

  test("Mounting Elem") {
    val div = dom.document.createElement("div")
    mount(div, <p class="cc" id="22">{"text"}</p>)
    assert(div.innerHTML == """<p class="cc" id="22">text</p>""")
  }

  test("Rx Elem") {
    val el: Var[Elem] = Var(<br/>)
    val div = dom.document.createElement("div")
    mount(div, el)
    assert(div.innerHTML == "")
    s.tick()
    assert(div.innerHTML == "<br>")
  }

  test("Updating binded String in Node Seq") {
    val text: Var[String] = Var("original text")
    val span: Elem = <span><p>pre {text} <br/> post </p></span>
    val div = dom.document.createElement("div")
    mount(div, span)
    s.tick()
    assert(div.innerHTML == "<span><p>pre original text <br> post </p></span>")
    text := "changed"
    s.tick()
    assert(div.innerHTML == "<span><p>pre changed <br> post </p></span>")
    text := "changed again"
    s.tick()
    assert(div.innerHTML == "<span><p>pre changed again <br> post </p></span>")
  }

  test("Updating binded Seq") {
    val list: Var[Seq[String]] = Var(Seq("original text 0", "original text 1"))
    val span: Elem = <span> <p> { list.map(xs => for (x <- xs) yield <b>{x}</b>) } </p> </span>
    val div = dom.document.createElement("div")
    mount(div, span)
    s.tick()
    assert(div.innerHTML == "<span> <p> <b>original text 0</b><b>original text 1</b> </p> </span>")
    list.update("prepended" +: _)
    s.tick()
    s.tick()
    assert(div.innerHTML == "<span> <p> <b>prepended</b><b>original text 0</b><b>original text 1</b> </p> </span>")
    list.update(_.patch(1, Nil, 1)) // Remove element as position 1
    s.tick()
    s.tick()
    assert(div.innerHTML == "<span> <p> <b>prepended</b><b>original text 1</b> </p> </span>")
  }

  test("Updating attribute") {
    val id: Var[String] = Var("oldId")
    val hr: Elem = <hr id={id}/>
    val div = dom.document.createElement("div")
    mount(div, hr)
    s.tick()
    assert(div.innerHTML == """<hr id="oldId">""")
    id := "newId"
    s.tick()
    assert(div.innerHTML == """<hr id="newId">""")
  }

  test("Updating attribute does not replace nodes") {
    val clazz: Var[String] = Var("oldClass")
    val tpe: Var[String] = Var("text")
    val p: Elem = <p class={clazz}><input type={tpe}/></p>
    val div = dom.document.createElement("div")
    mount(div, p)
    s.tick()
    val customInput = "foo"
    div.firstChild.firstChild.asInstanceOf[dom.html.Input].value = customInput
    assert(div.innerHTML == """<p class="oldClass"><input type="text"></p>""")
    clazz := "newClass"
    tpe   := "password"
    s.tick()
    assert(div.innerHTML == """<p class="newClass"><input type="password"></p>""")
    assert(div.firstChild.firstChild.asInstanceOf[dom.html.Input].value == customInput)
  }

  test("ForYieldIf") {
    final case class User(firstName: Var[String], lastName: Var[String], age: Var[Int])
    val filterPattern: Var[String] = Var("")

    val usersRx: Var[List[User]] = Var(List(
      User(Var("Steve"), Var("Jobs"), Var(10)),
      User(Var("Tim"), Var("Cook"), Var(12)),
      User(Var("Jeff"), Var("Lauren"), Var(13))
    ))

    def shouldShow(user: User): Rx[Boolean] =
      for {
        pattern   <- filterPattern
        firstName <- user.firstName
        lastName  <- user.lastName
      } yield {
        pattern == ""                             ||
          firstName.toLowerCase.contains(pattern) ||
          lastName.toLowerCase.contains(pattern)
      }

    implicit class SequencingListFFS[A](self: List[Rx[A]]) {
      def sequence: Rx[List[A]] =
        self.foldRight(Rx(List[A]()))(for {n<-_;s<-_} yield n+:s)
    }

    def tbodyRx: Elem =
      <tbody>{
          usersRx.flatMap { userList: List[User] =>
            userList
              .map(shouldShow) // List[Rx[Boolean]]
              .sequence        // Rx[List[Boolean]]
              .map {
                _ .zip(userList)
                  .collect { case (true, u) => u }
                  .map { user =>
                    <tr><td>{user.firstName}</td><td>{user.lastName}</td><td>{user.age}</td></tr>
                  }
              }
          }
      }</tbody>

    val tableRx =
      <table><thead><tr><td>First Name</td><td>Second Name</td><td>Age</td></tr></thead>{tbodyRx}</table>

    val div = dom.document.createElement("div")
    mount(div, tableRx)
    s.tick()
    s.tick()
    s.tick()
    s.tick()

    assert(div.innerHTML == """<table><thead><tr><td>First Name</td><td>Second Name</td><td>Age</td></tr></thead><tbody><tr><td>Steve</td><td>Jobs</td><td>10</td></tr><tr><td>Tim</td><td>Cook</td><td>12</td></tr><tr><td>Jeff</td><td>Lauren</td><td>13</td></tr></tbody></table>""")

    filterPattern := "o"
    s.tick()
    s.tick()
    s.tick()
    s.tick()

    assert(div.innerHTML == """<table><thead><tr><td>First Name</td><td>Second Name</td><td>Age</td></tr></thead><tbody><tr><td>Steve</td><td>Jobs</td><td>10</td></tr><tr><td>Tim</td><td>Cook</td><td>12</td></tr></tbody></table>""")
  }

  test("Updating Double Insertion") {
    val child = Var[Elem](<hr/>)
    val parent = <p><span>{child}</span><span>{child}</span></p>
    val div = dom.document.createElement("div")
    mount(div, parent)
    s.tick()
    assert(div.innerHTML == "<p><span><hr></span><span><hr></span></p>")
    child := <br/>
    s.tick()
    assert(div.innerHTML == "<p><span><br></span><span><br></span></p>")
  }

  test("Mounting with Style") {
    def hr  = <hr style="border-left: 123px"/>
    val div = dom.document.createElement("div")
    mount(div, hr)
    assert(div.firstChild.asInstanceOf[dom.html.HR].style.borderLeft == "123px")
  }

  test("Mounting with Class") {
    def hr = <hr class="myClass"/>
    val div = dom.document.createElement("div")
    mount(div, hr)
    assert(div.firstChild.asInstanceOf[dom.html.HR].className == "myClass")
  }

  test("Comment") {
    val comment = <div><!--my comment--></div>
    val div = dom.document.createElement("div")
    mount(div, comment)
    assert(div.innerHTML == "<div><!--my comment--></div>")
  }

  test("Escape") {
    val escaped = <div>&#32;</div>
    val div = dom.document.createElement("div")
    mount(div, escaped)
    assert(div.innerHTML == "<div> </div>")
  }

  test("Entity") {
    def entity = <div>&amp;&lt;&copy;&lambda;</div>
    val div = dom.document.createElement("div")
    mount(div, entity)
    assert(div.innerHTML == "<div>&amp;&lt;©λ</div>")
  }

  test("CustomAttribute") {
    def hr = <hr data:custom-key="value"/>
    val div = dom.document.createElement("div")
    mount(div, hr)
    assert(div.innerHTML == """<hr data:custom-key="value">""")
  }

  test("EntityRefMap arrays are equaly sized") {
    assert(EntityRefMap.keys.size == EntityRefMap.values.size)
  }

  test("onClick = Function0") {
    var clicked = false
    val button  = <button onclick={ () => clicked = true }>Click Me!</button>
    val div = dom.document.createElement("div")
    mount(div, button)
    assert(!clicked)
    assert(div.firstChild.asInstanceOf[dom.html.Button].innerHTML == "Click Me!")
    div.firstChild.asInstanceOf[dom.html.Button].click()
    assert(clicked)
  }

  test("onClick = Function1") {
    var clicked = false
    val button  = <button onclick={ _: dom.MouseEvent => clicked = true }>Click Me!</button>
    val div = dom.document.createElement("div")
    mount(div, button)
    assert(!clicked)
    assert(div.firstChild.asInstanceOf[dom.html.Button].innerHTML == "Click Me!")
    div.firstChild.asInstanceOf[dom.html.Button].click()
    assert(clicked)
  }

  test("README example") {
    import mhtml._
    import scala.xml.Node
    import org.scalajs.dom

    val count: Var[Int] = Var[Int](0)

    val dogs: Rx[Seq[Node]] =
      count.map(Seq.fill(_)(<img src="doge.png"></img>))

    val component = // ← look, you can even use fancy names!
      <div style="background-color: blue;">
        <button onclick={ () => count.update(_ + 1) }>Click Me!</button>
        <p>WOW!!!</p>
        <p>MUCH REACTIVE!!!</p>
        <p>SUCH BINDING!!!</p>
        {dogs}
      </div>

    val div = dom.document.createElement("div")
    mount(div, component)

    val start =
   """<div style="background-color: blue;">
        <button>Click Me!</button>
        <p>WOW!!!</p>
        <p>MUCH REACTIVE!!!</p>
        <p>SUCH BINDING!!!</p>"""

    assert(div.innerHTML == start + "\n        \n      </div>")
    assert(div.firstChild.firstChild.nextSibling.asInstanceOf[dom.html.Button].innerHTML == "Click Me!")
    div.firstChild.firstChild.nextSibling.asInstanceOf[dom.html.Button].click()
    s.tick()
    assert(div.innerHTML == start + "\n        <img src=\"doge.png\">\n      </div>")
    div.firstChild.firstChild.nextSibling.asInstanceOf[dom.html.Button].click()
    s.tick()
    assert(div.innerHTML == start + "\n        <img src=\"doge.png\"><img src=\"doge.png\">\n      </div>")
  }
}
