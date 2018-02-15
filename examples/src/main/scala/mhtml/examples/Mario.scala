package examples

import scala.scalajs.js
import org.scalajs.dom
import org.scalajs.dom.KeyboardEvent
import org.scalajs.dom.ext.KeyCode
import scala.xml.Node
import Function.tupled
import mhtml._
import mhtml.implicits.cats._
import cats.implicits._

/** mhtml port of http://elm-lang.org:1234/examples/mario */
object Mario extends Example {
  // MODEL

  sealed trait Direction
  final case object Left  extends Direction
  final case object Right extends Direction

  case class Model(x: Double, y: Double, vx: Double, vy: Double, dir: Direction)
  case class Keys(x: Int, y: Int)

  // UPDATE

  def step(dt: Double, keys: Keys): Model => Model =
    gravity(dt) _ andThen
    jump(keys)  _ andThen
    walk(keys)  _ andThen
    physics(dt) _ // andThen { m => println(s"model: $m, dt: $dt, keys: $keys"); m }

  def jump(keys: Keys)(mario: Model): Model =
    if (keys.y > 0 && mario.vy == 0) mario.copy(vy = 5.0) else mario

  def gravity(dt: Double)(mario: Model): Model =
    mario.copy(vy = if (mario.y > 0) mario.vy - dt/6 else 0)

  def physics(dt: Double)(mario: Model): Model =
    mario.copy(
      x = mario.x + dt * mario.vx,
      y = .0 max (mario.y + dt * mario.vy))

  def walk(keys: Keys)(mario: Model): Model =
    mario.copy(
      vx  = keys.x.toDouble,
      dir = if (keys.x < 0) Left
       else if (keys.x > 0) Right
       else mario.dir)

  // DISPLAY

  def display(mario: Rx[Model]): Node = {
    val style = mario.map(m => s"margin-top: ${80 - m.y}px; margin-left: ${m.x}px;")
    val src = mario.map { m =>
      val verb = if (m.y   > 0) "jump"
            else if (m.vx != 0) "walk"
            else "stand"
      val dir = m.dir.toString.toLowerCase
      s"http://elm-lang.org:1234/imgs/mario/$verb/$dir.gif"
    }
    <div style="height: 120px">
      <img width="35" height="35" src={src.dropRepeats} style={style} />
    </div>
  }

  // SIGNALS

  val deltas: Rx[Double] = InputLib.fps(60).map(_ / 10)
  val keys: Rx[Keys] = InputLib.arrows.map(tupled(Keys))
  val inputs: Rx[(Double, Keys)] = (deltas, keys).tupled.sampleOn(deltas)

  val mari0 = Model(x = 0, y = 0, vx = 0, vy = 0, dir = Right)
  def step0(m: Model, i: (Double, Keys)): Model = tupled(step _)(i)(m)
  def app = display(inputs.foldp(mari0)(step0))
}

/** Models time and keyboard events as `Rx`. */
object InputLib {
  /** Sequence of time deltas for the desired amount of frame par second (FPS). */
  def fps(n: Int): Rx[Double] =
    Var.create[Double](0)({ tick =>
      var previous = js.Date.now()
      val i = dom.window.setInterval({ () =>
        val now = js.Date.now()
        tick := now - previous
        previous = now
      }, 1000.0 / n)
      Cancelable(() => dom.window.clearInterval(i))
    })

  /** A `Rx` of indicating which arrow keys are pressed. */
  val arrows: Rx[(Int, Int)] =
    Var.create[(Int, Int)]((0, 0))({ out =>
      // Simplistic implementation: DR DL UL results in neutral instead of L.
      dom.document.onkeydown = { e: KeyboardEvent =>
        e.keyCode match {
          case KeyCode.Left  => out.update(_.copy(_1 = -1))
          case KeyCode.Right => out.update(_.copy(_1 =  1))
          case KeyCode.Up    => out.update(_.copy(_2 =  1))
          case _ => ()
        }
      }
      dom.document.onkeyup = { e: KeyboardEvent =>
        e.keyCode match {
          case KeyCode.Left  => out.update(_.copy(_1 = 0))
          case KeyCode.Right => out.update(_.copy(_1 = 0))
          case KeyCode.Up    => out.update(_.copy(_2 = 0))
          case _ => ()
        }
      }
      Cancelable({ () =>
        dom.document.onkeydown = null
        dom.document.onkeyup = null
      })
    })
}
