package com.entertainment.nifi.controller.util;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMDecryptorProvider;
import org.bouncycastle.openssl.PEMEncryptedKeyPair;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.misc.BASE64Decoder;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;


/**
 * Created by dwang on 4/27/17.
 */

/**
 * Created by dwang on 4/26/17.
 */
public class CryptoUtil {

    private static final Logger logger;
    public static Base64.Encoder B64Encoder = Base64.getEncoder();
    public static Base64.Decoder B64Decoder = Base64.getDecoder();

    static {

//        ClassLoader cl= ClassLoader.getSystemClassLoader();
//        URL[] urls = ((URLClassLoader)cl).getURLs();
//
//        for(URL url: urls) {
//            System.out.println(url.getFile());
//        }
//        //
        logger = LoggerFactory.getLogger(CryptoUtil.class);
        logger.info("Loading bouncycastle jce provider");
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());

    }

    public static void main(String[] args) throws Exception {
        //Security.addProvide r(new BouncyCastleProvider());
        byte[] input = "changeit".getBytes();
        System.out.println("original " + new String(input));

    }

    public static byte[] encrypt(PublicKey publicKey, String plain){
        byte[] results =null;
        try {
            Cipher cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            results = cipher.doFinal(plain.getBytes());
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
        } catch (BadPaddingException e) {
            e.printStackTrace();
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
        }
        return results;
    }

    public static String ecryptAndB64Encode(PublicKey publicKey, String plain) {
        byte[] results = encrypt(publicKey, plain);
        return encodeBase64(results);
    }

    public static byte[] decodeBase64(byte[] b64Bytes) {
        return B64Decoder.decode(b64Bytes);
    }

    public static byte[] decodeBase64(String b64String) {
        return decodeBase64(b64String.getBytes());
    }

    public static String encodeBase64(byte[] bytes) {
        return B64Encoder.encodeToString(bytes);
    }

    public static String decryptB64Encoded(PrivateKey privKey, byte[] encryptedB64) {
        Cipher cipher;
        String result = null;
        try {
            cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.DECRYPT_MODE, privKey);
            byte[] inputs = decodeBase64(encryptedB64);
            byte[] decryptedBytes = cipher.doFinal(inputs);
            result = new String(decryptedBytes);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (BadPaddingException e) {
            e.printStackTrace();
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
        }
        return result;
    }

    public static String decryptB64Encoded(PrivateKey privKey, String encryptedB64) {
        return decryptB64Encoded(privKey, encryptedB64.getBytes());
    }

    public static PrivateKey loadPrivatekey(String file, String password) {
        PrivateKey privateKey = null;
        FileReader reader = null;
        try {
            reader = new FileReader(file);
            PEMParser pemParser = new PEMParser(reader);
            Object object = pemParser.readObject();
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");
            KeyPair kp;
            if (object instanceof PEMEncryptedKeyPair) {
                // Encrypted key - we will use provided password
                if (password == null) {
                    throw new IOException("Invalid password for private key file");
                }
                PEMEncryptedKeyPair ckp = (PEMEncryptedKeyPair) object;
                PEMDecryptorProvider decProv = new JcePEMDecryptorProviderBuilder().build(password.toCharArray());
                kp = converter.getKeyPair(ckp.decryptKeyPair(decProv));
            } else {
                // Unencrypted key - no password needed
                PEMKeyPair ukp = (PEMKeyPair) object;
                kp = converter.getKeyPair(ukp);
            }

            privateKey = kp.getPrivate();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                }
            }
        }
        return privateKey;
    }

    public static PublicKey loadPublicKey(String filename) {
        PublicKey publicKey=null;
        File f = new File(filename);
        try {
            FileInputStream fis = new FileInputStream(f);
            DataInputStream dis = new DataInputStream(fis);
            byte[] keyBytes = new byte[(int) f.length()];
            dis.readFully(keyBytes);
            dis.close();

            String temp = new String(keyBytes);
            String publicKeyPEM = temp.replace("-----BEGIN PUBLIC KEY-----\n", "");
            publicKeyPEM = publicKeyPEM.replace("-----END PUBLIC KEY-----", "");


            BASE64Decoder b64=new BASE64Decoder();
            byte[] decoded = b64.decodeBuffer(publicKeyPEM);

            X509EncodedKeySpec spec =
                    new X509EncodedKeySpec(decoded);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            publicKey =  kf.generatePublic(spec);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InvalidKeySpecException e) {
            e.printStackTrace();
        }
        return publicKey;
    }
}

