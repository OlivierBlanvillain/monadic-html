package examples

import scala.xml.Node
import scala.scalajs.js
import mhtml._
import mhtml.implicits.cats._
import cats.implicits._
import org.scalajs.dom.KeyboardEvent
import org.scalajs.dom.ext.KeyCode
import org.scalajs.dom.raw.HTMLInputElement

/** Typeclass for [[Chosen]] select lists */
trait Searcheable[T] {
  def show(t: T): String
}

object Searcheable {
  def apply[T](implicit ev: Searcheable[T]): Searcheable[T] = ev
  def instance[T](f: T => String): Searcheable[T] = new Searcheable[T] {
    override def show(t: T): String = f(t)
  }
  implicit val stringSearchable: Searcheable[String] =
    instance[String](identity)
}

/** Searchable select lists inspired by https://harvesthq.github.io/chosen/ */
object Chosen {
  def underline(toUnderline: String, query: String): Node = {
    val index = toUnderline.toLowerCase.indexOf(query)
    if (index == -1) <span>{ toUnderline }</span>
    else {
      val before  = toUnderline.substring(0, index)
      val after   = toUnderline.substring(index + query.length)
      val matched = toUnderline.substring(index, index + query.length)
      <span>{ before }<u>{ matched }</u>{ after }</span>
    }
  }

  def singleSelect[T: Searcheable](candidates: Rx[List[T]], placeholder: String): (Node, Rx[Option[T]]) = {
    // These are the 5 streams of events involved in into this component.
    // By events, we mean that these are actually binded exactly once to
    // external sources (via :=). Given that scalac prohibits forward
    // references, and everything is composed functionally, this code is
    // guaranteed to have no infinite loops or race conditions.

    val focusEvents: Var[Unit] = Var(())
    val queryEvents: Var[String] = Var("")
    val arrowPressedEvents: Var[Int] = Var(-1) // -1 → up; +1 → down
    val enterPressedEvents: Var[Unit] = Var(())
    val clickSelectionEvents: Var[Option[T]] = Var(None)

    val maxCandidates: Int = 10
    val rxFilteredCandidates =
      (queryEvents, candidates).mapN { case (query, allCandidates) =>
        allCandidates.filter(Searcheable[T].show(_).toLowerCase.contains(query.toLowerCase))
      }

    val rxIndex: Rx[Int] = (
        arrowPressedEvents.map(Option(_)) |+| focusEvents.map(_ => None),
        rxFilteredCandidates
      ).mapN { case (event, filteredCandidates) =>
        (event, filteredCandidates.size - 1)
      }.foldp(0) {
        case (last, (Some(delta), limit)) =>
          0 max (last + delta) min limit
        case _ => 0 // This reset corresponds to an acquisition of focus.
      }

    val rxFocused: Rx[Boolean] = // LOL scalafmt
               focusEvents.map(_ => true ) |+|
               queryEvents.map(_ => true ) |+|
        arrowPressedEvents.map(_ => true ) |+|
        enterPressedEvents.map(_ => false) |+|
      clickSelectionEvents.map(_ => false)

    val rxHighlightedCandidate: Rx[Option[T]] =
      (rxFilteredCandidates, rxIndex).mapN { case (cands, index) =>
        cands.zipWithIndex.find(_._2 == index).map(_._1)
      }.keepIf(_.nonEmpty)(None)

    val rxSelected: Rx[Option[T]] =
      rxHighlightedCandidate.sampleOn(enterPressedEvents) |+| clickSelectionEvents

    val rxChosenOptions: Rx[Node] =
      (rxIndex, rxFocused, queryEvents, rxFilteredCandidates).mapN {
        case(index, focus, query, fcand) =>
          def bounds(i: Int): Int = if (fcand.size > maxCandidates) i max 0 else 0
          val toDrop = bounds(index - 3)
          val remain = bounds(fcand.size - (toDrop + maxCandidates))
          val style  = if (focus)  None else Some("display: none")
          val itemsBefore = if (toDrop == 0) None else Some(<li>{ toDrop } more items...</li>)
          val itemsAfter  = if (remain == 0) None else Some(<li>{ remain } more items...</li>)
          val listItems   =
            fcand.zipWithIndex.slice(toDrop, toDrop + maxCandidates).map { case (candidate, i) =>
              <li class={ if (i == index) "chosen-highlight" else "" }>
                <a onclick={ () => clickSelectionEvents := Some(candidate) }>
                  { underline(Searcheable[T].show(candidate), query.toLowerCase) }
                </a>
              </li>
            }
          <div class="chosen-options">
            <ul style={ style }>
              { itemsBefore }
              { listItems }
              { itemsAfter }
            </ul>
          </div>
      }

    var cancelableSelectionHandler = Cancelable.empty
    def selectionHandler(node: HTMLInputElement): Unit =
      cancelableSelectionHandler =
        rxSelected.impure.run(c => node.value = c.map(Searcheable[T].show).getOrElse(""))

    def onkeydown(e: KeyboardEvent): Unit =
      e.keyCode match {
        case KeyCode.Up    => arrowPressedEvents := -1
        case KeyCode.Down  => arrowPressedEvents := +1
        case KeyCode.Enter => enterPressedEvents := (())
        case _ => ()
      }

    def oninput(event: js.Dynamic): Unit =
      queryEvents := event.target.value.asInstanceOf[String]

    // @olfa: This implementation does not really makes sense as it's a tick
    // over the absolute clock. Proper implementation should start/cancel the
    // timeout on focus gained/lost.
    // def onblur(): Unit = { scala.scalajs.js.timers.setTimeout(1000)(rxFocused := false); () }

    val app =
      <div class="chosen-wrapper">
        <input type="text" class="chosen-searchbar"
          mhtml-onmount   = { selectionHandler _ }
          mhtml-onunmount = { () => cancelableSelectionHandler.cancel() }
          placeholder     = { placeholder }
          onfocus         = { () => focusEvents := (()) }
          onkeydown       = { onkeydown _ }
          oninput         = { oninput _ }/>
        { rxChosenOptions }
      </div>

    (app, rxSelected)
  }
}
