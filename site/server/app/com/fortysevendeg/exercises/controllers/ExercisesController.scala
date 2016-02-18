/*
 * scala-exercises-server
 * Copyright (C) 2015-2016 47 Degrees, LLC. <http://www.47deg.com>
 */

package com.fortysevendeg.exercises.controllers

import cats.data.Xor
import com.fortysevendeg.exercises.app._
import com.fortysevendeg.exercises.persistence.domain.SaveUserProgress
import com.fortysevendeg.exercises.services.free.{ UserOps, UserProgressOps }
import com.fortysevendeg.exercises.services.interpreters.ProdInterpreters
import com.fortysevendeg.exercises.utils.StringUtils.ExerciseType
import com.fortysevendeg.shared.free.ExerciseOps
import doobie.imports._
import play.api.libs.json.JsValue
import play.api.mvc.{ Action, BodyParsers, Controller }
import shared.{ ExerciseEvaluation, User }

import com.fortysevendeg.exercises.services.interpreters.FreeExtensions._

import scalaz.concurrent.Task

class ExercisesController(
    implicit
    exerciseOps:     ExerciseOps[ExercisesApp],
    userOps:         UserOps[ExercisesApp],
    userProgressOps: UserProgressOps[ExercisesApp],
    T:               Transactor[Task]
) extends Controller with JsonFormats with AuthenticationModule with ProdInterpreters {

  def evaluate(libraryName: String, sectionName: String): Action[JsValue] =
    AuthenticationAction(BodyParsers.parse.json) { request ⇒
      request.body.validate[ExerciseEvaluation] match {
        case JsSuccess(evaluation, _) ⇒

          userOps.getUserByLogin(request.userId).runTask match {
            case Xor.Right(Some(user)) ⇒
              val eval = for {
                exerciseEvaluation ← exerciseOps.evaluate(evaluation = evaluation)
                _ ← userProgressOps.saveUserProgress(
                  mkSaveProgressRequest(user.id, evaluation, exerciseEvaluation.isRight)
                )
              } yield exerciseEvaluation

        // this match expression is now a really bad joke
        eval.runTask match {
          case Xor.Right(response) ⇒ response match {
            case Xor.Right(result) ⇒ result match {
              case Xor.Right(value)    ⇒ Ok("Evaluation succeeded : " + value)
              case Xor.Left(codeError) ⇒ BadRequest("Runtime error : " + codeError.getMessage)
            }
            case Xor.Left(error) ⇒ BadRequest("Compilation error : " + error.getMessage)
          }
          case Xor.Left(error) ⇒ BadRequest("Evaluation failed : " + error)
        }
    }

  private[this] def mkSaveProgressRequest(userId: Long, evaluation: ExerciseEvaluation, success: Boolean) =
    new SaveUserProgress.Request(
      userId = userId,
      libraryName = evaluation.libraryName,
      sectionName = evaluation.sectionName,
      method = evaluation.method,
      version = evaluation.version,
      exerciseType = ExerciseType fromString evaluation.exerciseType,
      args = evaluation.args.headOption map (_ ⇒ evaluation.args.mkString("##")),
      succeeded = success
    )
}
