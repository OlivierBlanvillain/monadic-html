package examples

import mhtml._

object Counter extends Example {
  def app: xml.Node = {
    val counter = Var(0)
    <div>
      <button onclick={() => counter.update(_ - 1)}>-</button>
      {counter}
      <button onclick={() => counter.update(_ + 1)}>+</button>
    </div>
  }
}
