package mhtml.examples

import scala.scalajs.js
import scala.xml.Elem
import scala.xml.Node

import mhtml._
import Utils._
import org.scalajs.dom.Event
import org.scalajs.dom.KeyboardEvent
import org.scalajs.dom.ext.KeyCode

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
  private def search(query: String)(
      toSearch: String): Option[(String, String, String)] = {
    val index = toSearch.toLowerCase.indexOf(query)
    if (index == -1) None
    else {
      val before = toSearch.substring(0, index)
      val after = toSearch.substring(index + query.length)
      val matched = toSearch.substring(index, index + query.length)
      Some((before, matched, after))
    }
  }

  def singleSelect[T <: AnyRef](getOptions: String => Rx[Seq[T]],
                                placeholder: String = "")(
      implicit ev: Searcheable[T]): (Node, Rx[Option[T]]) = {
    val rxFocused = Var(false)
    val rxIndex = Var(0)
    val rxQuery = Var("")
    val rxSelected = Var(Option.empty[T])
    def setCandidate(candidate: T): Unit = {
      rxIndex := 0
      rxSelected := Some(candidate)
      rxFocused := false
    }
    val candidatesWithApps: Rx[(Seq[T], Elem)] =
      // TODO(olafur) use applicative syntax where possible
      for {
        query <- rxQuery
        queryLower = query.toLowerCase
        index <- rxIndex
        options <- getOptions(query)
      } yield {
        val (x, lis) = options
          .map(x => x -> search(queryLower)(ev.show(x)))
          .zipWithIndex
          .collect {
            case ((candidate, Some((before, matched, after))), i) =>
              val cssClass =
                if (i == index) "chosen-highlight"
                else ""
              val li =
                <li class={cssClass}>
                  <a onclick={() => setCandidate(candidate)}>
                    {before}<u>{matched}</u>{after}
                  </a>
                </li>
              candidate -> li
          }
          .unzip
        val div =
          <div class="chosen-options">
            <ul style={rxFocused.map(if (_) None else Some("display: none"))}>
              {lis}
            </ul>
          </div>
        x -> div
      }
    val rxInputValue =
      for {
        selected <- rxSelected
        query <- rxQuery
      } yield selected.map(ev.show).getOrElse(query)
    val onkeyup = { e: KeyboardEvent =>
      e.keyCode match {
        case KeyCode.Up => rxIndex.update(x => Math.max(x - 1, 0))
        case KeyCode.Down =>
          rxIndex.update(_ + 1)
          rxFocused.update {
            case false => true
            case x => x
          }
        case KeyCode.Enter =>
          // TODO(olafur) is there a cleaner way?
          rxIndex.foreach { index =>
            candidatesWithApps
              .map(_._1)
              .foreach { candidates =>
                val i = index % candidates.length
                candidates.zipWithIndex
                  .find(_._2 == i)
                  .map(_._1)
                  .foreach(setCandidate)
              }
              .cancel()
          }.cancel()
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
               placeholder={placeholder}
               class="chosen-searchbar"
               value={rxInputValue}
               onblur={onblur}
               onfocus={() => rxFocused := true}
               onkeyup={onkeyup}/>{candidatesWithApps.map(_._2)}
      </div>
    (app, rxSelected)
  }
}
