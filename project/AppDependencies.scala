import play.sbt.PlayImport._
import sbt._

object AppDependencies {

  private val bootstrapVersion = "5.8.0"

  val compile = Seq(
    ws,
    "org.scala-lang.modules" %% "scala-xml"        % "1.1.0",
    "uk.gov.hmrc"   %% "bootstrap-backend-play-28" % bootstrapVersion,
    "uk.gov.hmrc"   %% "simple-reactivemongo"      % "8.0.0-play-28",
    "org.typelevel" %% "cats-core"                 % "1.0.1",
    "uk.gov.hmrc"   %% "work-item-repo"            % "8.0.0-play-28"
  )

  def test(scope: String = "test,it") = Seq(
    "com.github.tomakehurst" %  "wiremock-jre8"            % "2.21.0"            % scope,
    "uk.gov.hmrc"            %% "bootstrap-test-play-28"   % bootstrapVersion    % Test,
    "org.mockito"            %% "mockito-scala"            % "1.16.23"           % Test,
    "uk.gov.hmrc"            %% "service-integration-test" % "1.1.0-play-28"     % scope,
    "org.pegdown"            %  "pegdown"                  % "1.6.0"             % scope,
    "org.xmlunit"            %  "xmlunit-core"             % "2.6.0"             % scope,
    "com.vladsch.flexmark"   %  "flexmark-all"             % "0.35.10"           % scope
  )

}
