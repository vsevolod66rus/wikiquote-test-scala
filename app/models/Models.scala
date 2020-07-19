package models

import play.api.libs.functional.syntax._
import java.sql.Timestamp
import java.text.SimpleDateFormat
import play.api.libs.json._

case class QuoteImportFormat(id: Option[Int],
                             createTimestamp: Timestamp,
                             timestamp: Timestamp,
                             language: String,
                             wiki: String,
                             category: List[String],
                             title: String,
                             auxiliaryText: Option[List[String]])

case class QuoteGetFormat(id: Int,
                          creationDate: Long,
                          lastModified: Long,
                          language: String,
                          wiki: String,
                          category: List[String],
                          title: String,
                          auxiliaryText: Option[List[String]])

case class QuoteCategoriesInfo(category: String, count: Int)

case class QuoteAddFormat(language: String,
                          wiki: String,
                          category: List[String],
                          title: String,
                          auxiliaryText: Option[List[String]])

object QuoteImportFormat {
  implicit object timestampFormat extends Format[Timestamp] {
    val format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
    def reads(json: JsValue) = {
      val str = json.as[String]
      JsSuccess(new Timestamp(format.parse(str).getTime))
    }
    def writes(ts: Timestamp) = JsString(format.format(ts))
  }
  implicit val quoteReader: Reads[QuoteImportFormat] = (
    (__ \ "id").readNullable[Int] and
      (__ \ "create_timestamp").read[Timestamp] and
      (__ \ "timestamp").read[Timestamp] and
      (__ \ "language").read[String] and
      (__ \ "wiki").read[String] and
      (__ \ "category").read[List[String]] and
      (__ \ "title").read[String] and
      (__ \ "auxiliary_text")
        .readNullable[List[String]]
  )(QuoteImportFormat.apply _)
}

object QuoteGetFormat {
  implicit val quoteGetFormatWriter: Format[QuoteGetFormat] =
    Json.format[QuoteGetFormat]
}

object QuoteCategoriesInfo {
  implicit val quoteCategoriesInfoWriter: Format[QuoteCategoriesInfo] =
    Json.format[QuoteCategoriesInfo]
}

object QuoteAddFormat {
  implicit val quoteAddFormatReader: Format[QuoteAddFormat] =
    Json.format[QuoteAddFormat]
}
