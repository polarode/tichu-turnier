package com.example.template.vocabulary

/** Thrown when a requested entity does not exist. Mapped to HTTP 404 in the REST layer. */
class NotFoundException(message: String) : RuntimeException(message)

/** Thrown when input fails a domain-level validation rule. Mapped to HTTP 400. */
class InvalidInputException(message: String) : RuntimeException(message)
