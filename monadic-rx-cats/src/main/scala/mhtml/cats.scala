package mhtml

import _root_.cats.MonadFilter

object cats {
  implicit val mhtmlRxMonadFilterIntstance: MonadFilter[Rx] =
    new MonadFilter[Rx] {
      def pure[A](x: A): Rx[A] =
        Rx(x)

      def flatMap[A, B](fa: Rx[A])(f: A => Rx[B]): Rx[B] =
        fa.flatMap(f)

      def tailRecM[A, B](a: A)(f: A => Rx[Either[A, B]]): Rx[B] =
        defaultTailRecM(a)(f)

      def empty[A]: Rx[A] =
        new Var[A](Non, _ => Cancelable.empty)

      override def filter[A](x: Rx[A])(f: A => Boolean): Rx[A] =
        x.filter(f)
    }
}
