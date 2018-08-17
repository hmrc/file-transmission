import play.core.PlayVersion
import play.sbt.PlayImport._
import sbt.Keys.libraryDependencies
import sbt._

object AppDependencies {

  val compile = Seq(
    ws,
    "uk.gov.hmrc"            %% "bootstrap-play-25" % "1.7.0",
    "org.scala-lang.modules" %% "scala-xml"         % "1.1.0",
    "org.typelevel"     %% "cats-core"         % "1.0.1"
  )

  def test(scope: String = "test,it") = Seq(
    "uk.gov.hmrc"            %% "hmrctest"    % "3.0.0"             % scope,
    "org.scalatest"          %% "scalatest"   % "3.0.4"             % scope,
    "org.mockito"            % "mockito-core" % "2.6.2"             % scope,
    "org.pegdown"            % "pegdown"      % "1.6.0"             % scope,
    "com.typesafe.play"      %% "play-test"   % PlayVersion.current % scope,
    "org.xmlunit"            % "xmlunit-core" % "2.6.0"             % scope,
    "com.github.tomakehurst" % "wiremock"                     % "2.2.2"             % scope,
    "org.scalamock"          %% "scalamock-scalatest-support" % "3.5.0"             % scope,
    "org.scalatestplus.play" %% "scalatestplus-play"          % "2.0.0"             % scope
  )

}
