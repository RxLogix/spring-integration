/*
 * Copyright 2017-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.integration.support.json;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.GenericMessage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Gary Russell
 * @author Artem Bilan
 * @author Glenn Renfro
 *
 * @since 5.0
 *
 */
public class EmbeddedJsonHeadersMessageMapperTests {

	@Test
	public void testEmbedAll() {
		EmbeddedJsonHeadersMessageMapper mapper = new EmbeddedJsonHeadersMessageMapper("*");
		GenericMessage<String> message = new GenericMessage<>("foo");
		assertThat(mapper.toMessage(mapper.fromMessage(message))).isEqualTo(message);
	}

	@Test
	public void testEmbedSome() throws Exception {
		EmbeddedJsonHeadersMessageMapper mapper = new EmbeddedJsonHeadersMessageMapper(MessageHeaders.ID);
		GenericMessage<String> message = new GenericMessage<>("foo");
		byte[] encodedMessage = mapper.fromMessage(message);
		// some delay to prove the original timestamp (not a fresh one) is restored
		Thread.sleep(2);
		Message<?> decoded = mapper.toMessage(encodedMessage);
		assertThat(decoded.getPayload()).isEqualTo(message.getPayload());
		assertThat(decoded.getHeaders().getTimestamp()).isEqualTo(message.getHeaders().getTimestamp());

		ObjectMapper objectMapper = new ObjectMapper();
		Map<String, Object> encodedMessageToCheck =
				objectMapper.readValue(encodedMessage, new TypeReference<>() {

				});

		Object headers = encodedMessageToCheck.get("headers");
		assertThat(headers).isNotNull();
		assertThat(headers).isInstanceOf(Map.class);

		@SuppressWarnings("unchecked")
		Map<String, Object> headersToCheck = (Map<String, Object>) headers;
		assertThat(headersToCheck).containsKey(MessageHeaders.TIMESTAMP);
	}

	@Test
	public void testBytesEmbedAll() throws Exception {
		EmbeddedJsonHeadersMessageMapper mapper = new EmbeddedJsonHeadersMessageMapper("*");
		GenericMessage<byte[]> message = new GenericMessage<>("foo".getBytes());
		Thread.sleep(2);
		byte[] bytes = mapper.fromMessage(message);
		ByteBuffer bb = ByteBuffer.wrap(bytes);
		int headerLen = bb.getInt();
		byte[] headerBytes = new byte[headerLen];
		bb.get(headerBytes);
		String headers = new String(headerBytes);
		assertThat(headers).contains(message.getHeaders().getId().toString());
		assertThat(headers).contains(String.valueOf(message.getHeaders().getTimestamp()));
		assertThat(bb.getInt()).isEqualTo(3);
		assertThat(bb.remaining()).isEqualTo(3);
		assertThat((char) bb.get()).isEqualTo('f');
		assertThat((char) bb.get()).isEqualTo('o');
		assertThat((char) bb.get()).isEqualTo('o');
	}

	@Test
	public void testBytesEmbedSome() {
		EmbeddedJsonHeadersMessageMapper mapper = new EmbeddedJsonHeadersMessageMapper("I*");
		GenericMessage<byte[]> message = new GenericMessage<>("foo".getBytes(), Collections.singletonMap("bar", "baz"));
		byte[] bytes = mapper.fromMessage(message);
		ByteBuffer bb = ByteBuffer.wrap(bytes);
		int headerLen = bb.getInt();
		byte[] headerBytes = new byte[headerLen];
		bb.get(headerBytes);
		String headers = new String(headerBytes);
		assertThat(headers).contains(message.getHeaders().getId().toString());
		assertThat(headers).contains(MessageHeaders.TIMESTAMP);
		assertThat(headers).doesNotContain("bar");
		assertThat(bb.getInt()).isEqualTo(3);
		assertThat(bb.remaining()).isEqualTo(3);
		assertThat((char) bb.get()).isEqualTo('f');
		assertThat((char) bb.get()).isEqualTo('o');
		assertThat((char) bb.get()).isEqualTo('o');
	}

	@Test
	public void testBytesEmbedAllJson() {
		EmbeddedJsonHeadersMessageMapper mapper = new EmbeddedJsonHeadersMessageMapper();
		mapper.setRawBytes(false);
		GenericMessage<byte[]> message = new GenericMessage<>("foo".getBytes());
		byte[] mappedBytes = mapper.fromMessage(message);
		String mapped = new String(mappedBytes);
		assertThat(mapped).contains("[B\",\"Zm9v");
		@SuppressWarnings("unchecked")
		Message<byte[]> decoded = (Message<byte[]>) mapper.toMessage(mappedBytes);
		assertThat(new String(decoded.getPayload())).isEqualTo("foo");

	}

