package kz.kaspi.lab.fileuploader.config;

import kz.kaspi.lab.fileuploader.controller.FileUploadHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration
public class RouterConfig {

    @Bean
    public RouterFunction<ServerResponse> uploadRoutes(FileUploadHandler handler) {
        return route(POST("/api/v1/upload"), handler::uploadFile);
    }
}