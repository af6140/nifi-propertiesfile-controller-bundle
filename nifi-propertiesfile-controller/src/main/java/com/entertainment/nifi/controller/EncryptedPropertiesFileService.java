package com.entertainment.nifi.controller;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.annotation.lifecycle.OnEnabled;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.controller.ConfigurationContext;
import org.apache.nifi.controller.ControllerServiceInitializationContext;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.reporting.InitializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.entertainment.nifi.controller.util.CryptoUtil;

/**
 * Created by dwang on 4/26/17.
 */
@Tags({"rsa", "encrypted", "properties"})
@CapabilityDescription("PorpertiesFileService implementation that support RSA encrypted and base64 encoded property,"
        +" loading one property file or files from uri, the properties can then be looked up.")
public class EncryptedPropertiesFileService extends StandardPropertiesFileService {

    private static final Logger log = LoggerFactory.getLogger(EncryptedPropertiesFileService.class);

    public static final String PROPERTY_SEPERATOR = ",";

    public static final PropertyDescriptor PRIVATE_KEY_PATH = new PropertyDescriptor.Builder()
            .name("Private RSA Key Path")
            .description("The fully qualified path to the Private Key file")
            .required(true)
            .addValidator(StandardValidators.FILE_EXISTS_VALIDATOR)
            .build();
    public static final PropertyDescriptor PRIVATE_KEY_PASSPHRASE = new PropertyDescriptor.Builder()
            .name("Private Key Passphrase")
            .description("Password for the private key")
            .required(false)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .sensitive(true)
            .build();

    public static final PropertyDescriptor ENCRYPTED_PROPERTIES = new PropertyDescriptor.Builder()
            .name("Encrypted Properties")
            .description("Properties that are encrypted, seperated by ',', in the format of scope.propertyName")
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .required(false)
            .build();

    private PrivateKey privateKey;
    private List<String> ecryptedProperties;

    @Override
    public List<PropertyDescriptor> init_properties(ControllerServiceInitializationContext config) {
        List<PropertyDescriptor> props = super.init_properties(config);
        props.add(PRIVATE_KEY_PATH);
        props.add(PRIVATE_KEY_PASSPHRASE);
        props.add(ENCRYPTED_PROPERTIES);
        return props;
    }


    public String getProperty(String scope, String key) {
        String value = null;
        String propKey = new StringBuilder(scope).append('.').append(key).toString();
        boolean isEncrypted  = this.ecryptedProperties.contains(propKey);
        if(!isEncrypted) {
            value = super.getProperty(scope, key);
        }else {
            //assume it's base64 encrypted
            log.info("Property is encrypted");
            String encryptedValue = super.getProperty(scope, key);
            value = CryptoUtil.decryptB64Encoded(this.privateKey, encryptedValue);
        }
        return value;
    }

    @OnEnabled
    @Override
    public void onConfigured(final ConfigurationContext context) throws InitializationException {
        if (this.ecryptedProperties ==null ) {
            this.ecryptedProperties=new ArrayList<String>();
            String encryptedPropertiesConfigured = context.getProperty(ENCRYPTED_PROPERTIES).getValue();

            if(encryptedPropertiesConfigured!=null){
                String[] propKeys = encryptedPropertiesConfigured.split(PROPERTY_SEPERATOR);
                this.ecryptedProperties.addAll(Arrays.asList(propKeys));
            }
        }
        if(this.privateKey==null) {
            String keyPath = context.getProperty(PRIVATE_KEY_PATH).getValue();
            String keyPass = context.getProperty(PRIVATE_KEY_PASSPHRASE).getValue();
            PrivateKey privateKey = CryptoUtil.loadPrivatekey(keyPath, keyPass);
            this.privateKey = privateKey;
        }
        super.onConfigured(context);
    }

}
