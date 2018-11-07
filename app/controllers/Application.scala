package controllers

import javax.inject.Inject
import akka.actor.ActorSystem
import play.api.libs.json._
import play.api.mvc._
import play.api.Configuration
import sangria.execution._
import sangria.parser.{QueryParser, SyntaxError}
import sangria.marshalling.playJson._
import models.{ProductRepo, SchemaDefinition}
import sangria.renderer.SchemaRenderer
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future
import scala.util.{Failure, Success}

class Application @Inject() (system: ActorSystem, config: Configuration) extends InjectedController {

  def renderSchema = Action {
    Ok(SchemaRenderer.renderSchema(SchemaDefinition.MySchema))
  }

  def graphqlBody = Action.async(parse.json) { request =>
    val query = (request.body \ "query").as[String]
    val operation = (request.body \ "operationName").asOpt[String]

    val variables = (request.body \ "variables").toOption.flatMap {
      case JsString(vars) => Some(parseVariables(vars))
      case obj: JsObject => Some(obj)
      case _ => None
    }

    executeQuery(query, variables, operation)
  }

  def graphql(query: String, variables: Option[String], operation: Option[String]) = Action.async { request =>
    executeQuery(query, variables map parseVariables, operation)

  }

  private def parseVariables(variables: String) =
    if (variables.trim == "" || variables.trim == "null") Json.obj() else Json.parse(variables).as[JsObject]

  private def executeQuery(query: String, variables: Option[JsObject], operation: Option[String]) =
    QueryParser.parse(query) match {
      case Success(queryAst) =>
        Executor.execute(SchemaDefinition.MySchema, queryAst, new ProductRepo,
          operationName = operation,
          variables = variables getOrElse Json.obj())
        .map(Ok(_))

      case Failure(error: SyntaxError) =>
        Future.successful(BadRequest(Json.obj(
          "syntaxError" -> error.getMessage,
        "locations" -> Json.arr(Json.obj(
          "line" -> error.originalError.position.line,
          "column" -> error.originalError.position.column)))))

      case Failure(error) =>
        throw error

  }
}
