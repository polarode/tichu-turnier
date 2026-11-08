package tichu.infra.rest.web

import jakarta.servlet.http.HttpServletRequest

/**
 * The client address feeding the rate limiter. Behind the TLS-terminating
 * proxy, the right-most `X-Forwarded-For` entry is the one our own
 * trusted proxy appended — the left-most entries are client-controlled and
 * would let an attacker rotate spoofed IPs to dodge the limiter. Without the
 * header, fall back to the direct remote address.
 */
internal fun sourceIpOf(request: HttpServletRequest): String? =
    request.getHeader("X-Forwarded-For")?.substringAfterLast(',')?.trim()?.takeIf { it.isNotEmpty() }
        ?: request.remoteAddr?.takeIf { it.isNotEmpty() }
