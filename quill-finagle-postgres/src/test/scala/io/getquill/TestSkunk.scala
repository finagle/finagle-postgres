package io.getquill

import cats.effect.{IO, Resource}
import com.twitter.finagle.postgres.PostgresClient
import com.twitter.util.Await
import weaver.*
import skunk.*
import natchez.Trace.Implicits.noop

import scala.util.Try

object UtilsSkunk {
  def getClient: Option[PostgresClient] =
    for {
      host <- sys.env.get("PG_HOST")
      port = sys.env
        .get("PG_PORT")
        .flatMap { x =>
          Try { x.toInt }.toOption
        }
        .getOrElse(5432)
      user <- sys.env.get("PG_USER")
      password = sys.env.get("PG_PASSWORD")
      dbname <- sys.env.get("PG_DBNAME")
    } yield {
      new finagle_postgres.skunk.PostgresClientImpl(
        Session.single(
          host = host,
          port = port,
          user = user,
          database = dbname,
          password = password
        )
      )
    }
}

object TestSkunk extends MutableTwitterFutureSuite {
  case class TableTestSkunk(id: Int, name: String)

  override type Res = FinaglePostgresContext[SnakeCase.type]
  override def sharedResource: Resource[IO, Res] =
    Resource.make {
      IO.fromOption(UtilsSkunk.getClient)(new Exception()).map { client =>
        val ctx = new FinaglePostgresContext[SnakeCase.type](SnakeCase, client)
        val a = {
          import ctx.*
          for {
            _ <- ctx
              .run(sql"DROP TABLE IF EXISTS table_test_skunk".as[Insert[Unit]])
            _ <- ctx.run(
              sql"CREATE TABLE table_test_skunk (id integer, name text)"
                .as[Insert[Unit]]
            )
            _ <- ctx.run(
              sql"INSERT INTO table_test_skunk (id, name) VALUES (1, null)"
                .as[Insert[Unit]]
            )
          } yield ()
        }
        Await.result(a)
        ctx
      }
    } { client =>
      IO.blocking(client.close()).void
    }

  future("Can make insert and fetch") { ctx =>
    import ctx.*
    val results = ctx.transaction {
      for {
        _ <- ctx.run(
          query[TableTestSkunk].insertValue(TableTestSkunk(0, "hola"))
        )
        result0 <- ctx.run(query[TableTestSkunk].filter(_.id == 0))
        result1 <- ctx.run(query[TableTestSkunk].filter(_.id == 1))
        result2 <- ctx.run(query[TableTestSkunk])
      } yield Seq(result0, result1)
    }
    results.map { case Seq(result0, result1) =>
      expect(result0.contains(TableTestSkunk(0, "hola")))
      expect(result1.contains(TableTestSkunk(1, null)))
    }
  }
}
