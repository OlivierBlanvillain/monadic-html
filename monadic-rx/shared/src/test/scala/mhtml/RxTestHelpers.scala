package mhtml

trait RxTestHelpers {

  implicit class MoreImpureStuff[A](impure: RxImpureOps[A]) {
    def value: A = {
      var v: Option[A] = None
      impure.run(a => v = Some(a)).cancel
      // This can never happen if using the default Rx/Var constructors and
      // methods. The proof is a simple case analysis showing that every method
      // preserves non emptiness. Var created with unsafeCreate Messing up with
      // Var unsafe constructor or internal could lead to this exception.
      def error = new NoSuchElementException("Requesting value of an empty Rx.")
      v.getOrElse(throw error)
    }
  }

}
