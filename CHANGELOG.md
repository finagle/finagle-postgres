# Change Log
All notable changes to this project will be documented in this file.

This project adheres to [Semantic Versioning](http://semver.org/). Note that Semantic Versioning is not
necessarily followed during pre-1.0 development.

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

