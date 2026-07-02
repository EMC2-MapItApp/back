package emc.mapIt.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

@Configuration
public class MongoConfig {

    @Value("${spring.data.mongodb.uri:}")
    private String mongoUri;

    @Value("${spring.data.mongodb.database:mapit_db}")
    private String mongoDatabase;

    @Bean
    public MongoClient mongoClient() {
        if (mongoUri == null || mongoUri.isBlank()) {
            return MongoClients.create();
        }
        return MongoClients.create(mongoUri);
    }

    @Bean
    public MongoTemplate mongoTemplate(MongoClient mongoClient) {
        return new MongoTemplate(mongoClient, mongoDatabase);
    }
}
