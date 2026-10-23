package vn.edu.hutech.nqb.server;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PasswordHasherTest {
    @Test void saltIsUniqueAndVerificationIsCorrect(){char[] password="Tiếng Việt Password 123".toCharArray();var a=PasswordHasher.hash(password);var b=PasswordHasher.hash(password);assertNotEquals(a.salt(),b.salt());assertNotEquals(a.value(),b.value());assertEquals(600000,a.iterations());assertTrue(PasswordHasher.verify(password,a));assertFalse(PasswordHasher.verify("wrong-password".toCharArray(),a));}
}
