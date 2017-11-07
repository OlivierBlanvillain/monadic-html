package mhtml
package implicits

import _root_.cats.{Monad, Semigroup}
import scala.language.implicitConversions

object cats {
  /**
   * `Monad` instance for `Rx`. Proof:
   *
   * # Left identity law: M.flatMap(M.pure(a), f) === f(a)
   *
   * Proving that LHS.impure.run(effect) == RHS.impure.run(effect). Starting from the LHS.
   *
   * <=> (by definition of M)
   * FlatMap(Pure(a), f).impure.run(effect)
   *
   * <=> (by definition of .impure.run for FlatMap)
   * var c1 = Cancelable.empty
   * val c2 = run(Pure(a)) { b => // [self := Pure(a)]
   *   c1.cancel
   *   val fa = f(b)
   *   c1 = run(fa)(effect)
   * }
   * Cancelable { () => c1.cancel; c2.cancel }
   *
   * <=> (by definition of .impure.run for Pure)
   * var c1 = Cancelable.empty
   * val c2 = {
   *   { b => // [effect := { b => ... }]
   *    c1.cancel
   *    val fa = f(b)
   *    c1 = run(fa)(effect)
   *   }.apply(a)
   *   Cancelable.empty
   * }
   * Cancelable { () => c1.cancel; c2.cancel }
   *
   * <=> (by beta reduction on the { b => ... } closure)
   * var c1 = Cancelable.empty
   * val c2 = {
   *   c1.cancel
   *   val fa = f(a)
   *   c1 = run(fa)(effect)
   *   Cancelable.empty
   * }
   * Cancelable { () => c1.cancel; c2.cancel }
   *
   * <=> (simplifications following from Cancelable.empty.cancel === ())
   * run(f(a))(effect)
   *
   * <=> (by definition of .impure.run)
   * f(a).impure.run(effect)
   *
   * Both sides are equivalent. Q.E.D.
   *
   *
   * # Right identity law: M.flatMap(m, M.pure) === m
   *
   * Proving that LHS.impure.run(effect) == RHS.impure.run(effect). Starting from the LHS.
   *
   * (by definition of M)
   * FlatMap(m, Pure).impure.run(effect)
   *
   * <=> (by definition of .impure.run for FlatMap)
   * var c1 = Cancelable.empty
   * val c2 = run(m) { b => // [self := m]
   *   c1.cancel
   *   val fa = Pure(b)     // [f := Pure.apply]
   *   c1 = run(fa)(effect)
   * }
   * Cancelable { () => c1.cancel; c2.cancel }
   *
   * <=> (by definition of .impure.run for Pure)
   * var c1 = Cancelable.empty
   * val c2 = run(m) { b =>
   *   c1.cancel
   *   effect(b) // [a := b]
   *   Cancelable.empty
   * }
   * Cancelable { () => c1.cancel; c2.cancel }
   *
   * <=> (simplifications following from Cancelable.empty.cancel === ())
   * run(m)(effect)
   *
   * <=> (by definition of .impure.run)
   * m.impure.run(effect)
   *
   * Both sides are equivalent. Q.E.D.
   *
   *
   * # Associativity law: M.flatMap(M.flatMap(m, f), g) === M.flatMap(m, x => M.flatMap(f(x), g))
   *
   * Proving that LHS.impure.run(effect) == RHS.impure.run(effect). Starting from the RHS.
   *
   * (by definition of M)
   * FlatMap(m, x => FlatMap(f(x), g)).impure.run(effect)
   *
   * <=> (by definition of .impure.run for FlatMap (local variable primed))
   * var c1' = Cancelable.empty
   * val c2' = run(m) { b' =>      // [self := m]
   *   c1'.cancel
   *   val fa' = FlatMap(f(b'), g) // [f := x => FlatMap(f(x), g)] + β reduction
   *   c1' = run(fa')(effect)
   * }
   * Cancelable { () => c1'.cancel; c2'.cancel }
   *
   * <=> (by definition of run for FlatMap  & inlining fa')
   * var c1' = Cancelable.empty
   * val c2' = run(m) { b' =>
   *   c1'.cancel
   *   c1' = {
   *     var c1 = Cancelable.empty
   *     val c2 = run(f(b')) { b => // [self := f(b) ]
   *       c1.cancel
   *       val fa = g(b)            // [f := g]
   *       c1 = run(fa)(effect)
   *     }
   *     Cancelable { () => c1.cancel; c2.cancel }
   *   }
   * }
   * Cancelable { () => c1'.cancel; c2'.cancel }
   *
   * <=> (local inlining and reordering)
   *     (this step also uses the following equivalance:)
   *     { ... val c1 = <> ..} === var c1 = Cancelable.empty; { ... c1 = <> ...}
   * var c1 = Cancelable.empty
   * var c2 = Cancelable.empty
   * val c2' = run(m) { b' =>
   *   c2.cancel
   *   c2 = run(f(b')) { b =>
   *     c1.cancel
   *     c1 = run(g(b))(effect)
   *   }
   * }
   * Cancelable { () => c1.cancel; c2.cancel; c2'.cancel }
   *
   * <=> (renamings [c1 := x, c2 := y, c2' := z, b' := b, b := b'])
   * var x = Cancelable.empty
   * var y = Cancelable.empty
   * val z = run(m) { b =>
   *   y.cancel
   *   y = run(f(b)) { b' =>
   *     x.cancel
   *     x = run(g(b'))(effect)
   *   }
   * }
   * Cancelable { () => x.cancel; y.cancel; z.cancel }
   *
   * Starting from the LHS.
   *
   * (by definition of M)
   * FlatMap(FlatMap(m, f), g).impure.run(effect)
   *
   * <=> (by definition of .impure.run for FlatMap (local variable primed))
   * var c1' = Cancelable.empty
   * val c2' = run(FlatMap(m, f)) { b' => // [self' := FlatMap(m, f)]
   *   c1'.cancel
   *   val fa' = g(b')                    // [f' := g]
   *   c1' = run(fa')(effect)
   * }
   * Cancelable { () => c1'.cancel; c2'.cancel }
   *
   * <=> (by definition of run for FlatMap)
   * var c1' = Cancelable.empty
   * val c2' = {
   *   var c1 = Cancelable.empty
   *   val c2 = run(m) { b => // [self := m]
   *     c1.cancel
   *     val fa = f(b)
   *     c1 = run(fa) { b' => // [effect := { b' => ... }]
   *       c1'.cancel
   *       val fa' = g(b')
   *       c1' = run(fa')(effect)
   *     }
   *   }
   *   Cancelable { () => c1.cancel; c2.cancel }
   * }
   * Cancelable { () => c1'.cancel; c2'.cancel }
   *
   * <=> (local inlining and reordering)
   * var c1' = Cancelable.empty
   * var c1 = Cancelable.empty
   * val c2 = run(m) { b =>
   *   c1.cancel
   *   c1 = run(f(b)) { b' =>
   *     c1'.cancel
   *     c1' = run(g(b'))(effect)
   *   }
   * }
   * Cancelable { () => c1'.cancel; c1.cancel; c2.cancel }
   *
   * <=> (renamings [c1' := x, c1 := y, c2 := z])
   * var x = Cancelable.empty
   * var y = Cancelable.empty
   * val z = run(m) { b =>
   *   y.cancel
   *   y = run(f(b)) { b' =>
   *     x.cancel
   *     x = run(g(b'))(effect)
   *   }
   * }
   * Cancelable { () => x.cancel; y.cancel; z.cancel }
   *
   * Both sides are equivalent. Q.E.D.
   */
  implicit val mhtmlRxMonadIntstance: Monad[Rx] =
    new Monad[Rx] {
      def pure[A](x: A): Rx[A] =
        Rx(x)

      def flatMap[A, B](fa: Rx[A])(f: A => Rx[B]): Rx[B] =
        fa.flatMap(f)

      def tailRecM[A, B](a: A)(f: A => Rx[Either[A, B]]): Rx[B] =
        flatMap(f(a)) {
          case Right(b) => pure(b)
          case Left(nextA) => tailRecM(nextA)(f)
        }

      override def product[A, B](a: Rx[A], b: Rx[B]): Rx[(A, B)] =
        a.zip(b)
    }

