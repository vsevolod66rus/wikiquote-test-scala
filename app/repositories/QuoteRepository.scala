package repositories

import java.sql.Timestamp

import doobie.ConnectionIO
import doobie.implicits._
import doobie.util.transactor.Transactor
import doobie.free.connection
import cats.effect.{ContextShift, IO}
import cats.syntax.flatMap.catsSyntaxIfM
import cats.instances.list._
import cats.syntax.applicative._
import cats.syntax.traverse._
import doobie.implicits.javasql._
import java.nio.file.{Files, Paths}

import scala.io.Source
import com.google.inject.Inject
import doobie.util.fragment.Fragment
import play.api.Configuration
import models._
import akka.actor.ActorSystem
import exceptions.InvalidInputException
import play.api.libs.json.Json

trait QuoteRepository {
  def addQuote(quote: Quote): IO[Int]

  def readQuotesFromJsonFile: IO[(Int, Int)]

  def findQuote(title: String): IO[QuoteGetFormat]

  def checkPath: IO[(Boolean, String)]
}

class QuoteRepositoryImpl @Inject()(configuration: Configuration)
    extends QuoteRepository {

  def addQuotes(quotes: List[Quote]): IO[List[Int]] =
    for {
      res <- quotes.traverse(addQuote)
    } yield res

  def addQuote(quote: Quote): IO[Int] =
    (for {
      _ <- isQuoteExist(quote.title)
      id <- addQuoteInfo(
        quote.createTimestamp,
        quote.timestamp,
        quote.language,
        quote.wiki,
        quote.title
      )
      _ <- quote.category.traverse { category =>
        addCategory(id, category)
      }
      _ <- quote.auxiliaryText.get
        .traverse { text =>
          insertAuxiliaryText(id, text)
        }
        .whenA(quote.auxiliaryText.isDefined)
    } yield id)
      .transact(transactor)

  def addQuoteInfo(creationTimestamp: Timestamp,
                   timestamp: Timestamp,
                   language: String,
                   wiki: String,
                   title: String): ConnectionIO[Int] =
    sql"""insert into quote (creation_timestamp, last_modified, language, wiki, title) 
          values ($creationTimestamp, $timestamp, $language ,$wiki, $title) returning id"""
      .query[Int]
      .unique

  private def insertCategory(category: String): ConnectionIO[Int] =
    isCategoryExists(category)
      .ifM(
        sql"select id from quote_category where caption = $category"
          .query[Int]
          .unique,
        sql"insert into quote_category (caption) values ($category) returning id"
          .query[Int]
          .unique
      )

  private def isQuoteExist(title: String): ConnectionIO[Boolean] =
    for {
      exists <- sql"select count(*) > 0 from quote where title = $title"
        .query[Boolean]
        .unique
      _ <- connection
        .raiseError(InvalidInputException(s"Quote $title already exists"))
        .whenA(exists)
    } yield exists

  private def isCategoryExists(category: String): ConnectionIO[Boolean] =
    sql"select count(*) > 0 from quote_category where caption = $category"
      .query[Boolean]
      .unique

  def readQuotesFromJsonFile: IO[(Int, Int)] = {
    if (Files
          .exists(Paths.get(configuration.get[String]("read.path")))) {
      val buffer = Source.fromFile(configuration.get[String]("read.path"))
      val raw = buffer.getLines.toList
      buffer.close()
      val rawQuotes = raw
        .map(str => Json.parse(str))
        .filter(_.validate[Quote].isSuccess)
        .map(_.as[Quote])
      def quotesLoop(quotes: List[Quote],
                     success: Int,
                     fail: Int): IO[(Int, Int)] = {
        quotes match {
          case head :: tail =>
            addQuote(head).attempt.flatMap {
              case Right(_) => quotesLoop(tail, success + 1, fail)
              case Left(_)  => quotesLoop(tail, success, fail + 1)
            }
          case Nil => (success, fail).pure[IO]
        }
      }
      quotesLoop(rawQuotes, 0, 0)
    } else (0, 0).pure[IO]
  }

  private def insertAuxiliaryText(id: Int, text: String): ConnectionIO[Int] =
    sql"insert into quote_auxiliary_text (quote_id, caption) values ($id, $text)".update.run

  private def addCategory(id: Int, category: String): ConnectionIO[Int] =
    for {
      categoryId <- insertCategory(category)
      _ <- sql"""insert into quote_category_relation (quote_id, category_id) values ($id, $categoryId)""".update.run
    } yield categoryId

  case class QuoteInfo(createTimestamp: Timestamp,
                       timestamp: Timestamp,
                       wiki: String,
                       language: String,
                       title: String)

  def findQuote(title: String): IO[QuoteGetFormat] =
    (for {
      exists <- isTitleExists(title)
      _ <- connection
        .raiseError(
          InvalidInputException(s"there is no quote with title $title")
        )
        .whenA(!exists)
      id <- getId(title)
      categories <- getQuoteCategories(id)
      auxiliaryText <- getQuoteAuxiliaryText(id)
      quoteInfo <- getQuoteInfo(id)
    } yield
      QuoteGetFormat(
        id,
        quoteInfo.createTimestamp.getTime,
        quoteInfo.timestamp.getTime,
        quoteInfo.language,
        quoteInfo.wiki,
        categories,
        quoteInfo.title,
        Option(auxiliaryText)
      ))
      .transact(transactor)

  private def getQuoteCategories(id: Int): ConnectionIO[List[String]] =
    sql"""select caption from quote_category_relation 
          left join quote_category on quote_category_relation.category_id = quote_category.id
          where quote_id = $id""".query[String].to[List]

  private def getQuoteAuxiliaryText(id: Int): ConnectionIO[List[String]] =
    sql"select caption from quote_auxiliary_text where quote_id = $id"
      .query[String]
      .to[List]

  private def getQuoteInfo(id: Int): ConnectionIO[QuoteInfo] =
    sql"""select creation_timestamp, last_modified, wiki, language, title from quote where id = $id"""
      .query[QuoteInfo]
      .unique

  private def getId(title: String): ConnectionIO[Int] =
    for {
      titleWithPath <- (s"К удалению/$title").pure[ConnectionIO]
      id <- sql"select id from quote  where lower(title) = lower($titleWithPath) or lower(title) = lower($title)"
        .query[Int]
        .unique
    } yield id

  private def isTitleExists(title: String): ConnectionIO[Boolean] =
    for {
      titleWithPath <- (s"К удалению/$title").pure[ConnectionIO]
      exists <- sql"select count(*) from quote where lower(title) = lower($titleWithPath) or lower(title) = lower($title)"
        .query[Boolean]
        .unique
    } yield exists

  def checkPath: IO[(Boolean, String)] =
    for {
      path <- configuration.get[String]("read.path").pure[IO]
      res <- Files
        .exists(Paths.get(configuration.get[String]("read.path")))
        .pure[IO]
    } yield (res, path)

  val system = ActorSystem("wiki-test")

  private val dbExecutionContext = system.dispatcher

  implicit val contextShift: ContextShift[IO] =
    IO.contextShift(dbExecutionContext)

  private val driver = configuration.get[String]("db.default.driver")

  private val url = configuration.get[String]("db.default.url")

  private val username = configuration.get[String]("db.default.username")

  private val password = configuration.get[String]("db.default.password")

  val transactor: Transactor[IO] = Transactor
    .fromDriverManager(driver, url, username, password)

  private val createTable: Unit =
    Source
      .fromFile("app/resources/quotes.sql")
      .mkString
      .pure[IO]
      .flatMap(
        Fragment(_, Nil).update.run
          .transact(transactor)
      )
      .unsafeRunAsyncAndForget()
}
