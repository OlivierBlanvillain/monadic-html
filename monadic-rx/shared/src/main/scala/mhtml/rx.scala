package mhtml

/** Reactive value of type `A`. Automatically recalculate on dependency update. */
sealed trait Rx[+A] { self =>
  import Rx._

  /**
   * Apply a function to each element of this `Rx`.
   *
   * ```
   * val numbers: Rx[Int]
   * val doubles: Rx[Int] = numbers.map(2.*)
   * // numbers => 0 1 4 3 2 ...
   * // doubles => 0 2 8 6 4 ...
   * ```
   */
  def map[B](f: A => B): Rx[B] = Map[A, B](this, f)

  /**
   * Dynamically switch between different `Rx`s according to the given
   * function, applied on each element of this `Rx`. Each switch will cancel
   * the subscriptions for the previous outgoing `Rx` and start a new
   * subscription on the next `Rx`.
   *
   * Together with `Rx#map` and `Rx.apply`, flatMap forms a `Monad`. [Proof](https://github.com/OlivierBlanvillain/monadic-html/blob/master/monadic-rx-cats/src/main/scala/mhtml/cats.scala).
   */
  def flatMap[B](f: A => Rx[B]): Rx[B] = FlatMap[A, B](this, f)

  /**
   * Create the Cartesian product of two `Rx`. The output tuple contains the
   * latest values from each input `Rx`, which updates whenever the value from
   * either input `Rx` update. This method is faster than combining `Rx`s using
   * `for { a <- ra; b <- rb } yield (a, b)`.
   *
   * ```
   * val r1: Rx[Int]
   * val r2: Rx[Int]
   * val zipped: Rx[Int] = r1.zip(r2)
   * // r1     => 0     8                       9     ...
   * // r2     => 1           4     5     6           ...
   * // zipped => (0,1) (8,1) (8,4) (8,5) (8,6) (9,6) ...
   * ```
   *
   * This method, together with `Rx.apply`, forms am `Applicative`.
   * `mapN` syntax is available via the `monadic-rx-cats` package.
   */
  def zip[B](other: Rx[B]): Rx[(A, B)] = Zip[A, B](this, other)

  @deprecated("Renamed to zip", "0.3.3")
  def product[B](other: Rx[B]): Rx[(A, B)] = zip(other)

  /**
   * Drop repeated value of this `Rx`.
   *
   * ```
   * val numbers: Rx[Int]
   * val noDups: Rx[Int] = numbers.dropRepeats
   * // numbers => 0 0 3 3 5 5 5 4 ...
   * // noDups  => 0   3   5     4 ...
   * ```
   *
   * Note: This could also be implemented in terms of keepIf, map, and foldp.
   */
  def dropRepeats: Rx[A] = DropRep[A](this)

  /**
   * Merge two `Rx` into one. Updates coming from either of the incoming `Rx`
   * trigger updates in the outgoing `Rx`. Upon creation, the outgoing `Rx`
   * first receives the current value from this `Rx`, then from the other `Rx`.
   *
   * ```
   * val r1: Rx[Int]
   * val r2: Rx[Int]
   * val merged: Rx[Int] = r1.merge(r2)
   * // r1     => 0 8     3 ...
   * // r2     => 1   4 3   ...
   * // merged => 0 8 4 3 3 ...
   * ```
   *
   * With this operation, `Rx` forms a `Semigroup`. [Proof](https://github.com/OlivierBlanvillain/monadic-html/blob/master/monadic-rx-cats/src/main/scala/mhtml/cats.scala).
   * `|+|` syntax is available via the `monadic-rx-cats` package.
   */
  def merge[B >: A](other: Rx[B]): Rx[B] = Merge[A, B](this, other)

