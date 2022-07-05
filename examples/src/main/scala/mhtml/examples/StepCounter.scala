package examples

import mhtml._
import org.scalajs.dom

object StepCounter extends Example {
  def app: xml.Node = {
    sealed trait Operation
    final case class IncrementStep(step: Int) extends Operation
    case object Reset                         extends Operation

    val step = Var(1)
    val incrementClicks = Var(())
    val resetClicks = Var(Reset)

    val incrementOps =  step.sampleOn(incrementClicks).map(IncrementStep.apply)
    val allOps: Rx[Operation] = incrementOps.merge(resetClicks)

    val counter = allOps.foldp(0) { (prev, op) =>
      op match {
        case IncrementStep(step) => prev + step
        case Reset               => 0
      }
    }

    <div>
      <h1>{ counter }</h1>
      <p><input type="number" value="1" onchange={ (e: dom.Event) =>
        step := e.target.asInstanceOf[dom.html.Input].value.toInt
      } /></p>
      <div>
        <button onclick={ () => incrementClicks := (()) }>Increment</button>
        <button onclick={ () => resetClicks := Reset }>Reset</button>
      </div>
    </div>
  }
}
