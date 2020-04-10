package com.twitter.finagle.postgres

import com.twitter.finagle.Postgres
import com.twitter.util.Try
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import org.scalatest.BeforeAndAfterAll

abstract class EmbeddedPgSqlSpec extends Spec with BeforeAndAfterAll {

  var embeddedPgSql: Option[EmbeddedPostgres] = None

  final val TestDbUser = "postgres"
  final val TestDbPassword: Option[String] = None
  final val TestDbName = "finagle_postgres_test"

  def configure(b: EmbeddedPostgres.Builder): EmbeddedPostgres.Builder = b

  private[this] def using[C <: AutoCloseable, T](c: => C)(f: C => T): T =
    Try(f(c)).ensure(c.close()).get

  // NOTE: this replicates what .travis.yml does.
  private[this] def prep(psql: EmbeddedPostgres): EmbeddedPostgres = {
    using(psql.getPostgresDatabase.getConnection()) { conn =>
      using(conn.createStatement()) { stmt =>
        stmt.execute(s"CREATE DATABASE $TestDbName")
      }
    }
    using(psql.getDatabase(TestDbUser, TestDbName).getConnection()) { conn =>
      using(conn.createStatement()) { stmt =>
        stmt.execute("CREATE EXTENSION IF NOT EXISTS hstore")
        stmt.execute("CREATE EXTENSION IF NOT EXISTS citext")
      }
    }
    psql
  }

  def getClient: PostgresClientImpl = embeddedPgSql match {
    case None => sys.error("getClient invoked outside of test fragment")
    case Some(pgsql) =>
      val client = Postgres.Client()
        .withCredentials(TestDbUser, TestDbPassword)
        .database(TestDbName)
        .withSessionPool.maxSize(1)
        // TODO
//        .conditionally(useSsl, c => sslHost.fold(c.withTransport.tls)(c.withTransport.tls(_)))
        .newRichClient(s"localhost:${pgsql.getPort}")

      while (!client.isAvailable) {}
      client
  }

  override def beforeAll(): Unit = {
    val builder =
      EmbeddedPostgres.builder()
        .setCleanDataDirectory(true)
        .setErrorRedirector(ProcessBuilder.Redirect.INHERIT)
        .setOutputRedirector(ProcessBuilder.Redirect.INHERIT)

    embeddedPgSql = Some(prep(configure(builder).start()))
  }

  override def afterAll(): Unit = embeddedPgSql.foreach(_.close())

}
