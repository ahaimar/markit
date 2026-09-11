package com.packs.sharedlib;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class TraceIdAutoConfiguration {

	@Bean
	public FilterRegistrationBean<TraceIdFilter> traceIdFilterRegistration() {
		FilterRegistrationBean<TraceIdFilter> registration = new FilterRegistrationBean<>(new TraceIdFilter());
		registration.setName("traceIdFilter");
		registration.addUrlPatterns("/*");
		registration.setOrder(-102);
		return registration;
	}
}