	@Test
	public void testBytesDecodeAll() {
		EmbeddedJsonHeadersMessageMapper mapper = new EmbeddedJsonHeadersMessageMapper("*");
		GenericMessage<byte[]> message = new GenericMessage<>("foo".getBytes());
		Message<?> decoded = mapper.toMessage(mapper.fromMessage(message));
		assertThat(decoded).isEqualTo(message);
	}

	@Test
	public void testStandardHeadersAlwaysIncludedEvenWhenExcludedByPattern() throws Exception {
		EmbeddedJsonHeadersMessageMapper mapper = new EmbeddedJsonHeadersMessageMapper("!" + MessageHeaders.ID, "*");
		GenericMessage<String> message = new GenericMessage<>("foo", Collections.singletonMap("bar", "baz"));
		byte[] encodedMessage = mapper.fromMessage(message);

		ObjectMapper objectMapper = new ObjectMapper();
		Map<String, Object> encodedMessageToCheck =
				objectMapper.readValue(encodedMessage, new TypeReference<>() {

				});

		Object headers = encodedMessageToCheck.get("headers");
		assertThat(headers).isNotNull();
		assertThat(headers).isInstanceOf(Map.class);

		@SuppressWarnings("unchecked")
		Map<String, Object> headersToCheck = (Map<String, Object>) headers;
		assertThat(headersToCheck).containsKeys(MessageHeaders.ID, MessageHeaders.TIMESTAMP, "bar");

		Message<?> decoded = mapper.toMessage(mapper.fromMessage(message));
		assertThat(decoded.getHeaders().getTimestamp()).isEqualTo(message.getHeaders().getTimestamp());
		assertThat(decoded.getHeaders().getId()).isEqualTo(message.getHeaders().getId());
		assertThat(decoded.getHeaders().get("bar")).isEqualTo("baz");
	}

	@Test
	public void testStandardHeadersAlwaysEmbeddedByDefault() throws InterruptedException {
		EmbeddedJsonHeadersMessageMapper mapper = new EmbeddedJsonHeadersMessageMapper();
		GenericMessage<String> message =
				new GenericMessage<>("foo", Collections.singletonMap(MessageHeaders.CONTENT_TYPE, "text/plain"));
		Thread.sleep(2);
		Message<?> decoded = mapper.toMessage(mapper.fromMessage(message));
		assertThat(decoded.getHeaders().getId()).isEqualTo(message.getHeaders().getId());
		assertThat(decoded.getHeaders().getTimestamp()).isEqualTo(message.getHeaders().getTimestamp());
		assertThat(decoded.getHeaders().get(MessageHeaders.CONTENT_TYPE)).isEqualTo("text/plain");
	}

	@Test
	public void testUntrustedHeadersOnWireArePrunedOnDecodeNativeFormat() {
		EmbeddedJsonHeadersMessageMapper untrustedProducer = new EmbeddedJsonHeadersMessageMapper("*");
		GenericMessage<byte[]> message =
				new GenericMessage<>("test".getBytes(), Collections.singletonMap("untrusted", "attacker-value"));
		byte[] bytesOnTheWire = untrustedProducer.fromMessage(message);

		EmbeddedJsonHeadersMessageMapper restrictedConsumer = new EmbeddedJsonHeadersMessageMapper();
		Message<?> decoded = restrictedConsumer.toMessage(bytesOnTheWire);

		assertThat(decoded.getHeaders().get("untrusted")).isNull();
		assertThat(decoded.getHeaders().getId()).isEqualTo(message.getHeaders().getId());
		assertThat(decoded.getHeaders().getTimestamp()).isEqualTo(message.getHeaders().getTimestamp());
	}

	@Test
	public void testUntrustedHeadersOnWireArePrunedOnDecodeJson() {
		EmbeddedJsonHeadersMessageMapper untrustedProducer = new EmbeddedJsonHeadersMessageMapper("*");
		untrustedProducer.setRawBytes(false);
		GenericMessage<String> message =
				new GenericMessage<>("test", Collections.singletonMap("untrusted", "attacker-value"));
		byte[] bytesOnTheWire = untrustedProducer.fromMessage(message);

		EmbeddedJsonHeadersMessageMapper restrictedConsumer = new EmbeddedJsonHeadersMessageMapper();
		restrictedConsumer.setRawBytes(false);
		Message<?> decoded = restrictedConsumer.toMessage(bytesOnTheWire);

		assertThat(decoded.getHeaders().get("untrusted")).isNull();
		assertThat(decoded.getHeaders().getId()).isEqualTo(message.getHeaders().getId());
		assertThat(decoded.getHeaders().getTimestamp()).isEqualTo(message.getHeaders().getTimestamp());
	}

}
