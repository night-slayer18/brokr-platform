package io.brokr.api.config;

import graphql.language.IntValue;
import graphql.language.StringValue;
import graphql.schema.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class GraphQLScalarConfig {

    @Bean
    public RuntimeWiringConfigurer runtimeWiringConfigurer() {
        return wiringBuilder -> wiringBuilder
                .scalar(longScalar())
                .scalar(mapScalar());
    }

    private GraphQLScalarType longScalar() {
        return GraphQLScalarType.newScalar()
                .name("Long")
                .description("A custom scalar that represents long values")
                .coercing(new Coercing<Long, Long>() {
                    @Override
                    public Long serialize(Object dataFetcherResult) throws CoercingSerializeException {
                        if (dataFetcherResult instanceof Long) {
                            return (Long) dataFetcherResult;
                        } else if (dataFetcherResult instanceof Integer) {
                            return ((Integer) dataFetcherResult).longValue();
                        } else if (dataFetcherResult instanceof String) {
                            return Long.parseLong((String) dataFetcherResult);
                        }
                        throw new CoercingSerializeException("Expected a Long value");
                    }

                    @Override
                    public Long parseValue(Object input) throws CoercingParseValueException {
                        if (input instanceof Long) {
                            return (Long) input;
                        } else if (input instanceof Integer) {
                            return ((Integer) input).longValue();
                        } else if (input instanceof String) {
                            return Long.parseLong((String) input);
                        }
                        throw new CoercingParseValueException("Expected a Long value");
                    }

                    @Override
                    public Long parseLiteral(Object input) throws CoercingParseLiteralException {
                        if (input instanceof IntValue) {
                            return ((IntValue) input).getValue().longValue();
                        } else if (input instanceof StringValue) {
                            return Long.parseLong(((StringValue) input).getValue());
                        }
                        throw new CoercingParseLiteralException("Expected a Long value");
                    }
                })
                .build();
    }

    private GraphQLScalarType mapScalar() {
        return GraphQLScalarType.newScalar()
                .name("Map")
                .description("A custom scalar that represents a map/dictionary")
                .coercing(new Coercing<Map<String, Object>, Map<String, Object>>() {
                    @Override
                    public Map<String, Object> serialize(Object dataFetcherResult) throws CoercingSerializeException {
                        if (dataFetcherResult instanceof Map) {
                            return (Map<String, Object>) dataFetcherResult;
                        }
                        throw new CoercingSerializeException("Expected a Map value");
                    }

                    @Override
                    public Map<String, Object> parseValue(Object input) throws CoercingParseValueException {
                        if (input instanceof Map) {
                            return (Map<String, Object>) input;
                        }
                        throw new CoercingParseValueException("Expected a Map value");
                    }

                    @Override
                    public Map<String, Object> parseLiteral(Object input) throws CoercingParseLiteralException {
                        if (input instanceof Map) {
                            return (Map<String, Object>) input;
                        }
                        return new HashMap<>();
                    }
                })
                .build();
    }
}

