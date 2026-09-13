package com.packs.notificationservice.config;

import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConsumerConfig {

	@Bean
	public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
		ConsumerFactory<?, ?> consumerFactory,
		KafkaTemplate<String, String> kafkaTemplate) {
		ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
		@SuppressWarnings("unchecked")
		ConsumerFactory<String, String> typedConsumerFactory = (ConsumerFactory<String, String>) consumerFactory;
		factory.setConsumerFactory(typedConsumerFactory);

		DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
			(record, ex) -> new TopicPartition(record.topic() + "-dlq", 0));

		DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3));
		factory.setCommonErrorHandler(errorHandler);
		return factory;
	}
}