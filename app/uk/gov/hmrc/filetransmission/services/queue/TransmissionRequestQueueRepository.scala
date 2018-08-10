package uk.gov.hmrc.filetransmission.services.queue
import org.joda.time.{DateTime, Duration}
import play.api.{Configuration, Play}
import play.api.libs.json.Format
import reactivemongo.api.DB
import uk.gov.hmrc.filetransmission.config.ServiceConfiguration
import uk.gov.hmrc.filetransmission.model.TransmissionRequest
import uk.gov.hmrc.workitem.WorkItemModuleRepository

class RequestQueueService(collectionName : String, mongo: () => DB, configuration: ServiceConfiguration)(implicit requestFormat : Format[TransmissionRequest])
  extends WorkItemModuleRepository[TransmissionRequest](collectionName, "transmission-request", mongo) {

  override def now: DateTime                             = DateTime.now()

  override def inProgressRetryAfterProperty: String       = ???

  override lazy val inProgressRetryAfter: Duration =
    configuration.{
    implicit val app = Play.current
    val configValue = Play.application.configuration.
      getMilliseconds(inProgressRetryAfterProperty).
      getOrElse(throw new IllegalStateException(s"$inProgressRetryAfterProperty config value not set"))
    Duration.millis(configValue)
  }
}
