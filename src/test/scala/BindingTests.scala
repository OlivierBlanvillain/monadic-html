package monixbinding

import monix.execution.schedulers.TestScheduler
import org.scalajs.dom
import org.scalatest.FunSuite
import scala.xml.Elem

class BindingTests extends FunSuite {
  implicit val s = TestScheduler()

  // test("Mounting Elem") {
  //   val div = dom.document.createElement("div")
  //   mount(div, <p class="cc" id="22">{"text"}</p>)
  //   assert(div.innerHTML == """<p class="cc" id="22">text</p>""")
  // }

  test("Binding Elem") {
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
    mount(div, span); s.tick()
    assert(div.innerHTML == "<span><p>pre original text <br> post </p></span>")
    text := "changed"; s.tick()
    assert(div.innerHTML == "<span><p>pre changed <br> post </p></span>")
    text := "changed again"; s.tick()
    assert(div.innerHTML == "<span><p>pre changed again <br> post </p></span>")
  }

  test("Updating binded Seq") {
    val list: Var[Seq[String]] = Var(Seq("original text 0", "original text 1"))
    val span: Elem = <span> <p> { list.map(xs => for (x <- xs) yield <b>{x}</b>) } </p> </span>
    val div = dom.document.createElement("div")
    mount(div, span); s.tick()
    assert(div.innerHTML == "<span> <p> <b>original text 0</b><b>original text 1</b> </p> </span>")
    list.update("prepended" +: _); s.tick(); s.tick()
    assert(div.innerHTML == "<span> <p> <b>prepended</b><b>original text 0</b><b>original text 1</b> </p> </span>")
    list.update(_.patch(1, Nil, 1)); s.tick(); s.tick() // Remove element as position 1
    assert(div.innerHTML == "<span> <p> <b>prepended</b><b>original text 1</b> </p> </span>")
  }

  // test("Updating Attribute") {
  //   val id: Var[String] = Var("oldId")
  //   val hr: Binding[Elem] = id.map(i => <hr id={i}/>)
  //   val div = dom.document.createElement("div")
  //   mount(div, hr); s.tick()
  //   assert(div.innerHTML == """<hr id="oldId">""")
  //   id := "newId"; s.tick()
  //   assert(div.innerHTML == """<hr id="newId">""")
  // }

  test("ForYieldIf") {
    final case class User(firstName: Var[String], lastName: Var[String], age: Var[Int])
    val filterPattern: Var[String] = Var("")

    val usersBinding: Var[Seq[User]] = Var(Seq(
      User(Var("Steve"), Var("Jobs"), Var(10)),
      User(Var("Tim"), Var("Cook"), Var(12)),
      User(Var("Jeff"), Var("Lauren"), Var(13))
    ))

    def shouldShow(user: User): Binding[Boolean] =
      for {
        pattern   <- filterPattern
        firstName <- user.firstName
        lastName  <- user.lastName
      } yield {
        pattern == ""                             ||
          firstName.toLowerCase.contains(pattern) ||
          lastName.toLowerCase.contains(pattern)
      }

    // Node: If using cats/scalaz, you might as well replace this boilerplate with the following instance:
    // implicit val BindingMonadInstance: cats.Monad[Binding] = new cats.Monad[Binding] {
    //   def pure[A](x: A): Binding[A] = apply(x)
    //   def flatMap[A, B](fa: Binding[A])(f: A => Binding[B]): Binding[B] = fa.flatMap(f)
    //   def tailRecM[A, B](a: A)(f: (A) => Binding[Either[A, B]]): Binding[B] = defaultTailRecM(a)(f)
    // }
    implicit class SequencingSeqFFS[A](self: Seq[Binding[A]]) {
      def sequence: Binding[Seq[A]] =
        self.foldRight(Binding(Seq[A]()))(for {n<-_;s<-_} yield n+:s)
    }

    def tbodyBinding: Elem =
      <tbody>{
          usersBinding.flatMap { userSeq: Seq[User] =>
            userSeq
              .map(shouldShow) // Seq[Binding[Boolean]]
              .sequence        // Binding[Seq[Boolean]]
              .map {
                _ .zip(userSeq)
                  .collect { case (true, u) => u }
                  .map { user =>
                    <tr><td>{user.firstName}</td><td>{user.lastName}</td><td>{user.age}</td></tr>
                  }
              }
          }
      }</tbody>

    val tableBinding =
      <table><thead><tr><td>First Name</td><td>Second Name</td><td>Age</td></tr></thead>{tbodyBinding}</table>

    val div = dom.document.createElement("div")
    mount(div, tableBinding); s.tick(); s.tick(); s.tick(); s.tick()

    assert(div.innerHTML == """<table><thead><tr><td>First Name</td><td>Second Name</td><td>Age</td></tr></thead><tbody><tr><td>Steve</td><td>Jobs</td><td>10</td></tr><tr><td>Tim</td><td>Cook</td><td>12</td></tr><tr><td>Jeff</td><td>Lauren</td><td>13</td></tr></tbody></table>""")

    filterPattern := "o"; s.tick(); s.tick(); s.tick(); s.tick()

    assert(div.innerHTML == """<table><thead><tr><td>First Name</td><td>Second Name</td><td>Age</td></tr></thead><tbody><tr><td>Steve</td><td>Jobs</td><td>10</td></tr><tr><td>Tim</td><td>Cook</td><td>12</td></tr></tbody></table>""")
  }

  test("Updating Double Insertion") {
    val child = Var[Elem](<hr/>)
    val parent = <p><span>{child}</span><span>{child}</span></p>
    val div = dom.document.createElement("div")
    mount(div, parent); s.tick()
    assert(div.innerHTML == "<p><span><hr></span><span><hr></span></p>")
    child := <br/>; s.tick()
    assert(div.innerHTML == "<p><span><br></span><span><br></span></p>")
  }

  // test("Mounting with Style") {
  //   def hr  = <hr style="border-left: 123px"/>
  //   val div = dom.document.createElement("div")
  //   mount(div, hr)
  //   assert(div.firstChild.asInstanceOf[dom.html.HR].style.borderLeft == "123px")
  // }

  // test("Mounting with Class") {
  //   def hr = <hr class="myClass"/>
  //   val div = dom.document.createElement("div")
  //   mount(div, hr)
  //   assert(div.firstChild.asInstanceOf[dom.html.HR].className == "myClass")
  // }

  // test("Comment") {
  //   val comment = <div><!--my comment--></div>
  //   val div = dom.document.createElement("div")
  //   mount(div, comment)
  //   assert(div.innerHTML == "<div><!--my comment--></div>")
  // }

  test("Escape") {
    val escaped = <div>&#32;</div>
    val div = dom.document.createElement("div")
    mount(div, escaped)
    assert(div.innerHTML == "<div> </div>")
  }

  // test("Entity") {
  //   def entity = <div>&amp;&lt;&copy;&lambda;</div>
  //   val div = dom.document.createElement("div")
  //   mount(div, entity)
  //   assert(div.innerHTML == "<div>&amp;&lt;©λ</div>")
  // }

  // test("CustomAttribute") {
  //   def hr = <hr data:custom-key="value"/>
  //   val div = dom.document.createElement("div")
  //   mount(div, hr)
  //   assert(div.innerHTML == """<hr data:custom-key="value">""")
  // }
}
