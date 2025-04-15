import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Room {
    private String id;
    private String name;
    private String accessCode;
    private List<String> messages;
    private List<String> users;
    
    public Room(String name, String accessCode) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.accessCode = accessCode;
        this.messages = new ArrayList<>();
        this.users = new ArrayList<>();
    }
    
    // For loading from database
    public Room(String id, String name, String accessCode) {
        this.id = id;
        this.name = name;
        this.accessCode = accessCode;
        this.messages = new ArrayList<>();
        this.users = new ArrayList<>();
    }
    
    public String getId() {
        return id;
    }
    
    public String getName() {
        return name;
    }
    
    public String getAccessCode() {
        return accessCode;
    }
    
    public List<String> getMessages() {
        return messages;
    }
    
    public void addMessage(String message) {
        messages.add(message);
        // Limit message history to last 100 messages
        if (messages.size() > 100) {
            messages.remove(0);
        }
    }
    
    public void addUser(String username) {
        if (!users.contains(username)) {
            users.add(username);
        }
    }
    
    public void removeUser(String username) {
        users.remove(username);
    }
    
    public List<String> getUsers() {
        return users;
    }
    
    public boolean verifyAccessCode(String code) {
        return this.accessCode.equals(code);
    }
}