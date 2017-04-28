package com.entertainment.nifi.controller;

import org.apache.nifi.reporting.InitializationException;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.Before;
import org.junit.Test;

/**
 * Created by dwang on 4/27/17.
 */
public class TestEncryptedPropertiesFileService {
    @Before
    public void init() {

    }

    @Test
    public void testService() throws InitializationException {
        final TestRunner runner = TestRunners.newTestRunner(TestStandardPropertiesFileProcessor.class);
        final PropertiesFileService service = new EncryptedPropertiesFileService();

        runner.addControllerService("test-encrypted", service);

        runner.setProperty(service, EncryptedPropertiesFileService.CONFIG_URI, "./target/test-classes/conf");
        runner.setProperty(service, EncryptedPropertiesFileService.PRIVATE_KEY_PATH, "./target/test-classes/pki/private.pem");
        runner.setProperty(service, EncryptedPropertiesFileService.PRIVATE_KEY_PASSPHRASE, "changeit");
        runner.setProperty(service, EncryptedPropertiesFileService.ENCRYPTED_PROPERTIES,"encrypted.password");
        runner.enableControllerService(service);
        String value = service.getProperty("encrypted", "password");
        System.out.println("Read property: "+value+"*");
        assert "changeit".equals(value);
        runner.assertValid(service);
    }

}