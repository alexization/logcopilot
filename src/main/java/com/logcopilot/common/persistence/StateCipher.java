package com.logcopilot.common.persistence;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

public class StateCipher {

	private static final String ALGORITHM = "AES";
	private static final String TRANSFORMATION = "AES/GCM/NoPadding";
	private static final int IV_LENGTH = 12;
	private static final int TAG_LENGTH_BITS = 128;

	private final SecureRandom secureRandom;
	private final SecretKeySpec keySpec;

	public StateCipher(String encryptionKey) {
		if (encryptionKey == null || encryptionKey.isBlank()) {
			throw new IllegalArgumentException("encryptionKey must not be blank");
		}
		this.secureRandom = new SecureRandom();
		this.keySpec = new SecretKeySpec(deriveKey(encryptionKey), ALGORITHM);
	}

	public byte[] encrypt(byte[] plainBytes) {
		if (plainBytes == null) {
			return new byte[0];
		}
		try {
			byte[] iv = new byte[IV_LENGTH];
			secureRandom.nextBytes(iv);

			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
			byte[] encrypted = cipher.doFinal(plainBytes);

			ByteBuffer buffer = ByteBuffer.allocate(IV_LENGTH + encrypted.length);
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
			byte[] iv = Arrays.copyOfRange(encryptedBytes, 0, IV_LENGTH);
			byte[] encryptedPayload = Arrays.copyOfRange(encryptedBytes, IV_LENGTH, encryptedBytes.length);

			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
			return cipher.doFinal(encryptedPayload);
		} catch (Exception exception) {
			throw new IllegalStateException("Failed to decrypt state payload", exception);
		}
	}

	private byte[] deriveKey(String encryptionKey) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hashed = digest.digest(encryptionKey.getBytes(StandardCharsets.UTF_8));
			return Arrays.copyOf(hashed, 16);
		} catch (Exception exception) {
			throw new IllegalStateException("Failed to derive encryption key", exception);
		}
	}
}
