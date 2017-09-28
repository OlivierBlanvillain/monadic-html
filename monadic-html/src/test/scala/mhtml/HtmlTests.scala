package mhtml

import org.scalajs.dom
import org.scalatest.FunSuite

import scala.xml.{Elem, Group}

class HtmlTests extends FunSuite {

  test("Mounting Elem") {
    val div = dom.document.createElement("div")
    mount(div, <p class="cc" id="22">{"text"}</p>)
    assert(div.innerHTML == """<p class="cc" id="22">text</p>""")
  }

  test("Mounting Group") {
    val cssUrls = Seq(
      "./target/bootstrap.min.css",
      "./target/bootstrap-theme.min.css"
    )
    val head = dom.document.createElement("head")
    val linkRelCss =
      Group(cssUrls.map(cssUrl => <link rel="stylesheet" href={cssUrl}/>))
    mount(head, linkRelCss)
    assert(head.innerHTML ==
      """<link rel="stylesheet" href="./target/bootstrap.min.css">""" +
      """<link rel="stylesheet" href="./target/bootstrap-theme.min.css">"""
    )
  }

  test("Rx Elem") {
    val el: Var[Elem] = Var(<br/>)
    val div = dom.document.createElement("div")
    mount(div, el)
    assert(div.innerHTML == "<br>")
  }

  test("Updating binded String in Node Seq") {
    val text: Var[String] = Var("original text")
    val span: Elem = <span><p>pre {text} <br/> post </p></span>
    val div = dom.document.createElement("div")
    mount(div, span)
    assert(div.innerHTML == "<span><p>pre original text <br> post </p></span>")
    text := "changed"
    assert(div.innerHTML == "<span><p>pre changed <br> post </p></span>")
    text := "changed again"
    assert(div.innerHTML == "<span><p>pre changed again <br> post </p></span>")
  }

  test("Updating binded Seq") {
    val list: Var[Seq[String]] = Var(Seq("original text 0", "original text 1"))
    val span: Elem = <span> <p> { list.map(xs => for (x <- xs) yield <b>{x}</b>) } </p> </span>
    val div = dom.document.createElement("div")
    mount(div, span)
    assert(div.innerHTML == "<span> <p> <b>original text 0</b><b>original text 1</b> </p> </span>")
    list.update("prepended" +: _)
    assert(div.innerHTML == "<span> <p> <b>prepended</b><b>original text 0</b><b>original text 1</b> </p> </span>")
    list.update(_.patch(1, Nil, 1)) // Remove element as position 1
    assert(div.innerHTML == "<span> <p> <b>prepended</b><b>original text 1</b> </p> </span>")
  }

  test("Updating attribute") {
    val id: Var[String] = Var("oldId")
    val hr: Elem = <hr id={id}/>
    val div = dom.document.createElement("div")
    mount(div, hr)
    assert(div.innerHTML == """<hr id="oldId">""")
    id := "newId"
    assert(div.innerHTML == """<hr id="newId">""")
    id := null
    assert(div.innerHTML == """<hr>""")
  }

  test("Updating attribute does not replace nodes") {
    val clazz: Var[String] = Var("oldClass")
    val tpe: Var[String] = Var("text")
    val p: Elem = <p class={clazz}><input type={tpe}/></p>
    val div = dom.document.createElement("div")
    mount(div, p)
    val customInput = "foo"
    div.firstChild.firstChild.asInstanceOf[dom.html.Input].value = customInput
    assert(div.innerHTML == """<p class="oldClass"><input type="text"></p>""")
    clazz := "newClass"
    tpe   := "password"
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

    assert(div.innerHTML == """<table><thead><tr><td>First Name</td><td>Second Name</td><td>Age</td></tr></thead><tbody><tr><td>Steve</td><td>Jobs</td><td>10</td></tr><tr><td>Tim</td><td>Cook</td><td>12</td></tr><tr><td>Jeff</td><td>Lauren</td><td>13</td></tr></tbody></table>""")

    filterPattern := "o"

    assert(div.innerHTML == """<table><thead><tr><td>First Name</td><td>Second Name</td><td>Age</td></tr></thead><tbody><tr><td>Steve</td><td>Jobs</td><td>10</td></tr><tr><td>Tim</td><td>Cook</td><td>12</td></tr></tbody></table>""")
  }

