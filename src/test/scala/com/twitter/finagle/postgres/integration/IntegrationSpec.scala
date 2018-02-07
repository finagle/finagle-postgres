package com.twitter.finagle.postgres.integration

import java.sql.Timestamp
import java.time.Instant

import com.twitter.finagle.Postgres
import com.twitter.finagle.Status
import com.twitter.finagle.postgres._
import com.twitter.finagle.postgres.codec.ServerError
import com.twitter.finagle.postgres.messages.Listener
import com.twitter.util.Await
import com.twitter.util.Duration
import com.twitter.util.Future
import com.twitter.util.Try

object IntegrationSpec {
  val pgTestTable = "finagle_test"
}

/*
 * Note: For these to work, you need to have:
 *
 * (1) An environment variable PG_HOST_PORT which specifies "host:port" of the test server
 * (2) Environment variables PG_USER and optionally PG_PASSWORD which specify the username and password to the server
 * (3) An environment variable PG_DBNAME which specifies the test database
 *
 * If these are conditions are met, the integration tests will be run.
 *
 * The tests can be run with SSL by also setting the USE_PG_SSL variable to "1".
 *
 */
class IntegrationSpec extends Spec {

  for {
    hostPort <- sys.env.get("PG_HOST_PORT")
    user <- sys.env.get("PG_USER")
    password = sys.env.get("PG_PASSWORD")
    dbname <- sys.env.get("PG_DBNAME")
    useSsl = sys.env.getOrElse("USE_PG_SSL", "0") == "1"
  } yield {


    val queryTimeout = Duration.fromSeconds(2)

    def getClient: PostgresClientImpl = {
      val client = Postgres.Client()
        .withCredentials(user, password)
        .database(dbname)
        .withSessionPool.maxSize(1)
        .conditionally(useSsl, _.withTransport.tlsWithoutValidation)
        .newRichClient(hostPort)

      Await.result(Future[PostgresClientImpl] {
        while (!client.isAvailable) {}
        client
      })
    }

    def getBadClient = {
      Postgres.Client()
        .withCredentials(user, password)
        .database(dbname)
        .withSessionPool.maxSize(1)
        .conditionally(useSsl, _.withTransport.tlsWithoutValidation)
        .newRichClient("badhost:5432")
    }

    def cleanDb(client: PostgresClient): Unit = {
      val dropQuery = client.executeUpdate("DROP TABLE IF EXISTS %s".format(IntegrationSpec.pgTestTable))
      val response = Await.result(dropQuery, queryTimeout)

      response must equal(OK(1))

      val createTableQuery = client.executeUpdate(
        """
          |CREATE TABLE %s (
          | str_field VARCHAR(40),
          | int_field INT,
          | double_field DOUBLE PRECISION,
          | timestamp_field TIMESTAMP WITH TIME ZONE,
          | bool_field BOOLEAN
          |)
        """.stripMargin.format(IntegrationSpec.pgTestTable))
      val response2 = Await.result(createTableQuery, queryTimeout)
      response2 must equal(OK(1))

      Try(Await.result(client.execute("create extension hstore"))) // enable hstore type
    }

    def insertSampleData(client: PostgresClient): Unit = {
      val insertDataQuery = client.executeUpdate(
        """
          |INSERT INTO %s VALUES
          | ('hello', 1234, 10.5, '2015-01-08 11:55:12-0800', TRUE),
          | ('hello', 5557, -4.51, '2015-01-08 12:55:12-0800', TRUE),
          | ('hello', 7787, -42.51, '2013-12-24 07:01:00-0800', FALSE),
          | ('goodbye', 4567, 15.8, '2015-01-09 16:55:12+0500', FALSE)
        """.stripMargin.format(IntegrationSpec.pgTestTable))

      val response = Await.result(insertDataQuery, queryTimeout)

      response must equal(OK(4))
    }

    "A postgres client" should {
      "insert and select rows" in {
        val client = getClient
        cleanDb(client)
        insertSampleData(client)

        val selectQuery = client.select(
          "SELECT * FROM %s WHERE str_field='hello' ORDER BY timestamp_field".format(IntegrationSpec.pgTestTable)
        )(
          identity)

        val resultRows = Await.result(selectQuery, queryTimeout)

        resultRows.size must equal(3)

        // Spot check the first row
        val firstRow = resultRows.head

        firstRow.getOption[String]("str_field") must equal(Some("hello"))
        firstRow.getOption[Int]("int_field") must equal(Some(7787))
        firstRow.getOption[Double]("double_field") must equal(Some(-42.51))
        firstRow.getOption[Instant]("timestamp_field") must equal(Some(
          new Timestamp(1387897260000L).toInstant
        ))
        firstRow.getOption[Boolean]("bool_field") must equal(Some(false))
        firstRow.getOption[String]("bad_column") must equal(None)


      }

      "execute a select that returns nothing" in {
        val client = getClient
        cleanDb(client)

        insertSampleData(client)

        val
        selectQuery = client.select(
          "SELECT * FROM %s WHERE str_field='xxxx' ORDER BY timestamp_field".
            format(

              IntegrationSpec.pgTestTable)
        )(identity)

        val

        resultRows = Await.result(
          selectQuery, queryTimeout)
        resultRows.size must equal(0)
      }


      "update a row" in {
        val client = getClient
        cleanDb(client)
        insertSampleData(client)

        val updateQuery = client.executeUpdate(

          "UPDATE %s SET str_field='hello_updated' where int_field=4567".format(IntegrationSpec.pgTestTable)
        )

        val response = Await.

          result(updateQuery, queryTimeout)

        response must equal(OK(1))

        val selectQuery = client.select(

          "SELECT * FROM %s WHERE str_field='hello_updated'".format(IntegrationSpec.pgTestTable)
        )(

          identity)

        val
        resultRows = Await.result(selectQuery, queryTimeout)

        resultRows.size must equal(1)
        resultRows.head.getOption[String]("str_field") must equal(Some("hello_updated"))
      }


      "delete rows" in {
        val client = getClient
        cleanDb(client)
        insertSampleData(client)

        val updateQuery = client.executeUpdate(
          "DELETE FROM %s WHERE str_field='hello'"
            .format(IntegrationSpec.pgTestTable)
        )

        val response = Await.result(updateQuery, queryTimeout)

        response must equal(OK(3))

        val selectQuery = client.select(
          "SELECT * FROM %s".format(IntegrationSpec.pgTestTable)
        )(identity)

        val resultRows = Await.result(selectQuery, queryTimeout)

        resultRows.size must equal(1)
        resultRows.head.getOption[String]("str_field") must equal(Some("goodbye"))
      }


      "select rows via a prepared query" in {
        val client = getClient
        cleanDb(client)
        insertSampleData(client)

        val preparedQuery = client.prepareAndQuery(
          "SELECT * FROM %s WHERE str_field=$1 AND bool_field=$2".format(IntegrationSpec.pgTestTable),
          "hello",
          true)(identity)

        val resultRows = Await.result(
          preparedQuery,
          queryTimeout
        )

        resultRows.size must equal(2)
        resultRows.foreach {
          row =>
            row.getOption[String]("str_field") must equal(Some("hello"))
            row.getOption[Boolean]("bool_field") must equal(Some(true))
        }
      }

      "execute an update via a prepared statement" in {
        val client = getClient
        cleanDb(client)
        insertSampleData(client)

        val preparedQuery = client.prepareAndExecute(
          "UPDATE %s SET str_field = $1 where int_field = 4567".format(IntegrationSpec.pgTestTable),
          "hello_updated"
        )

        val numRows = Await.result(preparedQuery)

        val resultRows = Await.result(client.select(
          "SELECT * from %s WHERE str_field = 'hello_updated' AND int_field = 4567".format(IntegrationSpec.pgTestTable)
        )(identity))

        resultRows.size must equal(numRows)
      }

      "execute an update via a prepared statement using a Some(value)" in {
        val client = getClient
        cleanDb(client)
        insertSampleData(client)


        val preparedQuery = client.prepareAndExecute(
          "UPDATE %s SET str_field = $1 where int_field = 4567".format(IntegrationSpec.pgTestTable),
          Some("hello_updated_some")
        )

        val numRows = Await.result(preparedQuery)

        val resultRows = Await.result(client.select(
          "SELECT * from %s WHERE str_field = 'hello_updated_some' AND int_field = 4567".format(IntegrationSpec.pgTestTable)
        )(identity))

        resultRows.size must equal(numRows)
      }

      "execute an update via a prepared statement using a None" in {
        val client = getClient
        cleanDb(client)
        insertSampleData(client)


        val preparedQuery = client.prepareAndExecute(
          "UPDATE %s SET str_field = $1 where int_field = 4567".format(IntegrationSpec.pgTestTable),
          None: Option[String]
        )

        val numRows = Await.result(preparedQuery)

        val resultRows = Await.result(client.select(
          "SELECT * from %s WHERE str_field IS NULL AND int_field = 4567".format(IntegrationSpec.pgTestTable)
        )(identity))

        resultRows.size must equal(numRows)
      }

      "return rows from UPDATE...RETURNING" in {
        val client = getClient
        cleanDb(client)
        insertSampleData(client)


        val preparedQuery = client.prepareAndQuery(
          "UPDATE %s SET str_field = $1 where int_field = 4567 RETURNING *".format(IntegrationSpec.pgTestTable),
          "hello_updated"
        )(identity)

        val resultRows = Await.result(preparedQuery)

        resultRows.size must equal(1)
        resultRows.head.get[String]("str_field") must equal("hello_updated")
      }

      "return rows from DELETE...RETURNING" in {
        val client = getClient
        cleanDb(client)
        insertSampleData(client)

        Await.result(client.prepareAndExecute(
          s"""INSERT INTO ${IntegrationSpec.pgTestTable}
              VALUES ('delete', 9012, 15.8, '2015-01-09 16:55:12+0500', FALSE)"""
        ))

        val preparedQuery = client.prepareAndQuery(
          "DELETE FROM %s where int_field = 9012 RETURNING *".format(IntegrationSpec.pgTestTable)
        )(identity)

        val resultRows = Await.result(preparedQuery)

        resultRows.size must equal(1)
        resultRows.head.get[String]("str_field") must equal("delete")
      }


      "execute an UPDATE...RETURNING that updates nothing" in {
        val client = getClient
        cleanDb(client)
        insertSampleData(client)
        val preparedQuery = client.prepareAndQuery(
          "UPDATE %s SET str_field = $1 where str_field = $2 RETURNING *".format(IntegrationSpec.pgTestTable),
          "hello_updated",
          "xxxx"
        )(identity)

        val resultRows = Await.result(preparedQuery)

        resultRows.size must equal(0)
      }

      "execute a DELETE...RETURNING that deletes nothing" in {

        val client = getClient
        cleanDb(client)
        insertSampleData(client)

        val preparedQuery = client.prepareAndQuery(
          "DELETE FROM %s WHERE str_field=$1".format(IntegrationSpec.pgTestTable),
          "xxxx"
        )(identity)

        val resultRows = Await.result(preparedQuery)
        resultRows.size must equal(0)
      }

      // this test will fail if the test DB user doesn't have permission
      "create an extension using CREATE EXTENSION" in {
        if (user == "postgres") {
          val client = getClient
          val result = client.prepareAndExecute("CREATE EXTENSION IF NOT EXISTS hstore")
          Await.result(result)
        }
      }

      "support multi-statement DDL" in {
        val client = getClient
        val result = client.query(
          """
            |CREATE TABLE multi_one(id integer);
            |CREATE TABLE multi_two(id integer);
            |DROP TABLE multi_one;
            |DROP TABLE multi_two;
          """.stripMargin)
        Await.result(result)
      }


      "throw a ServerError" when {
        "query has error" in {
          val client = getClient
          cleanDb(client)

          val selectQuery = client.select(
            "SELECT * FROM %s WHERE unknown_column='hello_updated'".format(IntegrationSpec.pgTestTable)
          )(identity)

          a[ServerError] must be thrownBy {
            Await.result(selectQuery, queryTimeout)
          }
        }

        "query in a prepared statement has an error" in {
          val client = getClient
          a[ServerError] must be thrownBy {
            Await.result(client.prepareAndQuery("Garbage query")(identity))
          }
        }

        "prepared query is missing parameters" in {

          val client = getClient
          cleanDb(client)

          val preparedQuery = client.prepareAndQuery(
            "SELECT * FROM %s WHERE str_field=$1 AND bool_field=$2".format(IntegrationSpec.pgTestTable),
            "hello"
          )(identity)

          a[ServerError] must be thrownBy {
            Await.result(
              preparedQuery,
              queryTimeout
            )
          }

        }
      }

      "return correct availability information" when {
        "client is good" in {
          val client: PostgresClient = getClient
          client.isAvailable must equal(true)
          client.status must equal(Status.Open)
        }
        "client is bad" in {
          val badClient: PostgresClient = getBadClient
          badClient.isAvailable must equal(false)
          Set(Status.Busy, Status.Closed) must contain(badClient.status)
        }
        "client is closed" in {
          val client: PostgresClient = getClient
          client.close()
          client.isAvailable must equal(false)
          client.status must equal(Status.Closed)
        }
      }

      "support notifications" when {
        val client = getClient
        val channel = "foo"
        val payload = "my payload"
        val notify = () => {
          val q = client.select(
            s"SELECT pg_notify('$channel', '$payload');"
          )(identity)
          Await.result(q, queryTimeout)
          Thread.sleep(200)
        }

        "listening to notified channel receive message before stopping" in {
          var received = false
          val Listener(stream, _) = Await.result(client.listen(channel))
          stream.take(1).toSeq().map(_.foreach(not => {
            not.channel must equal(channel)
            not.payload must equal(payload)
            received = true
          }))

          notify()
          if (!received) fail("notification not received")
        }

        "listening to another channel do not receive the message" in {
          var received = false
          val Listener(stream, _) = Await.result(client.listen("notvalid"))
          stream.foreach(_ => received = true)
          notify()
          received must equal(false)
        }

        "the client stops listening no messages are received" in {
          val Listener(stream, stop) = Await.result(client.listen(channel))
          var stopped = true
          stream.foreach(_ => stopped = false)
          stop()
          notify()
          stopped must equal(true)
        }
      }
    }
  }
}
