package tichu.domain

private val BCRYPT_SHAPE = Regex("""^\$2[aby]?\$\d{2}\$[./A-Za-z0-9]{53}$""")

/**
 * Shape check for a bcrypt hash: `$2a$`, `$2b$` or `$2y$` (or the bare `$2$`), a two-digit
 * cost and 53 characters of the bcrypt alphabet, 60 characters in total. It does not verify
 * the hash, only that it is one — a truncated value such as `$2a$12` is rejected before it
 * can be stored as an operator's credential.
 */
fun isBcryptHash(value: String): Boolean = BCRYPT_SHAPE.matches(value)
