package mhtml

import org.scalajs.dom
import org.scalajs.dom.raw.SVGAElement
import org.scalatest.funsuite.AnyFunSuite

class RenderTests extends AnyFunSuite {
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

  check(<text>{">"}</text>, """<text>&gt;</text>""")
  check(<text>{"<"}</text>, """<text>&lt;</text>""")
  check(<text>{'"'}</text>, """<text>"</text>""")
  check(<text>{"\u00A0"}</text>, "<text>&nbsp;</text>") // &nbsp;

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
    val svgNode = firstByTagName(mountNode(<svg xmlns="http://hello.com"></svg>), "svg")
    assert(svgNode.namespaceURI == "http://hello.com")
  }

  test("attributes with prefix are set with namespace") {
    val svg =
      <svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink">
        <a xlink:href="test.html">
          <rect x="10" y="10" ry="5" width="40" height="40" />
        </a>
      </svg>

    val linkNode = firstByTagName(mountNode(svg), "a").asInstanceOf[SVGAElement]

    assert(linkNode.namespaceURI == "http://www.w3.org/2000/svg")
    assert(linkNode.getAttributeNS("http://www.w3.org/1999/xlink", "href") == "test.html")
    assert(linkNode.getAttribute("href") == null)
    assert(linkNode.getAttribute("xlink:href") == "test.html")
  }

  test("created element has default namespace if no namespace defined") {
    val divNode = firstByTagName(mountNode(<div></div>), "div")
    assert(divNode.namespaceURI == "http://www.w3.org/1999/xhtml")
  }

  def firstByTagName(parent: dom.raw.Element, tagName: String): dom.Element =
    parent.getElementsByTagName(tagName)(0).asInstanceOf[dom.Element]
}
