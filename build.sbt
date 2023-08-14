import ScalaProject._

ThisBuild / crossScalaVersions := List(scala2Version, scala3Version)

val weaverVersion = "0.6.15"
val monixVersion = "3.4.0"
val catsVersion = "2.9.0"
val catsEffect2Version = "2.5.4"
val catsCoreDeps = List(
  "org.typelevel" %% "cats-core" % catsVersion,
)

val ce2Deps = catsCoreDeps ++ List(
  "org.typelevel" %% "cats-effect" % catsEffect2Version,
)
val monixDeps = ce2Deps ++ List(
    "io.monix" %% "monix" % monixVersion,
)
val weaverDeps = List(
  "com.disneystreaming" %% "weaver-cats" % weaverVersion,
  "com.disneystreaming" %% "weaver-monix" % weaverVersion,
)

ThisBuild / versionScheme := Some("semver-spec")

lazy val commonSettings = Seq(
  organization := "net.gfxmonk",
  testFrameworks += new TestFramework("weaver.framework.CatsEffect"),
  testFrameworks += new TestFramework("weaver.framework.Monix"),
  libraryDependencies ++= monixDeps.map(_ % Test) ++ weaverDeps.map(_ % Test),
)

lazy val core = (project in file("core")).settings(
  commonSettings,
  publicProjectSettings,
  name := "backpressure-sensor-core",
)

lazy val testkit = (project in file("testkit")).settings(
  commonSettings,
  publicProjectSettings,
  name := "backpressure-sensor-testkit",
).dependsOn(core)

lazy val statsd = (project in file("statsd")).settings(
  commonSettings,
  publicProjectSettings,
  name := "backpressure-sensor-statsd",
  libraryDependencies ++= Seq(
    "com.datadoghq" % "java-dogstatsd-client" % "4.0.0"
  )
).dependsOn(core)

lazy val monix = (project in file("monix")).settings(
  commonSettings,
  publicProjectSettings,
  name := "backpressure-sensor-monix",
  libraryDependencies ++= ce2Deps ++ List(
    "io.monix" %% "monix" % monixVersion,
  )
).dependsOn(core, statsd, testkit % "test")

lazy val fs2Cats2 = (project in file("fs2-cats2")).settings(
  commonSettings,
  publicProjectSettings,
  name := "backpressure-sensor-fs2-cats2",
  libraryDependencies ++= ce2Deps ++ List(
    "co.fs2" %% "fs2-core" % "2.5.10",
    "org.typelevel" %% "cats-effect-laws" % catsEffect2Version % "test", // provides TestContext
),
).dependsOn(core, statsd, testkit % "test")

//lazy val fs2Cats3 = (project in file("fs2")).settings(
//  commonSettings,
//  publicProjectSettings,
//  name := "backpressure-sensor-fs2-cats2",
//  libraryDependencies ++= cats3Deps ++ List(
//    "co.fs2" %% "fs2-core" % "3.2.9"
//  ),
//).dependsOn(core, statsd)

lazy val pekko = (project in file("pekko")).settings(
  commonSettings,
  publicProjectSettings,
  name := "backpressure-sensor-pekko",
  libraryDependencies ++= Seq(
    "org.apache.pekko" %% "pekko-stream" % "1.0.1",
  ),
).dependsOn(core, statsd, testkit % "test")

lazy val example = (project in file("example")).settings(
  commonSettings,
  hiddenProjectSettings,
  name := "backpressure-sensor-example",
).dependsOn(monix)

lazy val root = (project in file("."))
  .settings(
    name := "root",
    hiddenProjectSettings
  )
  .aggregate(core, statsd, monix, pekko, fs2Cats2, example, testkit)
