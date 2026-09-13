package com.packs.orderservice.config;

import com.packs.orderservice.repository.IdempotencyRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class IdempotencyCleanupJob {

	private static final Logger log = LoggerFactory.getLogger(IdempotencyCleanupJob.class);

	private final IdempotencyRecordRepository idempotencyRecordRepository;

	public IdempotencyCleanupJob(IdempotencyRecordRepository idempotencyRecordRepository) {
		this.idempotencyRecordRepository = idempotencyRecordRepository;
	}

	@Scheduled(fixedDelayString = "${app.idempotency.cleanup-interval-ms:3600000}")
	@Transactional
	public void purgeExpiredRecords() {
		long purged = idempotencyRecordRepository.deleteByExpiresAtBefore(Instant.now());
		if (purged > 0) {
			log.info("Purged {} expired idempotency records", purged);
		}
	}
}