  test("Updating Double Insertion") {
    val child = Var[Elem](<hr/>)
    val parent = <p><span>{child}</span><span>{child}</span></p>
    val div = dom.document.createElement("div")
    mount(div, parent)
    assert(div.innerHTML == "<p><span><hr></span><span><hr></span></p>")
    child := <br/>
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
    var w: List[String] = Nil
    def hr = <hr data:custom-key="value"/>
    val div = dom.document.createElement("div")
    mount(div, hr, new DevSettings { override def warn(s: String) = w = s :: w })
    assert(div.innerHTML == """<hr data:custom-key="value">""")
    assert(w == List("Unknown attribute data:custom-key. Did you mean accesskey instead?"))
  }

  test("onClick = Function0") {
    var clicked = false
    val button  = <button class="c" onclick={ () => clicked = true } id="1">Click Me!</button>
    val div = dom.document.createElement("div")
    mount(div, button)
    assert(!clicked)
    div.firstChild.asInstanceOf[dom.html.Button].click()
    assert(clicked)
    assert(div.innerHTML == """<button class="c" id="1">Click Me!</button>""")
  }

  test("onClick = Function1") {
    var clicked = false
    def handler(e: dom.MouseEvent): Unit = clicked = true
    val button  = <button class="c" onclick={handler _} id="1">Click Me!</button>
    val div = dom.document.createElement("div")
    mount(div, button)
    assert(!clicked)
    div.firstChild.asInstanceOf[dom.html.Button].click()
    assert(clicked)
    assert(div.innerHTML == """<button class="c" id="1">Click Me!</button>""")
  }

  test("onClick = Var[Function0]") {
    var ext = 0
    def handler1(e: dom.MouseEvent): Unit = ext = 1
    def handler2(e: dom.MouseEvent): Unit = ext = 2
    val varHandler = Var[dom.MouseEvent => Unit](handler1)
    val button  = <button class="c" onclick={varHandler} id="1">Click Me!</button>
    val div = dom.document.createElement("div")
    mount(div, button)
    assert(ext == 0)
    div.firstChild.asInstanceOf[dom.html.Button].click()
    assert(ext == 1)
    varHandler := handler2
    div.firstChild.asInstanceOf[dom.html.Button].click()
    assert(ext == 2)
    varHandler := handler1
    div.firstChild.asInstanceOf[dom.html.Button].click()
    assert(ext == 1)
    assert(div.innerHTML == """<button class="c" id="1">Click Me!</button>""")
  }

  test("README examples") {
    import mhtml._
    import scala.xml.Node
    import org.scalajs.dom

    val count: Var[Int] = Var(0)

    val doge: Node =
      <img style="width: 100px;" src="http://doge2048.com/meta/doge-600.png"/>

    val rxDoges: Rx[Seq[Node]] =
      count.map(i => Seq.fill(i)(doge))

    val component = // ← look, you can even use fancy names!
      <div>
        <button onclick={ () => count.update(_ + 1) }>Click Me!</button>
        { count.map(i => if (i <= 0) <div></div> else <h2>WOW!!!</h2>) }
        { count.map(i => if (i <= 2) <div></div> else <h2>MUCH REACTIVE!!!</h2>) }
        { count.map(i => if (i <= 5) <div></div> else <h2>SUCH BINDING!!!</h2>) }
        { rxDoges }
      </div>

    val div = dom.document.createElement("div")
    mount(div, component)
    val start = """<div><button>ClickMe!</button>"""

    assert(div.innerHTML.filterNot(_.isWhitespace) == start + "<div></div><div></div><div></div></div>")
    assert(div.firstChild.firstChild.nextSibling.asInstanceOf[dom.html.Button].innerHTML == "Click Me!")
    div.firstChild.firstChild.nextSibling.asInstanceOf[dom.html.Button].click()
    assert(div.innerHTML.filterNot(_.isWhitespace) == start + """<h2>WOW!!!</h2><div></div><div></div><imgsrc="http://doge2048.com/meta/doge-600.png"style="width:100px;"></div>""")
    div.firstChild.firstChild.nextSibling.asInstanceOf[dom.html.Button].click()
    assert(div.innerHTML.filterNot(_.isWhitespace) == start + """<h2>WOW!!!</h2><div></div><div></div><imgsrc="http://doge2048.com/meta/doge-600.png"style="width:100px;"><imgsrc="http://doge2048.com/meta/doge-600.png"style="width:100px;"></div>""")

    def myCounter(): (xml.Node, Rx[Int]) = {
      val fugitive: Var[Int] = Var[Int](0) // It won't escape it's scope!
      val node: xml.Node =
        <div>
          <h1>So far, you clicked { fugitive } times.</h1>
          <button onclick={ () => fugitive.update(1.+) }></button>
        </div>
      (node, fugitive)
    }
  }

