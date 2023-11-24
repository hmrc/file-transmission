import play.sbt.PlayImport._
import sbt.Keys.libraryDependencies
import sbt._

object AppDependencies {

  val bootstrapVersion = "8.0.0"
  val hmrcMongoVersion = "1.5.0"

  val compile = Seq(
    "uk.gov.hmrc"             %% "bootstrap-backend-play-30"         % bootstrapVersion,
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-play-30"                % hmrcMongoVersion,
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-work-item-repo-play-30" % hmrcMongoVersion,
    "org.typelevel"           %% "cats-core"                         % "2.9.0"

  )

  val test = Seq(
    "uk.gov.hmrc"             %% "bootstrap-test-play-30"     % bootstrapVersion   % Test,
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-test-play-30"    % hmrcMongoVersion   % Test,
    "org.mockito"             %% "mockito-scala-scalatest"    % "1.17.29"          % Test
  )
}
