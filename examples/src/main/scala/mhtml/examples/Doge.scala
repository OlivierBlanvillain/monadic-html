package mhtml.examples

import mhtml._

object Doge extends Example {
  def app: xml.Node = {
    val count = Var(0)
    val doge =
      <img style="width: 100px;" src="http://doge2048.com/meta/doge-600.png"/>
    val rxDoges: Rx[Seq[xml.Node]] =
      count.map(i => Seq.fill(i)(doge))
    <div>
      <button onclick={ () => count.update(_ + 1) }>Click Me!</button>
      {count.filter(_ > 0).map(_ => <h2>WOW!!!</h2>)}
      {count.filter(_ > 2).map(_ => <h2>MUCH REACTIVE!!!</h2>)}
      {count.filter(_ > 5).map(_ => <h2>SUCH BINDING!!!</h2>)}
      {rxDoges}
    </div>
  }
}
