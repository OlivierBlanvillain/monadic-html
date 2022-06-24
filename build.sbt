import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}

Global / onChangedBuildSource := ReloadOnSourceChanges

inThisBuild(List(
  crossScalaVersions := Seq("2.13.8", "3.1.3"),
  scalaVersion := crossScalaVersions.value.head,
  scalacOptions := Seq(
    "-deprecation",
    "-encoding", "UTF-8",
    "-feature",
    "-unchecked",
    "-language:higherKinds"),
  organization := "in.nvilla",
  scalaJSLinkerConfig ~= { _.withSourceMap(true) },
  licenses := Seq(("MIT", url("http://opensource.org/licenses/MIT"))),
  homepage := Some(url("https://github.com/OlivierBlanvillain/monadic-html")),
  developers := List(
    Developer(
    "OlivierBlanvillain",
    "Olivier Blanvillain",
    "noreply@github.com",
    url("https://github.com/OlivierBlanvillain/")
  )
)))

val scalajsdom = "2.2.0"
val scalatest  = "3.2.12"
val cats       = "2.7.0"

publish / skip := true

lazy val `monadic-html` = project
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(
    `monadic-rxJS`,
    `monadic-rx-catsJS` % "test->compile")
  .settings(
    testSettings,
    libraryDependencies += "org.scala-js"  %%% "scalajs-dom" % scalajsdom,
    libraryDependencies += "org.scalatest" %%% "scalatest" % scalatest % Test)

lazy val `monadic-rxJS`  = `monadic-rx`.js
lazy val `monadic-rxJVM` = `monadic-rx`.jvm
lazy val `monadic-rx`    = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .jvmSettings(publish / skip := true)
  .jsSettings(testSettings)
  .settings(
    libraryDependencies += "org.scalatest" %%% "scalatest" % scalatest % Test)

lazy val `monadic-rx-catsJS`  = `monadic-rx-cats`.js
lazy val `monadic-rx-catsJVM` = `monadic-rx-cats`.jvm
lazy val `monadic-rx-cats`    = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .jvmSettings(publish / skip := true)
  .dependsOn(`monadic-rx`)
  .settings(
    testSettings,
    libraryDependencies += "org.typelevel" %%% "cats-core" % cats)

lazy val `examples` = project
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(`monadic-html`, `monadic-rx-catsJS`)
  .settings(
    testSettings,
    publish / skip := true,
    Test / test := {},
    libraryDependencies += "com.github.japgolly.scalacss" %%% "core" % "1.0.0",
    libraryDependencies += "org.scala-js" %%% "scala-js-macrotask-executor" % "1.0.0")

lazy val testSettings = Seq(
  Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-oDF"),
  Test / jsEnv       := new org.scalajs.jsenv.jsdomnodejs.JSDOMNodeJSEnv())
