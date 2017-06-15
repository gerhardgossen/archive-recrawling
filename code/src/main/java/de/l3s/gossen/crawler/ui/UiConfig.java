package de.l3s.gossen.crawler.ui;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@Configuration
@ComponentScan
public class UiConfig {

    @ResponseStatus(HttpStatus.NOT_FOUND)
    public static class ResourceNotFoundException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    
        public ResourceNotFoundException(String path) {
            super("Not found: " + path);
        }
    }

    @Component
    public static class RequestMappingHandlerMappingPostProcessor implements BeanPostProcessor {
    
        @Override
        public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
            if (bean instanceof RequestMappingHandlerMapping) {
                process((RequestMappingHandlerMapping) bean);
            }
            return bean;
        }
    
        private void process(RequestMappingHandlerMapping rmhm) {
            rmhm.setUseSuffixPatternMatch(false);
        }
    
        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
            if (bean instanceof RequestMappingHandlerMapping) {
                process((RequestMappingHandlerMapping) bean);
            }
            return bean;
        }
    }

}
