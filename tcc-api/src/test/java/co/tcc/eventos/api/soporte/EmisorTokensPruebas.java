package co.tcc.eventos.api.soporte;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/** Emite tokens como los de Keycloak, firmados con una llave local que el decodificador de pruebas acepta. */
public final class EmisorTokensPruebas {

    public static final String EMISOR = "https://emisor-pruebas/realms/tcc";
    public static final String AUDIENCIA = "api-ingesta";

    public static final KeyPair LLAVES = nuevasLlaves();

    private EmisorTokensPruebas() {
    }

    public static RSAPublicKey llavePublica() {
        return (RSAPublicKey) LLAVES.getPublic();
    }

    public static String token() {
        return token("tms", "eventos:escribir");
    }

    public static String token(String cliente, String alcance) {
        return firmar(reclamos(cliente, alcance, AUDIENCIA, EMISOR, Duration.ofMinutes(5)), LLAVES);
    }

    public static String tokenConAudiencia(String audiencia) {
        return firmar(reclamos("tms", "eventos:escribir", audiencia, EMISOR, Duration.ofMinutes(5)), LLAVES);
    }

    public static String tokenConEmisor(String emisor) {
        return firmar(reclamos("tms", "eventos:escribir", AUDIENCIA, emisor, Duration.ofMinutes(5)), LLAVES);
    }

    public static String tokenConVigencia(Duration vigencia) {
        return firmar(reclamos("tms", "eventos:escribir", AUDIENCIA, EMISOR, vigencia), LLAVES);
    }

    public static String tokenConOtraLlave() {
        return firmar(reclamos("tms", "eventos:escribir", AUDIENCIA, EMISOR, Duration.ofMinutes(5)), nuevasLlaves());
    }

    /** "alg confusion": HS256 con un secreto cualquiera. Debe rechazarse aunque los reclamos sean correctos. */
    public static String tokenSimetrico() {
        try {
            var secreto = new byte[32];
            new SecureRandom().nextBytes(secreto);
            var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256),
                    reclamos("tms", "eventos:escribir", AUDIENCIA, EMISOR, Duration.ofMinutes(5)));
            jwt.sign(new MACSigner(secreto));
            return jwt.serialize();
        } catch (JOSEException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static JWTClaimsSet reclamos(String cliente, String alcance, String audiencia, String emisor, Duration vigencia) {
        var ahora = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer(emisor)
                .audience(List.of(audiencia))
                .issueTime(Date.from(ahora.minusSeconds(60)))
                .expirationTime(Date.from(ahora.plus(vigencia)))
                .claim("azp", cliente)
                .claim("scope", alcance)
                .build();
    }

    private static String firmar(JWTClaimsSet reclamos, KeyPair llaves) {
        try {
            var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("pruebas").build(), reclamos);
            jwt.sign(new RSASSASigner(llaves.getPrivate()));
            return jwt.serialize();
        } catch (JOSEException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static KeyPair nuevasLlaves() {
        try {
            var generador = KeyPairGenerator.getInstance("RSA");
            generador.initialize(2048);
            return generador.generateKeyPair();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
