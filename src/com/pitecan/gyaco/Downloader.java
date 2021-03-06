package com.pitecan.gyaco;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

import android.util.Log;

public class Downloader {
    private int connectTimeout = 0;
    private int readTimeout = 0;
	  
    public void setConnectTimeout(int timeout){
	connectTimeout = timeout;
    }
	  
    public void setReadTimeout(int timeout){
	readTimeout = timeout;
    }
	
    public long getLastModified(String url) throws IOException{
    	HttpURLConnection con =  (HttpURLConnection)new URL(url).openConnection();
    	con.setRequestMethod("HEAD");
    	con.setConnectTimeout(connectTimeout);
    	con.setReadTimeout(readTimeout);
    	con.connect();
    	long date = con.getLastModified();
    	Log.v("Gyaco - File Last modified", Long.toString(date));
    	return date;
    }
    public byte[] getContent(String url) throws IOException {
	HttpURLConnection con =  (HttpURLConnection)new URL(url).openConnection();
	con.setRequestMethod("GET");
	con.setConnectTimeout(connectTimeout);
	con.setReadTimeout(readTimeout);
	con.connect();
	InputStream in = con.getInputStream();
	byte[] content;
	try{
	    content = toBytes(in);
	}finally{
	    in.close();
	}
	return content;
    }
	  
    private static byte[] toBytes(InputStream in) throws IOException {
	ByteArrayOutputStream out = new ByteArrayOutputStream();
	copy(in, out);
	return out.toByteArray();
    }
	 
    private static void copy(InputStream in, OutputStream out) throws IOException {
	byte[] buff = new byte[256];
	int len = in.read(buff);
	while (len != -1){
	    out.write(buff, 0, len);
	    len = in.read(buff);
	}
    }
}
