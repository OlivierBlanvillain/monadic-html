package mhtml.examples

import scala.scalajs.js
import scala.xml.Node

import mhtml._
import org.scalajs.dom
import org.scalajs.dom.Event
import org.scalajs.dom.KeyboardEvent
import org.scalajs.dom.ext.KeyCode
import org.scalajs.dom.raw.HTMLInputElement

/** Typeclass for [[Chosen]] select lists */
trait Searcheable[T] {
  def show(t: T): String
  def isCandidate(query: String)(t: T): Boolean =
    show(t).toLowerCase().contains(query)
}

object Searcheable {
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
    if (index == -1) <span>{toUnderline}</span>
    else {
      val before = toUnderline.substring(0, index)
      val after = toUnderline.substring(index + query.length)
      val matched = toUnderline.substring(index, index + query.length)
      <span>{before}<u>{matched}</u>{after}</span>
    }
  }

  def singleSelect[T](getCandidates: String => Rx[Seq[T]],
                      placeholder: String = "",
                      maxCandidates: Int = 10)(
      implicit ev: Searcheable[T]): (Node, Rx[Option[T]]) = {
    val id = "chosen-" + Math.random().toInt // to reference input dom
    val rxFocused = Var(false)
    val rxIndex = Var(0)
    val rxQuery = Var("")
    val rxSelected = Var(Option.empty[T])
    def setQuery(value: String): Unit = {
      rxQuery := value
      rxIndex := 0
      rxFocused := true
    }
    def setCandidate(candidate: T): Unit = {
      rxSelected := Some(candidate)
      rxFocused := false
      dom.document.getElementById(id) match {
        case input: HTMLInputElement => input.value = ev.show(candidate)
        case _ =>
      }
    }
    val rxCandidatesWithApp: Rx[(Node, Seq[T])] = for {
      query <- rxQuery
      index <- rxIndex
      allCandidates <- getCandidates(query)
      queryLower = query.toLowerCase
    } yield {
      val candidates =
        allCandidates.filter(ev.show(_).toLowerCase.contains(queryLower))
      val toDrop = Math.max(0, index - 3)
      val listItems =
        candidates.zipWithIndex.slice(toDrop, toDrop + maxCandidates).map {
          case (candidate, i) =>
            val cssClass =
              if (i == index) "chosen-highlight"
              else ""
            <li class={cssClass}>
              <a onclick={() => setCandidate(candidate)}>
                {underline(ev.show(candidate), queryLower)}
              </a>
            </li>
        }
      val itemsBefore: Node =
        if (toDrop == 0) <span></span>
        else <li>{toDrop} more items...</li>
      val itemsAfter: Node = {
        val remaining =
          Math.max(0, candidates.length - (toDrop + maxCandidates))
        if (remaining == 0) <span></span>
        else <li>{remaining} more items...</li>
      }
      val style = rxFocused.map { focused =>
        val display = if (focused) "" else "display: none"
        s"$display"
      }
      val div =
        <div class="chosen-options">
            <ul style={style}>
              {itemsBefore}
              {listItems.toList}
              {itemsAfter}
            </ul>
          </div>
      div -> candidates
    }
    val rxCandidates: Rx[Seq[T]] = rxCandidatesWithApp.map(_._2)
    val highlightedCandidate: Rx[T] =
      (for { index <- rxIndex; candidates <- rxCandidates } yield {
        candidates.zipWithIndex.find(_._2 == index).map(_._1)
      }).collect { case Some(x) => x }
    // event handlers
    val onkeyup = { e: KeyboardEvent =>
      e.keyCode match {
        case KeyCode.Up =>
          rxIndex.update(x => Math.max(x - 1, 0))
          rxFocused := true
        case KeyCode.Down =>
          rxCandidates.foreach { candidates =>
            rxIndex.update(x => Math.min(x + 1, candidates.length - 1))
          }.cancel()
          rxFocused := true
        case KeyCode.Enter =>
          highlightedCandidate.foreach(setCandidate).cancel()
        case _ =>
          e.target match {
            case input: HTMLInputElement => setQuery(input.value)
            case _ =>
          }
      }
      ()
    }
    val onblur = { _: Event =>
      js.timers.setTimeout(300)(rxFocused := false)
      ()
    }
    val app =
      <div class="chosen-wrapper">
        <input type="text"
               id={id}
               placeholder={placeholder}
               class="chosen-searchbar"
               onblur={onblur}
               onfocus={() => rxFocused := true}
               onkeyup={onkeyup}/>
        {rxCandidatesWithApp.map(_._1)}
      </div>
    (app, rxSelected)
  }
}
