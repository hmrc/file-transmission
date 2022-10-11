import play.sbt.PlayImport._
import sbt.Keys.libraryDependencies
import sbt._

object AppDependencies {

  val bootstrapVersion = "5.24.0"
  val hmrcMongoVersion = "0.73.0"

  val compile = Seq(
    "uk.gov.hmrc"             %% "bootstrap-backend-play-28"         % bootstrapVersion,
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-play-28"                % hmrcMongoVersion,
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-work-item-repo-play-28" % hmrcMongoVersion,
    "org.typelevel"           %% "cats-core"                         % "2.6.1",

  )

  val test = Seq(
    "uk.gov.hmrc"             %% "bootstrap-test-play-28"     % bootstrapVersion   % "test, it",
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-test-play-28"    % hmrcMongoVersion   % Test,
    "org.mockito"             %% "mockito-scala-scalatest"    % "1.17.5"           % Test,
  )
}
