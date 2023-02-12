ThisBuild / organization := "dev.kovstas"

ThisBuild / scalaVersion := "2.13.10"
ThisBuild / crossScalaVersions := List("2.12.17", "2.13.10", "3.2.2")
ThisBuild / scalacOptions ++= scalaOptions(scalaVersion.value)

ThisBuild / libraryDependencies ++= Seq(
  "co.fs2" %% "fs2-core" % "3.6.1",
  "org.scalameta" %% "munit" % "0.7.29" % Test,
  "org.typelevel" %% "cats-effect-testkit" % "3.4.6" % Test
)

lazy val root =
  crossProject(JSPlatform, JVMPlatform, NativePlatform)
    .crossType(CrossType.Pure)
    .withoutSuffixFor(JVMPlatform)
    .settings(
      name := "fs2-throttler"
    )
    .in(file("."))

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

ThisBuild / homepage := Some(url("https://github.com/kovstas/fs2-throttler"))
ThisBuild / licenses := List("MIT" -> url("http://opensource.org/licenses/MIT"))
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
ThisBuild / sonatypeCredentialHost := "s01.oss.sonatype.org"
ThisBuild / versionScheme := Some("early-semver")
ThisBuild / scalafmtOnCompile := true
