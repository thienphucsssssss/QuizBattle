package vn.edu.hutech.nqb.server;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.*;
import java.util.*;

public final class PasswordHasher {
    public static final int ITERATIONS = 600_000;
    public record Hash(String salt, String value, int iterations) { }
    public static Hash hash(char[] password) {
        if (password.length < 8 || password.length > 128) throw new IllegalArgumentException("Password requires 8–128 characters");
        byte[] salt = new byte[16]; new SecureRandom().nextBytes(salt);
        return new Hash(Base64.getEncoder().encodeToString(salt), Base64.getEncoder().encodeToString(derive(password,salt,ITERATIONS)), ITERATIONS);
    }
    public static boolean verify(char[] password, Hash hash) {
        if (hash.iterations < 100_000 || hash.iterations > 2_000_000 || password.length > 128) return false;
        byte[] actual = derive(password,Base64.getDecoder().decode(hash.salt),hash.iterations);
        try { return MessageDigest.isEqual(actual,Base64.getDecoder().decode(hash.value)); } finally { Arrays.fill(actual,(byte)0); }
    }
    private static byte[] derive(char[] password, byte[] salt,int iterations) {
        var spec=new PBEKeySpec(password,salt,iterations,256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        catch (GeneralSecurityException e) { throw new IllegalStateException("Password KDF unavailable",e); }
        finally { spec.clearPassword(); }
    }
    private PasswordHasher() { }
}
