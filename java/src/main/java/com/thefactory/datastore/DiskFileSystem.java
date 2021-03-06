package com.thefactory.datastore;

import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Closeable;
import java.io.File;
import java.nio.ByteBuffer;
import java.lang.Override;
import java.util.Collection;
import java.util.ArrayList;


public class DiskFileSystem implements FileSystem {

    @Override
    public DatastoreChannel create(String name) {
        try {
            return new FileDatastoreChannel(new FileOutputStream(new File(name)).getChannel());
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to create " + name + ": " + e);
        }
    }

    @Override
    public DatastoreChannel open(String name) {
        try {
            return new FileDatastoreChannel(new FileInputStream(new File(name)).getChannel());
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to open " + name + " for writing: " + e);
        }
    }

    @Override
    public DatastoreChannel append(String name) {
        try {
            return new FileDatastoreChannel(new FileOutputStream(new File(name), true).getChannel());
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to open " + name + " for appending: " + e);
        }
    }

    @Override
    public boolean exists(String name) {
        return new File(name).exists();
    }

    @Override
    public void remove(String name) {
        new File(name).delete();
    }

    @Override
    public void rename(String oldName, String newName) {
        if(new File(oldName).renameTo(new File(newName))) {
            return;
        }
        throw new IllegalArgumentException("Failed to rename file: " + oldName + " to " + newName);
    }

    @Override
    public void mkdirs(String path) {
        if(!new File(path).mkdirs()) {
            throw new IllegalArgumentException("Failed to create path: " + path);
        }
    }

    @Override
    public Closeable lock(String name) throws IOException{
        File lockFile = new File(name);
        if(lockFile.isDirectory()) {
            throw new IOException("can't create lock file " + name 
                + " because there is already a directory existing with the same name");
        }
        if(!lockFile.exists() && !lockFile.createNewFile()) {
            throw new IOException("failed to create lock file " + name); 
        }
        FileChannel fc = new FileOutputStream(lockFile).getChannel();
        FileLock lock;
        try {
            lock = fc.tryLock();
        } catch (Exception e) {
            throw new IOException("Failed to obtain lock for " + name + " due to " + e);            
        }
        if(lock == null){
            throw new IOException("Failed to obtain lock for " + name);
        }
        return new Lock(lock);
    }

    @Override
    public void storeList(Collection<String> items, String name) throws IOException {
        FileWriter fw = new FileWriter(name);
        try {
            for(String item: items) {
                fw.write(String.format("%s\n", item));
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Storing list failed: " + e);            
        }
        finally {
            fw.close();
        }
    }

    @Override
    public Collection<String> loadList(String name) throws IOException {
        Collection<String> ret = new ArrayList<String>();
        FileReader fr = new FileReader(name);
        try {
            BufferedReader reader = new BufferedReader(fr);
            String line;
            while((line = reader.readLine()) != null){
                ret.add(line.trim());
            }
            return ret;
        } catch (Exception e) {
            throw new IllegalArgumentException("Loading list failed: " + e);            
        }
        finally {
            fr.close();
        }
    }

    public long size(String name) {
        File f = new File(name);
        if(!f.exists() || !f.isFile()){
            throw new IllegalArgumentException("Not found: " + name);            
        }
        return f.length();
    }

    private class FileDatastoreChannel implements DatastoreChannel{
        
        private final FileChannel channel;
        
        private FileDatastoreChannel(FileChannel channel){
            this.channel = channel;
        }

        @Override
        public int read(ByteBuffer dst) throws IOException {
            return channel.read(dst);
        }

        @Override
        public int read(ByteBuffer dst, long position) throws IOException{
            return channel.read(dst, position);
        }

        @Override
        public int write(ByteBuffer src) throws IOException {
            return channel.write(src);
        }

        @Override
        public void close() throws IOException {
            channel.close();
        }

        @Override
        public final boolean isOpen() {
            return channel.isOpen();
        }

        @Override
        public long size() throws IOException {
            return channel.size();
        }
    }

    private class Lock implements Closeable {

        private final FileLock lock;

        private Lock(FileLock lock){
            this.lock = lock;
        }

        @Override
        public void close() throws IOException {
            lock.release();
        }
    }


}