  /**
   * `Semigroup` instance for `Rx`. Proof:
   *
   * # Associativity law: S.combine(S.combine(x, y), z) = S.combine(x, Semigroup[Rx].combine(y, z))
   *
   * Proving that LHS.impure.run(effect) == RHS.impure.run(effect). Starting from the RHS.
   *
   * (by definition of Semigroup[Rx])
   * Merge(Merge(x, y), z).impure.run(effect)
   *
   * <=> (by definition of .impure.run for Merge (local variable primed))
   * val c1' = run(Merge(x, y))(effect) // [self := Merge(x, y)]
   * val c2' = run(z)(effect)           // [other := z]
   * Cancelable { () => c1'.cancel; c2'.cancel }
   *
   * <=> (by definition of run for Merge)
   * val c1' = {
   *   val c1 = run(x)(effect) // [self := x]
   *   val c2 = run(y)(effect) // [other := y]
   *   Cancelable { () => c1.cancel; c2.cancel }
   * }
   * val c2' = run(z)(effect)
   * Cancelable { () => c1'.cancel; c2'.cancel }
   *
   * <=> (local inlining and reordering)
   * val c1 = run(x)(effect)
   * val c2 = run(y)(effect)
   * val c2' = run(z)(effect)
   * Cancelable { () => c1.cancel; c2.cancel; c2'.cancel }
   *
   * <=> (renamings [c1 := a, c2 := b, c2' := c])
   * val a = run(x)(effect)
   * val b = run(y)(effect)
   * val c = run(z)(effect)
   * Cancelable { () => a.cancel; b.cancel; c.cancel }
   *
   * Starting from the LHS.
   *
   * (by definition of Semigroup[Rx])
   * Merge(x, Merge(y, z)).impure.run(effect)
   *
   * <=> (by definition of .impure.run for Merge (local variable primed))
   * val c1' = run(x)(effect)           // [self := x]
   * val c2' = run(Merge(y, z))(effect) // [other := Merge(y, z)]
   * Cancelable { () => c1'.cancel; c2'.cancel }
   *
   * <=> (by definition of run for Merge)
   * val c1' = run(x)(effect)
   * val c2' = {
   *   val c1 = run(y)(effect) // [self := y]
   *   val c2 = run(z)(effect) // [other := z]
   *   Cancelable { () => c1.cancel; c2.cancel }
   * }
   * Cancelable { () => c1'.cancel; c2'.cancel }
   *
   * <=> (local inlining and reordering)
   * val c1' = run(x)(effect)
   * val c1 = run(y)(effect)
   * val c2 = run(z)(effect)
   * Cancelable { () => c1'.cancel; c1.cancel; c2.cancel }
   *
   * <=> (renamings [c1' := a, c1 := b, c2 := c])
   * val a = run(x)(effect)
   * val b = run(y)(effect)
   * val c = run(z)(effect)
   * Cancelable { () => a.cancel; b.cancel; c.cancel }
   *
   * Both sides are equivalent. Q.E.D.
   */
  implicit def mhtmlRxSemigroupIntstance[A]: Semigroup[Rx[A]] =
    new Semigroup[Rx[A]] {
      def combine(x: Rx[A], y: Rx[A]): Rx[A] = x.merge(y)
    }

  // Custom syntax instances for Vars. Without these, users would have to
  // manually upcast their `Var`s as `Rx`s to be able to use `|@|` and `|+|`.

  implicit def mhtmlVarSyntaxCartesian[A](fa: Var[A]) =
    new _root_.cats.syntax.CartesianOps[Rx, A] {
      val self = fa
      val typeClassInstance = mhtmlRxMonadIntstance
    }

  implicit def mhtmlVarSyntaxSemigroup[A](fa: Var[A]) =
    new _root_.cats.syntax.SemigroupOps[Rx[A]](fa)(mhtmlRxSemigroupIntstance)
}
