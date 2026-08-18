import AppDependencies.bootstrapVersion
import scoverage.ScoverageKeys
import uk.gov.hmrc.DefaultBuildSettings.itSettings

val appName = "file-transmission"

val scala3_3_6      = "3.3.6"

val testDirectory            = "test"
val scalaStyleConfigFile     = "scalastyle-config.xml"
val testScalaStyleConfigFile = "test-scalastyle-config.xml"

ThisBuild / majorVersion := 1
ThisBuild / scalaVersion := scala3_3_6

lazy val it = project
  .enablePlugins(PlayScala)
  .dependsOn(microservice % "test->test")
  .settings(itSettings())
  .settings(libraryDependencies ++= Seq("uk.gov.hmrc" %% "bootstrap-test-play-30" % bootstrapVersion % Test))

lazy val scalastyleSettings = Seq(
  scalastyleConfig := baseDirectory.value / scalaStyleConfigFile,
  (Test / scalastyleConfig) := baseDirectory.value / testDirectory / testScalaStyleConfigFile
)

lazy val microservice = Project(appName, file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .settings(PlayKeys.playDefaultPort := 9878)
  .disablePlugins(sbt.plugins.JUnitXmlReportPlugin)
  .settings(Compile / unmanagedResourceDirectories += baseDirectory.value / "resources")
  .settings(
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test,
    ScoverageKeys.coverageExcludedPackages := List(
      "<empty>",
      ".*Reverse.*",
      ".*(BuildInfo|Routes|testOnly).*",
    ).mkString(";"),
    ScoverageKeys.coverageMinimumStmtTotal := 90,
    ScoverageKeys.coverageFailOnMinimum := true,
    ScoverageKeys.coverageHighlighting := true,
    scalacOptions := scalacOptions.value.diff(Seq("-Wunused:all")),
    scalacOptions += "-Wconf:msg=Flag.*repeatedly:s",
    Test / scalacOptions := scalacOptions.value.diff(Seq("-Wunused:all"))
  )
  .settings(scalastyleSettings)
  .settings(Test / parallelExecution := false)

addCommandAlias(
  "runAllChecks",
  "clean;compile;coverage;test;it/test;coverageReport"
)
