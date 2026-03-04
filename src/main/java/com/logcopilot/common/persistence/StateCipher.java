package com.logcopilot.common.persistence;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

public class StateCipher {

	private static final String ALGORITHM = "AES";
	private static final String TRANSFORMATION = "AES/GCM/NoPadding";
	private static final String KDF_ALGORITHM = "PBKDF2WithHmacSHA256";
	private static final int IV_LENGTH = 12;
	private static final int SALT_LENGTH = 16;
	private static final int TAG_LENGTH_BITS = 128;
	private static final int PBKDF2_ITERATIONS = 120_000;
	private static final int DERIVED_KEY_LENGTH_BITS = 128;
	private static final byte[] PAYLOAD_HEADER = "LCP1".getBytes(StandardCharsets.US_ASCII);

	private final SecureRandom secureRandom;
	private final String encryptionKey;
	private final SecretKeySpec legacyKeySpec;

	public StateCipher(String encryptionKey) {
		if (encryptionKey == null || encryptionKey.isBlank()) {
			throw new IllegalArgumentException("encryptionKey must not be blank");
		}
		this.secureRandom = new SecureRandom();
		this.encryptionKey = encryptionKey.trim();
		this.legacyKeySpec = new SecretKeySpec(deriveLegacyKey(this.encryptionKey), ALGORITHM);
	}

	public byte[] encrypt(byte[] plainBytes) {
		if (plainBytes == null) {
			return new byte[0];
		}
		try {
			byte[] salt = new byte[SALT_LENGTH];
			secureRandom.nextBytes(salt);
			byte[] iv = new byte[IV_LENGTH];
			secureRandom.nextBytes(iv);

			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			SecretKeySpec keySpec = new SecretKeySpec(deriveKey(encryptionKey, salt), ALGORITHM);
			cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
			byte[] encrypted = cipher.doFinal(plainBytes);

			ByteBuffer buffer = ByteBuffer.allocate(PAYLOAD_HEADER.length + SALT_LENGTH + IV_LENGTH + encrypted.length);
			buffer.put(PAYLOAD_HEADER);
			buffer.put(salt);
			buffer.put(iv);
			buffer.put(encrypted);
			return buffer.array();
		} catch (Exception exception) {
			throw new IllegalStateException("Failed to encrypt state payload", exception);
		}
	}

	public byte[] decrypt(byte[] encryptedBytes) {
		if (encryptedBytes == null || encryptedBytes.length == 0) {
			return new byte[0];
		}
		if (encryptedBytes.length <= IV_LENGTH) {
			throw new IllegalStateException("Encrypted payload is too short");
		}
		try {
			if (isVersionedPayload(encryptedBytes)) {
				return decryptVersionedPayload(encryptedBytes);
			}
			return decryptLegacyPayload(encryptedBytes);
		} catch (Exception exception) {
			throw new IllegalStateException("Failed to decrypt state payload", exception);
		}
	}

	private boolean isVersionedPayload(byte[] encryptedBytes) {
		int minimumLength = PAYLOAD_HEADER.length + SALT_LENGTH + IV_LENGTH + 1;
		if (encryptedBytes.length < minimumLength) {
			return false;
		}
		for (int index = 0; index < PAYLOAD_HEADER.length; index++) {
			if (encryptedBytes[index] != PAYLOAD_HEADER[index]) {
				return false;
			}
		}
		return true;
	}

	private byte[] decryptVersionedPayload(byte[] encryptedBytes) throws Exception {
		int offset = PAYLOAD_HEADER.length;
		byte[] salt = Arrays.copyOfRange(encryptedBytes, offset, offset + SALT_LENGTH);
		offset += SALT_LENGTH;
		byte[] iv = Arrays.copyOfRange(encryptedBytes, offset, offset + IV_LENGTH);
		offset += IV_LENGTH;
		byte[] encryptedPayload = Arrays.copyOfRange(encryptedBytes, offset, encryptedBytes.length);

		Cipher cipher = Cipher.getInstance(TRANSFORMATION);
		SecretKeySpec keySpec = new SecretKeySpec(deriveKey(encryptionKey, salt), ALGORITHM);
		cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
		return cipher.doFinal(encryptedPayload);
	}

	private byte[] decryptLegacyPayload(byte[] encryptedBytes) throws Exception {
		byte[] iv = Arrays.copyOfRange(encryptedBytes, 0, IV_LENGTH);
		byte[] encryptedPayload = Arrays.copyOfRange(encryptedBytes, IV_LENGTH, encryptedBytes.length);
		Cipher cipher = Cipher.getInstance(TRANSFORMATION);
		cipher.init(Cipher.DECRYPT_MODE, legacyKeySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
		return cipher.doFinal(encryptedPayload);
	}

	private byte[] deriveKey(String encryptionKey, byte[] salt) {
		PBEKeySpec spec = new PBEKeySpec(
			encryptionKey.toCharArray(),
			salt,
			PBKDF2_ITERATIONS,
			DERIVED_KEY_LENGTH_BITS
		);
		try {
			SecretKeyFactory factory = SecretKeyFactory.getInstance(KDF_ALGORITHM);
			return factory.generateSecret(spec).getEncoded();
		} catch (Exception exception) {
			throw new IllegalStateException("Failed to derive encryption key", exception);
		} finally {
			spec.clearPassword();
		}
	}

	private byte[] deriveLegacyKey(String encryptionKey) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hashed = digest.digest(encryptionKey.getBytes(StandardCharsets.UTF_8));
			return Arrays.copyOf(hashed, 16);
		} catch (Exception exception) {
			throw new IllegalStateException("Failed to derive legacy encryption key", exception);
		}
	}
}
