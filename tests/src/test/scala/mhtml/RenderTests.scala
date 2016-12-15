package mhtml

import org.scalajs.dom
import org.scalatest.FunSuite

class RenderTests extends FunSuite {
  def render(node: xml.Node): String = {
    val div = dom.document.createElement("div")
    mount(div, node)
    div.innerHTML
  }

  def check(node: xml.Node, expected: String): Unit =
    test(expected)(assert(render(node) == expected))

  // well-typed elements
  check(<div>{Rx(Option(1))}</div>                , "<div>1</div>")
  check(<div>{Option(2)}</div>                    , "<div>2</div>")
  check(<div>{'a'}</div>                          , "<div>a</div>")
  check(<div>{3}</div>                            , "<div>3</div>")
  check(<div>{4L}</div>                           , "<div>4</div>")
  check(<div>{4.2d}</div>                         , "<div>4.2</div>")
  check(<div>{5.0f}</div>                         , "<div>5</div>")
  check(<div>{Rx(Option.empty[Int])}6</div>       , "<div>6</div>")
  check(<div>{Option.empty[Int]}7</div>           , "<div>7</div>")
  check(<div>{"string"}</div>                     , "<div>string</div>")
  check(<div>{List(scala.xml.Comment("a"))}</div> , "<div><!--a--></div>")

  // optional attribute
  check(<form disabled={Rx(true)}>1</form>             , """<form disabled="">1</form>""")
  check(<form disabled={Rx(false)}>1</form>            , """<form>1</form>""")
  check(<form disabled={true}>2</form>                 , """<form disabled="">2</form>""")
  check(<form disabled={false}>2</form>                , """<form>2</form>""")
  check(<form disabled={Option("banana")}>3</form>     , """<form disabled="banana">3</form>""")
  check(<form disabled={Option.empty[String]}>3</form> , """<form>3</form>""")
  check(<form disabled={Option(Rx(true))}>4</form>     , """<form disabled="">4</form>""")

}
