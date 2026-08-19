package com.streamhub.platform.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
@EnableCaching
public class RedisConfig {

    @Value("${app.redis.cache-ttl-seconds:36000}")
    private long cacheTtlSeconds;

    @Bean
    public GenericJackson2JsonRedisSerializer redisJsonSerializer(
            ObjectMapper objectMapper) {

        ObjectMapper redisMapper = objectMapper.copy();

        redisMapper.registerModule(new JavaTimeModule());

        redisMapper.disable(
                SerializationFeature.WRITE_DATES_AS_TIMESTAMPS
        );

        return new GenericJackson2JsonRedisSerializer(redisMapper);
    }

    @Bean
    public RedisCacheManager redisCacheManager(
            RedisConnectionFactory redisConnectionFactory,
            GenericJackson2JsonRedisSerializer redisJsonSerializer) {

        RedisCacheConfiguration cacheConfiguration =
                RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(Duration.ofSeconds(cacheTtlSeconds))
                        .disableCachingNullValues()

                        .serializeKeysWith(
                                RedisSerializationContext.SerializationPair
                                        .fromSerializer(
                                                new StringRedisSerializer()
                                        )
                        )

                        .serializeValuesWith(
                                RedisSerializationContext.SerializationPair
                                        .fromSerializer(redisJsonSerializer)
                        );

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(cacheConfiguration)
                .build();
    }
}