package com.entertainment.nifi.controller.util;

import org.apache.nifi.util.file.monitor.UpdateMonitor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.ByteBuffer;

import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;


/**
 * Created by dwang on 6/9/17.
 */
public class DirectoryUpdateMonitor implements UpdateMonitor {
    @Override
    public Object getCurrentState(Path path) throws IOException {
        HashMap<String,ByteBuffer> states =new HashMap<String,ByteBuffer>();
        final File propFile = path.toFile();
        if (propFile.isDirectory()) {
            File[] propFiles = propFile.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File fileDir, String fileName) {
                    return fileName.endsWith(".properties");
                }
            });

            for(File f: propFiles) {
                ByteBuffer md5 = null;
                try {
                    md5=this.getMD5(f);
                    states.put(f.getAbsolutePath(), md5);
                }catch(IOException e) {
                    System.out.println(e.getMessage());
                }
            }
        } else if (propFile.isFile()) {
            try {
                ByteBuffer md5=this.getMD5(propFile);
                states.put(propFile.getAbsolutePath(), md5);
            }catch(IOException e) {
                System.out.println(e.getMessage());
            }

        }
        return states;
    }

    private ByteBuffer getMD5(File f) throws IOException{
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("MD5");
        } catch (final NoSuchAlgorithmException nsae) {
            throw new AssertionError(nsae);
        }

        try (final FileInputStream fis = new FileInputStream(f)) {
            int len;
            final byte[] buffer = new byte[8192];
            while ((len = fis.read(buffer)) > -1) {
                if (len > 0) {
                    digest.update(buffer, 0, len);
                }
            }
        }

        // Return a ByteBuffer instead of byte[] because we want equals() to do a deep equality
        return ByteBuffer.wrap(digest.digest());
    }
}
