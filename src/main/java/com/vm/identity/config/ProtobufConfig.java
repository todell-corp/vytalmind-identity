package com.vm.identity.config;

import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

/**
 * Configuration for Protocol Buffer JSON serialization/deserialization in Spring MVC.
 * Enables @RequestBody and @ResponseBody with protobuf messages.
 */
@Configuration
public class ProtobufConfig implements WebMvcConfigurer {

    @Override
    public void extendMessageConverters(java.util.List<org.springframework.http.converter.HttpMessageConverter<?>> converters) {
        converters.add(0, new ProtobufJsonHttpMessageConverter());
    }

    /**
     * Custom HTTP message converter for protobuf JSON serialization.
     */
    public static class ProtobufJsonHttpMessageConverter extends AbstractHttpMessageConverter<Message> {

        private final JsonFormat.Parser parser = JsonFormat.parser().ignoringUnknownFields();
        private final JsonFormat.Printer printer = JsonFormat.printer()
                .includingDefaultValueFields()
                .omittingInsignificantWhitespace();

        public ProtobufJsonHttpMessageConverter() {
            super(MediaType.APPLICATION_JSON, new MediaType("application", "*+json"));
        }

        @Override
        protected boolean supports(Class<?> clazz) {
            return Message.class.isAssignableFrom(clazz);
        }

        @Override
        protected Message readInternal(Class<? extends Message> clazz, HttpInputMessage inputMessage)
                throws IOException, HttpMessageNotReadableException {

            try {
                // Get the default instance to access the builder
                Message.Builder builder = (Message.Builder) clazz.getMethod("newBuilder").invoke(null);

                // Parse JSON into the builder
                try (InputStreamReader reader = new InputStreamReader(inputMessage.getBody(), StandardCharsets.UTF_8)) {
                    parser.merge(reader, builder);
                }

                return builder.build();
            } catch (Exception e) {
                throw new HttpMessageNotReadableException("Failed to read protobuf message: " + e.getMessage(), e, inputMessage);
            }
        }

        @Override
        protected void writeInternal(Message message, HttpOutputMessage outputMessage)
                throws IOException, HttpMessageNotWritableException {

            try (OutputStreamWriter writer = new OutputStreamWriter(outputMessage.getBody(), StandardCharsets.UTF_8)) {
                printer.appendTo(message, writer);
            }
        }
    }
}
