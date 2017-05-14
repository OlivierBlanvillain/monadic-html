package mhtml

object Platform {
  private[mhtml] def checkIsProduction: Boolean =
    scala.scalajs.LinkingInfo.productionMode
}