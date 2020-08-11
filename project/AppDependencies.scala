import play.core.PlayVersion
import play.sbt.PlayImport._
import sbt._

object AppDependencies {

  val compile = Seq(
    ws,
    "org.scala-lang.modules" %% "scala-xml"        % "1.1.0",
    "uk.gov.hmrc"   %% "bootstrap-backend-play-27" % "2.24.0",
    "uk.gov.hmrc"   %% "simple-reactivemongo"      % "7.30.0-play-27",
    "org.typelevel" %% "cats-core"                 % "1.0.1",
    "uk.gov.hmrc"   %% "work-item-repo"            % "7.6.0-play-27"
  )

  def test(scope: String = "test,it") = Seq(
    "com.github.tomakehurst" %  "wiremock-jre8"            % "2.21.0"            % scope,
    "uk.gov.hmrc"            %% "service-integration-test" % "0.12.0-play-27"    % scope,
    "org.scalatest"          %% "scalatest"                % "3.0.5"             % scope,
    "org.mockito"            %  "mockito-core"             % "2.6.2"             % scope,
    "org.pegdown"            %  "pegdown"                  % "1.6.0"             % scope,
    "com.typesafe.play"      %% "play-test"                % PlayVersion.current % scope,
    "org.xmlunit"            %  "xmlunit-core"             % "2.6.0"             % scope,

    "org.scalamock"          %% "scalamock-scalatest-support" % "3.5.0" % scope,
    "org.scalatestplus.play" %% "scalatestplus-play"          % "4.0.3" % scope
  )

}
