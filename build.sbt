import uk.gov.hmrc.DefaultBuildSettings.integrationTestSettings

lazy val microservice = Project("file-transmission", file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .settings(
    majorVersion        := 1,
    scalaVersion        := "2.13.16",
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test
  )
  .settings(scalacOptions += "-Wconf:src=routes/.*:s")
  .settings(PlayKeys.playDefaultPort := 9575)
  .settings(resolvers += Resolver.jcenterRepo)
