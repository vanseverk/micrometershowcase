package be.vanseverk.demo.micrometershowcase.cafeservice;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

@SpringBootApplication
public class CafeServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CafeServiceApplication.class, args);
    }

    @Component
    class Runner implements CommandLineRunner {

        @Override
        public void run(String... args) {
            final Random r = new Random();
            final RestTemplate restTemplate = new RestTemplate();

            while (true) {
                final List<String> drinkTypes = Arrays.asList("beer", "cola", "duvel");

                try {
                    restTemplate.getForEntity("http://localhost:8080/drink/{drink}/price", Double.class, drinkTypes.get(r.nextInt(drinkTypes.size() - 1)));
                } catch (HttpClientErrorException e) {
                }
            }
        }
    }

    @RestController
    class DrinkController {

        @Autowired DrinkService drinkService;

        @GetMapping("/drink/{drink}/price")
        public double getPrice(@PathVariable String drink)  {
            return drinkService.getDrinkPrice(drink);
        }
    }

    @ResponseStatus(value = HttpStatus.CONFLICT, reason = "Unknown drink")
    class UnknownDrinkException extends RuntimeException {
    }

    @Bean
    MeterRegistryCustomizer<MeterRegistry> meterRegistryCustomizer() {
        return registry -> registry.config()
                .commonTags("host", "localhost",
                        "service", "cafeservice",
                        "region", "EU-WEST-1")
                .meterFilter(MeterFilter.deny(id -> {
                    String uri = id.getTag("uri");
                    return uri != null && uri.startsWith("/actuator");
                }))
                .meterFilter(MeterFilter.deny(id -> {
                    String uri = id.getTag("uri");
                    return uri != null && uri.contains("favicon");
                }));
    }

    @Service
    class DrinkService {

        private final Random r = new Random();

        private final Timer drinkCalculationDetailedTimer;
        private final DistributionSummary valueDistributionSummary;
        private final Counter getDrinkPriceCounter;

        @Autowired
        public DrinkService(MeterRegistry meterRegistry) {
            //drinkCalculationDetailedTimer = meterRegistry.timer("drinkcalculation.getDrinkPrice.timer.detailed");
            drinkCalculationDetailedTimer = Timer.builder("drinkcalculation.getDrinkPrice.timer.detailed")
                    .publishPercentiles(0.5, 0.95) // median and 95th percentile
                    .publishPercentileHistogram()
                    .sla(Duration.ofMillis(500))
                    .minimumExpectedValue(Duration.ofMillis(450))
                    .maximumExpectedValue(Duration.ofMillis(550))
                    .register(meterRegistry);
            //valueDistributionSummary = meterRegistry.summary("drinkcalculation.getDrinkPrice.values");

            valueDistributionSummary = DistributionSummary
                    .builder("drinkcalculation.getDrinkPrice.values")
                    .distributionStatisticExpiry(Duration.ofHours(1))
                    .baseUnit("Euro")
                    .publishPercentileHistogram(true)
                    .minimumExpectedValue(20L)
                    .maximumExpectedValue(30L)
                    .scale(10)
                    .percentilePrecision(3)
                    .description("Distribution summary requested prices")
                    .register(meterRegistry);

            getDrinkPriceCounter = meterRegistry.counter("drinkcalculation.getDrinkPrice.counter");
        }

        @Timed("drinkcalculation.getDrinkPrice.timer.general")
        public int getDrinkPrice(String drink) {
            getDrinkPriceCounter.increment();

            doWork();
            drinkCalculationDetailedTimer.record(() -> doWork());

            int price;
            if (drink.startsWith("b")) {
                price = 2;
            } else if (drink.startsWith("c")) {
                price = 3;
            } else {
                throw new UnknownDrinkException();
            }

            valueDistributionSummary.record(price);

            return price;
        }

        void doWork() {
            try {
                Thread.sleep(450 + r.nextInt(100));
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
