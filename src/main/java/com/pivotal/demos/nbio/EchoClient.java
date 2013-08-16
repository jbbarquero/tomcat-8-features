package com.pivotal.demos.nbio;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.RandomStringUtils;

public class EchoClient {
	public static void main(String[] args) throws Exception {
		EchoClient echoClient = new EchoClient();
		Map<String, List<String>> resHeaders = new HashMap<>();
//		System.out.println("Out [" + 
//				echoClient.doPost(false, new EmptyBytesStreamer(), resHeaders, null) +
//			"]");
//		System.out.println("Out [" + 
//				echoClient.doPost(false, new HelloBytesStreamer(), resHeaders, null) +
//			"]");
		System.out.println("Out [" + 
				echoClient.doPost(true, new RandomBytesStreamer(), resHeaders, null) +
			"]");
//		System.out.println("Out [" + 
//				echoClient.doPost(false, new RandomBytesStreamer(512, 16), resHeaders, null) +
//			"]");
//		System.out.println("Out [" + 
//				echoClient.doPost(false, new RandomBytesStreamer(1048576, 16), resHeaders, null) +
//			"]");
	}
	
	private static final String DEFAULT_PATH = 
			"http://localhost:8080/tomcat-8-demos/non-blocking-io/EchoNbioServlet";
	
	public String doPost(boolean stream, BytesStreamer streamer,
            Map<String, List<String>> reqHead,
            Map<String, List<String>> resHead) throws IOException
    {
        URL url = new URL(DEFAULT_PATH);
        HttpURLConnection connection =
            (HttpURLConnection) url.openConnection();
        connection.setDoOutput(true);
        connection.setReadTimeout(1000000);
        if (reqHead != null) {
            for (Map.Entry<String, List<String>> entry : reqHead.entrySet()) {
                StringBuilder valueList = new StringBuilder();
                for (String value : entry.getValue()) {
                    if (valueList.length() > 0) {
                        valueList.append(',');
                    }
                    valueList.append(value);
                }
                connection.setRequestProperty(entry.getKey(),
                        valueList.toString());
            }
        }
        if (streamer != null && stream) {
            if (streamer.getLength()>0) {
                connection.setFixedLengthStreamingMode(streamer.getLength());
            } else {
                connection.setChunkedStreamingMode(1024);
            }
        }

        connection.connect();

        // Write the request body
        OutputStream os = null;
        try {
            os = connection.getOutputStream();
            while (streamer!=null && streamer.available()>0) {
                byte[] next = streamer.next();
                os.write(next);
                os.flush();
            }

        } finally {
            if (os != null) {
                try {
                    os.close();
                } catch (IOException ioe) {
                    // Ignore
                }
            }
        }

        int rc = connection.getResponseCode();
        if (resHead != null) {
            Map<String, List<String>> head = connection.getHeaderFields();
            resHead.putAll(head);
        }
        InputStream is;
        if (rc < 400) {
            is = connection.getInputStream();
        } else {
            is = connection.getErrorStream();
        }

        StringBuilder sb = new StringBuilder();
        BufferedInputStream bis = null;
        try {
            bis = new BufferedInputStream(is);
            byte[] buf = new byte[2048];
            int rd = 0;
            while((rd = bis.read(buf)) > 0) {
                sb.append(new String(buf, "utf-8"));
            }
        } finally {
            if (bis != null) {
                try {
                    bis.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
        return sb.toString();
	}
	
	private static interface BytesStreamer {
	    int getLength();
	    int available();
	    byte[] next();
	}
	
	private static class EmptyBytesStreamer implements BytesStreamer {
		public int getLength() {
			return 0;
		}
		public int available() {
			return 0;
		}
		public byte[] next() {
			return null;
		}
	}
	
	private static class HelloBytesStreamer implements BytesStreamer {
		private byte[] data = "Hello World!".getBytes();
		private int available = data.length;
		
		public int getLength() {
			return data.length;
		}
		public int available() {
			return available;
		}
		public byte[] next() {
			available = 0;
			return data;
		}
	}
	
	private static class RandomBytesStreamer implements BytesStreamer {
		private int available = 10;
		private int total = 10;
		private int chunkSize = 1;
		
		public RandomBytesStreamer() {}
		
		public RandomBytesStreamer(int total, int chunkSize) {
			this.total = total;
			this.available = total;
			this.chunkSize = chunkSize;
		}
		
		public int getLength() {
			return total;
		}
		public int available() {
			return available;
		}
		public byte[] next() {
			available -= chunkSize;
			byte[] data;
			try {
				String tmp = RandomStringUtils.randomAlphanumeric(chunkSize);
				System.out.println("Writing [" + tmp + "]");
				data = tmp.getBytes("utf-8");
			} catch (UnsupportedEncodingException e) {
				data = e.getMessage().getBytes();
			}
			try {
				System.out.println("Pausing...");
				Thread.sleep(1 * 1000);
			} catch (InterruptedException ex) {
				// ignore
			}
			return data;
		}
	}
}