package mhtml

private[mhtml] object Platform {
  def checkIsProduction: Boolean =
    scala.scalajs.LinkingInfo.productionMode
}