package org.apache.synapse.transport.netty.passthru;


import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.HttpContent;

import java.io.IOException;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Pipe {



private String name ="Buffer";

private byte[] content;

 private BlockingQueue<HttpContent> contentQueue = new LinkedBlockingQueue<HttpContent>();


private Map   trailingheaders = new ConcurrentHashMap<String, String>();

private Lock lock = new ReentrantLock();

    private Condition readCondition = lock.newCondition();

public Pipe(String name){
    this.name= name;

}


public void writeContent(DefaultHttpContent defaultHttpContent){
    lock.lock();
    try{
        ByteBuf buf = defaultHttpContent.content();
        content = new byte[buf.readableBytes()];
        buf.readBytes(content);
        readCondition.signalAll();

    }finally {
        lock.unlock();
    }

}

    public void writeFullContent(byte[] bytes){
        content = bytes;
    }



public byte[] readContent(){
    lock.lock();
    try{
       waitForData();
        return content;
    } catch (IOException e) {
        e.printStackTrace();
    } finally{
        lock.unlock();
    }
    return content;
}

public void addTrailingHeader(String key,String value){
    trailingheaders.put(key,value);
}


public Map getTrailingheaderMap(){
    return trailingheaders;
}


    private void waitForData() throws IOException {
        lock.lock();
        try {
            try {
                while (content==null) {
                    readCondition.await();
                }
            } catch (InterruptedException e) {
                throw new IOException("Interrupted while waiting for data");
            }
        } finally {
            lock.unlock();
        }
    }

   public HttpContent getContent(){
       try {
           return  contentQueue.take();
       } catch (InterruptedException e) {
           e.printStackTrace();
           return null;
       }
   }

   public void addContent(HttpContent defaultHttpContent){
       contentQueue.add(defaultHttpContent);
   }

}
