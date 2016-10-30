package mhtml.examples

import scala.xml.Elem
import scala.xml.Node

import mhtml._
import Utils._

object ProductTable extends Example {
  case class Product(name: String,
                     price: Double,
                     category: String,
                     stocked: Boolean)
  case class State(query: String, showOnlyStockedItems: Boolean)
  val allProducts = Seq[Product](
    Product("Football", 49.99, "Sporting Goods", true),
    Product("Baseball", 9.99, "Sporting Goods", true),
    Product("Basketball", 29.99, "Sporting Goods", false),
    Product("iPod touch", 99.99, "Electronics", true),
    Product("iPhone 5", 499.99, "Electronics", true),
    Product("Nexus 7", 199.99, "Electronics", true)
  )

  def productFilter(s: State)(p: Product): Boolean =
    p.name.toLowerCase.contains(s.query.toLowerCase) &&
      (!s.showOnlyStockedItems || p.stocked)

  def row(product: Product) = {
    val color = if (product.stocked) "" else "color: red"
    <tr>
      <td><span style={color}>{product.name}</span></td>
      <td>{product.price}</td>
    </tr>
  }

  def app: Node = {
    val rxState = Var(State("", showOnlyStockedItems = false))
    val filteredProducts: Rx[Seq[Elem]] = rxState.map { state =>
      allProducts.filter(productFilter(state)).map(row)
    }
    val onkeyup = inputEvent(
      input => rxState.update(_.copy(query = input.value)))
    val onclick = inputEvent(
      input => rxState.update(_.copy(showOnlyStockedItems = input.checked)))
    <div>
      <form>
        <input type="text" placeholder="Search bar..." onkeyup={onkeyup}/>
        <p>
          <input type="checkbox" onclick={onclick}/>
          Only show products in stock
        </p>
      </form>
      <table>
        <thead>
          <tr>
            <th>Name</th> <th>Price</th>
          </tr>
        </thead>
        <tbody>
          {filteredProducts}
        </tbody>
      </table>
    </div>
  }
}