  test("setUnsafeRawHTML") {
    val va: Var[String] = Var("")
    val ra = va.map(a => <div mhtml-onmount={ setUnsafeRawHTML(a) _ }></div>)
    val div = dom.document.createElement("div")
    mount(div, ra)
    assert(div.innerHTML == "<div></div>")
    va := "<h1>yolo<br>"
    assert(div.innerHTML == "<div><h1>yolo<br></h1></div>")
  }

  test("EntityRef warn") {
    var w: List[String] = Nil
    def entity = <div>&yolo;</div>
    val div = dom.document.createElement("div")
    mount(div, entity, new DevSettings { override def warn(s: String) = w = s :: w })
    assert(div.innerHTML == "<div>yolo</div>")
    assert(w == List("Unknown EntityRef yolo. Did you mean Iota instead?"))
  }

  test("Element warn") {
    var w: List[String] = Nil
    def entity = <yolo></yolo>
    val div = dom.document.createElement("div")
    mount(div, entity, new DevSettings { override def warn(s: String) = w = s :: w })
    assert(div.innerHTML == "<yolo></yolo>")
    assert(w == List("Unknown element yolo. Did you mean col instead?"))
  }

  test("EventKey warn") {
    var w: List[String] = Nil
    def entity = <div onClick={ () => () }></div>
    val div = dom.document.createElement("div")
    mount(div, entity, new DevSettings { override def warn(s: String) = w = s :: w })
    assert(div.innerHTML == "<div></div>")
    assert(w == List("Unknown event onClick. Did you mean onclick instead?"))
  }

  test("AttributeKey warn") {
    var w: List[String] = Nil
    def entity = <div yolo="true"></div>
    val div = dom.document.createElement("div")
    mount(div, entity, new DevSettings { override def warn(s: String) = w = s :: w })
    assert(div.innerHTML == """<div yolo="true"></div>""")
    assert(w == List("Unknown attribute yolo. Did you mean cols instead?"))
  }

  test("README warnings") {
    var w: List[String] = Nil
    def entity = <captain yolo="true" onClick={ () => println("Oh yeah!") }>{None.toString}</captain>
    val div = dom.document.createElement("div")
    mount(div, entity, new DevSettings { override def warn(s: String) = w = s :: w })
    assert(w == List(
      """Unknown event onClick. Did you mean onclick instead?""",
      """Unknown attribute yolo. Did you mean cols instead?""",
      """Unknown element captain. Did you mean caption instead?"""
    ))
  }

  test("Elements, attributes, events and EntityRef.keys arrays are binary searchable") {
    val d = new DevSettings {}
    def sorted(a: Array[String]): Boolean = a.toList.sorted == a.toList
    assert(sorted(d.elements))
    assert(sorted(d.attributes))
    assert(sorted(d.events))
    assert(sorted(EntityRefMap.keys))
  }

  test("EntityRefMap arrays and equaly sized") {
    assert(EntityRefMap.keys.length == EntityRefMap.values.length)
  }

  test("levenshtein") {
    val d = new DevSettings {}
    assert(d.levenshtein("kitten")("sitting") == 3)
    assert(d.levenshtein("rosettacode")("raisethysword") == 8)
  }

  test("Ill-typed Attribute") {
    assertTypeError("<form disabled={1.2f}></form>")
    assertTypeError("<form disabled={1}></form>")
    assertTypeError("<form disabled={Option.empty[Int]}></form>")
    assertTypeError("<form disabled={List.empty[String]}></form>")
    assertTypeError("<form disabled={List.empty[Text]}></form>")
    assertTypeError("<form disabled={Text(\"\"): xml.Node}></form>")
    assertTypeError("<form disabled={Rx(<div></div>)}></form>")
  }

