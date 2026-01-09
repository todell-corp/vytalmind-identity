package com.vm.identity.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Utility class for performing deep copies of objects using Jackson serialization.
 * This is useful for creating independent copies of objects that need to be modified
 * while preserving the original state for rollback scenarios.
 */
public class DeepCopyUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    /**
     * Creates a deep copy of an object by serializing and deserializing it.
     *
     * @param object the object to copy
     * @param type the class type of the object
     * @param <T> the type parameter
     * @return a deep copy of the object
     * @throws RuntimeException if the copy operation fails
     */
    public static <T> T deepCopy(T object, Class<T> type) {
        try {
            byte[] bytes = MAPPER.writeValueAsBytes(object);
            return MAPPER.readValue(bytes, type);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deep copy object", e);
        }
    }
}
