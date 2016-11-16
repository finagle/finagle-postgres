package com.twitter.finagle.postgres.generic

trait ColumnNamer extends (String => String)

object ColumnNamer {
  implicit val default: ColumnNamer = snake.snake

  object snake {
    implicit object snake extends ColumnNamer {
      private val regex = """([a-z])([A-Z])""".r

      def apply(scalaName: String): String = regex.replaceAllIn(
        scalaName, m => m.group(1) + "_" + m.group(2).toLowerCase)
    }
  }

  object identity {
    implicit object identity extends ColumnNamer {
      def apply(scalaName: String): String = scalaName
    }
  }
}
