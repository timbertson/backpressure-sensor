import ScalaProject._

ThisBuild / crossScalaVersions := List(scala2Version, scala3Version)

val weaverVersion = "0.13.0"
val weaverVersionCe2 = "0.6.15"
val monixVersion = "3.4.0"
val catsVersion = "2.9.0"
val catsEffect2Version = "2.5.4"
val catsEffect3Version = "3.7.1"
val catsCoreDeps = List(
  "org.typelevel" %% "cats-core" % catsVersion,
)
val ce3Deps = catsCoreDeps ++ List(
  "org.typelevel" %% "cats-effect" % catsEffect3Version,
)
val ce2Deps = catsCoreDeps ++ List(
  "org.typelevel" %% "cats-effect" % catsEffect2Version,
)
val monixDeps = ce2Deps ++ List(
    "io.monix" %% "monix" % monixVersion,
)
val weaverDepsCe2 = List(
  "com.disneystreaming" %% "weaver-cats" % weaverVersionCe2,
  "com.disneystreaming" %% "weaver-monix" % weaverVersionCe2,
)

val weaverDeps = List(
  "org.typelevel" %% "weaver-cats" % weaverVersion,
)

ThisBuild / versionScheme := Some("semver-spec")

lazy val commonSettings = Seq(
  organization := "net.gfxmonk",
  testFrameworks += new TestFramework("weaver.framework.CatsEffect"),
)

lazy val ce3TestSettings = Seq(
  testFrameworks += new TestFramework("weaver.framework.CatsEffect"),
  libraryDependencies ++= weaverDeps.map(_ % Test),
)

lazy val monixTestSettings = Seq(
  testFrameworks += new TestFramework("weaver.framework.CatsEffect"),
  testFrameworks += new TestFramework("weaver.framework.Monix"),
  libraryDependencies ++= monixDeps.map(_ % Test) ++ weaverDepsCe2.map(_ % Test),
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
    "com.datadoghq" % "java-dogstatsd-client" % "4.4.3"
  ),
  scalacOptions ~= (_.filterNot(Set(
    "-Wunused:nowarn",
  ))),
).dependsOn(core)

lazy val monix = (project in file("monix")).settings(
  commonSettings,
  publicProjectSettings,
  monixTestSettings,
  name := "backpressure-sensor-monix",
  libraryDependencies ++= ce2Deps ++ List(
    "io.monix" %% "monix" % monixVersion,
  )
).dependsOn(core, statsd, testkit % "test")

lazy val fs2Cats2 = (project in file("fs2-cats2")).settings(
  commonSettings,
  publicProjectSettings,
  monixTestSettings,
  name := "backpressure-sensor-fs2-cats2",
  libraryDependencies ++= ce2Deps ++ List(
    "co.fs2" %% "fs2-core" % "2.5.10",
    "org.typelevel" %% "cats-effect-laws" % catsEffect2Version % "test", // provides TestContext
),
).dependsOn(core, statsd, testkit % "test")

lazy val fs2 = (project in file("fs2")).settings(
  commonSettings,
  publicProjectSettings,
  ce3TestSettings,
  name := "backpressure-sensor-fs2",
  libraryDependencies ++= ce3Deps ++ List(
    "co.fs2" %% "fs2-core" % "3.2.9",
    "org.typelevel" %% "cats-effect-testkit" % catsEffect3Version % Test,
  ),
).dependsOn(core, statsd, testkit % "test")

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
  .aggregate(core, statsd, monix, pekko, fs2, fs2Cats2, example, testkit)
