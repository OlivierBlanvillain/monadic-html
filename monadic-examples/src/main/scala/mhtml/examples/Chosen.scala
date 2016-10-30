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

class QueryMatcher[T](queryUpper: String)(implicit val ev: Searcheable[T]) {
  val query = queryUpper.toLowerCase
  def unapply(arg: T): Option[(String, String, String)] = {
    val toSearch = ev.show(arg)
    val index = toSearch.toLowerCase.indexOf(query)
    if (index == -1) None
    else {
      val before = toSearch.substring(0, index)
      val after = toSearch.substring(index + query.length)
      val matched = toSearch.substring(index, index + query.length)
      Some((before, matched, after))
    }
  }
}

/** Searchable select lists inspired by https://harvesthq.github.io/chosen/ */
object Chosen {

  def singleSelect[T <: AnyRef](getOptions: String => Rx[Seq[T]],
                                placeholder: String = "")(
      implicit ev: Searcheable[T]): (Node, Rx[Option[T]]) = {
    val id = "chosen-" + Math.random().toInt
    val rxFocused = Var(false)
    val rxIndex = Var(0)
    val rxQuery = Var("")
    val rxSelected = Var(Option.empty[T])
    def setCandidate(candidate: T): Unit = {
      rxSelected := Some(candidate)
      dom.document.getElementById(id) match {
        case input: HTMLInputElement => input.value = ev.show(candidate)
        case _ =>
      }
    }
    val rxCandidatesWithApp: Rx[(Seq[T], Node)] =
      for {
        query <- rxQuery
        index <- rxIndex
        options <- getOptions(query)
      } yield {
        val Match = new QueryMatcher[T](query)
        val candidatesWithMatches = options.collect {
          case candidate @ Match(a, b, c) => (candidate, a, b, c)
        }
        val candidates = candidatesWithMatches.map(_._1)
        val listItems =
          candidatesWithMatches.zipWithIndex.collect {
            case ((candidate, before, matched, after), i) =>
              val cssClass =
                if (i == index) "chosen-highlight"
                else ""
              <li class={cssClass}>
                <a onclick={() => setCandidate(candidate)}>
                  {before}<u>{matched}</u>{after}
                </a>
              </li>
          }
        val div =
          <div class="chosen-options">
            <ul style={rxFocused.map(if (_) "" else "display: none")}>
              {listItems}
            </ul>
          </div>
        candidates -> div
      }
    val rxCandidates: Rx[Seq[T]] = rxCandidatesWithApp.map(_._1)
    val highlightedCandidate: Rx[T] = (for {
      index <- rxIndex
      candidates <- rxCandidates
    } yield {
      candidates.zipWithIndex.find(_._2 == index).map(_._1)
    }).collect { case Some(x) => x }
    val onkeyup = { e: KeyboardEvent =>
      e.keyCode match {
        case KeyCode.Up => rxIndex.update(x => Math.max(x - 1, 0))
        case KeyCode.Down =>
          rxCandidates.foreach { candidates =>
            rxIndex.update(x => Math.min(x + 1, candidates.length - 1))
          }.cancel()
          rxFocused.update {
            case false => true
            case x => x
          }
        case KeyCode.Enter =>
          highlightedCandidate.foreach(setCandidate).cancel()
        case _ =>
          e.target match {
            case input: HTMLInputElement =>
              rxQuery.update(_ => input.value)
            case _ =>
          }
      }
      ()
    }
    val onblur = { _: Event =>
      js.timers.setTimeout(200)(rxFocused := false)
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
               onkeyup={onkeyup}/>{rxCandidatesWithApp.map(_._2)}
      </div>
    (app, rxSelected)
  }
}
