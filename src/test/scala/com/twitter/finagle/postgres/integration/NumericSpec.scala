package com.twitter.finagle.postgres.integration

import com.twitter.finagle.Postgres
import com.twitter.finagle.postgres._
import com.twitter.util.{Await, Future}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

class NumericSpec extends Spec with ScalaCheckDrivenPropertyChecks {
  IntegrationSpec.clientBuilder().foreach { clientBuilder =>
    val binaryClient = clientBuilder
      .withBinaryParams(true)
      .withBinaryResults(true)
      .newRichClient()

    val textClient = clientBuilder
      .withBinaryParams(false)
      .withBinaryResults(false)
      .newRichClient()

    Await.result((textClient.query(
      """
        |DROP TABLE IF EXISTS numeric_test;
        |CREATE TABLE numeric_test(d DECIMAL NOT NULL);
      """.stripMargin)))

    def rowsOf(qr: QueryResponse): Future[Seq[Row]] = qr match {
      case OK(_) => Future.value(Seq.empty)
      case ResultSet(rs) => rs.toSeq
    }

    def testBinaryEncode(in: BigDecimal) = Await.result {
      for {
        _ <- binaryClient.execute("DELETE FROM numeric_test")
        _ <- binaryClient.prepareAndExecute("INSERT INTO numeric_test VALUES($1)", Param(in))
        r <- textClient.query("SELECT * FROM numeric_test")
        rows <- rowsOf(r)
      } yield rows.map(_.get[BigDecimal](0)) must equal(Seq(in))
    }

    def testBinaryDecode(in: BigDecimal) = Await.result {
      for {
        _ <- textClient.execute("DELETE FROM numeric_test")
        _ <- textClient.prepareAndExecute("INSERT INTO numeric_test VALUES($1)", Param(in))
        r <- binaryClient.query("SELECT * FROM numeric_test")
        rows <- rowsOf(r)
      } yield rows.map(_.get[BigDecimal](0)) must equal(Seq(in))
    }

    "Binary client" should {
      "encode decimal agree with text client" in forAll { in: BigDecimal =>
        testBinaryEncode(in)
      }
      "decode decimal agree with text client" in forAll { in: BigDecimal =>
        testBinaryDecode(in)
      }
    }

  }
}
