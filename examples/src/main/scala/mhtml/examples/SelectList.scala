package examples

import scala.util.Failure
import scala.util.Success
import scala.xml.Node

import mhtml._
import org.scalajs.dom
import org.scalajs.dom.ext.Ajax

case class Country(name: String, isoCode: String) {
  def svgUrl(iso: String): String =
    s"https://raw.githubusercontent.com/hjnilsson/country-flags/master/svg/${iso.toLowerCase}.svg"
  def svg: Node = {
    val id = Math.random().toString // Random id to insert loaded svg.
    Utils.fromFuture(Ajax.get(svgUrl(isoCode))).impure.foreach {
      case Some(Success(response)) =>
        // Can't scala.xml.Xml.load to get scala.xml.Node instance, instead
        // we bypass mhtml and insert the svg directly into the dom.
        val elem = dom.document.getElementById(id)
        elem.innerHTML = response.responseText
      case _ =>
    }
    <span id={id}></span>
  }
}
object Country {
  implicit val countrySearchable = Searcheable.instance[Country](_.name)
}

object SelectList extends Example {
  val countriesUrl =
    "https://gist.githubusercontent.com/marijn/396531/raw/5007a42db72636a9eee6efcb115fbfe348ff45ee/countries.txt"

  // Each line in the input is formatted like this:
  // AS|American Samoa
  val country = "([A-Z]{2})\\|(.*)".r

  def app: Node = {
    val options = Var(List.empty[Country])
    Utils.fromFuture(Ajax.get(countriesUrl)).impure.foreach {
      case Some(Success(response)) =>
        options := response.responseText.lines.collect {
          case country(code, name) => Country(name, code)
        }.toSeq
      case Some(Failure(e)) =>
        e.printStackTrace()
      case _ =>
    }
    val (app, selected) = Chosen.singleSelect(_ => options)
    val message: Rx[Node] = selected.map {
      case Some(x) => <div>
        <p>You selected: '{x.name}'</p>
        <p>{x.svg}</p>
      </div>
      case _ => <p>Please select a country</p>
    }
    <div>
      {app}
      <p>{message}</p>
    </div>
  }
}
