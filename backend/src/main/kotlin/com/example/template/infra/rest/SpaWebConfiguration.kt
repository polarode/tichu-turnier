package com.example.template.infra.rest

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.Resource
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.servlet.resource.PathResourceResolver

/**
 * Single-origin SPA serving (ADR 0002): static assets from `classpath:/static`,
 * with an `index.html` fallback for unmatched non-API paths so client routes
 * survive a refresh. The API namespace is excluded at the handler-mapping
 * level: an empty `/api` catch-all outranks the SPA handler below and serves
 * nothing, so unknown API paths yield a JSON ProblemDetail 404
 * (`NoResourceFoundException`), never the SPA.
 */
@Configuration
class SpaWebConfiguration {

    @Bean
    fun spaWebMvcConfigurer(): WebMvcConfigurer = object : WebMvcConfigurer {

        override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
            // Serves nothing: keeps unknown API paths on the JSON 404 contract.
            registry.addResourceHandler("/api/**").resourceChain(true)

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
