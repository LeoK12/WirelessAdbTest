package com.leok12.wirelessadbtest;

import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAKeyGenParameterSpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.GCMParameterSpec;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509ExtendedTrustManager;

public class AdbKey {
    private static final String TAG = AdbKey.class.getSimpleName();
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String KEYALIAS = "_adbkey_encryption_key_";

    public static final int ANDROID_PUBKEY_MODULUS_SIZE = 256;
    public static final int ANDROID_PUBKEY_MODULUS_SIZE_WORDS = 64;
    public static final int RSA_PUBLIC_KEY_SIZE = 524;

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_SIZE_IN_BYTES = 12;
    private static final int TAG_SIZE_IN_BYTES = 16;
    private static final byte[] PADDING = new byte[]{
            0x00, 0x01, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x00,
            0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00,
            0x04, 0x14
    };

    private PreferenceAdbKeyStore mPreferenceAdbKeyStore;
    private String mCode;
    private Key mKey;
    private RSAPublicKey mPublicKey;
    private RSAPrivateKey mPrivateKey;
    private X509Certificate mCertificate;
    private X509ExtendedKeyManager mKeyManager;
    private X509ExtendedTrustManager mTrustManager;

    public AdbKey(PreferenceAdbKeyStore store, String code){
        mPreferenceAdbKeyStore = store;
        mCode = code;
        mKeyManager = null;
        mTrustManager = null;
    }

    public boolean init(){
        mKey = getOrCreateEncryptionKey();
        if (mKey == null){
            return false;
        }

        mPrivateKey = getOrCreatePrivateKey();
        if (mPrivateKey == null){
            return false;
        }

        mPublicKey = getPublicKey(mPrivateKey);
        if (mPublicKey == null){
            return false;
        }

        mCertificate = getCertificate(mPrivateKey, mPublicKey);
        if (mCertificate == null){
            return false;
        }

        return true;
    }

    private X509Certificate getCertificate(RSAPrivateKey privateKey, RSAPublicKey publicKey){
        try {
            ContentSigner contentSigner =
                    new JcaContentSignerBuilder("SHA256withRSA").build(privateKey);
            X509CertificateHolder x509CertificateHolder = new X509v3CertificateBuilder(
                    new X500Name("CN=00"),
                    BigInteger.ONE,
                    new Date(0),
                    new Date(2_461_449_600L * 1000),
                    Locale.ROOT,
                    new X500Name("CN=00"),
                    SubjectPublicKeyInfo.getInstance(publicKey.getEncoded())).build(contentSigner);
            return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(
                    new ByteArrayInputStream(x509CertificateHolder.getEncoded()));
        } catch (Exception e){
            e.printStackTrace();
            return null;
        }
    }

    private RSAPublicKey getPublicKey(RSAPrivateKey privateKey){
        RSAPublicKey publicKey = null;
        try {
            publicKey = (RSAPublicKey) KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_RSA).
                    generatePublic(new RSAPublicKeySpec(
                            privateKey.getModulus(),
                            RSAKeyGenParameterSpec.F4));
        } catch (Exception e){
            e.printStackTrace();
        }

