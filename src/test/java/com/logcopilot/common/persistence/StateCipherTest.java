package com.logcopilot.common.persistence;

import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class StateCipherTest {

	@Test
	void encryptsAndDecryptsWithVersionedPayload() {
		StateCipher cipher = new StateCipher("state-cipher-test-secret");
		byte[] plain = "sensitive-state-payload".getBytes(StandardCharsets.UTF_8);

		byte[] encrypted = cipher.encrypt(plain);

		assertThat(encrypted.length).isGreaterThan(plain.length);
		assertThat(new String(encrypted, StandardCharsets.UTF_8)).doesNotContain("sensitive-state-payload");
		assertThat(cipher.decrypt(encrypted)).containsExactly(plain);
	}

	@Test
	void decryptsLegacyPayloadForBackwardCompatibility() {
		String secret = "state-cipher-legacy-secret";
		byte[] plain = "legacy-payload".getBytes(StandardCharsets.UTF_8);
		StateCipher cipher = new StateCipher(secret);

		byte[] legacyEncrypted = encryptLegacy(secret, plain);

		assertThat(cipher.decrypt(legacyEncrypted)).containsExactly(plain);
	}

	private byte[] encryptLegacy(String secret, byte[] plain) {
		try {
			byte[] iv = new byte[12];
			new SecureRandom().nextBytes(iv);
			byte[] hashed = MessageDigest.getInstance("SHA-256")
				.digest(secret.getBytes(StandardCharsets.UTF_8));
			byte[] legacyKey = Arrays.copyOf(hashed, 16);

			Cipher legacyCipher = Cipher.getInstance("AES/GCM/NoPadding");
			legacyCipher.init(
				Cipher.ENCRYPT_MODE,
				new SecretKeySpec(legacyKey, "AES"),
				new GCMParameterSpec(128, iv)
			);
			byte[] encrypted = legacyCipher.doFinal(plain);
			ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);
			buffer.put(iv);
			buffer.put(encrypted);
			return buffer.array();
		} catch (Exception exception) {
			throw new IllegalStateException("Failed to build legacy payload for test", exception);
		}
	}
}
