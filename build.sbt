import uk.gov.hmrc.DefaultBuildSettings.integrationTestSettings

lazy val microservice = Project("file-transmission", file("."))
  .enablePlugins(PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin) //Required to prevent https://github.com/scalatest/scalatest/issues/1427
  .settings(
    majorVersion        := 1,
    scalaVersion        := "3.3.6",
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test
  )
  .settings(
    scalacOptions += "-Wconf:msg=Flag.*repeatedly:s",
    scalacOptions += "-Wconf:src=routes/.*:s"
  )
  .settings(PlayKeys.playDefaultPort := 9575)
