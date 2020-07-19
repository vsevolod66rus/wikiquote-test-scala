package controllers

import akka.actor.ActorSystem
import services.QuoteServiceImpl

import javax.inject._
import play.api.libs.json.Json
import play.api.mvc._
import models._

import scala.concurrent.Future

@Singleton
class QuoteController @Inject()(cc: ControllerComponents,
                                service: QuoteServiceImpl)
    extends AbstractController(cc) {

  val system = ActorSystem("wiki-test")

  implicit val ec = system.dispatcher

  def importQuotes() = Action.async { implicit request: Request[AnyContent] =>
    service.readQuotesFromJsonFile
    service.checkPath
      .flatMap {
        case Left(err) =>
          Future.successful(BadRequest(err.getMessage))
        case Right(value) => {
          if (value._1)
            Future.successful(Ok(s"Started import from file: ${value._2}"))
          else Future.successful(BadRequest(s"File ${value._2} not found"))
        }
      }
  }

  def findQuote(title: String, pretty: Option[String]) = Action.async {
    implicit request: Request[AnyContent] =>
      service.findQuote(title).flatMap {
        case Left(err) =>
          Future.successful(BadRequest(err.getMessage))
        case Right(value) =>
          request.getQueryString("pretty") match {
            case Some(pretty) =>
              pretty match {
                case "" =>
                  Future.successful((Ok(Json.prettyPrint(Json.toJson(value)))))
                case _ => Future.successful(Ok(Json.toJson(value)))
              }
            case None => Future.successful(Ok(Json.toJson(value)))
          }
      }
  }

  def getCategoriesInfo = Action.async {
    implicit request: Request[AnyContent] =>
      service.getCategoriesInfo.flatMap {
        case Right(value) => Future.successful(Ok(Json.toJson(value)))
        case Left(err)    => Future.successful(BadRequest(err.getMessage))
      }
  }

  def changeQuote = Action.async(parse.json) { implicit request =>
    request.body
      .validate[QuoteGetFormat]
      .fold(
        _ => {
          Future.successful(BadRequest("Invalid quote data format"))
        },
        service.changeQuote(_).flatMap {
          case Right(_)  => Future.successful(Ok("Quote was changed"))
          case Left(err) => Future.successful(BadRequest(err.getMessage))
        }
      )
  }

  def addQuote = Action.async(parse.json) { implicit request =>
    request.body
      .validate[QuoteAddFormat]
      .fold(
        _ => {
          Future.successful(BadRequest("Invalid quote data format"))
        },
        service.addQuote(_).flatMap {
          case Right(_)  => Future.successful(Ok("Quote was added"))
          case Left(err) => Future.successful(BadRequest(err.getMessage))
        }
      )
  }
}