  /**
   * Produces a `Rx` containing cumulative results of applying a binary
   * operator to each element of this `Rx`, starting from a `seed` and the
   * current value of the upstream `Rx`, and moving forward in time; no internal
   * state is maintained.
   *
   * ```
   * val numbers: Rx[Int]
   * val folded: Rx[Int] = numbers.foldp(0)(_ + _)
   * // numbers => 1 2 1 1 3 ...
   * // folded  => 1 3 4 5 8 ...
   * ```
   */
  def foldp[B](seed: B)(step: (B, A) => B): Rx[B] = Foldp[A, B](this, seed, step)

  /**
   * Returns a new `Rx` with updates fulfilling a predicate.
   * If the first update is dropped, the default value is used instead.
   *
   * ```
   * val numbers: Rx[Int]
   * val even: Rx[Int] = numbers.keepIf(_ % 2 == 0)(-1)
   * // numbers => 0 0 3 4 5 6 ...
   * // even    => 0 0   4   6 ...
   * ```
   */
  def keepIf[B >: A](f: B => Boolean)(b: B): Rx[B] =
    this.collect[B] { case e if f(e) => e }(b)

  /**
   * Returns a new `Rx` without updates fulfilling a predicate.
   * If the first update is dropped, the default value is used instead.
   *
   * ```
   * val numbers: Rx[Int]
   * val even: Rx[Int] = numbers.dropIf(_ % 2 == 0)(-1)
   * // numbers =>  0 0 3 4 5 6 ...
   * // even    => -1   3   5   ...
   * ```
   */
  def dropIf[B >: A](f: B => Boolean)(b: B): Rx[B] =
    this.collect[B] { case e if !f(e) => e }(b)

  /**
   * Returns a new `Rx` with updates where `f` is defined and mapped by `f`.
   * If the first update is dropped, the default value is used instead.
   */
  def collect[B](f: PartialFunction[A, B])(b: B): Rx[B] = Collect[A, B](this, f, b)

  /**
   * Sample this `Rx` using another `Rx`: every time an event occurs on
   * the second `Rx` the output updates with the latest value of this `Rx`.
   *
   * ```
   * val r1: Rx[Char]
   * val r2: Rx[Int]
   * val sp: Rx[Int] = r2.sampleOn(r1)
   * // r1 =>   u   u   u   u ...
   * // r2 => 1   2 3     4   ...
   * // sp =>   1   3   3   4 ...
   * ```
   */
  def sampleOn[B](other: Rx[B]): Rx[A] = SampleOn[A, B](this, other)

  val impure: RxImpureOps[A] = RxImpureOps[A](this)
}

case class RxImpureOps[+A](self: Rx[A]) extends AnyVal {
  /**
   * Applies the side effecting function `f` to each element of this `Rx`.
   * Returns an `Cancelable` which can be used to cancel the subscription.
   * Omitting to canceling subscription can lead to memory leaks.
   *
   * If you use this in your code, you are probably doing it wrong.
   */
  def run(effect: A => Unit): Cancelable = Rx.run(self)(effect)

  /**
    * Memoizes this `Rx` using an internal `Var`. This is only
    * useful for optimizing an Rx graph, so that values generated
    * by this `Rx` are computed only once and shared between all executions.
    */
  @deprecated("This will eventually be made private and used under-the-hood, automatically.", "1.0.0")
  def sharing: Rx[A] = Rx.Sharing[A](self)
}

object Rx {
  /** Creates a constant `Rx`. */
  def apply[A](v: A): Rx[A] = Var.create(v)(_ => Cancelable.empty)

