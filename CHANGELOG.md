# Change Log
All notable changes to this project will be documented in this file.

This project adheres to [Semantic Versioning](http://semver.org/). Note that Semantic Versioning is not
necessarily followed during pre-1.0 development.

## <Next release>

* Select results are now exposed as `AsyncStream[DataRow]` and result sets as `AsyncStream[Row]`
* incompatible change: `PostgresClient.select` now returns `Future[AsyncStream[T]]` instead of `Future[Seq[T]]`
  * use `PostgresClient.selectToSeq` for the buffering behaviour

## 0.8.2
* Fix SSL session verification.
* Fix #75 - Name resolution failed

## 0.8.0
* Don't include password in stack registry.
* Add JSON in default types.
* Fix unsafe ChannelBuffer sharing for Nones in ValueEncoder.
* Prepend class name to connection.

## 0.7.0
* Added value encoder/decoder for `JSONB`
* Update to Finagle 18.2.0

## 0.6.0
* Updated to Finagle 18.1.0

## 0.5.0
* Update to Finagle 17.11.0
* Update to SBT 1.1.0
* Updated dependencies

## 0.4.2
* Added support for Scala 2.12
* Updated dependencies

## 0.4.1
* Added dependency of finagle-postgres-shapeless on patchless
* Support `Updates` quoting from patchless `Patch` values

## 0.4.0
* Breaking API changes:
  * There is no more `Value` wrapper
  * Decoding a column value is based on the requested type - this means that there is no more `row.get(Int)` method
    (without type arguments). Instead, there is `getAnyOption` which returns an `Option[Any]` of the default type for
    that column's OID.
* Added a new subproject `finagle-postgres-shapeless`, which supports shapeless for boilerplate elimination
* Added a new query DSL under the `finagle-postgres-shapeless` module
* Started an effort for some decent documentation

## 0.3.2
* Added a default monitor which handles most ServerErrors
* Updated response classifier and retry policy to look at SQLSTATE
* Added value encoder/decoder for `Instant`

### 0.3.1
* Converted to Stack-based client
* Service is properly closed on channel disconnect

### 0.2.2
* Update to Finagle 6.39.0
* Fix issue when an error during extended query flow causes connection hangs

### 0.2.0
* Rewrite of encoding and decoding mechanism
* Uses `Param` to encode params, with an implicit conversion available for many types
* Provides binary protocol support for parameters and results
* Changes `timestamp` and `timestamptz` to return as `java.time.LocalDateTime` and `java.time.ZonedDateTime` respectively
* Provides support for `numeric` as `BigDecimal`
* Provides many other native type conversions

### 0.1.0
* SSL support
* Manual transactions support

### 0.0.2
* Prepared statements support
* Async responses logging
* Exceptions handling
* Create and drop table support

### 0.0.1
* Clear-text authentication support
* MD5 authentication support
* Select, update, insert, and delete queries

