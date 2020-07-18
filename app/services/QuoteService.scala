package services

import models._
import com.google.inject.Inject
import repositories.QuoteRepositoryImpl
import scala.concurrent.Future

trait QuoteService {
  def readQuotesFromJsonFile: Future[Either[Throwable, (Int, Int)]]

  def findQuote(title: String): Future[Either[Throwable, QuoteGetFormat]]

  def checkPath: Future[Either[Throwable, (Boolean, String)]]

  def getCategoriesInfo: Future[Either[Throwable, List[QuoteCategoriesInfo]]]

  def changeQuote(
    quoteGetFormat: QuoteGetFormat
  ): Future[Either[Throwable, Int]]

  def addQuote(quoteAddFormat: QuoteAddFormat): Future[Either[Throwable, Int]]
}

class QuoteServiceImpl @Inject()(repository: QuoteRepositoryImpl)
    extends QuoteService {
  def readQuotesFromJsonFile: Future[Either[Throwable, (Int, Int)]] =
    repository.readQuotesFromJsonFile.attempt.unsafeToFuture()

  def findQuote(title: String): Future[Either[Throwable, QuoteGetFormat]] =
    repository.findQuote(title).attempt.unsafeToFuture()

  def checkPath: Future[Either[Throwable, (Boolean, String)]] =
    repository.checkPath.attempt.unsafeToFuture()

  def getCategoriesInfo: Future[Either[Throwable, List[QuoteCategoriesInfo]]] =
    repository.getCategoriesInfo.attempt.unsafeToFuture()

  def changeQuote(
    quoteGetFormat: QuoteGetFormat
  ): Future[Either[Throwable, Int]] =
    repository.changeQuote(quoteGetFormat).attempt.unsafeToFuture()

  def addQuote(quoteAddFormat: QuoteAddFormat) =
    repository.addQuote(quoteAddFormat).attempt.unsafeToFuture()
}
