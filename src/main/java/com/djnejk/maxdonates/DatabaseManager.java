package com.djnejk.maxdonates;

import java.sql.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DatabaseManager {

    private final MaxDonates plugin;
    private Connection connection;

    public DatabaseManager(MaxDonates plugin) {
        this.plugin = plugin;
    }

    public void connect() throws SQLException {
        String host = plugin.getConfig().getString("mysql.host");
        int port = plugin.getConfig().getInt("mysql.port");
        String database = plugin.getConfig().getString("mysql.database");
        String user = plugin.getConfig().getString("mysql.user");
        String pass = plugin.getConfig().getString("mysql.password");
        String params = plugin.getConfig().getString("mysql.params", "useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");

        String url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?" + params;
        connection = DriverManager.getConnection(url, user, pass);
    }

    public void createTables() throws SQLException {
        execute("""
                CREATE TABLE IF NOT EXISTS md_player_profiles (
                  player_uuid VARCHAR(36) PRIMARY KEY,
                  description TEXT NULL,
                  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """);

        execute("""
                CREATE TABLE IF NOT EXISTS md_companies (
                  id INT AUTO_INCREMENT PRIMARY KEY,
                  name VARCHAR(64) NOT NULL UNIQUE,
                  owner_uuid VARCHAR(36) NOT NULL,
                  description TEXT NULL,
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """);

        execute("""
                CREATE TABLE IF NOT EXISTS md_donations_player (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  donor_uuid VARCHAR(36) NOT NULL,
                  recipient_uuid VARCHAR(36) NOT NULL,
                  amount DOUBLE NOT NULL,
                  notified TINYINT(1) NOT NULL DEFAULT 0,
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  INDEX idx_recipient_created (recipient_uuid, created_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """);

        execute("""
                CREATE TABLE IF NOT EXISTS md_donations_company (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  donor_uuid VARCHAR(36) NOT NULL,
                  company_id INT NOT NULL,
                  owner_uuid VARCHAR(36) NOT NULL,
                  amount DOUBLE NOT NULL,
                  notified TINYINT(1) NOT NULL DEFAULT 0,
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  INDEX idx_company_created (company_id, created_at),
                  INDEX idx_owner_created (owner_uuid, created_at),
                  CONSTRAINT fk_md_company FOREIGN KEY (company_id) REFERENCES md_companies(id) ON DELETE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """);

        ensureColumnExists("md_donations_player", "notified", "TINYINT(1) NOT NULL DEFAULT 0");
        ensureColumnExists("md_donations_company", "notified", "TINYINT(1) NOT NULL DEFAULT 0");
    }

    private void ensureColumnExists(String table, String column, String definition) throws SQLException {
        if (columnExists(table, column)) {
            return;
        }
        execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
    }

    private boolean columnExists(String table, String column) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet rs = metaData.getColumns(connection.getCatalog(), null, table, column)) {
            if (rs.next()) {
                return true;
            }
        }
        try (ResultSet rs = metaData.getColumns(connection.getCatalog(), null, table.toUpperCase(), column)) {
            return rs.next();
        }
    }

    private void execute(String sql) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute(sql);
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException ignored) {
        }
    }

    public int getCompanyCount(UUID owner) throws SQLException {
        String sql = "SELECT COUNT(*) FROM md_companies WHERE owner_uuid=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, owner.toString());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public boolean createCompany(String name, UUID owner) throws SQLException {
        String sql = "INSERT INTO md_companies(name, owner_uuid) VALUES(?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, owner.toString());
            return ps.executeUpdate() > 0;
        }
    }

    public Company getCompanyByName(String name) throws SQLException {
        String sql = "SELECT id,name,owner_uuid,description FROM md_companies WHERE LOWER(name)=LOWER(?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new Company(rs.getInt("id"), rs.getString("name"), UUID.fromString(rs.getString("owner_uuid")), rs.getString("description"));
            }
        }
    }


    public List<String> getAllCompanyNames() throws SQLException {
        String sql = "SELECT name FROM md_companies ORDER BY name";
        List<String> list = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(rs.getString("name"));
            }
        }
        return list;
    }

    public List<Company> getCompaniesByOwner(UUID owner) throws SQLException {
        String sql = "SELECT id,name,owner_uuid,description FROM md_companies WHERE owner_uuid=? ORDER BY name";
        List<Company> list = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, owner.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Company(rs.getInt("id"), rs.getString("name"), UUID.fromString(rs.getString("owner_uuid")), rs.getString("description")));
                }
            }
        }
        return list;
    }

    public boolean removeCompany(String name, UUID owner) throws SQLException {
        String sql = "DELETE FROM md_companies WHERE LOWER(name)=LOWER(?) AND owner_uuid=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, owner.toString());
            return ps.executeUpdate() > 0;
        }
    }

    public boolean updateCompanyDescription(String name, UUID owner, String description) throws SQLException {
        String sql = "UPDATE md_companies SET description=? WHERE LOWER(name)=LOWER(?) AND owner_uuid=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, description);
            ps.setString(2, name);
            ps.setString(3, owner.toString());
            return ps.executeUpdate() > 0;
        }
    }

    public boolean transferCompany(String name, UUID oldOwner, UUID newOwner) throws SQLException {
        String sql = "UPDATE md_companies SET owner_uuid=? WHERE LOWER(name)=LOWER(?) AND owner_uuid=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, newOwner.toString());
            ps.setString(2, name);
            ps.setString(3, oldOwner.toString());
            return ps.executeUpdate() > 0;
        }
    }

    public void setPlayerDescription(UUID uuid, String description) throws SQLException {
        String sql = "INSERT INTO md_player_profiles(player_uuid, description) VALUES(?,?) ON DUPLICATE KEY UPDATE description=VALUES(description)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, description);
            ps.executeUpdate();
        }
    }

    public void addPlayerDonation(UUID donor, UUID recipient, double amount, boolean notified) throws SQLException {
        String sql = "INSERT INTO md_donations_player(donor_uuid, recipient_uuid, amount, notified) VALUES(?,?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, donor.toString());
            ps.setString(2, recipient.toString());
            ps.setDouble(3, amount);
            ps.setBoolean(4, notified);
            ps.executeUpdate();
        }
    }

    public void addCompanyDonation(UUID donor, int companyId, UUID owner, double amount, boolean notified) throws SQLException {
        String sql = "INSERT INTO md_donations_company(donor_uuid, company_id, owner_uuid, amount, notified) VALUES(?,?,?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, donor.toString());
            ps.setInt(2, companyId);
            ps.setString(3, owner.toString());
            ps.setDouble(4, amount);
            ps.setBoolean(5, notified);
            ps.executeUpdate();
        }
    }

    public List<PendingDonation> getPendingPlayerDonations(UUID recipient) throws SQLException {
        String sql = "SELECT id, donor_uuid, amount FROM md_donations_player WHERE recipient_uuid=? AND notified=0 ORDER BY created_at ASC";
        List<PendingDonation> list = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, recipient.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new PendingDonation(rs.getLong("id"), UUID.fromString(rs.getString("donor_uuid")), rs.getDouble("amount"), null));
                }
            }
        }
        return list;
    }

    public List<PendingDonation> getPendingCompanyDonations(UUID owner) throws SQLException {
        String sql = "SELECT d.id, d.donor_uuid, d.amount, c.name AS company_name " +
                "FROM md_donations_company d " +
                "JOIN md_companies c ON c.id = d.company_id " +
                "WHERE d.owner_uuid=? AND d.notified=0 ORDER BY d.created_at ASC";
        List<PendingDonation> list = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, owner.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new PendingDonation(rs.getLong("id"), UUID.fromString(rs.getString("donor_uuid")), rs.getDouble("amount"), rs.getString("company_name")));
                }
            }
        }
        return list;
    }

    public void markPlayerDonationsNotified(List<Long> ids) throws SQLException {
        markNotified("md_donations_player", ids);
    }

    public void markCompanyDonationsNotified(List<Long> ids) throws SQLException {
        markNotified("md_donations_company", ids);
    }

    private void markNotified(String table, List<Long> ids) throws SQLException {
        if (ids.isEmpty()) return;
        StringBuilder sql = new StringBuilder("UPDATE ").append(table).append(" SET notified=1 WHERE id IN (");
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) sql.append(',');
            sql.append('?');
        }
        sql.append(')');

        try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            int i = 1;
            for (Long id : ids) {
                ps.setLong(i++, id);
            }
            ps.executeUpdate();
        }
    }

    public List<DonationRecord> getPlayerDonations(UUID recipient, int page, int pageSize) throws SQLException {
        String sql = "SELECT donor_uuid,amount,created_at FROM md_donations_player WHERE recipient_uuid=? ORDER BY created_at DESC LIMIT ? OFFSET ?";
        return loadDonations(sql, recipient.toString(), page, pageSize);
    }

    public List<DonationRecord> getCompanyDonations(int companyId, int page, int pageSize) throws SQLException {
        String sql = "SELECT donor_uuid,amount,created_at FROM md_donations_company WHERE company_id=? ORDER BY created_at DESC LIMIT ? OFFSET ?";
        return loadDonations(sql, String.valueOf(companyId), page, pageSize);
    }

    public Stats getPlayerStats(UUID recipient) throws SQLException {
        return computeStats("md_donations_player", "recipient_uuid", recipient.toString());
    }

    public Stats getCompanyStats(int companyId) throws SQLException {
        return computeStats("md_donations_company", "company_id", String.valueOf(companyId));
    }

    public double totalSentBy(UUID donor) throws SQLException {
        String sql = "SELECT COALESCE(SUM(amount),0) FROM md_donations_player WHERE donor_uuid=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, donor.toString());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getDouble(1);
            }
        }
    }

    private List<DonationRecord> loadDonations(String sql, String idValue, int page, int pageSize) throws SQLException {
        List<DonationRecord> list = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            if (idValue.matches("\\d+")) {
                ps.setInt(1, Integer.parseInt(idValue));
            } else {
                ps.setString(1, idValue);
            }
            ps.setInt(2, pageSize);
            ps.setInt(3, Math.max(0, (page - 1) * pageSize));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new DonationRecord(UUID.fromString(rs.getString("donor_uuid")), rs.getDouble("amount"), rs.getTimestamp("created_at").toInstant()));
                }
            }
        }
        return list;
    }

    private Stats computeStats(String table, String column, String value) throws SQLException {
        String sql = "SELECT COALESCE(SUM(amount),0) AS total, " +
                "COALESCE(SUM(CASE WHEN created_at >= ? THEN amount ELSE 0 END),0) AS day_total, " +
                "COALESCE(SUM(CASE WHEN created_at >= ? THEN amount ELSE 0 END),0) AS week_total, " +
                "COALESCE(SUM(CASE WHEN created_at >= ? THEN amount ELSE 0 END),0) AS month_total, " +
                "COALESCE(SUM(CASE WHEN created_at >= ? THEN amount ELSE 0 END),0) AS year_total " +
                "FROM " + table + " WHERE " + column + "=?";

        Instant now = Instant.now();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.from(now.minus(1, ChronoUnit.DAYS)));
            ps.setTimestamp(2, Timestamp.from(now.minus(7, ChronoUnit.DAYS)));
            ps.setTimestamp(3, Timestamp.from(now.minus(30, ChronoUnit.DAYS)));
            ps.setTimestamp(4, Timestamp.from(now.minus(365, ChronoUnit.DAYS)));
            if (value.matches("\\d+")) ps.setInt(5, Integer.parseInt(value)); else ps.setString(5, value);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return new Stats(rs.getDouble("total"), rs.getDouble("day_total"), rs.getDouble("week_total"), rs.getDouble("month_total"), rs.getDouble("year_total"));
            }
        }
    }

    public record Company(int id, String name, UUID owner, String description) {
    }

    public record DonationRecord(UUID donor, double amount, Instant createdAt) {
    }

    public record Stats(double total, double day, double week, double month, double year) {
    }

    public record PendingDonation(long id, UUID donor, double amount, String companyName) {
    }
}
