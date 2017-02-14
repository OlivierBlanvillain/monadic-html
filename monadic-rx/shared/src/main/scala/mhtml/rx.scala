package mhtml

/** Reactive value of type `A`.
  * Automatically recalculation when one of it's dependencies is updated. */
trait Rx[+A] {
  /** Applies the side effecting function `f` to each element of this [[Rx]].
    * Returns an [[Cancelable]] which can be used to cancel the subscription.
    * Use with caution: not canceling `foreach` subscription on short lived
    * [[Rx]]s will leak memory. */
  def foreach(s: A => Unit): Cancelable

  /** Alternative to `foreach` which is not triggered immediately,
    * but only once the underlying value is updated. */
  protected[mhtml] def foreachNext(s: A => Unit): Cancelable

  /** Returns a new [[Rx]] that maps each element of this [[Rx]] via `f`. */
  def map[B](f: A => B): Rx[B] =
    new Var[B](f(value), self => foreachNext(self := f(_)))

  /** Returns a new [[Rx]] that flat-maps each element of this [[Rx]] via `f`. */
  def flatMap[B](f: A => Rx[B]): Rx[B] = {
    var cc = Cancelable.empty
    var fb: Rx[B] = f(value)

    val result = new Var[B](fb.value, self => foreachNext { a =>
      val newFb = f(a)
      if (fb != newFb) {
        cc.cancel()
        cc = newFb.foreach(self.:=)
        fb = newFb
      }
    })

    cc = fb.foreach(result.:=)
    result
  }

  /** Returns the current value of this [[Rx]]. Using `value` in conjonction
    * with `:=` is an anti-pattern which can lead to infinit cycles and
    * other surprising side effects of the value propagation order. */
  def value: A
}

object Rx {
  /** Creates a constant [[Rx]] from a value. */
  def apply[A](v: A): Rx[A] =
    new Rx[A] {
      def value = v

      def foreachNext(s: A => Unit): Cancelable = Cancelable.empty

      def foreach(f: A => Unit): Cancelable = {
        f(value)
        Cancelable.empty
      }

    }
}

class Var[A] (initialValue: A, register: Var[A] => Cancelable) extends Rx[A] {
  // Last computed value, retained to be sent to new subscribers as they come in.
  protected var cacheElem: A = initialValue
  // Current registration to the feeding `Rx`, canceled whenever nobody's listening.
  protected var registration: Cancelable = Cancelable.empty
  // Mutable set of all currently subscribed functions, implementing with an `Array`.
  protected val subscribers = buffer.empty[A => Unit]

  def value: A = cacheElem

  override def foreachNext(s: A => Unit): Cancelable = {
    if (subscribers.isEmpty) registration = register(this)
    subscribers += s
    Cancelable { () =>
      subscribers -= s
      if (subscribers.isEmpty) registration.cancel()
    }
  }

  override def foreach(s: A => Unit): Cancelable = {
    s(cacheElem)
    foreachNext(s)
  }

  /** Sets the value of this [[Var]].
    * This will automatically trigger recalculation of all the depending [[Rx]]s. */
  def :=(newValue: A): Unit = {
    cacheElem = newValue
    subscribers.foreach(_(newValue))
  }

  /** Updates the value of this [[Var]] with a mutation function.
    * This will automatically trigger recalculation of all the depending [[Rx]]s. */
  def update(f: A => A): Unit =
    foreach(a => :=(f(a))).cancel()
}

object Var {
  /** Create a [[Var]] from an initial value. */
  def apply[A](initialValue: A): Var[A] =
    new Var(initialValue, _ => Cancelable.empty)
}

/** Action that can be used to cancel `foreach` subscription. */
final case class Cancelable(cancel: () => Unit) extends AnyVal {
  def alsoCanceling(c: Cancelable): Cancelable = Cancelable { () => cancel(); c.cancel() }
}
object Cancelable { val empty = Cancelable(() => ()) }
