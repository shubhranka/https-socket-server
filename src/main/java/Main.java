import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HexFormat;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;

public class Main {

    public static byte[] compress(String input) throws IOException {
        // Create a Deflater instance
        Deflater deflater = new Deflater();

        // Set the input data to be compressed
        deflater.setInput(input.getBytes());
        deflater.finish();

        // Create an output buffer
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(input.length());

        // Compress the data
        byte[] buffer = new byte[1024];
        while (!deflater.finished()) {
            int count = deflater.deflate(buffer);
            outputStream.write(buffer, 0, count);
        }

        // Clean up
        outputStream.close();
        deflater.end();

        // Return the compressed data
        return outputStream.toByteArray();
    }
    public static byte[] gzipEncode(String value) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             GZIPOutputStream gzipOutputStream = new GZIPOutputStream(baos)) {
            byte[] bytes = value.getBytes();
            gzipOutputStream.write(bytes);
            gzipOutputStream.finish();
            byte[] compressedBytes = baos.toByteArray();
            return compressedBytes;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
    private static String readRequestBody(BufferedReader in, int contentLength) throws IOException {
        char[] buffer = new char[contentLength];
        int bytesRead = in.read(buffer, 0, contentLength);
        if (bytesRead != contentLength) {
            throw new IOException("Failed to read the entire request body");
        }
        return new String(buffer);
    }
  public static void main(String[] args) {
      // You can use print statements as follows for debugging, they'll be visible when running tests.
      System.out.println("Logs from your program will appear here!");

      // Uncomment this block to pass the first stage

      ServerSocket serverSocket = null;
      Socket clientSocket = null;

      String directory = "";
      if (args.length > 1 && args[0].equals("--directory")) {
          directory = args[1];
      }

      try {
          serverSocket = new ServerSocket(4221);
          // Since the tester restarts your program quite often, setting SO_REUSEADDR
          // ensures that we don't run into 'Address already in use' errors
          serverSocket.setReuseAddress(true);
          while (true) {
              clientSocket = serverSocket.accept(); // Wait for connection from client.
              System.out.println("accepted new connection");

              BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
              String[] paths = new String[0];
              boolean userAgent = false;
              boolean file = false;
              boolean addFile = false;
              boolean echo = false;
              String fileName = "";
              String fileData = "";
              String userAgentValue = "";
              String line;
              String encoding = "";

              while ((line = in.readLine()) != null) {
                  if (line.isEmpty()) {
                      break;
                  }
                  System.out.println(line);
                  if (line.contains("GET")) {
                      String path = line.split(" ")[1];
                      paths = path.split("/");
                      if (paths.length > 1 && paths[1].equals("user-agent")) {
                          userAgent = true;
                      } else if (paths.length > 1 && paths[1].equals("files")) {
                          file = true;
                          fileName = paths[2];
                      } else if (paths.length > 1 && paths[1].equals("echo")) {
                          echo = true;
                      }
                  }
                  if (line.contains("POST")) {
                      String path = line.split(" ")[1];
                      paths = path.split("/");
                      if (paths.length > 1 && paths[1].equals("files")) {
                          addFile = true;
                          fileName = paths[2];
                      }
                  }
                  if (line.toLowerCase().startsWith("user-agent:")) {
                      userAgentValue = line.split(" ")[1];
                  }
                  if (line.toLowerCase().startsWith("content-length:")) {
                      int contentLength = Integer.parseInt(line.split(" ")[1]);
                      fileData = readRequestBody(in, contentLength + 2);
                      fileData = fileData.trim();
                      System.out.println("File Data: " + fileData);
                      break;
                  }
                  if (line.toLowerCase().startsWith("accept-encoding:")) {
                      if(line.contains("gzip")){
                            encoding = "gzip";
                      }
                  }
              }
              if (echo) {
                  String value = paths[2];
                  if (!encoding.isBlank()) {
                      if (encoding.equals("gzip")) {
                          byte[] encodedValue = gzipEncode(value);
                          clientSocket.getOutputStream().write(String.format("HTTP/1.1 200 OK\r\nContent-Encoding: gzip\r\nContent-Type: text/plain\r\nContent-Length: %d\r\n\r\n",encodedValue.length).getBytes("UTF-8"));
                            clientSocket.getOutputStream().write(encodedValue);
                      } else {
                            clientSocket.getOutputStream().write(String.format("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 3\r\n\r\nbar").getBytes());
                      }
                  }
                      clientSocket.getOutputStream().write(String.format("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: %d\r\n\r\n%s", value.length(), value).getBytes());
                  } else if (file) {
                      File myFile = new File(directory + "/" + fileName);
                      if (myFile.exists()) {
                          String text = new String(Files.readAllBytes(myFile.toPath()), StandardCharsets.UTF_8);
                          String response = String.format("HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nContent-Length: %d\r\n\r\n%s", myFile.length(), text);
                          clientSocket.getOutputStream().write(response.getBytes());
                      } else {
                          clientSocket.getOutputStream().write("HTTP/1.1 404 Not Found\r\n\r\n".getBytes());
                      }
                  } else if (addFile) {
                      System.out.println("Request Body: " + fileData);
                      File myFile = new File(directory + "/" + fileName);
                      Files.write(myFile.toPath(), fileData.getBytes());
                      clientSocket.getOutputStream().write("HTTP/1.1 201 Created\r\n\r\n".getBytes());
                      System.out.println("File created");
                  } else if (userAgent) {
                      clientSocket.getOutputStream().write(String.format("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: %d\r\n\r\n%s", userAgentValue.length(), userAgentValue).getBytes());
                  } else if (paths.length > 1) {
                      clientSocket.getOutputStream().write("HTTP/1.1 404 Not Found\r\n\r\n".getBytes());
                  } else {
                      clientSocket.getOutputStream().write("HTTP/1.1 200 OK\r\n\r\n".getBytes());
                  }
              }
          } catch(IOException e){
              System.out.println("IOException: " + e.getMessage());
          }
      }
  }
