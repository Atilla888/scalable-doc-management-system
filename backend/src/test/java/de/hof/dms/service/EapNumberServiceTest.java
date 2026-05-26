package de.hof.dms.service;

import de.hof.dms.exception.ApiException;
import de.hof.dms.mongo.LocalMongoSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Year;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@DataMongoTest
@Import(EapNumberService.class)
@org.springframework.test.context.ActiveProfiles("default")
class EapNumberServiceTest {

    @DynamicPropertySource
    static void registerLocalMongo(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> LocalMongoSupport.LOCAL_URI);
    }

    @BeforeAll
    static void requireComposeMongo() {
        assumeTrue(
                LocalMongoSupport.isAvailable(),
                () -> "Start Compose MongoDB: cd infra/docker-compose && docker compose up -d mongodb");
    }

    @Autowired
    private EapNumberService eapNumberService;

    @Autowired
    private MongoTemplate mongoTemplate;

    private String testCategory;

    @BeforeEach
    void resetCategorySequence() {
        testCategory = "8" + UUID.randomUUID().toString().replaceAll("\\D", "").substring(0, 3);
        mongoTemplate.remove(
                Query.query(Criteria.where("category").is(testCategory)), "eap_sequences");
    }

    @Test
    void generateNextProducesValidFormat() {
        String category = "42" + UUID.randomUUID().toString().replaceAll("\\D", "").substring(0, 4);
        String eap = eapNumberService.generateNext(category, "ITDLZ");

        assertThat(eap).matches(EapNumberService.EAP_FORMAT);
        assertThat(eap).startsWith(category + "-ITDLZ-" + Year.now().getValue() + "-");
    }

    @Test
    void validateFormatRejectsInvalidNumbers() {
        assertThatThrownBy(() -> eapNumberService.validateFormat("not-an-eap"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Invalid EAP number format");
    }

    @Test
    void validateCategoryRejectsInvalidCategory() {
        assertThatThrownBy(() -> eapNumberService.validateCategory("ABC"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("eapCategory");
    }

    @Test
    void concurrentIncrementsProduceUniqueSequences() throws Exception {
        int threads = 12;
        Set<String> generated = new HashSet<>();
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            var futures =
                    java.util.stream.IntStream.range(0, threads)
                            .mapToObj(
                                    i ->
                                            pool.submit(
                                                    () -> {
                                                        ready.countDown();
                                                        start.await(10, TimeUnit.SECONDS);
                                                        return eapNumberService.generateNext(
                                                                testCategory, "ITDLZ");
                                                    }))
                            .toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<String> future : futures) {
                generated.add(future.get(30, TimeUnit.SECONDS));
            }
        }

        assertThat(generated).hasSize(threads);
        Set<Long> sequences = new HashSet<>();
        for (String eap : generated) {
            assertThat(eap).matches(EapNumberService.EAP_FORMAT);
            String seqPart = eap.substring(eap.lastIndexOf('-') + 1);
            sequences.add(Long.parseLong(seqPart));
        }
        assertThat(sequences).hasSize(threads);
    }
}
