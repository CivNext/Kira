package com.github.maxopoly.Kira.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import org.apache.logging.log4j.Logger;

import com.github.maxopoly.Kira.permission.KiraPermission;
import com.github.maxopoly.Kira.permission.KiraRole;
import com.github.maxopoly.Kira.permission.KiraRoleManager;
import com.github.maxopoly.Kira.user.User;

public class DAO {

	private static final String timestampField = "last_updated timestamp with time zone not null default now()";

	private DBConnection db;
	private Logger logger;

	public DAO(DBConnection connection, Logger logger) {
		this.logger = logger;
		this.db = connection;
		if (!createTables()) {
			logger.error("Failed to init account DB, shutting down");
			System.exit(1);
		}
	}

	private boolean createTables() {
		try (Connection conn = db.getConnection()) {
			try (PreparedStatement prep = conn.prepareStatement("CREATE TABLE IF NOT EXISTS users "
					+ "(id serial primary key, discord_id bigint not null unique, name varchar(255) unique, "
					+ "uuid char(36) unique, reddit varchar(255) unique," + timestampField + ");")) {
				prep.execute();
			}
			try (PreparedStatement prep = conn.prepareStatement("CREATE TABLE IF NOT EXISTS group_chats "
					+ "(id serial primary key, channel_id bigint, guild_id bigint, name varchar(255) not null unique,"
					+ timestampField + ");")) {
				prep.execute();
			}
			try (PreparedStatement prep = conn.prepareStatement("CREATE TABLE IF NOT EXISTS group_chat_members "
					+ "(user_id references users(id), group_id references group_chats(id)," + timestampField
					+ ", unique(group_id, user_id);")) {
				prep.execute();
			}
			try (PreparedStatement prep = conn.prepareStatement("CREATE TABLE IF NOT EXISTS permissions "
					+ "(id serial primary key, name varchar(255) not null unique," + timestampField + ");")) {
				prep.execute();
			}
			try (PreparedStatement prep = conn.prepareStatement("CREATE TABLE IF NOT EXISTS roles "
					+ "(id serial primary key, name varchar(255) not null unique," + timestampField + ");")) {
				prep.execute();
			}
			try (PreparedStatement prep = conn.prepareStatement("CREATE TABLE IF NOT EXISTS role_permissions "
					+ "(role_id references role(id), permission_id references permissions(id)," + timestampField
					+ ", unique(role_id, permission_id);")) {
				prep.execute();
			}
			try (PreparedStatement prep = conn.prepareStatement("CREATE TABLE IF NOT EXISTS role_members "
					+ "(user_id references users(id), role_id references roles(id)," + timestampField
					+ ", unique(role_id, user_id);")) {
				prep.execute();
			}
		} catch (SQLException e) {
			logger.error("Failed to create table", e);
			return false;
		}
		return true;
	}

	public Set<User> loadUsers() {
		Set<User> result = new HashSet<>();
		try (Connection conn = db.getConnection();
				PreparedStatement prep = conn.prepareStatement("select id, name, discord_id, uuid, reddit from users;");
				ResultSet rs = prep.executeQuery()) {
			while (rs.next()) {
				int id = rs.getInt(1);
				String name = rs.getString(2);
				long discordID = rs.getLong(3);
				String uuidString = rs.getString(4);
				UUID uuid = uuidString != null ? UUID.fromString(uuidString) : null;
				String redditAcc = rs.getString(5);
				result.add(new User(id, name, discordID, uuid, redditAcc));
			}
		} catch (SQLException e) {
			logger.error("Failed to retrieve users", e);
			return null;
		}
		return result;
	}

	public int createUser(long discordID) {
		try (Connection conn = db.getConnection();
				PreparedStatement prep = conn.prepareStatement("insert into users (discord_id) values (?);",
						Statement.RETURN_GENERATED_KEYS)) {
			prep.setLong(1, discordID);
			prep.execute();
			try (ResultSet rs = prep.getGeneratedKeys()) {
				if (!rs.next()) {
					logger.error("No key created for user?");
					return -1;
				}
				return rs.getInt(1);
			}
		} catch (SQLException e) {
			logger.error("Failed to create user", e);
			return -1;
		}
	}

	public void updateUser(User user) {
		try (Connection conn = db.getConnection();
				PreparedStatement prep = conn.prepareStatement(
						"update users set name = ?, discord_id = ?, uuid = ?, reddit = ? where id = ?;")) {
			prep.setString(1, user.getName());
			if (user.hasDiscord()) {
				prep.setLong(2, user.getDiscordID());
			} else {
				prep.setObject(2, null);
			}
			if (user.getIngameUUID() != null) {
				prep.setString(3, user.getIngameUUID().toString());
			} else {
				prep.setString(3, null);
			}
			prep.setString(4, user.getRedditAccount());
			prep.setInt(5, user.getID());
			prep.executeUpdate();
		} catch (SQLException e) {
			logger.error("Failed to update user", e);
		}
	}
	
