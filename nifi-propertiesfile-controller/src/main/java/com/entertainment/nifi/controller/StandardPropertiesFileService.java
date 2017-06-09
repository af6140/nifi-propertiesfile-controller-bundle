package com.entertainment.nifi.controller;

import com.entertainment.nifi.controller.PropertiesFileService;
import com.entertainment.nifi.controller.util.DirectoryUpdateMonitor;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.annotation.lifecycle.OnDisabled;
import org.apache.nifi.annotation.lifecycle.OnEnabled;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.controller.AbstractControllerService;
import org.apache.nifi.controller.ConfigurationContext;
import org.apache.nifi.controller.ControllerServiceInitializationContext;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.reporting.InitializationException;
import org.apache.nifi.util.file.monitor.LastModifiedMonitor;
import org.apache.nifi.util.file.monitor.SynchronousFileWatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Created by dwang on 4/26/17.
 * Reuse code from https://github.com/pcgrenier/nifi-examples
 */
@Tags({"rsa", "properties"})
@CapabilityDescription("PorpertiesFileService implementaion, loading one property file or files from uri, the properties can then be looked up.")
public class StandardPropertiesFileService extends AbstractControllerService implements PropertiesFileService {

    private static final Logger log = LoggerFactory.getLogger(StandardPropertiesFileService.class);
    public static final PropertyDescriptor CONFIG_URI = new PropertyDescriptor.Builder()
            .name("Configuration Directory")
            .description("Configuration directory for properties files.")
            .defaultValue(null)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    public static final PropertyDescriptor RELOAD_INTERVAL = new PropertyDescriptor.Builder()
            .name("Reload Interval")
            .description("Time before looking for changes")
            .defaultValue("10 min")
            .required(true)
            .addValidator(StandardValidators.TIME_PERIOD_VALIDATOR)
            .build();

    private List<PropertyDescriptor> serviceProperties;


    private ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private ScheduledExecutorService executor;
    private Map<String, Properties> properties = new HashMap<String,Properties>();
    private SynchronousFileWatcher fileWatcher;

    private String configUri;
    private long reloadIntervalMilli;

    @Override
    protected void init(ControllerServiceInitializationContext config) throws InitializationException {
        List<PropertyDescriptor> props = init_properties(config);
        serviceProperties = Collections.unmodifiableList(props);
        System.out.println("logger class:"+log.getClass().toString());
    }

    protected List<PropertyDescriptor> init_properties(ControllerServiceInitializationContext config) {
        List<PropertyDescriptor> props = new ArrayList<>();
        props.add(CONFIG_URI);
        props.add(RELOAD_INTERVAL);
        return props;
    }

    @Override
    protected List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        return serviceProperties;
    }

    @OnEnabled
    public void onEnabled(final ConfigurationContext context) {
        log.info("Starting properties file service");
        configUri = context.getProperty(CONFIG_URI).getValue();
        reloadIntervalMilli = context.getProperty(RELOAD_INTERVAL).asTimePeriod(TimeUnit.MILLISECONDS);

        // Initialize the properties
        log.debug("Loading properties");
        loadPropertiesFiles();
        //it's really a state comparator, no magic, synchronous calls, no threads here
        // path may change, so we need create one
        fileWatcher = new SynchronousFileWatcher(Paths.get(configUri), new DirectoryUpdateMonitor());
        //recreate executor
        executor = Executors.newSingleThreadScheduledExecutor();
        FilesWatcherWorker reloadTask = new FilesWatcherWorker();
        executor.scheduleWithFixedDelay(reloadTask, reloadIntervalMilli, reloadIntervalMilli, TimeUnit.MILLISECONDS);

    }

    @OnDisabled
    public void onDisabled() {
        log.info("Stopping properties file service");
        if(executor!=null) {
            executor.shutdown();
            try {
                executor.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }finally {
                if(!executor.isTerminated()) {
                    executor.shutdownNow();
                }
            }
            executor=null;
        }
    }

    protected void loadPropertiesFiles(){
        log.info("Start loading properties files from (" + configUri + ")");
        File[] propFiles = new File[1];

        lock.readLock().lock();
        try {
            log.info("Read Locked. (" + configUri + ")");
            final File propFile = new File(configUri);

            // Leave room for a single file if that is what is configured

            if (propFile.isDirectory()) {
                log.info("Is directory");
                propFiles = propFile.listFiles(new FilenameFilter() {
                    @Override
                    public boolean accept(File fileDir, String fileName) {
                        return fileName.endsWith(".properties");
                    }
                });
            } else if (propFile.isFile()) {
                log.info("Is file");
                propFiles[0] = propFile;
            } else {
                log.info("What type of file is it?");
            }
        }finally {
            lock.readLock().unlock();
        }

        lock.writeLock().lock();
        try{
            log.info("Start loading properties");
            // Clear all properties
            for(File entry : propFiles){
                BufferedInputStream in = new BufferedInputStream(new FileInputStream(entry));
                String entry_name = entry.toPath().getFileName().toString();
                log.info("Start loading " + entry_name);
                String only_name = entry_name.replace(".properties", "");
                if(properties.containsKey(only_name)) {
                    Properties currentProperties = properties.get(only_name);
                    currentProperties.clear();
                    currentProperties.load(in);
                }else {
                    Properties currentProperties = new Properties();
                    currentProperties.load(in);
                    properties.put(only_name, currentProperties);
                }
            }
        } catch (FileNotFoundException e) {
            log.error("Could not find file", e);
        } catch (IOException e) {
            log.error("Failed to read file", e);
        } finally{
            lock.writeLock().unlock();
        }
    }

    private class FilesWatcherWorker implements Runnable {
        @Override
        public void run() {
            try{
                log.info("Check file watcher");
                if(fileWatcher.checkAndReset()){
                    //log.error("I found a change?");
                    log.info("Properties file change found");
                    loadPropertiesFiles();
                }
            } catch (IOException e) {
                log.error("Failed to check file watcher!", e);
            }
        }
    }

    public String getProperty(String scope, String key) {
        lock.readLock().lock();
        try{
            Properties scopeProperties= properties.get(scope);
            log.info("Looking up property {} in scope {}", new Object[]{key, scope});
            if (scopeProperties!=null) {
                return scopeProperties.getProperty(key);
            }else {
                log.warn("Scope " + scope + " not found!");
                return null;
            }
        }finally{
            lock.readLock().unlock();
        }
    }

}

