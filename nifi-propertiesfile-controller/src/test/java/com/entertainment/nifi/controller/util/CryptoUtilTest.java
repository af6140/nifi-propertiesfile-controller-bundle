package com.entertainment.nifi.controller.util;

import org.junit.Before;
import org.junit.Test;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.security.*;

/**
 * Created by dwang on 4/27/17.
 */
public class CryptoUtilTest {

    @Before
    public void setUp() {
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
    }

    @Test
    public void testCryptoUtil() {
        String inputString = "changeit";
        byte[] input = inputString.getBytes();
        System.out.println("original " + new String(input));


        // Create the public and private keys

        PrivateKey privateKey = CryptoUtil.loadPrivatekey("target/test-classes/pki/private.pem", "changeit");
        PublicKey publicKey = CryptoUtil.loadPublicKey("target/test-classes/pki/public.pem");
        String ecrypted = CryptoUtil.ecryptAndB64Encode(publicKey, inputString);
        System.out.println("Encrypted: " + ecrypted);
        String decoded = CryptoUtil.decryptB64Encoded(privateKey, ecrypted);
        System.out.println("decoded: " + decoded);
        assert inputString.equals(decoded);

    }
}
