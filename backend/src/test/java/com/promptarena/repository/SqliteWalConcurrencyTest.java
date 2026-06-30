package com.promptarena.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies the runtime SQLite posture from {@code application.properties} (T059): with {@code
 * journal_mode=WAL} and a {@code busy_timeout}, concurrent writers from independent connections all
 * commit successfully instead of failing with {@code SQLITE_BUSY}. Runs against a throwaway
 * file-backed database (WAL is a no-op on {@code :memory:}), fully isolated from the app.
 */
class SqliteWalConcurrencyTest {

  private static final int WRITERS = 8;
  private static final int ROWS_PER_WRITER = 25;

  @Test
  void concurrentWritersDoNotHitSqliteBusy(@TempDir Path dir) throws Exception {
    Path db = dir.resolve("wal-probe.db");
    String url = "jdbc:sqlite:" + db.toAbsolutePath() + "?journal_mode=WAL&busy_timeout=5000";

    try (Connection setup = DriverManager.getConnection(url);
        Statement stmt = setup.createStatement()) {
      stmt.execute("CREATE TABLE entry (id INTEGER PRIMARY KEY AUTOINCREMENT, writer INT)");
      try (ResultSet rs = stmt.executeQuery("PRAGMA journal_mode")) {
        assertThat(rs.next()).isTrue();
        assertThat(rs.getString(1)).isEqualToIgnoringCase("wal");
      }
    }

    ExecutorService pool = Executors.newFixedThreadPool(WRITERS);
    CountDownLatch start = new CountDownLatch(1);
    AtomicReference<Throwable> failure = new AtomicReference<>();

    for (int w = 0; w < WRITERS; w++) {
      int writer = w;
      pool.execute(
          () -> {
            try (Connection conn = DriverManager.getConnection(url)) {
              start.await();
              for (int row = 0; row < ROWS_PER_WRITER; row++) {
                try (Statement stmt = conn.createStatement()) {
                  stmt.executeUpdate("INSERT INTO entry (writer) VALUES (" + writer + ")");
                }
              }
            } catch (Throwable ex) {
              failure.compareAndSet(null, ex);
            }
          });
    }

    start.countDown();
    pool.shutdown();
    assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
    assertThat(failure.get()).as("no writer should fail (e.g. SQLITE_BUSY)").isNull();

    try (Connection check = DriverManager.getConnection(url);
        Statement stmt = check.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM entry")) {
      assertThat(rs.next()).isTrue();
      assertThat(rs.getInt(1)).isEqualTo(WRITERS * ROWS_PER_WRITER);
    }
  }
}
