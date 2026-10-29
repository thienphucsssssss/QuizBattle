package vn.edu.hutech.nqb.shared;

import java.security.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Credentials are ephemeral, scoped to one room generation, and never persisted. */
public final class ResumeRules {
    public static final String CAPABILITY="SESSION_RESUME_V1";
    private static final SecureRandom RANDOM=new SecureRandom();
    private ResumeRules() { }
    public static String token(){byte[] bytes=new byte[32];RANDOM.nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
    public static boolean valid(String token){return token!=null&&token.matches("[A-Za-z0-9_-]{43}");}
    public static byte[] hash(String token){
        try{return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII));}
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    public static boolean matches(byte[] hash,String token){return hash!=null&&valid(token)&&MessageDigest.isEqual(hash,hash(token));}
}
