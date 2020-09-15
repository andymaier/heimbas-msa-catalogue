package de.predi8.catalogue.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.predi8.catalogue.error.NotFoundException;
import de.predi8.catalogue.event.Operation;
import de.predi8.catalogue.model.Article;
import de.predi8.catalogue.repository.ArticleRepository;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.servlet.http.HttpServletRequest;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/articles")
public class CatalogueRestController {

	public static final String PRICE = "price";
	public static final String NAME = "name";

	private ArticleRepository repo;
	private KafkaTemplate<String, Operation> kafka;
	final private ObjectMapper mapper;

	public CatalogueRestController(ArticleRepository repo, KafkaTemplate<String, Operation> kafka, ObjectMapper mapper) {
		this.repo = repo;
		this.kafka = kafka;
		this.mapper = mapper;
	}

	@GetMapping
	public List<Article> index() {
		return repo.findAll();
	}

	@GetMapping("/count")
	public long count() {
		return repo.count();
	}

	public Article get(String id) {
		return repo.findById(id).orElseThrow(NotFoundException::new);
	}

	@GetMapping("/{id}")
	public Article getById(@PathVariable String id) {
		return get(id);
	}

	@PostMapping
	public ResponseEntity<Article> create(@RequestBody Article article, UriComponentsBuilder builder, HttpServletRequest request) {
		String uuid = UUID.randomUUID().toString();
		article.setUuid(uuid);

		System.out.println("article = " + article);

		//Article a = repo.save(article);

		Operation op = new Operation("article", "upsert", mapper.valueToTree(article));
		kafka.send(new ProducerRecord<>("shop", op));

		return ResponseEntity.accepted().build();
	}

	@DeleteMapping("/{id}")
	public void deleteArticle(@PathVariable String id) {
		Article article = get(id);
		//repo.delete(article);
		Operation op = new Operation("article", "remove", mapper.valueToTree(article));
		kafka.send(new ProducerRecord<>("shop", op));
	}

	@PutMapping("/{id}")
	public void change(@PathVariable String id, @RequestBody Article article) {
		get(id);
		article.setUuid(id);
		//repo.save(article);
		Operation op = new Operation("article", "upsert", mapper.valueToTree(article));
		kafka.send(new ProducerRecord<>("shop", op));
	}

	@PatchMapping("/{id}")
	public ResponseEntity<Article> patch(@PathVariable String id, @RequestBody JsonNode json, UriComponentsBuilder builder) {
		Article old = get(id);

		if(json.has(PRICE)) {
			if(json.hasNonNull(PRICE)) {
				old.setPrice(new BigDecimal(json.get(PRICE).asDouble()));
			}
		}
		if(json.has(NAME)) {
			old.setName(json.get(NAME).asText());
		}

		//Article obj = repo.save(old);
		Operation op = new Operation("article", "upsert", mapper.valueToTree(old));
		kafka.send(new ProducerRecord<>("shop", op));

		return ResponseEntity.accepted().build();
	}
}