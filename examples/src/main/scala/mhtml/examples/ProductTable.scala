package examples

import mhtml._

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

  def productFilter(query: String, showOnlyStockedItems: Boolean)(
      p: Product): Boolean =
    p.name.toLowerCase.contains(query.toLowerCase) &&
      (!showOnlyStockedItems || p.stocked)

  def row(product: Product) = {
    val color = if (product.stocked) "" else "color: red"
    <tr>
      <td><span style={color}>{product.name}</span></td>
      <td>{product.price.toString}</td>
    </tr>
  }

  def app: xml.Node = {
    val rxQuery = Var("")
    val rxShowOnlyStockedItems = Var(false)
    val filteredProducts: Rx[Seq[xml.Node]] = for {
      query <- rxQuery
      onlyStockedItems <- rxShowOnlyStockedItems
    } yield {
      allProducts.filter(productFilter(query, onlyStockedItems)).map(row)
    }
    val onkeyup =
      Utils.inputEvent(input => rxQuery := input.value)
    val onclick =
      Utils.inputEvent(input => rxShowOnlyStockedItems := input.checked)
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
