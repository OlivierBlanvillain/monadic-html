package mhtml

import org.scalajs.dom
import org.scalatest.FunSuite

class RenderTests extends FunSuite {
  def render(node: xml.Node): String = mountNode(node).innerHTML

  def mountNode(node: xml.Node): dom.raw.Element = {
    val div = dom.document.createElement("div")
    mount(div, node)
    div
  }

  def check(node: xml.Node, expected: String): Unit =
    test(expected)(assert(render(node) == expected))

  // Well-typed elements
  check(<div>{Rx(Some(1))}</div>            , "<div>1</div>")
  check(<div>{Some(2)}</div>                , "<div>2</div>")
  check(<div>{Some(<p></p>)}</div>          , "<div><p></p></div>")
  check(<div>{None}</div>                   , "<div></div>")
  check(<div>{'a'}</div>                    , "<div>a</div>")
  check(<div>{3}</div>                      , "<div>3</div>")
  check(<div>{4L}</div>                     , "<div>4</div>")
  check(<div>{4.2d}</div>                   , "<div>4.2</div>")
  check(<div>{5.0f}</div>                   , "<div>5</div>")
  check(<div>{Rx(None)}6</div>              , "<div>6</div>")
  check(<div>{None}7</div>                  , "<div>7</div>")
  check(<div>{"string"}</div>               , "<div>string</div>")
  check(<div>{List(xml.Comment("a"))}</div> , "<div><!--a--></div>")

  // Optional attributes
  check(<form disabled={Rx(true)}>1</form>       , """<form disabled="">1</form>""")
  check(<form disabled={Rx(false)}>1</form>      , """<form>1</form>""")
  check(<form disabled={true}>2</form>           , """<form disabled="">2</form>""")
  check(<form disabled={false}>2</form>          , """<form>2</form>""")
  check(<form disabled={Some("banana")}>3</form> , """<form disabled="banana">3</form>""")
  check(<form disabled={None}>3</form>           , """<form>3</form>""")
  check(<form disabled={Some(Rx(true))}>4</form> , """<form disabled="">4</form>""")

  check(<p>{emptyHTML}</p>                       , "<p></p>")

  check(<svg xmlns="http://hello.com"></svg>,
     """<svg xmlns="http://hello.com"></svg>""")

  check(<svg xmlns:hello="http://hello.com" xmlns:world="http://world.com"></svg>,
     """<svg xmlns:hello="http://hello.com" xmlns:world="http://world.com"></svg>""")

  // No way to get that XXX out, it's apparently thrown away by scalac parser
  check(<svg xmlnsXXX="http://hola.com"></svg>,
     """<svg xmlns="http://hola.com"></svg>""")

  // Position of xmlns among other attributes is also lost forever in the parser
  check(<svg id="I" xmlns="http://hello.com" class="C"></svg>,
     """<svg xmlns="http://hello.com" id="I" class="C"></svg>""")

  test("created element has namespace if defined") {
    val svgNode = mountNode(<svg xmlns="http://hello.com"></svg>).getElementsByTagName("svg")(0)
    assert(svgNode.namespaceURI == "http://hello.com")
  }
}
