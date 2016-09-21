enablePlugins(ScalaJSPlugin)

libraryDependencies ++= Seq(
  "org.scala-js"   %%% "scalajs-dom" % "0.9.0",
  "io.monix"       %%% "monix"       % "2.0-RC7",
  "scala-xml"      %%% "scala-xml"   % "1.0.6-SNAPSHOT",
  "org.scalatest"  %%% "scalatest"   % "3.0.0-RC3")

scalaVersion := "2.11.8"

scalacOptions := Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-feature",
  "-unchecked",
  "-Xfatal-warnings",
  "-Xfuture",
  "-Xlint",
  "-Yinline-warnings",
  "-Yno-adapted-args",
  "-Ywarn-dead-code",
  "-Ywarn-value-discard",
  "-Ywarn-numeric-widen",
  "-Ywarn-unused-import")

testOptions  in Test += Tests.Argument(TestFrameworks.ScalaTest, "-oDF")
requiresDOM  in Test := true
scalaJSStage in Test := FastOptStage
jsEnv        in Test := PhantomJSEnv().value
