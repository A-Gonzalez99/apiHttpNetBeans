package com.alejandroj.apihttp;


import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.*;
import org.json.*;

public class ApiHttp {

    private static final int PORT = 8080;
    private static final String DB_URL = "jdbc:mysql://localhost:3330/library_db";
    private static final String DB_USER = "root"; // Cambia por tu usuario
    private static final String DB_PASSWORD = "1234"; // Cambia por tu contraseña

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Servidor HTTP iniciado en el puerto " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(() -> handleRequest(clientSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleRequest(Socket clientSocket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             OutputStream out = clientSocket.getOutputStream()) {

            String requestLine = in.readLine();
            if (requestLine == null) return;

            String[] requestParts = requestLine.split(" ");
            String method = requestParts[0];
            String path = requestParts[1];

            int contentLength = 0;
            String line;
            while (!(line = in.readLine()).isEmpty()) {
                if (line.startsWith("Content-Length:")) {
                    contentLength = Integer.parseInt(line.substring("Content-Length:".length()).trim());
                }
            }

            StringBuilder requestBody = new StringBuilder();
            if (contentLength > 0) {
                char[] body = new char[contentLength];
                in.read(body, 0, contentLength);
                requestBody.append(body);
            }

            String response = handleEndpoint(method, path, requestBody.toString());

            out.write(("HTTP/1.1 200 OK\r\n").getBytes());
            out.write(("Content-Type: application/json\r\n").getBytes());
            out.write(("\r\n").getBytes());
            out.write(response.getBytes());

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private static String handleEndpoint(String method, String path, String requestBody) {
        try {
            if (path.equals("/books") && method.equals("GET")) {
                return getAllBooks();
            } else if (path.startsWith("/books/") && method.equals("GET")) {
                int id = Integer.parseInt(path.split("/")[2]);
                return getBookById(id);
            } else if (path.equals("/books") && method.equals("POST")) {
                return createBook(requestBody);
            } else if (path.startsWith("/books/") && method.equals("PUT")) {
                int id = Integer.parseInt(path.split("/")[2]);
                return updateBook(id, requestBody);
            } else if (path.startsWith("/books/") && method.equals("DELETE")) {
                int id = Integer.parseInt(path.split("/")[2]);
                return deleteBook(id);
            } else {
                return "{\"error\": \"Endpoint no válido\"}";
            }
        } catch (Exception e) {
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    private static String getAllBooks() throws SQLException {
        List<Map<String, Object>> books = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM books")) {

            while (rs.next()) {
                Map<String, Object> book = new HashMap<>();
                book.put("id", rs.getInt("id"));
                book.put("title", rs.getString("title"));
                book.put("author", rs.getString("author"));
                book.put("year", rs.getInt("year"));
                books.add(book);
            }
        }
        return new JSONArray(books).toString();
    }

    private static String getBookById(int id) throws SQLException {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement("SELECT * FROM books WHERE id = ?")) {

            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                Map<String, Object> book = new HashMap<>();
                book.put("id", rs.getInt("id"));
                book.put("title", rs.getString("title"));
                book.put("author", rs.getString("author"));
                book.put("year", rs.getInt("year"));
                return new JSONObject(book).toString();
            } else {
                return "{\"error\": \"Libro no encontrado\"}";
            }
        }
    }

    private static String createBook(String requestBody) throws SQLException, JSONException {
        try {
            JSONObject bookJson = new JSONObject(requestBody); // Intenta parsear el JSON
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                 PreparedStatement stmt = conn.prepareStatement(
                         "INSERT INTO books (title, author, year) VALUES (?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {

                stmt.setString(1, bookJson.getString("title"));
                stmt.setString(2, bookJson.getString("author"));
                stmt.setInt(3, bookJson.getInt("year"));
                stmt.executeUpdate();

                ResultSet rs = stmt.getGeneratedKeys();
                if (rs.next()) {
                    bookJson.put("id", rs.getInt(1));
                }
                return bookJson.toString();
            }
        } catch (JSONException e) {
            return "{\"error\": \"JSON inválido: " + e.getMessage() + "\"}";
        }
    }

    private static String updateBook(int id, String requestBody) throws SQLException, JSONException {
        JSONObject bookJson = new JSONObject(requestBody);
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(
                     "UPDATE books SET title = ?, author = ?, year = ? WHERE id = ?")) {

            stmt.setString(1, bookJson.getString("title"));
            stmt.setString(2, bookJson.getString("author"));
            stmt.setInt(3, bookJson.getInt("year"));
            stmt.setInt(4, id);
            int rowsUpdated = stmt.executeUpdate();

            if (rowsUpdated > 0) {
                return "{\"message\": \"Libro actualizado\"}";
            } else {
                return "{\"error\": \"Libro no encontrado\"}";
            }
        }
    }

    private static String deleteBook(int id) throws SQLException {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM books WHERE id = ?")) {

            stmt.setInt(1, id);
            int rowsDeleted = stmt.executeUpdate();

            if (rowsDeleted > 0) {
                return "{\"message\": \"Libro eliminado\"}";
            } else {
                return "{\"error\": \"Libro no encontrado\"}";
            }
        }
    }
}