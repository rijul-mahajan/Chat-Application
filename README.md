# 💬 OpenChat

**OpenChat** is a Java-based client-server chat application that supports multiple chat rooms, user management, and persistent message storage using an integrated database system. It also features a Server Manager utility for starting/stopping servers, modifying configurations like IPs/ports, and viewing logs.

<br/>

## 🚀 Features

- 👥 Multi-client chat support

- 💬 Chat rooms with isolated conversations

- 💾 Persistent message history using a database

- 📦 Modular design: `Client`, `Server`, `Room`, `DatabaseManager`

- 🛠 Server Manager utility to:

  - Start/stop the server
  - Modify IP address or port
  - View logs and room/user information

<br/>

## ⚙️ Requirements

- Java 11 or higher
- JDBC-compatible database (e.g., SQLite, MySQL)

> **Note:** You must configure the database connection in `DatabaseManager.java` before running the app.

<br/>

## 🧪 How It Works

### 🖥 Server Side

1.  `Server.java` listens on a port for client connections.
2.  On each connection, it spawns a new thread to manage the client.
3.  Messages are routed via `Room.java`, ensuring separation of chat rooms.
4.  All messages and metadata are stored in the database via `DatabaseManager.java`.

### 💻 Client Side

1.  `Client.java` connects to the server.
2.  Users can join/create rooms and exchange messages in real-time.
3.  Messages are displayed in the terminal.

<br/>

## 🛠 Server Manager Utility

The `Server Manager` is a bundled admin tool used to manage your server instance. It allows you to:

- Start and stop the server process
- Change the server IP or port
- View logs and errors
- Monitor user activity and room details

> ⚠️ **Do not delete or modify this utility** if you're using precompiled builds. It's required for server lifecycle control.

<br/>

## 🧑‍💻 Usage

For Regular Users (Chatting):

- Use "OpenChat" to join chat rooms and participate in conversations.

For Server Administrators:

- Use "Server Manager" to set up and manage chat servers.
- Each user can run their own server instance for private groups.
- The server will automatically choose an available port and display connection details.

Launch Options:
After installation, you can launch the applications via:

- Start Menu shortcuts (OpenChat \> OpenChat / Server Manager)
- Desktop shortcuts (if created)
- Batch files in installation directory:
  - OpenChat-App.bat (for the main chat application)
  - Server-Manager.bat (for server management)

<br/>

## 🔧 Configuration

The Server Manager is used to configure server setting. The server will automatically choose an available port and display connection details.

<br/>

## 📄 License

This project is open-source and licensed under the [MIT License](LICENSE).