  final case class Map     [A, B]     (self: Rx[A], f: A => B)                      extends Rx[B]
  final case class FlatMap [A, B]     (self: Rx[A], f: A => Rx[B])                  extends Rx[B]
  final case class Zip     [A, B]     (self: Rx[A], other: Rx[B])                   extends Rx[(A, B)]
  final case class DropRep [A]        (self: Rx[A])                                 extends Rx[A]
  final case class Merge   [A, B >: A](self: Rx[A], other: Rx[B])                   extends Rx[B]
  final case class Foldp   [A, B]     (self: Rx[A], seed: B, step: (B, A) => B)     extends Rx[B]
  final case class Collect [A, B]     (self: Rx[A], f: PartialFunction[A, B], b: B) extends Rx[B]
  final case class SampleOn[A, B]     (self: Rx[A], other: Rx[B])                   extends Rx[A]
  final case class Imitate [A]        (self: Var[A], other: Rx[A])                  extends Rx[A]
  final case class Sharing [A]        (self: Rx[A])                                 extends Rx[A] {
    protected[Rx] val sharingMemo: Var[Option[A]] = Var(None)
    protected[Rx] def isSharing = !(sharingCancelable == Cancelable.empty)
    protected[Rx] var sharingCancelable: Cancelable = Cancelable.empty
  }

  /**
   * The `impure.run` interpreter. Traverses the `Rx` tree and registers
   * callbacks to run the outer most effect according to documented semantics.
   */
  def run[A](rx: Rx[A])(effect: A => Unit): Cancelable = rx match {
    case Map(self, f) =>
      run(self)(x => effect(f(x)))

    case FlatMap(self, f) =>
      var c1 = Cancelable.empty
      val c2 = run(self) { a =>
        val rxb = f(a)
        c1.cancel()
        c1 = run(rxb)(effect)
      }
      Cancelable { () => c1.cancel(); c2.cancel() }

    case Zip(self, other) =>
      var go = false
      var v1: Any = null
      var v2: Any = null
      val c1 = run(self)  { a => v1 = a; if(go) effect((v1, v2)) }
      val c2 = run(other) { b => v2 = b; if(go) effect((v1, v2)) }
      go = true
      effect((v1, v2))
      Cancelable { () => c1.cancel(); c2.cancel() }

    case DropRep(self) =>
      var previous: Option[A] = None
      run(self) { a =>
        if (previous.forall(a.!=)) {
          effect(a)
          previous = Some(a)
        }
      }

    case Merge(self, other) =>
      val c1 = run(self)(effect)
      val c2 = run(other)(effect)
      Cancelable { () => c1.cancel(); c2.cancel() }

    // Workaround required because otherwise scalac messes up GADT skolems. Note: dotty doesn't need it.
    case rx: Foldp[t,A] => val Foldp(self, seed, step) = rx
      var b = seed
      run(self) { a =>
        val next = step(b, a)
        b = next
        effect(next)
      }

    case Collect(self, f, fallback) =>
      var first = true
      run(self) { a =>
        // Semantically:
        // if (f.isDefinedAt(a))
        //   effect(f(a))
        // else if(first)
        //   effect(fallback)
        // else ()
        //
        // But we have to use applyOrElse to prevent double evaluation:
        var isDefinedAt = true
        val fa = {
          f.applyOrElse(a, (_: Any) => {
            isDefinedAt = false
            fallback
          })
        }
        val out = if (isDefinedAt | first) effect(fa) else ()
        first = false
        out
      }

    case SampleOn(self, other) =>
      var currentA: A = null.asInstanceOf[A]
      val ca = run(self)(currentA = _)
      val cb = run(other)(_ => effect(currentA))
      Cancelable { () => ca.cancel(); cb.cancel() }

    case im: Imitate[A] => val Imitate(self, other) = im
      if (!self.imitating) {
        self.imitating = true
        val cc = run(other) { case a =>
          self := a
          effect(a)
        }
        Cancelable { () => cc.cancel(); self.imitating = false }
      } else run(other)(effect)

    case rx: Sharing[A] => val Sharing(self) = rx
      if (!rx.isSharing) {
        rx.sharingCancelable = run(self)(rx.sharingMemo := Some(_))
      }
      val foreachCancelable = rx.sharingMemo.foreach(_.foreach(effect))
      Cancelable { () =>
        foreachCancelable.cancel()
        if (rx.sharingMemo.subscribers.isEmpty) {
          rx.sharingCancelable.cancel()
          rx.sharingCancelable = Cancelable.empty
        }
      }

    case leaf: Var[A] =>
      leaf.foreach(effect)

    case null => throw new NullPointerException("null is not a valid Rx!")
  }
}

