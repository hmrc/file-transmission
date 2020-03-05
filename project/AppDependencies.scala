import play.core.PlayVersion
import play.sbt.PlayImport._
import sbt._

object AppDependencies {

  val akkaVersion = "2.5.23"
  val akkaHttpVersion = "10.0.15"

  val compile = Seq(
    ws,
    "uk.gov.hmrc" %% "bootstrap-play-26" % "1.5.0",
    "org.scala-lang.modules" %% "scala-xml" % "1.1.0",
    "uk.gov.hmrc" %% "simple-reactivemongo" % "7.23.0-play-26",
    "org.typelevel" %% "cats-core" % "1.0.1",
    "uk.gov.hmrc" %% "work-item-repo" % "6.10.0-play-26"
  )

  def test(scope: String = "test,it") = Seq(
    "com.github.tomakehurst" % "wiremock-jre8" % "2.21.0" % scope,
    "uk.gov.hmrc"             %% "service-integration-test"   % "0.9.0-play-26"         % scope,
    "org.scalatest" %% "scalatest" % "3.0.5" % scope,
    "org.mockito" % "mockito-core" % "2.6.2" % scope,
    "org.pegdown" % "pegdown" % "1.6.0" % scope,
    "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
    "org.xmlunit" % "xmlunit-core" % "2.6.0" % scope,

    "org.scalamock" %% "scalamock-scalatest-support" % "3.5.0" % scope,
    "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.3" % scope
  )


  // Ensure akka versions do not mismatch
  val overrides = Seq(
  "com.typesafe.akka" %% "akka-stream"    % akkaVersion,
  "com.typesafe.akka" %% "akka-protobuf"  % akkaVersion,
  "com.typesafe.akka" %% "akka-slf4j"     % akkaVersion,
  "com.typesafe.akka" %% "akka-actor"     % akkaVersion,
  "com.typesafe.akka" %% "akka-http-core" % akkaHttpVersion
  )


}
