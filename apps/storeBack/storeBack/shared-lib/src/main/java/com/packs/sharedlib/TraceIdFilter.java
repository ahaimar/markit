package com.packs.sharedlib;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.UUID;

public class TraceIdFilter extends OncePerRequestFilter implements Ordered {

	public static final String REQUEST_ID_HEADER = "X-Request-Id";
	public static final String MDC_KEY = "requestId";

	private static final int ORDER = -102;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
		throws ServletException, IOException {
		String incoming = request.getHeader(REQUEST_ID_HEADER);
		String requestId = (incoming != null && !incoming.isBlank()) ? incoming : UUID.randomUUID().toString();

		MDC.put(MDC_KEY, requestId);
		response.setHeader(REQUEST_ID_HEADER, requestId);
		try {
			filterChain.doFilter(request, response);
		} finally {
			MDC.remove(MDC_KEY);
		}
	}

	@Override
	public int getOrder() {
		return ORDER;
	}
}