	public KiraPermission registerPermission(String perm) {
		try (Connection conn = db.getConnection();
				PreparedStatement prep = conn.prepareStatement("insert into permissions (name) values (?);",
						Statement.RETURN_GENERATED_KEYS)) {
			prep.setString(1, perm);
			prep.execute();
			try (ResultSet rs = prep.getGeneratedKeys()) {
				if (!rs.next()) {
					logger.error("No key created for perm?");
					return null;
				}
				int id = rs.getInt(1);
				return new KiraPermission(id, perm);
			}
		} catch (SQLException e) {
			logger.error("Failed to create permission", e);
			return null;
		}
	}
	
	public KiraRole registerRole(String name) {
		try (Connection conn = db.getConnection();
				PreparedStatement prep = conn.prepareStatement("insert into roles (name) values (?);",
						Statement.RETURN_GENERATED_KEYS)) {
			prep.setString(1, name);
			prep.execute();
			try (ResultSet rs = prep.getGeneratedKeys()) {
				if (!rs.next()) {
					logger.error("No key created for role?");
					return null;
				}
				int id = rs.getInt(1);
				return new KiraRole(name, id);
			}
		} catch (SQLException e) {
			logger.error("Failed to create role", e);
			return null;
		}
	}
	
	public void addPermissionToRole(KiraPermission permission, KiraRole role) {
		try (Connection conn = db.getConnection();
				PreparedStatement prep = conn.prepareStatement("insert into role_permissions (role_id, permission_id) "
						+ "values (?, ?);")) {
			prep.setInt(1, role.getID());
			prep.setInt(2, permission.getID());
			prep.execute();
		} catch (SQLException e) {
			logger.error("Failed to insert role permission", e);
		}
	}
	
	public void removePermissionFromRole(KiraPermission permission, KiraRole role) {
		try (Connection conn = db.getConnection();
				PreparedStatement prep = conn.prepareStatement("insert into role_permissions (role_id, permission_id) "
						+ "values (?, ?);")) {
			prep.setInt(1, role.getID());
			prep.setInt(2, permission.getID());
			prep.execute();
		} catch (SQLException e) {
			logger.error("Failed to insert role permission", e);
		}
	}
	
	public void addUserToRole(User user, KiraRole role) {
		try (Connection conn = db.getConnection();
				PreparedStatement prep = conn.prepareStatement("insert into role_members (role_id, user_id) "
						+ "values (?, ?);")) {
			prep.setInt(1, role.getID());
			prep.setInt(2, user.getID());
			prep.execute();
		} catch (SQLException e) {
			logger.error("Failed to insert role addition for user", e);
		}
	}
	
	public void takeRoleFromUser(User user, KiraRole role) {
		try (Connection conn = db.getConnection();
				PreparedStatement prep = conn.prepareStatement("delete from role_members where role_id=?, user_id=?;")) {
			prep.setInt(1, role.getID());
			prep.setInt(2, user.getID());
			prep.execute();
		} catch (SQLException e) {
			logger.error("Failed to delete role for user", e);
		}
	}

	public KiraRoleManager loadAllRoles() {
		KiraRoleManager manager = new KiraRoleManager();
		Map<Integer, KiraPermission> permsById = new TreeMap<Integer, KiraPermission>();
		Map<Integer, KiraRole> roleById = new TreeMap<Integer, KiraRole>();
		try (Connection conn = db.getConnection()) {
			try (PreparedStatement ps = conn.prepareStatement("select id, name from permissions;");
					ResultSet rs = ps.executeQuery()) {
				while(rs.next()) {
					int id = rs.getInt(1);
					String name = rs.getString(2);
					KiraPermission perm = new KiraPermission(id, name);
					permsById.put(id, perm);
				}
			}
			try (PreparedStatement ps = conn.prepareStatement("select id, name from roles;");
					ResultSet rs = ps.executeQuery()) {
				while(rs.next()) {
					int id = rs.getInt(1);
					String name = rs.getString(2);
					KiraRole role = new KiraRole(name, id);
					roleById.put(id, role);
					manager.registerRole(role);
				}
			}
			try (PreparedStatement ps = conn.prepareStatement("select role_id, permission_id from role_permissions;");
					ResultSet rs = ps.executeQuery()) {
				while(rs.next()) {
					int roleID = rs.getInt(1);
					int permissionID = rs.getInt(2);
					KiraRole role = roleById.get(roleID);
					KiraPermission perm = permsById.get(permissionID);
					role.addPermission(perm);
				}
			}
			try (PreparedStatement ps = conn.prepareStatement("select user_id, role_id from role_members;");
					ResultSet rs = ps.executeQuery()) {
				while(rs.next()) {
					int userID = rs.getInt(1);
					int roleID = rs.getInt(2);
					KiraRole role = roleById.get(roleID);
					manager.addRole(userID, role);
				}
			}
		} catch (SQLException e) {
			logger.error("Failed to load permissions", e);
			return null;
		}
		return manager;
	}

}