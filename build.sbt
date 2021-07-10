ThisBuild / name := "fs2-throttler"
ThisBuild / organization := "dev.kovstas"

ThisBuild / scalaVersion := "2.13.6"
ThisBuild / crossScalaVersions := List("2.12.14", "2.13.6", "3.0.1")
ThisBuild / scalacOptions ++= scalaOptions(scalaVersion.value)

ThisBuild / libraryDependencies ++= Seq(
  "co.fs2" %% "fs2-core" % "3.0.6",
  "org.scalameta" %% "munit" % "0.7.27" % Test,
  "org.typelevel" %% "cats-effect-testkit" % "3.1.1" % Test
)

def scalaOptions(v: String) = {
  val options = List(
    "-Xfatal-warnings",
    "-deprecation",
    "-feature",
    "-unchecked",
    "-language:higherKinds"
  )

  CrossVersion.partialVersion(v) match {
    case Some((3, _)) => options :+ "-source:3.0-migration"
    case _            => options
  }
}
enablePlugins(AutomateHeaderPlugin)

ThisBuild / startYear := Some(2021)
ThisBuild / organizationName := "Stanislav Kovalenko"

ThisBuild / licenses += ("MIT", url("http://opensource.org/licenses/MIT"))
ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/kovstas/fs2-throttler"),
    "scm:git@github.com:kovstas/fs2-throttler.git"
  )
)
ThisBuild / developers := List(
  Developer(
    id = "kovstas",
    name = "Stanislav Kovalenko",
    email = "mail@kovstas.dev",
    url = url("https://kovstas.dev")
  )
)
ThisBuild / description := "Throttling for FS2 based on the Token bucket algorithm"
sonatypeCredentialHost := "s01.oss.sonatype.org"
ThisBuild / versionScheme := Some("early-semver")
ThisBuild / scalafmtOnCompile := true
