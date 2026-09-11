package com.packs.sharedlib;

import org.slf4j.MDC;

public record ApiError(String code, String message, Object details, String requestId) {

	public static ApiError of(String code, String message, Object details) {
		return new ApiError(code, message, details, MDC.get(TraceIdFilter.MDC_KEY));
	}
}