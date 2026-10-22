package com.example.template.infra.rest

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.Resource
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.servlet.resource.PathResourceResolver

/**
 * Single-origin SPA serving (ADR 0002): the API keeps its `/api` namespace under
 * controller mappings, and every other path is served from `classpath:/static` —
 * the built frontend. Unmatched non-API paths fall back to `index.html` so client
 * routes (e.g. admin deep links) survive a browser refresh.
 *
 * The namespace split lives in the handler mapping itself: the more specific
 * `/api/…` registration outranks the SPA catch-all within the same handler
 * mapping, so unknown API paths never reach the SPA resolver. That registration
 * serves nothing — its resolver rejects every request — which makes Spring throw
 * `NoResourceFoundException`, answered with a JSON ProblemDetail 404 by
 * `WebControllerAdvice`, never with `index.html`.
 */
@Configuration
class SpaWebConfiguration {

    @Bean
    fun spaWebMvcConfigurer(): WebMvcConfigurer = object : WebMvcConfigurer {

        override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
            // The API namespace has no static surface: an explicit catch-all for
            // `/api/**` outranks the SPA catch-all below and serves nothing, so
            // unknown API paths stay on the JSON ProblemDetail contract.
            registry.addResourceHandler("/api/**").resourceChain(true)

            // Everything else: static assets, with the SPA entry point as the
            // fallback for unmatched non-API paths (client routes on refresh).
            registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(object : PathResourceResolver() {
                    override fun getResource(resourcePath: String, location: Resource): Resource? {
                        val requested = location.createRelative(resourcePath)
                        return if (requested.exists() && requested.isReadable) requested
                        else location.createRelative("index.html")
                    }
                })
        }
    }
}
