package com.fortysevendeg.exercises.services

import com.fortysevendeg.exercises.models.UserStore
import com.fortysevendeg.exercises.services.messages._

import scala.concurrent.{ Future, ExecutionContext }

trait UserServices {

  def getUserOrCreate(request: GetUserOrCreateRequest): Future[GetUserOrCreateResponse]

  def getUserByLogin(request: GetUserByLoginRequest): Future[GetUserByLoginResponse]

  def createUser(request: CreateUserRequest): Future[CreateUserResponse]

}

class UserServiceImpl(userStore: UserStore)(implicit val executionContext: ExecutionContext) extends UserServices {

  override def getUserOrCreate(request: GetUserOrCreateRequest): Future[GetUserOrCreateResponse] =

    getUserByLogin(GetUserByLoginRequest(request.login))
      .flatMap {
        _.user
          .map(u ⇒ Future.successful(u))
          .getOrElse(
            createUser(CreateUserRequest(
              login = request.login,
              name = request.name,
              github_id = request.github_id,
              picture_url = request.picture_url,
              github_url = request.github_url,
              email = request.email
            ))
              .map(_.user)
          )
          .map(GetUserOrCreateResponse)
      }

  override def getUserByLogin(request: GetUserByLoginRequest): Future[GetUserByLoginResponse] = {

    val result = for {
      user ← userStore.getByLogin(login = request.login)
    } yield GetUserByLoginResponse(user = user.headOption)

    result recover {
      case e ⇒
        throw new Exception(s"User fetching error: ${e.getMessage}")
    }
  }

  override def createUser(request: CreateUserRequest): Future[CreateUserResponse] = {

    val result = for {
      user ← userStore.create(
        login = request.login,
        name = request.name,
        github_id = request.github_id,
        picture_url = request.picture_url,
        github_url = request.github_url,
        email = request.email
      )
    } yield CreateUserResponse(user = user)

    result recover {
      case e ⇒
        throw new Exception(s"User creation error: ${e.getMessage}")
    }
  }

}
