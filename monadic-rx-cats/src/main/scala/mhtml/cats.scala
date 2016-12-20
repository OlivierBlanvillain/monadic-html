package mhtml

import _root_.cats.Monad

object cats {
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
    }
}
