package io.getquill

import cats.effect.{IO, Resource}
import com.twitter.finagle.Postgres
import com.twitter.finagle.postgres.PostgresClientImpl
import com.twitter.util.Await
import com.typesafe.config.Config
import weaver.*

class PgClient(override val config: Config)
    extends FinaglePostgresContextConfig(config)

object Utils {
  def getClient: IO[PostgresClientImpl] =
    getConcurrentClient(maxConcurrency = 1)

  def getConcurrentClient(maxConcurrency: Int): IO[PostgresClientImpl] = IO
    .fromOption(for {
      hostPort <- sys.env.get("PG_HOST_PORT")
      user <- sys.env.get("PG_USER")
      password = sys.env.get("PG_PASSWORD")
      dbname <- sys.env.get("PG_DBNAME")
      useSsl = sys.env.getOrElse("USE_PG_SSL", "0") == "1"
      sslHost = sys.env.get("PG_SSL_HOST")
    } yield {
      val client = Postgres
        .Client()
        .withCredentials(user, password)
        .database(dbname)
        .withSessionPool
        .maxSize(maxConcurrency)
        .conditionally(
          useSsl,
          c => sslHost.fold(c.withTransport.tls)(c.withTransport.tls)
        )
        .newRichClient(hostPort)

      IO.blocking {
        while (!client.isAvailable) {}
        client
      }
    })(new Exception("Cant has confgs"))
    .flatten
}

object Test extends MutableTwitterFutureSuite {
  case class TableTest(id: Int, name: Option[String])

  override type Res = FinaglePostgresContext[SnakeCase.type]
  override def sharedResource: Resource[IO, Res] =
    Resource.make {
      Utils.getClient.map { client =>
        val ctx = new FinaglePostgresContext[SnakeCase.type](SnakeCase, client)
        val a = {
          import ctx.*
          for {
            _ <- ctx.run(sql"DROP TABLE IF EXISTS table_test".as[Insert[Unit]])
            _ <- ctx.run(
              sql"CREATE TABLE table_test (id integer, name text)"
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
        _ <- ctx.run(query[TableTest].insertValue(TableTest(0, Some("hola"))))
        result <- ctx.run(query[TableTest].filter(_.id == 0))
      } yield result
    }
    results.map(res => expect(res.head == TableTest(0, Some("hola"))))
  }
}
