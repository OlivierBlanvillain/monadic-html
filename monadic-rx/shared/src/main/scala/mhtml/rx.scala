package mhtml

/** Reactive value of type `A`. Automatically recalculate on dependency update. */
trait Rx[+A] {
  /**
   * Applies the side effecting function `f` to each update of this [[Rx]].
   *
   * Returns a [[Cancelable]] which can be used to cancel the subscription.
   * Caution: not canceling disposable subscriptions leaks memory.
   */
  def foreach(s: A => Unit): Cancelable

  /** Returns a new [[Rx]] that maps each update of this [[Rx]] via `f`. */
  def map[B](f: A => B): Rx[B] =
    Var.unsafeCreate[B](None)(self => this.foreach(self := f(_)))

  /** Returns a new [[Rx]] that flat-maps each update of this [[Rx]] via `f`. */
  def flatMap[B](f: A => Rx[B]): Rx[B] = {
    var cc = Cancelable.empty
    // fb is an optimization: it prevents un/resubscribing when f is a
    // constant function, which appends all the time in applicative style.
    var fb: Rx[B] = null

    Var.unsafeCreate[B](None)(self => this.foreach { a =>
      val newFb = f(a)
      if (fb != newFb) {
        cc.cancel()
        cc = newFb.foreach(self.:=)
        fb = newFb
      }
    })
  }

  /**
   * Create a past-dependent [[Rx]]. Each update from the incoming [[Rx]] will
   * be used to step the state forward. The outgoing [[Rx]] represents the
   * current state. Note that in the general settings of dynamic dependency
   * graph, `foldp` breaks referential transparency.
   */
  def foldp[B](seed: B)(step: (A, B) => B): Rx[B] = {
    var b = seed
    this.map { a =>
      val next = step(a, b)
      b = next
      next
    }
  }

  /**
   * Returns a new [[Rx]] with updates fulfilling the predicate `fp`.
   * If the first update is dropped, the `a` value is used instead.
   *
   * {{{
   * val numbers: Rx[Int]
   * val even: Rx[Int] = numbers.keepIf(_ % 2 == 0)(-1)
   * // numbers => 0 0 3 4 5 6 ...
   * // even    => 0 0   4   6 ...
   * }}}
   */
  def keepIf[B >: A](f: B => Boolean)(b: B): Rx[B] =
    Var.create[B](b)(self => this.foreach(a => if (f(a)) self := a else ()))

  /**
   * Returns a new [[Rx]] without updates fulfilling the predicate `f`.
   * If the first update is dropped, the `a` value is used instead.
   *
   * {{{
   * val numbers: Rx[Int]
   * val even: Rx[Int] = numbers.dropIf(_ % 2 == 0)(-1)
   * // numbers =>  0 0 3 4 5 6 ...
   * // even    => -1   3   5   ...
   * }}}
   */
  def dropIf[B >: A](f: B => Boolean)(a: B): Rx[B] = this.keepIf[B](!f(_))(a)

  /**
   * Returns a new [[Rx]] with updates where `f` is defined and mapped by `f`.
   * If the first update is dropped, the `b` value is used instead.
   */
  def collect[B](f: PartialFunction[A, B])(b: B): Rx[B] =
    Var.create[B](b)(self => this.foreach(x => if (f.isDefinedAt(x)) self := f(x) else ()))

  /**
   * Drop repeated value of this [[Rx]].
   *
   * {{{
   * val numbers: Rx[Int]
   * val noDups: Rx[Int] = numbers.dropRepeats
   * // numbers => 0 0 3 3 5 5 5 4 ...
   * // noDups  => 0   3   5     4 ...
   * }}}
   *
   * Note: This could also be implemented in term of keepIf map and foldp.
   */
  def dropRepeats: Rx[A] =
    Var.unsafeCreate[A](None)({ self =>
      var previous: Option[A] = None
      this.foreach { x =>
        if (previous.forall(x.!=)) {
          self := x
          previous = Some(x)
        }
      }
    })

