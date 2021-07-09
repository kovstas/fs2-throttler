name := "fs2-throttler"
organization := "dev.kovstas"
scalaVersion := "2.13.6"
crossScalaVersions := List("2.12.14", "2.13.6", "3.0.1")
licenses += ("MIT", url("http://opensource.org/licenses/MIT"))

libraryDependencies ++= Seq(
  "co.fs2" %% "fs2-core" % "3.0.6",
  "org.scalameta" %% "munit" % "0.7.27" % Test,
  "org.typelevel" %% "cats-effect-testkit" % "3.1.1" % Test
)