/** A smart variable that can be set manually. */
class Var[A](initialValue: Option[A], register: Var[A] => Cancelable) extends Rx[A] {
  // Last computed value, retained to be sent to new subscribers as they come in.
  private[mhtml] var cacheElem: Option[A] = initialValue
  // Current registration to the feeding `Rx`, canceled whenever nobody's listening.
  private[mhtml] var registration: Cancelable = Cancelable.empty
  // Mutable set of all currently subscribed functions, implementing with an `Array`.
  private[mhtml] val subscribers = buffer.empty[A => Unit]
  // Is this Var currently imitating another Rx?
  private[mhtml] var imitating = false

  private[mhtml] def foreach(s: A => Unit): Cancelable = {
    if (isCold) registration = register(this)
    cacheElem match {
      case Some(v) => s(v)
      case None    =>
    }
    subscribers += s
    Cancelable { () =>
      subscribers -= s
      if (isCold) registration.cancel()
    }
  }

  /**
   * Is there anything currently subscribed to this `Var`?
   *
   * This method is intended to be used to test the absence of memory leak.
   * For instance, all `Var`s should be cold after canceling a `mount`.
   */
  def isCold: Boolean = subscribers.isEmpty

  /**
   * Updates this `Var` with values emitted by the `other` `Rx`. This method
   * is side effect free. Consequently, the returned `Rx` must be used at
   * least once for the imitation to take place. This `Var` the `other` `Rx`
   * and the returned `Rx` will all emit the same values.
   *
   * This method exists (only) to allow *circular dependency* in `Rx` graphs.
   */
  def imitate(other: Rx[A]): Rx[A] = Rx.Imitate(this, other)

  /** Sets the value of this `Var`. Triggers recalculation of depending `Rx`s. */
  def :=(newValue: A): Unit = {
    cacheElem = Some(newValue)
    var i = subscribers.size
    val copy = buffer[A => Unit](i)
    while (i > 0) {
      i = i - 1
      val s = subscribers(i)
      copy(i) = s
    }
    copy.foreach { f =>
      f(newValue)
    }
  }

  /** Updates the value of this `Var`. Triggers recalculation of depending `Rx`s. */
  def update(f: A => A): Unit =
    foreach(a => :=(f(a))).cancel()

  override def toString: String =
    s"Var(${cacheElem.orNull})"
}

object Var {
  /** Create a `Var` from an initial value. */
  def apply[A](initialValue: A): Var[A] =
    new Var[A](Some(initialValue), _ => Cancelable.empty)

  /**
   * Create a `Var` from an initial value and a register function .
   *
   * The register function is called as soon as this Var becomes active.
   * The returned `Cancelable` is called when the Var becomes inactive.
   *
   * Registration & cancellation might append as many time as the Var goes
   * active/inactive. This mechanism is what prevents `flatMap` from leaking
   * memory: make sure that everything created in the register is canceled.
   */
  def create[A](initialValue: A)(register: Var[A] => Cancelable): Var[A] =
    new Var[A](Some(initialValue), register)
}

/** Action used to cancel subscription. */
final class Cancelable(val cancelFunction: () => Unit) extends AnyVal {
  // scalac: side-effecting nullary methods are discouraged: suggest defining
  // as `def cancel()` instead. Until we get systematic warnings for forgotten
  // parenthesis, this will stay a side-effecting nullary method...

  /** Cancel this subscription. */
  def cancel(): Unit = cancelFunction()
}

object Cancelable {
  /** Creates a `Cancelable` with the specified cancel function. */
  def apply(cancelFunction: () => Unit) = new Cancelable(cancelFunction)

  /** The empty `Cancelable`. */
  val empty = Cancelable(() => ())
}
