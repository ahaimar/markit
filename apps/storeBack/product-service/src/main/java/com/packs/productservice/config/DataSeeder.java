package com.packs.productservice.config;

import com.packs.productservice.entity.Product;
import com.packs.productservice.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Component
public class DataSeeder implements CommandLineRunner {

	private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

	private final ProductRepository productRepository;

	public DataSeeder(ProductRepository productRepository) {
		this.productRepository = productRepository;
	}

	@Override
	@Transactional
	public void run(String... args) {
		if (productRepository.count() > 0) {
			return;
		}

		seat(productRepository, "Coffee Beans - Ethiopia", "Single origin, medium roast.", "100.00", "Coffee", 40);
		seat(productRepository, "Ceramic Mug", "Hand-thrown stoneware mug, 350ml.", "18.50", "Mugs", 25);
		seat(productRepository, "Pour-Over Kettle", "Gooseneck kettle with thermometer.", "65.00", "Brewing", 12);
		seat(productRepository, "Paper Filters x100", "V60 compatible filters.", "7.99", "Brewing", 80);
		seat(productRepository, "Coffee Grinder", "Burr grinder, 30 grind settings.", "89.00", "Brewing", 8);
		seat(productRepository, "Espresso Cups (Set of 2)", "Double-wall glass espresso cups.", "24.00", "Mugs", 15);

		log.info("Seeded {} demo products", productRepository.count());
	}

	private void seat(ProductRepository repository, String name, String description, String price, String category, int stock) {
		Product product = new Product();
		product.setName(name);
		product.setDescription(description);
		product.setPrice(new BigDecimal(price));
		product.setCategory(category);
		product.setStockQuantity(stock);
		product.setCreatedAt(Instant.now());
		product.setUpdatedAt(Instant.now());
		repository.save(product);
	}
}