  test("Ill-typed Element") {
    assertTypeError("<div>{true}</div>")
    assertTypeError("<div>{List.empty[String]}</div>")
    assertTypeError("{ class A; <div>{new A}</div>}")
  }

  test("mhtml-{onmount,onunmount} with Function0") {
    val div = dom.document.createElement("div")
    val rx: Var[Option[xml.Node]] = Var(None)
    var mounted = 0
    var unmounted = 0
    val node =
      <div
        mhtml-onmount={ () => mounted += 1 }
        mhtml-onunmount={ () => unmounted += 1 }
      ></div>
    def entity = <div>{rx}</div>
    assert(mounted == 0 && unmounted == 0)
    mount(div, entity)
    assert(mounted == 0 && unmounted == 0)
    rx := Some(node)
    assert(mounted == 1 && unmounted == 0)
    rx := None
    assert(mounted == 1 && unmounted == 1)
    rx := Some(node)
    assert(mounted == 2 && unmounted == 1)
    rx := None
    assert(mounted == 2 && unmounted == 2)
  }

  test("mhtml-{onmount,onunmount} with Function1") {
    val div = dom.document.createElement("div")
    val rx: Var[Option[xml.Node]] = Var(None)
    var mounted = 0
    var unmounted = 0
    val node =
      <div
        mhtml-onmount={ (e: dom.html.Element) => mounted += 1 }
        mhtml-onunmount={ (e: dom.html.Element) => unmounted += 1 }
      ></div>
    def entity = <div>{rx}</div>
    assert(mounted == 0 && unmounted == 0)
    mount(div, entity)
    assert(mounted == 0 && unmounted == 0)
    rx := Some(node)
    assert(mounted == 1 && unmounted == 0)
    rx := None
    assert(mounted == 1 && unmounted == 1)
    rx := Some(node)
    assert(mounted == 2 && unmounted == 1)
    rx := None
    assert(mounted == 2 && unmounted == 2)
  }

  test("Manually mutate the DOM via mhtml-onmount") {
    val mutateMe = dom.document.createElement("h1")
    mutateMe.innerHTML = "foobar"
    val entity = <div mhtml-onmount={ (e: dom.html.Element) => e.appendChild(mutateMe); () }></div>
    val div = dom.document.createElement("div")
    mount(div, entity)
    assert(div.innerHTML == "<div><h1>foobar</h1></div>")
    mutateMe.appendChild(dom.document.createElement("h2"))
    assert(div.innerHTML == "<div><h1>foobar<h2></h2></h1></div>")
  }

  test("Store README example") {
    // Data type for the entire application state:
    sealed trait State
    object State {
      val empty = new State {}
    }

    // Data type for events coming from the outside world:
    sealed trait Action
    trait TimeAction extends Action

    // A single State => Html function for the entire page:
    def view(state: Rx[State]): xml.Node = <div></div>

    // Probably implemented with Var, but we can look at them as Rx. Note that the
    // type can easily me made more precise by using <: Action instead:
    val action1_clicks: Rx[Action] = Var(new Action {})
    val action2_inputs: Rx[Action] = Var(new Action {})
    val action3_AJAX:   Rx[Action] = Var(new Action {})
    val action4_timer:  Rx[TimeAction] = Var(new TimeAction {})

    // Let's merges all actions together:
    val allActions: Rx[Action] =
      action1_clicks merge
      action2_inputs merge
      action3_AJAX   merge
      action4_timer

    // Compute the new state given an action and a previous state:
    // (I'm really not convinced by the name)
    def reducer(previousState: State, action: Action): State = previousState

    // The application State, probably initialize that from local store / DB
    // updates could also be save on every update.
    val store: Rx[State] = allActions.foldp(State.empty)(reducer)

    // Tie everything together:
    val root = dom.document.createElement("div")
    mount(root, view(store))
  }

  test("toString on xml node") {
    val node = <button class="c" id="1">Click Me!</button>
    val makeMeDream = """Elem(None,button,UnprefixedAttribute(xmlns,null,UnprefixedAttribute(class,Text(c),UnprefixedAttribute(id,Text(1),Null))),Some(TopScope),ArrayBuffer(Text(ClickMe!)))"""
    assert(node.toString.filterNot(_.isWhitespace) == makeMeDream)
  }
}
