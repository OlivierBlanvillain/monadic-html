package mhtml

import monix.execution.Scheduler
import monix.reactive.Observable
import monix.reactive.subjects.BehaviorSubject

trait Rx[+A] {
  def underlying: Observable[A]

  def map[B](f: A => B): Rx[B]              = Rx.fromObservable(underlying.map(f))
  def filter(f: A => Boolean): Rx[A]        = Rx.fromObservable(underlying.filter(f))
  def flatMap[B](f: A => Rx[B]): Rx[B] = Rx.fromObservable(underlying.mergeMap(x => f(x).underlying))
  def foreach(f: A => Unit)(implicit s: Scheduler): Unit = underlying.foreach(f)
}

object Rx {
  def fromObservable[A](o: Observable[A]): Rx[A] = new Rx[A] { def underlying = o }
  def apply[A](initialValue: A): Rx[A] = Var(initialValue)
}

final class Var[A](initialValue: A) extends Rx[A] {
  private val subject = BehaviorSubject(initialValue)
  val underlying: Observable[A] = subject

  def :=(newValue: A): Unit = subject.onNext(newValue)
  def update(f: A => A)(implicit s: Scheduler): Unit = subject.firstL.runAsync(v => subject.onNext(f(v.get)))
}

object Var {
  def apply[A](initialValue: A): Var[A] = new Var(initialValue)
}
