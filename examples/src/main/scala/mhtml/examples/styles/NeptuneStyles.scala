package examples.styles

import scala.language.postfixOps
import scalacss.DevDefaults._

/**
  * Copyright (c) 2017 Amir Karimi
  */

object NeptuneStyles extends StyleSheet.Inline {
  import dsl._

  val neptuneActionbarColor = grey(0xFF)
  val neptuneBorderColor = rgba(10, 10, 10, 0.1)
  val neptuneBorderRadius = 5 px
  val neptuneBorderStyle = solid
  val neptuneBorderWidth = 1 px
  val neptuneBoxShadow = s"0 2px 3px ${neptuneBorderColor.value}, 0 0 0 1px ${neptuneBorderColor.value}"
  val neptuneButtonHeight = 30 px
  val neptuneButtonWidth = 30 px
  val neptuneContentHeight = 300 px
  val neptuneContentPadding = 10 px

  val neptune = style(
    borderRadius(neptuneBorderRadius),
    boxShadow := neptuneBoxShadow,
    boxSizing.borderBox,
    width(100 %%)
  )

  val neptuneContent = style(
    boxSizing.borderBox,
    height(neptuneContentHeight),
    outline.none,
    overflowY.auto,
    padding(neptuneContentPadding),
    width(100 %%)
  )

  val neptuneActionbar = style(
    backgroundColor(neptuneActionbarColor),
    borderBottom(neptuneBorderWidth, neptuneBorderStyle, neptuneBorderColor),
    borderTopLeftRadius(neptuneBorderRadius),
    borderTopRightRadius(neptuneBorderRadius),
    width(100 %%)
  )

  val neptuneButton = style(
    backgroundColor.transparent,
    border.none,
    cursor.pointer,
    height(neptuneButtonHeight),
    outline.none,
    width(neptuneButtonWidth)
  )
}