  /**
   * Merge two [[Rx]] into one.
   *
   * {{{
   * val r1: Rx[Int]
   * val r2: Rx[Int]
   * val merged: Rx[Int] = r1.merge(r2)
   * // r1     => 0 8     3 ...
   * // r2     => 1   4 3   ...
   * // merged => 0 8 4 3 3 ...
   * }}}
   *
   * If an update comes from either of the incoming [[Rx]], the outgoing [[Rx]]
   * is updated. Upon creation, the current value from the first [[Rx]] if kept.
   */
  def merge[B >: A](rx: Rx[B]): Rx[B] =
    Var.unsafeCreate[B](None)({ v =>
      val c2 = rx.foreach(v.:=)
      val c1 = this.foreach(v.:=)
      c1.alsoCanceling(c2)
    })

  /** Sample from the second input every time an event occurs on the first input. */
  def sampleOn[B](rb: Rx[B]): Rx[A] =
    Var.unsafeCreate[A](None)({ out =>
      var currentA: A = null.asInstanceOf[A]
      val ca = this.foreach(currentA = _)
      val cb = rb.foreach(_ => out := currentA)
      ca.alsoCanceling(cb)
    })

  /** The current value of this [[Rx]]. */
  def value: A = {
    var v: Option[A] = None
    foreach(a => v = Some(a)).cancel()
    // This can never happen if using the default Rx/Var constructors and
    // methods. The proof is a simple case analysis showing that every method
    // preseves non emptiness. Var created with unsafeCreate Messing up with Var unsafe constructor
    // or internal could lead to this exception.
    def error = new NoSuchElementException("Requesting value of an empty Rx.")
    v.getOrElse(throw error)
  }
}

object Rx {
  /** Creates a constant [[Rx]]. */
  def apply[A](v: A): Rx[A] =
    new Rx[A] {
      def foreach(f: A => Unit): Cancelable = {
        f(v)
        Cancelable.empty
      }
    }
}

/** A smart variable that can be set manually. */
class Var[A](initialValue: Option[A], register: Var[A] => Cancelable) extends Rx[A] {
  // Last computed value, retained to be sent to new subscribers as they come in.
  protected var cacheElem: Option[A] = initialValue
  // Current registration to the feeding `Rx`, canceled whenever nobody's listening.
  protected var registration: Cancelable = Cancelable.empty
  // Mutable set of all currently subscribed functions, implementing with an `Array`.
  protected val subscribers = buffer.empty[A => Unit]

  def foreach(s: A => Unit): Cancelable = {
    if (subscribers.isEmpty) registration = register(this)
    cacheElem match {
      case Some(v) => s(v)
      case None    =>
    }
    subscribers += s
    Cancelable { () =>
      subscribers -= s
      if (subscribers.isEmpty) registration.cancel()
    }
  }

  /** Sets the value of this [[Var]]. Triggers recalculation of depending [[Rx]]s. */
  def :=(newValue: A): Unit = {
    cacheElem = Some(newValue)
    subscribers.foreach(_(newValue))
  }

  /** Updates the value of this [[Var]]. Triggers recalculation of depending [[Rx]]s. */
  def update(f: A => A): Unit =
    foreach(a => :=(f(a))).cancel()
}

object Var {
  /** Create a [[Var]] from an initial value. */
  def apply[A](initialValue: A): Var[A] =
    new Var[A](Some(initialValue), _ => Cancelable.empty)

  /**
   * Create a [[Var]] from an initial value and a register function .
   *
   * The register function is called as soon as this Var becomes active.
   * The returned [[Cancelable]] is called when the Var becomes inactive.
   *
   * Registration & cancellation might append as many time as the Var goes
   * active/inactive. This mechanism is what prevents `flatMap` from leaking
   * memory: make sure that everything created in the register is canceled.
   */
  def create[A](initialValue: A)(register: Var[A] => Cancelable): Var[A] =
    new Var[A](Some(initialValue), register)

  /**
   * Unsafe version of `create`, for internal use only. Using this method
   * might exposes your code to empty Rx, making `.value` calls unsafe.
   */
  def unsafeCreate[A](initialValue: Option[A])(register: Var[A] => Cancelable): Var[A] =
    new Var[A](initialValue, register)
}

/** Action used to cancel `foreach` subscription. */
final case class Cancelable(cancel: () => Unit) extends AnyVal {
  def alsoCanceling(c: Cancelable): Cancelable = Cancelable { () => cancel(); c.cancel() }
}
object Cancelable { val empty = Cancelable(() => ()) }