         return publicKey;
    }

    private RSAPrivateKey getOrCreatePrivateKey(){
        RSAPrivateKey privateKey = null;
        try {
            byte[] aad = new byte[TAG_SIZE_IN_BYTES];
            byte[] preferenceKeyBytes =
                    PreferenceAdbKeyStore.PREFERENCE_KEY.getBytes(StandardCharsets.UTF_8);
            System.arraycopy(preferenceKeyBytes, 0, aad, 0, preferenceKeyBytes.length);

            byte[] bytes = mPreferenceAdbKeyStore.get();
            if (bytes != null){
                byte[] decrypt = decrypt(bytes, aad);
                if (decrypt == null){
                    Log.e(TAG, "decrypt should not be null");
                } else {
                    KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                    privateKey = (RSAPrivateKey) keyFactory.generatePrivate(new PKCS8EncodedKeySpec(decrypt));
                }
            }

            if (privateKey == null) {
                KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA);
                keyPairGenerator.initialize(new RSAKeyGenParameterSpec(2048, RSAKeyGenParameterSpec.F4));
                KeyPair keyPair = keyPairGenerator.generateKeyPair();
                if (keyPair == null) {
                    return null;
                }

                privateKey = (RSAPrivateKey) keyPair.getPrivate();
                byte[] encrypt = encrypt(privateKey.getEncoded(), aad);
                if (encrypt != null){
                    mPreferenceAdbKeyStore.put(encrypt);
                }
            }

            return privateKey;
        } catch (Exception e){
            e.printStackTrace();
            return null;
        }
    }

    private Key getOrCreateEncryptionKey(){
        try {
            KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
            keyStore.load(null);

            Key key = keyStore.getKey(KEYALIAS, null);
            if (key != null) {
                return key;
            }

            KeyGenParameterSpec parameterSpec = new KeyGenParameterSpec.Builder(
                    KEYALIAS,
                    KeyProperties.PURPOSE_DECRYPT | KeyProperties.PURPOSE_ENCRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build();
            if (parameterSpec == null) {
                return null;
            }

            KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
            keyGenerator.init(parameterSpec);
            return keyGenerator.generateKey();
        } catch (Exception e){
            e.printStackTrace();
            return null;
        }
    }

    public X509ExtendedKeyManager getKeyManager() {
        if (mKeyManager != null){
            return mKeyManager;
        }

        mKeyManager = new X509ExtendedKeyManager() {
            private final String alias = "key";

            @Override
            public String[] getClientAliases(String s, Principal[] principals) {
                return null;
            }

            @Override
            public String chooseClientAlias(String[] keyTypes, Principal[] principals, Socket socket) {
                for (String keyType : keyTypes) {
                    if (keyType.equals("RSA")){
                        return alias;
                    }
                }

                return null;
            }

            @Override
            public String[] getServerAliases(String s, Principal[] principals) {
                return null;
            }

            @Override
            public String chooseServerAlias(String s, Principal[] principals, Socket socket) {
                return null;
            }

            @Override
            public X509Certificate[] getCertificateChain(String alias) {
                if (alias.equals(this.alias)){
                    return new X509Certificate[]{mCertificate};
                }

                return null;
            }

            @Override
            public PrivateKey getPrivateKey(String alias) {
                if (alias.equals(this.alias)){
                    return mPrivateKey;
                }

                return null;
            }
        };

        return mKeyManager;
    }

    public X509ExtendedTrustManager getTrustManager() {
        if (mTrustManager != null) {
            return mTrustManager;
        }

        mTrustManager = new X509ExtendedTrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] x509Certificates, String s, Socket socket) throws CertificateException {

            }

            @Override
            public void checkServerTrusted(X509Certificate[] x509Certificates, String s, Socket socket) throws CertificateException {

            }

            @Override
            public void checkClientTrusted(X509Certificate[] x509Certificates, String s, SSLEngine sslEngine) throws CertificateException {

            }

            @Override
            public void checkServerTrusted(X509Certificate[] x509Certificates, String s, SSLEngine sslEngine) throws CertificateException {

            }

            @Override
            public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {

            }

            @Override
            public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {

            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };

        return mTrustManager;
    }

    public SSLContext getSSLContext(){
        try {
            SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
            sslContext.init(
                    new X509ExtendedKeyManager[]{getKeyManager()},
                    new X509ExtendedTrustManager[]{getTrustManager()},
                    new SecureRandom());
            return sslContext;
        } catch (Exception e){
            e.printStackTrace();
            return null;
        }
    }

    private int[] toAdbEncoded(BigInteger bigInteger){
        int[] endcoded = new int[ANDROID_PUBKEY_MODULUS_SIZE_WORDS];
        BigInteger r32 = BigInteger.ZERO.setBit(32);
        BigInteger tmp = bigInteger.add(BigInteger.ZERO);

        for (int i = 0; i < ANDROID_PUBKEY_MODULUS_SIZE_WORDS; i++) {
            BigInteger[] out = tmp.divideAndRemainder(r32);
            tmp = out[0];
            endcoded[i] = out[1].intValue();
        }

        return endcoded;
    }

    public byte[] getAdbPublicKey(){
        BigInteger r32 = BigInteger.ZERO.setBit(32);
        BigInteger n0inv = mPublicKey.getModulus().remainder(r32).modInverse(r32).negate();
        BigInteger r = BigInteger.ZERO.setBit(ANDROID_PUBKEY_MODULUS_SIZE * 8);
        BigInteger rr = r.modPow(BigInteger.valueOf(2L), mPublicKey.getModulus());
        ByteBuffer buffer = ByteBuffer.allocate(RSA_PUBLIC_KEY_SIZE).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(ANDROID_PUBKEY_MODULUS_SIZE_WORDS);
        buffer.putInt(n0inv.intValue());
        BigInteger modulus = mPublicKey.getModulus();
        if (modulus == null){
            return null;
        }

        int[] encoded = toAdbEncoded(modulus);
        for (int i = 0; i < encoded.length; i++) {
            buffer.putInt(encoded[i]);
        }

        encoded = toAdbEncoded(rr);
        for (int i = 0; i < encoded.length; i++) {
            buffer.putInt(encoded[i]);
        }

        buffer.putInt(mPublicKey.getPublicExponent().intValue());
        byte[] base64Bytes = Base64.encode(buffer.array(), Base64.NO_WRAP);
        String name = ' ' + mCode + '\u0000';
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        if (nameBytes == null){
            return null;
        }

        byte[] bytes = new byte[base64Bytes.length + nameBytes.length];
        System.arraycopy(base64Bytes, 0, bytes, 0, base64Bytes.length);
        System.arraycopy(nameBytes, 0, bytes, base64Bytes.length, nameBytes.length);

        return bytes;
    }

    public byte[] sign(byte[] data){
        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, mPrivateKey);
            cipher.update(PADDING);
            return cipher.doFinal(data);
        } catch (Exception e){
            e.printStackTrace();
            return null;
        }
    }

    private byte[] encrypt(byte[] data, byte[] aad){
        if (data.length > (Integer.MAX_VALUE - IV_SIZE_IN_BYTES - TAG_SIZE_IN_BYTES)){
            Log.e(TAG, String.format("data length [%d] should <  %d",data.length, (Integer.MAX_VALUE - IV_SIZE_IN_BYTES - TAG_SIZE_IN_BYTES)));
            return null;
        }

        byte[] bytes = new byte[IV_SIZE_IN_BYTES + data.length + TAG_SIZE_IN_BYTES];
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, mKey);
            cipher.updateAAD(aad);
            cipher.doFinal(data, 0, data.length, bytes, IV_SIZE_IN_BYTES);
            System.arraycopy(cipher.getIV(), 0, bytes, 0, IV_SIZE_IN_BYTES);
        } catch (Exception e){
            e.printStackTrace();
            return null;
        }

        return bytes;
    }

    private byte[] decrypt(byte[] data, byte[] aad){
        if (data.length < (IV_SIZE_IN_BYTES + TAG_SIZE_IN_BYTES)){
            Log.e(TAG, String.format("data length [%d] should > %d",data.length, (IV_SIZE_IN_BYTES + TAG_SIZE_IN_BYTES)));
            return null;
        }

        GCMParameterSpec gcmParameterSpec =
                new GCMParameterSpec(8 * TAG_SIZE_IN_BYTES, data, 0, IV_SIZE_IN_BYTES);

        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, mKey, gcmParameterSpec);
            cipher.updateAAD(aad);
            byte[] bytes = cipher.doFinal(data, IV_SIZE_IN_BYTES, data.length - IV_SIZE_IN_BYTES);
            return bytes;
        } catch (Exception e){
            e.printStackTrace();
            return null;
        }
    }
}
