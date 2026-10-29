package vn.edu.hutech.nqb.client;

import javax.net.ssl.*;
import java.net.Socket;
import java.security.*;
import java.security.cert.*;
import java.util.*;

/** Authenticates the pre-distributed Server key, independently of its DHCP address. */
final class PinnedServerTrust extends X509ExtendedTrustManager {
    private final X509ExtendedTrustManager delegate;
    private final List<byte[]> pins;

    static PinnedServerTrust from(KeyStore store) throws GeneralSecurityException, SSLException {
        var pins = new ArrayList<byte[]>();
        for (var aliases = store.aliases(); aliases.hasMoreElements();) {
            String alias = aliases.nextElement();
            if (store.isKeyEntry(alias)) throw new KeyStoreException("Client truststore must contain public certificates only");
            if (store.getCertificate(alias) instanceof X509Certificate cert) pins.add(digest(cert));
        }
        if (pins.isEmpty()) throw new SSLHandshakeException("No trusted Server identity in Client truststore");
        var factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init(store);
        for (var manager : factory.getTrustManagers()) {
            if (manager instanceof X509ExtendedTrustManager extended) return new PinnedServerTrust(extended, pins);
        }
        throw new SSLException("An extended X509 trust manager is required");
    }

    private PinnedServerTrust(X509ExtendedTrustManager delegate, List<byte[]> pins) {
        this.delegate = delegate;
        this.pins = List.copyOf(pins);
    }

    private static byte[] digest(X509Certificate cert) throws NoSuchAlgorithmException {
        return MessageDigest.getInstance("SHA-256").digest(cert.getPublicKey().getEncoded());
    }

    private void checkIdentity(X509Certificate[] chain) throws CertificateException {
        if (chain == null || chain.length == 0) throw new CertificateException("Missing Server certificate");
        var leaf = chain[0];
        // A pinned self-signed leaf can be a trust anchor; check its lifetime explicitly.
        leaf.checkValidity();
        var usage = leaf.getExtendedKeyUsage();
        if (usage != null && !usage.contains("1.3.6.1.5.5.7.3.1") && !usage.contains("2.5.29.37.0")) {
            throw new CertificateException("Certificate cannot authenticate a TLS Server");
        }
        try {
            byte[] actual = digest(leaf);
            for (byte[] expected : pins) if (MessageDigest.isEqual(expected, actual)) return;
        } catch (NoSuchAlgorithmException error) { throw new CertificateException(error); }
        throw new CertificateException("Server public key does not match the distributed Client identity");
    }

    @Override public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
        checkIdentity(chain); delegate.checkServerTrusted(chain, authType, socket);
    }
    @Override public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
        checkIdentity(chain); delegate.checkServerTrusted(chain, authType, engine);
    }
    @Override public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        checkIdentity(chain); delegate.checkServerTrusted(chain, authType);
    }
    @Override public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException { delegate.checkClientTrusted(chain, authType, socket); }
    @Override public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException { delegate.checkClientTrusted(chain, authType, engine); }
    @Override public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException { delegate.checkClientTrusted(chain, authType); }
    @Override public X509Certificate[] getAcceptedIssuers() { return delegate.getAcceptedIssuers(); }
}
