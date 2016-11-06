package mhtml

/** Reactive value of type `A`.
  * Automatically recalculation when one of it's dependencies is updated. */
sealed trait Rx[+A] {
  /** Applies the side effecting function `f` to each element of this [[Rx]].
    * Returns an [[Cancelable]] which can be used to cancel the subscription.
    * Use with caution: not canceling `foreach` subscription on short lived
    * [[Rx]]s will leak memory. */
  def foreach(f: A => Unit): Cancelable

  /** Returns a new [[Rx]] that maps each element of this [[Rx]] via `f`. */
  def map[B](f: A => B): Rx[B] =
    Var.create[B](self => foreach(self := f(_)))

  /** Returns a new [[Rx]] that filters each element of this [[Rx]] via `f`. */
  def filter(f: A => Boolean): Rx[A] =
    Var.create[A](self => foreach(a => if (f(a)) self := a else ()))

  /** Returns a new [[Rx]] filtered by where `f` is defined and mapped by `f` */
  def collect[B](f: PartialFunction[A, B]): Rx[B] =
    this.filter(f.isDefinedAt).map(f)

  /** Returns a new [[Rx]] that flat-maps each element of this [[Rx]] via `f`. */
  def flatMap[B](f: A => Rx[B]): Rx[B] = {
    var cc = Cancelable.empty
    // `fb` is a pure optimization, which will get triggered all the time
    // when building `Rx`s using the applicative style.
    var fb: Opt[Rx[B]] = Non
    Var.create[B](self => foreach { a =>
      val newFb = f(a)
      if (Som(fb) != newFb) {
        cc.cancel()
        cc = newFb.foreach(self.:=)
        fb = Som(newFb)
      }
    })
  }

  /** Returns a view of this [[Rx]] where value propagation appends `after` it
    * is complete on this [[Rx]]. Useful to finalize interaction imperative
    * systems, such as taking actions after mounting an element to the DOM. */
  def after(): Rx[A]
}

object Rx {
  /** Creates a constant [[Rx]] from a value. */
  def apply[A](value: A): Rx[A] =
    new Rx[A] {
      def foreach(f: A => Unit): Cancelable = {
        f(value)
        Cancelable.empty
      }

      def after(): Rx[A] = this
    }
}

final class Var[A] private[mhtml] (initialValue: Opt[A], register: Var[A] => Cancelable) extends Rx[A] {
  // Last computed value, retained to be sent to new subscribers as they come in.
  private[mhtml] var cacheElem: Opt[A] = initialValue
  // Current registration to the feeding `Rx`, canceled whenever nobody's listening.
  private[mhtml] var registration: Cancelable = Cancelable.empty
  // Mutable set of all currently subscribed functions, implementing with an `Array`.
  private[mhtml] val subscribers = buffer.empty[A => Unit]
  // Lazy view returned by `.after()`, at Non until first used.
  private[mhtml] var afterRx: Opt[Var[A]] = Non

  override def after(): Rx[A] =
    afterRx match {
      case Non =>
        val a = Var.empty[A]
        foreach(a.:=).cancel()
        afterRx = Som(a)
        a
      case Som(a) => a
    }

  override def foreach(s: A => Unit): Cancelable = {
    if (subscribers.isEmpty) registration = register(this)
    cacheElem match {
      case Non    => ()
      case Som(v) => s(v)
    }
    subscribers += s
    Cancelable { () =>
      subscribers -= s
      if (subscribers.isEmpty) registration.cancel()
    }
  }

  /** Sets the value of this [[Var]].
    * This will automatically trigger recalculation of all the depending [[Rx]]s. */
  def :=(newValue: A): Unit = {
    cacheElem = Som(newValue)
    subscribers.foreach(_(newValue))
    afterRx match {
      case Non    => ()
      case Som(a) => a := newValue
    }
  }

  /** Updates the value of this [[Var]] with a mutation function.
    * This will automatically trigger recalculation of all the depending [[Rx]]s. */
  def update(f: A => A): Unit =
    foreach(a => :=(f(a))).cancel()
}

object Var {
  /** Create a [[Var]] from an initial value. */
  def apply[A](initialValue: A): Var[A] =
    new Var(Som(initialValue), _ => Cancelable.empty)

  /** Create an empty [[Var]]. */
  def empty[A](): Var[A] =
    new Var(Non, _ => Cancelable.empty)

  // Create a `Var` from a cancelable registration function. A registration will
  // append as soon as there is any one subscribed, and be canceled when nobody
  // is listening. This is one the core mechanism to prevent memory leak.
  private[mhtml] def create[A](register: Var[A] => Cancelable): Var[A] =
    new Var(Non, register)
}

/** Action that can be used to cancel `foreach` subscription. */
final case class Cancelable(cancel: () => Unit) extends AnyVal {
  def alsoCanceling(c: () => Cancelable): Cancelable = Cancelable { () => cancel(); c().cancel() }
}
object Cancelable { val empty = Cancelable(() => ()) }

// Box-less `Option`
private[mhtml] sealed trait Opt[+A] extends Any
private[mhtml] final case class Som[A](value: A) extends AnyVal with Opt[A]
private[mhtml] final case object Non extends Opt[Nothing]
