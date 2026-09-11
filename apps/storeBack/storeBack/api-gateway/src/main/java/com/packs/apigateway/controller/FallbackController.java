package com.packs.apigateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class FallbackController {

	@RequestMapping("/fallback")
	public ResponseEntity<Map<String, Object>> fallback() {
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
			.body(Map.of(
				"code", "ERR_DOWNSTREAM_UNAVAILABLE",
				"message", "Downstream service is currently unavailable",
				"details", null));
	}
}