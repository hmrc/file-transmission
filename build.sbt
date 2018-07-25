import TestPhases.oneForkedJvmPerTest
import scoverage.ScoverageKeys
import uk.gov.hmrc.DefaultBuildSettings.{addTestReportOption, scalaSettings}
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin.publishingSettings

val appName = "file-transmission"


lazy val scoverageSettings = {
  Seq(
    // Semicolon-separated list of regexs matching classes to exclude
    ScoverageKeys.coverageExcludedPackages := "<empty>;Reverse.*;.*AuthService.*;models/.data/..*;view.*",
    ScoverageKeys.coverageExcludedFiles :=
      ".*/frontendGlobal.*;.*/frontendAppConfig.*;.*/frontendWiring.*;.*/views/.*_template.*;.*/govuk_wrapper.*;.*/routes_routing.*;.*/BuildInfo.*",
    // Minimum is deliberately low to avoid failures initially - please increase as we add more coverage
    ScoverageKeys.coverageMinimum := 25,
    ScoverageKeys.coverageFailOnMinimum := false,
    ScoverageKeys.coverageHighlighting := true,
    parallelExecution in Test := false
  )
}

lazy val microservice = Project(appName, file("."))
  .enablePlugins(play.sbt.PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin)
  .settings(PlayKeys.playDefaultPort := 9575)
  .settings(scalaSettings ++ scoverageSettings: _*)
  .settings(
    libraryDependencies              ++= AppDependencies.compile ++ AppDependencies.test(),
    evictionWarningOptions in update := EvictionWarningOptions.default.withWarnScalaVersionEviction(false)
  )
  .settings(
    publishingSettings: _*
  )
  .configs(IntegrationTest)
  .settings(inConfig(IntegrationTest)(Defaults.itSettings): _*)
  .settings(
    Keys.fork in IntegrationTest                  := false,
    unmanagedSourceDirectories in IntegrationTest := (baseDirectory in IntegrationTest) (base => Seq(base / "it")).value,
    testGrouping in IntegrationTest               := oneForkedJvmPerTest((definedTests in IntegrationTest).value),
    parallelExecution in IntegrationTest          := false,
    addTestReportOption(IntegrationTest, "int-test-reports")
  )
  .settings(
    resolvers += Resolver.jcenterRepo
  )
